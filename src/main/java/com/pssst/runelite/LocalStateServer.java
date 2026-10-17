package com.pssst.runelite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Serves the latest state to the phone: {@code GET /state} with {@code Authorization: Bearer <sync token>}.
 *
 * Deliberately tiny, because it is a listening socket on the player's PC:
 * - one route, GET only, no WebSocket, no CORS, and the token is accepted only in the Authorization header
 *   (a query parameter would end up in logs and browser history);
 * - it refuses to start without a token, so it can never serve unauthenticated;
 * - request reads are bounded in size (8 KB) and total time (3 s), and requests are handled one at a time;
 * - binds to 127.0.0.1 unless the player explicitly opts into LAN exposure.
 */
@Slf4j
@Singleton
public class LocalStateServer
{
	private static final int MAX_REQUEST_BYTES = 8 * 1024;
	private static final long REQUEST_DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(3);
	private static final int READ_TIMEOUT_MS = 1000;

	private ServerSocket serverSocket;
	private Thread acceptThread;
	private final AtomicBoolean running = new AtomicBoolean(false);

	private volatile int currentPort = -1;
	private volatile boolean currentLoopbackOnly = true;
	private volatile byte[] expectedToken = new byte[0];
	private volatile byte[] latestStateJson;

	@Inject
	public LocalStateServer()
	{
	}

	public synchronized void start(int port, boolean loopbackOnly, String syncToken)
	{
		String token = syncToken == null ? "" : syncToken.trim();
		if (token.isEmpty())
		{
			log.warn("Local server not started: no sync token is set");
			stop();
			return;
		}

		this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
		if (running.get() && currentPort == port && currentLoopbackOnly == loopbackOnly)
		{
			return;
		}

		stop();
		this.currentPort = port;
		this.currentLoopbackOnly = loopbackOnly;

		try
		{
			InetAddress bindAddr = InetAddress.getByName(loopbackOnly ? "127.0.0.1" : "0.0.0.0");
			serverSocket = new ServerSocket(port, 20, bindAddr);
			running.set(true);

			acceptThread = new Thread(this::acceptLoop, "pssst-local-state");
			acceptThread.setDaemon(true);
			acceptThread.start();

			log.info("Local state server listening on {}:{}", bindAddr.getHostAddress(), port);
		}
		catch (IOException e)
		{
			log.error("Failed to start local state server on port {}: {}", port, e.getMessage());
			stop();
		}
	}

	public synchronized void stop()
	{
		running.set(false);
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
		currentPort = -1;
	}

	public boolean isRunning()
	{
		return running.get();
	}

	/** Replaces the document served at {@code /state}. */
	public void publish(String envelopeJson)
	{
		latestStateJson = envelopeJson == null ? null : envelopeJson.getBytes(StandardCharsets.UTF_8);
	}

	private void acceptLoop()
	{
		ServerSocket socket = serverSocket;
		while (running.get() && socket != null && !socket.isClosed())
		{
			try (Socket client = socket.accept())
			{
				handle(client);
			}
			catch (IOException e)
			{
				if (running.get())
				{
					log.debug("Local state server connection error: {}", e.getMessage());
				}
			}
		}
	}

	private void handle(Socket client) throws IOException
	{
		client.setSoTimeout(READ_TIMEOUT_MS);
		OutputStream out = client.getOutputStream();

		String head = readHead(client.getInputStream());
		if (head == null)
		{
			respond(out, 431, "text/plain", "Request too large or too slow\n");
			return;
		}

		String[] lines = head.split("\r\n");
		String[] requestLine = lines[0].split(" ");
		if (requestLine.length < 2)
		{
			respond(out, 400, "text/plain", "Bad request\n");
			return;
		}

		if (!isAuthorized(lines))
		{
			respond(out, 401, "text/plain", "Unauthorized\n");
			return;
		}
		if (!"GET".equals(requestLine[0]))
		{
			respond(out, 405, "text/plain", "Method not allowed\n");
			return;
		}
		String path = requestLine[1];
		int query = path.indexOf('?');
		if (query >= 0)
		{
			path = path.substring(0, query);
		}
		byte[] state = latestStateJson;
		if (!"/state".equals(path) || state == null)
		{
			respond(out, 404, "text/plain", "Not found\n");
			return;
		}
		respond(out, 200, "application/json; charset=utf-8", state);
	}

	/** @return everything up to the blank line, or null if the request is oversized or too slow */
	private static String readHead(InputStream in) throws IOException
	{
		byte[] buf = new byte[MAX_REQUEST_BYTES];
		int len = 0;
		long deadline = System.nanoTime() + REQUEST_DEADLINE_NANOS;
		while (len < buf.length && System.nanoTime() < deadline)
		{
			int b;
			try
			{
				b = in.read();
			}
			catch (SocketTimeoutException e)
			{
				continue; // nothing yet; loop re-checks the overall deadline
			}
			if (b < 0)
			{
				return null;
			}
			buf[len++] = (byte) b;
			if (len >= 4 && buf[len - 4] == '\r' && buf[len - 3] == '\n' && buf[len - 2] == '\r' && buf[len - 1] == '\n')
			{
				return new String(buf, 0, len, StandardCharsets.ISO_8859_1);
			}
		}
		return null;
	}

	private boolean isAuthorized(String[] headerLines)
	{
		for (int i = 1; i < headerLines.length; i++)
		{
			String line = headerLines[i];
			int colon = line.indexOf(':');
			if (colon > 0 && "authorization".equals(line.substring(0, colon).trim().toLowerCase(Locale.ROOT)))
			{
				String value = line.substring(colon + 1).trim();
				if (value.regionMatches(true, 0, "Bearer ", 0, 7))
				{
					byte[] presented = value.substring(7).trim().getBytes(StandardCharsets.UTF_8);
					return MessageDigest.isEqual(expectedToken, presented); // constant time
				}
			}
		}
		return false;
	}

	private static void respond(OutputStream out, int status, String type, String body) throws IOException
	{
		respond(out, status, type, body.getBytes(StandardCharsets.UTF_8));
	}

	private static void respond(OutputStream out, int status, String type, byte[] body) throws IOException
	{
		String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
			+ "Content-Type: " + type + "\r\n"
			+ "Content-Length: " + body.length + "\r\n"
			+ "Cache-Control: no-store\r\n"
			+ "Connection: close\r\n\r\n";
		out.write(head.getBytes(StandardCharsets.ISO_8859_1));
		out.write(body);
		out.flush();
	}

	private static String reason(int status)
	{
		switch (status)
		{
			case 200:
				return "OK";
			case 400:
				return "Bad Request";
			case 401:
				return "Unauthorized";
			case 404:
				return "Not Found";
			case 405:
				return "Method Not Allowed";
			case 431:
				return "Request Header Fields Too Large";
			default:
				return "Error";
		}
	}
}
