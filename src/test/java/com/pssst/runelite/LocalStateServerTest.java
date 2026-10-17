package com.pssst.runelite;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class LocalStateServerTest
{
	private static final String TOKEN = "0123456789abcdef0123456789abcdef";
	private static final String STATE = "{\"data\":{\"inventory\":[{\"id\":4151,\"quantity\":1}]},\"updated_at\":1000}";

	private LocalStateServer server;
	private int port;

	@Before
	public void setUp() throws IOException
	{
		try (ServerSocket ss = new ServerSocket(0))
		{
			port = ss.getLocalPort();
		}
		server = new LocalStateServer();
	}

	@After
	public void tearDown()
	{
		server.stop();
	}

	private HttpURLConnection get(String pathAndQuery, String authHeader) throws IOException
	{
		HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + pathAndQuery).openConnection();
		conn.setConnectTimeout(2000);
		conn.setReadTimeout(4000);
		if (authHeader != null)
		{
			conn.setRequestProperty("Authorization", authHeader);
		}
		return conn;
	}

	@Test
	public void rejectsMissingOrWrongTokens()
	{
		server.start(port, true, TOKEN);
		server.publish(STATE);
		Assert.assertTrue(server.isRunning());

		assertStatus(401, "/state", null);
		assertStatus(401, "/state", "Bearer wrong");
		assertStatus(401, "/state", "Basic " + TOKEN);
		assertStatus(401, "/state", "Bearer " + TOKEN.substring(1));
	}

	@Test
	public void tokenInTheQueryStringIsNotAccepted()
	{
		server.start(port, true, TOKEN);
		server.publish(STATE);
		assertStatus(401, "/state?token=" + TOKEN, null);
	}

	@Test
	public void servesTheLatestStateWithTheBearerToken() throws IOException
	{
		server.start(port, true, TOKEN);

		assertStatus(404, "/state", "Bearer " + TOKEN); // nothing published yet, same as the Worker

		server.publish(STATE);
		HttpURLConnection conn = get("/state", "Bearer " + TOKEN);
		Assert.assertEquals(200, conn.getResponseCode());
		Assert.assertEquals("no-store", conn.getHeaderField("Cache-Control"));
		Assert.assertNull("no CORS: browsers on other origins must not be able to read it", conn.getHeaderField("Access-Control-Allow-Origin"));
		Assert.assertEquals(STATE, new String(readAll(conn.getInputStream()), StandardCharsets.UTF_8));

		server.publish(STATE.replace("1000", "2000"));
		Assert.assertTrue(new String(readAll(get("/state?x=1", "bearer " + TOKEN).getInputStream()), StandardCharsets.UTF_8).contains("2000"));
	}

	@Test
	public void onlyGetStateExists() throws IOException
	{
		server.start(port, true, TOKEN);
		server.publish(STATE);

		assertStatus(404, "/sync", "Bearer " + TOKEN);
		assertStatus(404, "/", "Bearer " + TOKEN);

		HttpURLConnection post = get("/state", "Bearer " + TOKEN);
		post.setRequestMethod("POST");
		Assert.assertEquals(405, post.getResponseCode());
	}

	@Test
	public void refusesToStartWithoutAToken()
	{
		server.start(port, true, "");
		Assert.assertFalse(server.isRunning());
		server.start(port, true, "   ");
		Assert.assertFalse(server.isRunning());
		server.start(port, true, null);
		Assert.assertFalse(server.isRunning());
	}

	@Test
	public void oversizedHeadersAreCutOffWithoutHanging() throws IOException
	{
		server.start(port, true, TOKEN);
		try (Socket socket = new Socket("127.0.0.1", port))
		{
			socket.setSoTimeout(5000);
			OutputStream out = socket.getOutputStream();
			out.write("GET /state HTTP/1.1\r\nX-Junk: ".getBytes(StandardCharsets.US_ASCII));
			byte[] junk = new byte[64 * 1024];
			java.util.Arrays.fill(junk, (byte) 'a'); // never terminates the header block
			try
			{
				out.write(junk);
				out.flush();
			}
			catch (IOException serverAlreadyHungUp)
			{
				// fine: the server closed the connection once the limit was hit
			}
			String response = new String(readAll(socket.getInputStream()), StandardCharsets.ISO_8859_1);
			Assert.assertTrue(response, response.isEmpty() || response.startsWith("HTTP/1.1 431"));
		}
		// and it still serves the next, well-formed request
		server.publish(STATE);
		assertStatus(200, "/state", "Bearer " + TOKEN);
	}

	@Test
	public void aSilentClientCannotPinTheServer() throws IOException
	{
		server.start(port, true, TOKEN);
		server.publish(STATE);
		try (Socket silent = new Socket("127.0.0.1", port))
		{
			// connects and says nothing; the 3 s request deadline must free the server for the next client
			long start = System.nanoTime();
			assertStatus(200, "/state", "Bearer " + TOKEN);
			long seconds = (System.nanoTime() - start) / 1_000_000_000L;
			Assert.assertTrue("took " + seconds + "s", seconds < 6);
		}
	}

	@Test
	public void listensOnLoopbackWhenAskedTo() throws IOException
	{
		server.start(port, true, TOKEN);
		try (Socket socket = new Socket("127.0.0.1", port))
		{
			Assert.assertTrue(socket.isConnected());
		}
		server.stop();
		Assert.assertFalse(server.isRunning());
	}

	private void assertStatus(int expected, String path, String auth)
	{
		try
		{
			Assert.assertEquals(path + " with " + auth, expected, get(path, auth).getResponseCode());
		}
		catch (IOException e)
		{
			throw new AssertionError(e);
		}
	}

	private static byte[] readAll(InputStream in) throws IOException
	{
		java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
		byte[] chunk = new byte[4096];
		int n;
		try
		{
			while ((n = in.read(chunk)) > 0)
			{
				buf.write(chunk, 0, n);
			}
		}
		catch (java.net.SocketException resetAfterResponse)
		{
			// the server may reset after closing; what arrived is what we assert on
		}
		return buf.toByteArray();
	}
}
