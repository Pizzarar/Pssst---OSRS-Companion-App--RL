package com.pssst.runelite;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Lightweight, zero-external-dependency RFC 6455 WebSocket and HTTP fallback server.
 * Binds locally using java.net.ServerSocket for 100% RuneLite Plugin Hub compliance.
 */
@Slf4j
@Singleton
public class LocalWebSocketServer
{
	private static final String WS_MAGIC_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	private ServerSocket serverSocket;
	private Thread acceptThread;
	private final AtomicBoolean isRunning = new AtomicBoolean(false);
	private final Set<ClientSession> activeClients = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final ExecutorService broadcastExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("pssst-ws-tx"));

	private volatile int currentPort = -1;
	private volatile boolean currentLoopbackOnly = false;
	private volatile String expectedSyncToken = "";
	private volatile String latestStateJson = null;

	@Inject
	public LocalWebSocketServer()
	{
	}

	public synchronized void start(int port, boolean loopbackOnly, String syncToken)
	{
		if (isRunning.get() && currentPort == port && currentLoopbackOnly == loopbackOnly)
		{
			this.expectedSyncToken = syncToken != null ? syncToken.trim() : "";
			return;
		}

		stop();

		this.currentPort = port;
		this.currentLoopbackOnly = loopbackOnly;
		this.expectedSyncToken = syncToken != null ? syncToken.trim() : "";

		try
		{
			InetAddress bindAddr = loopbackOnly
				? InetAddress.getByName("127.0.0.1")
				: InetAddress.getByName("0.0.0.0");

			serverSocket = new ServerSocket(port, 50, bindAddr);
			isRunning.set(true);

			acceptThread = new Thread(this::runAcceptLoop, "pssst-ws-accept");
			acceptThread.setDaemon(true);
			acceptThread.start();

			log.info("Local WebSocket server listening on {}:{}", bindAddr.getHostAddress(), port);
		}
		catch (IOException e)
		{
			log.error("Failed to start local WebSocket server on port {}: {}", port, e.getMessage());
			stop();
		}
	}

	public synchronized void stop()
	{
		isRunning.set(false);

		if (serverSocket != null)
		{
			try
			{
				serverSocket.close();
			}
			catch (IOException ignored)
			{
			}
			serverSocket = null;
		}

		if (acceptThread != null)
		{
			acceptThread.interrupt();
			acceptThread = null;
		}

		for (ClientSession client : activeClients)
		{
			client.close();
		}
		activeClients.clear();

		currentPort = -1;
		log.info("Local WebSocket server stopped");
	}

	public boolean isRunning()
	{
		return isRunning.get();
	}

	public int getPort()
	{
		return currentPort;
	}

	public int getClientCount()
	{
		return activeClients.size();
	}

	/**
	 * Broadcasts raw JSON to all active WebSocket clients asynchronously.
	 */
	public void broadcast(String jsonPayload)
	{
		if (jsonPayload == null)
		{
			return;
		}

		this.latestStateJson = jsonPayload;

		if (!isRunning.get() || activeClients.isEmpty())
		{
			return;
		}

		byte[] frame = encodeTextFrame(jsonPayload);
		broadcastExecutor.execute(() -> {
			for (ClientSession client : activeClients)
			{
				try
				{
					client.sendFrame(frame);
				}
				catch (IOException e)
				{
					log.debug("Failed to send frame to client {}: {}", client.remoteAddress, e.getMessage());
					client.close();
					activeClients.remove(client);
				}
			}
		});
	}

	private void runAcceptLoop()
	{
		while (isRunning.get() && serverSocket != null && !serverSocket.isClosed())
		{
			try
			{
				Socket socket = serverSocket.accept();
				Thread clientThread = new Thread(() -> handleIncomingConnection(socket), "pssst-ws-client");
				clientThread.setDaemon(true);
				clientThread.start();
			}
			catch (SocketException e)
			{
				if (!isRunning.get())
				{
					break;
				}
				log.warn("WebSocket server accept error: {}", e.getMessage());
			}
			catch (IOException e)
			{
				log.warn("WebSocket server accept IO error: {}", e.getMessage());
			}
		}
	}

	private void handleIncomingConnection(Socket socket)
	{
		try
		{
			socket.setTcpNoDelay(true);
			// Bound the header read so an idle/slow peer can't pin a thread forever
			socket.setSoTimeout(5000);
			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();

			// Read HTTP request headers
			HttpRequest request = readHttpRequest(in);
			if (request == null)
			{
				closeQuietly(socket);
				return;
			}

			// Validate token (Authorization header or ?token= query parameter)
			if (!isAuthorized(request))
			{
				log.warn("Rejecting unauthorized request from {} to {}", socket.getRemoteSocketAddress(), request.path);
				sendHttpResponse(out, 401, "Unauthorized", "text/plain", "Unauthorized: Invalid or missing sync token\r\n");
				closeQuietly(socket);
				return;
			}

			// Handle CORS Preflight OPTIONS
			if ("OPTIONS".equalsIgnoreCase(request.method))
			{
				sendCorsOptionsResponse(out);
				closeQuietly(socket);
				return;
			}

			// Check for WebSocket upgrade
			String upgradeHeader = request.getHeader("Upgrade");
			String wsKey = request.getHeader("Sec-WebSocket-Key");

			if ("websocket".equalsIgnoreCase(upgradeHeader) && wsKey != null && !wsKey.trim().isEmpty())
			{
				// Complete RFC 6455 Handshake
				String acceptKey = generateSecWebSocketAccept(wsKey.trim());
				String handshakeResponse =
					"HTTP/1.1 101 Switching Protocols\r\n" +
					"Upgrade: websocket\r\n" +
					"Connection: Upgrade\r\n" +
					"Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";

				out.write(handshakeResponse.getBytes(StandardCharsets.US_ASCII));
				out.flush();
				socket.setSoTimeout(0); // upgraded: long-lived connection, reader loop blocks by design

				ClientSession session = new ClientSession(socket, in, out);
				activeClients.add(session);
				log.info("WebSocket client connected: {}", socket.getRemoteSocketAddress());

				// Push initial state immediately upon connection
				String initialSnapshot = latestStateJson;
				if (initialSnapshot != null)
				{
					try
					{
						session.sendFrame(encodeTextFrame(initialSnapshot));
					}
					catch (IOException e)
					{
						log.warn("Failed to send initial snapshot to {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
					}
				}

				// Run reader loop for this WebSocket client (processes Close and Ping)
				session.runReaderLoop(activeClients);
			}
			else if ("GET".equalsIgnoreCase(request.method) && (request.path.endsWith("/state") || "/".equals(request.path)))
			{
				// HTTP Fallback: serve latest JSON snapshot
				String snapshot = latestStateJson;
				if (snapshot != null)
				{
					sendHttpResponse(out, 200, "OK", "application/json", snapshot);
				}
				else
				{
					sendHttpResponse(out, 404, "Not Found", "application/json", "{\"error\":\"No state available yet\"}");
				}
				closeQuietly(socket);
			}
			else if ("POST".equalsIgnoreCase(request.method) && request.path.endsWith("/sync"))
			{
				sendHttpResponse(out, 200, "OK", "application/json", "{\"success\":true}");
				closeQuietly(socket);
			}
			else if ("GET".equalsIgnoreCase(request.method) && request.path.endsWith("/health"))
			{
				String healthJson = "{\"status\":\"ok\",\"clients\":" + activeClients.size() + "}";
				sendHttpResponse(out, 200, "OK", "application/json", healthJson);
				closeQuietly(socket);
			}
			else
			{
				sendHttpResponse(out, 404, "Not Found", "text/plain", "Not Found\r\n");
				closeQuietly(socket);
			}
		}
		catch (Exception e)
		{
			log.debug("Connection handling terminated for {}: {}", socket.getRemoteSocketAddress(), e.getMessage());
			closeQuietly(socket);
		}
	}

	private boolean isAuthorized(HttpRequest request)
	{
		String expected = expectedSyncToken;
		if (expected == null || expected.trim().isEmpty())
		{
			return true; // No token configured yet
		}

		// 1. Check Authorization: Bearer <token>
		String authHeader = request.getHeader("Authorization");
		if (authHeader != null)
		{
			String[] parts = authHeader.trim().split("\\s+", 2);
			if (parts.length == 2 && "Bearer".equalsIgnoreCase(parts[0]))
			{
				if (expected.equals(parts[1].trim()))
				{
					return true;
				}
			}
		}

		// 2. Check ?token=<token> in query params
		String tokenParam = request.queryParams.get("token");
		return tokenParam != null && expected.equals(tokenParam.trim());
	}

	private static HttpRequest readHttpRequest(InputStream in) throws IOException
	{
		ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
		String requestLine = null;
		Map<String, String> headers = new HashMap<>();

		int prev = -1;
		int c;
		while ((c = in.read()) != -1)
		{
			if (prev == '\r' && c == '\n')
			{
				byte[] bytes = lineBuffer.toByteArray();
				// Exclude \r from the end
				String line = new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1).trim();
				lineBuffer.reset();

				if (requestLine == null)
				{
					if (line.isEmpty())
					{
						continue; // skip leading CRLF
					}
					requestLine = line;
				}
				else if (line.isEmpty())
				{
					// End of HTTP headers
					break;
				}
				else
				{
					int colonIdx = line.indexOf(':');
					if (colonIdx > 0)
					{
						String headerName = line.substring(0, colonIdx).trim().toLowerCase();
						String headerValue = line.substring(colonIdx + 1).trim();
						headers.put(headerName, headerValue);
					}
				}
			}
			else
			{
				lineBuffer.write(c);
			}
			prev = c;

			// Cap header size at 8KB
			if (lineBuffer.size() > 8192)
			{
				throw new IOException("HTTP request header too large");
			}
		}

		if (requestLine == null)
		{
			return null;
		}

		String[] parts = requestLine.split("\\s+");
		if (parts.length < 2)
		{
			return null;
		}

		String method = parts[0];
		String rawUri = parts[1];
		String path = rawUri;
		Map<String, String> queryParams = new HashMap<>();

		int questionIdx = rawUri.indexOf('?');
		if (questionIdx >= 0)
		{
			path = rawUri.substring(0, questionIdx);
			String query = rawUri.substring(questionIdx + 1);
			for (String pair : query.split("&"))
			{
				int eqIdx = pair.indexOf('=');
				if (eqIdx > 0)
				{
					queryParams.put(pair.substring(0, eqIdx), pair.substring(eqIdx + 1));
				}
				else if (!pair.isEmpty())
				{
					queryParams.put(pair, "");
				}
			}
		}

		return new HttpRequest(method, path, queryParams, headers);
	}

	private static void sendHttpResponse(OutputStream out, int status, String message, String contentType, String body) throws IOException
	{
		byte[] bodyBytes = body != null ? body.getBytes(StandardCharsets.UTF_8) : new byte[0];
		String response =
			"HTTP/1.1 " + status + " " + message + "\r\n" +
			"Content-Type: " + contentType + "; charset=utf-8\r\n" +
			"Content-Length: " + bodyBytes.length + "\r\n" +
			"Access-Control-Allow-Origin: *\r\n" +
			"Access-Control-Allow-Headers: Authorization, Content-Type\r\n" +
			"Connection: close\r\n\r\n";

		out.write(response.getBytes(StandardCharsets.US_ASCII));
		if (bodyBytes.length > 0)
		{
			out.write(bodyBytes);
		}
		out.flush();
	}

	private static void sendCorsOptionsResponse(OutputStream out) throws IOException
	{
		String response =
			"HTTP/1.1 204 No Content\r\n" +
			"Access-Control-Allow-Origin: *\r\n" +
			"Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
			"Access-Control-Allow-Headers: Authorization, Content-Type, Upgrade, Sec-WebSocket-Key, Sec-WebSocket-Version\r\n" +
			"Access-Control-Max-Age: 86400\r\n" +
			"Connection: close\r\n\r\n";

		out.write(response.getBytes(StandardCharsets.US_ASCII));
		out.flush();
	}

	private static String generateSecWebSocketAccept(String key)
	{
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA-1");
			byte[] digest = md.digest((key + WS_MAGIC_GUID).getBytes(StandardCharsets.ISO_8859_1));
			return Base64.getEncoder().encodeToString(digest);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-1 algorithm not available", e);
		}
	}

	/**
	 * Encodes an unmasked RFC 6455 server-to-client Text frame.
	 */
	public static byte[] encodeTextFrame(String text)
	{
		byte[] payload = text.getBytes(StandardCharsets.UTF_8);
		int length = payload.length;
		ByteArrayOutputStream out = new ByteArrayOutputStream(length + 10);

		// Byte 0: FIN = 1, Opcode = 1 (Text) -> 0x81
		out.write(0x81);

		// Byte 1+: Payload length (unmasked from server)
		if (length <= 125)
		{
			out.write(length);
		}
		else if (length <= 65535)
		{
			out.write(126);
			out.write((length >>> 8) & 0xFF);
			out.write(length & 0xFF);
		}
		else
		{
			out.write(127);
			for (int i = 56; i >= 0; i -= 8)
			{
				out.write((int) (((long) length >>> i) & 0xFF));
			}
		}

		out.write(payload, 0, length);
		return out.toByteArray();
	}

	private static void closeQuietly(Closeable c)
	{
		if (c != null)
		{
			try
			{
				c.close();
			}
			catch (IOException ignored)
			{
			}
		}
	}

	private static class HttpRequest
	{
		final String method;
		final String path;
		final Map<String, String> queryParams;
		final Map<String, String> headers;

		HttpRequest(String method, String path, Map<String, String> queryParams, Map<String, String> headers)
		{
			this.method = method;
			this.path = path;
			this.queryParams = queryParams;
			this.headers = headers;
		}

		String getHeader(String name)
		{
			return headers.get(name.toLowerCase());
		}
	}

	private static class ClientSession
	{
		final Socket socket;
		final InputStream in;
		final OutputStream out;
		final String remoteAddress;
		final AtomicBoolean closed = new AtomicBoolean(false);

		ClientSession(Socket socket, InputStream in, OutputStream out)
		{
			this.socket = socket;
			this.in = in;
			this.out = out;
			this.remoteAddress = String.valueOf(socket.getRemoteSocketAddress());
		}

		synchronized void sendFrame(byte[] frame) throws IOException
		{
			if (closed.get())
			{
				throw new SocketException("Session closed");
			}
			out.write(frame);
			out.flush();
		}

		void runReaderLoop(Set<ClientSession> pool)
		{
			try
			{
				while (!closed.get())
				{
					int b0 = in.read();
					if (b0 == -1)
					{
						break; // Client closed
					}

					int opcode = b0 & 0x0F;
					int b1 = in.read();
					if (b1 == -1)
					{
						break;
					}

					boolean masked = (b1 & 0x80) != 0;
					long payloadLen = b1 & 0x7F;

					if (payloadLen == 126)
					{
						int len0 = in.read();
						int len1 = in.read();
						if (len0 == -1 || len1 == -1) break;
						payloadLen = ((len0 & 0xFF) << 8) | (len1 & 0xFF);
					}
					else if (payloadLen == 127)
					{
						payloadLen = 0;
						for (int i = 0; i < 8; i++)
						{
							int b = in.read();
							if (b == -1) break;
							payloadLen = (payloadLen << 8) | (b & 0xFF);
						}
					}

					byte[] maskingKey = null;
					if (masked)
					{
						maskingKey = new byte[4];
						int read = in.read(maskingKey);
						if (read < 4) break;
					}

					// Discard or read payload
					for (long i = 0; i < payloadLen; i++)
					{
						int dataByte = in.read();
						if (dataByte == -1) break;
					}

					if (opcode == 0x8)
					{
						// Close frame: echo close frame and exit
						try
						{
							sendFrame(new byte[]{(byte) 0x88, 0x00});
						}
						catch (IOException ignored)
						{
						}
						break;
					}
					else if (opcode == 0x9)
					{
						// Ping: echo Pong (0x8A)
						try
						{
							sendFrame(new byte[]{(byte) 0x8A, 0x00});
						}
						catch (IOException ignored)
						{
						}
					}
				}
			}
			catch (Exception ignored)
			{
			}
			finally
			{
				close();
				pool.remove(this);
				log.info("WebSocket client disconnected: {}", remoteAddress);
			}
		}

		void close()
		{
			if (closed.compareAndSet(false, true))
			{
				closeQuietly(socket);
			}
		}
	}

	private static class NamedThreadFactory implements ThreadFactory
	{
		private final String prefix;
		private final AtomicInteger counter = new AtomicInteger(1);

		NamedThreadFactory(String prefix)
		{
			this.prefix = prefix;
		}

		@Override
		public Thread newThread(Runnable r)
		{
			Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
			t.setDaemon(true);
			return t;
		}
	}
}
