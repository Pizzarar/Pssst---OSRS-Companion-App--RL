package com.pssst.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigItem;
import okhttp3.HttpUrl;
import org.junit.Assert;
import org.junit.Test;

public class CompanionPluginComplianceTest
{
	private final Gson gson = new Gson();

	@Test
	public void testStateDtoPureIntegerSerialization()
	{
		StateDTO dto = StateDTO.builder()
			.inventory(Arrays.asList(
				new StateDTO.ItemEntry(4151, 1),
				new StateDTO.ItemEntry(995, 100000)
			))
			.bank(Collections.singletonList(
				new StateDTO.ItemEntry(11802, 1)
			))
			.potionStorage(Collections.singletonList(
				new StateDTO.ItemEntry(12913, 1183)
			))
			.skills(Collections.singletonList(
				new StateDTO.SkillEntry("Attack", 99, 99, 13034431L)
			))
			.geOffers(Collections.singletonList(
				new StateDTO.GeOfferEntry(1, 4151, 0, 1, 2000000L, 0L, "BUYING")
			))
			.varbits(new HashMap<Integer, Integer>()
			{{
				put(4068, 500); // SLAYER_POINTS
			}})
			.varps(new HashMap<Integer, Integer>()
			{{
				put(1060, 1500); // NZONE_REWARDPOINTS
			}})
			.build();

		String json = gson.toJson(dto);
		JsonObject obj = new JsonParser().parse(json).getAsJsonObject();

		// slayer is a typed object, and is simply absent until the plugin has read an assignment
		Assert.assertFalse("slayer must be omitted when unset", obj.has("slayer"));

		// Ensure inventory has only int id & quantity
		Assert.assertTrue(obj.has("inventory"));
		for (JsonElement item : obj.getAsJsonArray("inventory"))
		{
			JsonObject itemObj = item.getAsJsonObject();
			Assert.assertTrue(itemObj.get("id").isJsonPrimitive());
			Assert.assertTrue(itemObj.get("id").getAsJsonPrimitive().isNumber());
			Assert.assertTrue(itemObj.get("quantity").getAsJsonPrimitive().isNumber());
		}

		// Ensure potion_storage is present with int id and quantity
		Assert.assertTrue(obj.has("potion_storage"));
		for (JsonElement pot : obj.getAsJsonArray("potion_storage"))
		{
			JsonObject potObj = pot.getAsJsonObject();
			Assert.assertTrue(potObj.get("id").isJsonPrimitive());
			Assert.assertTrue(potObj.get("id").getAsJsonPrimitive().isNumber());
			Assert.assertTrue(potObj.get("quantity").getAsJsonPrimitive().isNumber());
		}

		// Ensure skills and ge_offers are present
		Assert.assertTrue(obj.has("skills"));
		Assert.assertTrue(obj.has("ge_offers"));

		// Ensure varbits has only integer keys and values
		Assert.assertTrue(obj.has("varbits"));
		JsonObject varbitsObj = obj.getAsJsonObject("varbits");
		for (Map.Entry<String, JsonElement> entry : varbitsObj.entrySet())
		{
			Integer.parseInt(entry.getKey());
			Assert.assertTrue(entry.getValue().getAsJsonPrimitive().isNumber());
		}

		// Varps live in their own map: same integer-only shape, separate id space
		JsonObject varpsObj = obj.getAsJsonObject("varps");
		Assert.assertEquals(1500, varpsObj.get("1060").getAsInt());
		Assert.assertFalse("varps must not leak into varbits", varbitsObj.has("1060"));
	}

	@Test
	public void testCompanionConfigDefaultsAndWarning() throws Exception
	{
		Method enableSyncMethod = CompanionConfig.class.getMethod("enableSync");

		// 1. Mandatory IP submission warning check (RuneLite's own ConfigItem.warning)
		ConfigItem configItemAnno = enableSyncMethod.getAnnotation(ConfigItem.class);
		Assert.assertNotNull("enableSync must have @ConfigItem annotation", configItemAnno);
		Assert.assertTrue("ConfigItem warning must mention IP address submission",
			configItemAnno.warning().toLowerCase().contains("ip address"));

		// 2. Default values verification via default methods
		CompanionConfig config = (CompanionConfig) Proxy.newProxyInstance(
			CompanionConfig.class.getClassLoader(),
			new Class<?>[]{CompanionConfig.class},
			(proxy, method, args) -> {
				if (method.isDefault())
				{
					return MethodHandlesLookupHelper.invokeDefault(proxy, method, args);
				}
				return null;
			}
		);

		Assert.assertFalse("Master sync must be opt-in (default false)", config.enableSync());
		Assert.assertTrue("Local WebSocket server should default to true", config.enableLocalServer());
		Assert.assertEquals("Default local port should be 8788", 8788, config.localServerPort());
		Assert.assertTrue("bindLoopbackOnly must default to true (LAN exposure is opt-in)", config.bindLoopbackOnly());
		Assert.assertTrue("BYOS push should default to true", config.enableByosPush());
		Assert.assertEquals("http://127.0.0.1:8787", config.serverUrl());
	}

	@Test
	public void testNetworkDispatcherUrlNormalization()
	{
		// Bare IP / Meshnet address
		HttpUrl meshnet = NetworkDispatcher.normalizeServerUrl("100.64.0.1:8787");
		Assert.assertNotNull(meshnet);
		Assert.assertEquals("http://100.64.0.1:8787/sync", meshnet.toString());

		// Trailing slash stripping & /sync appending
		HttpUrl localWithSlash = NetworkDispatcher.normalizeServerUrl("http://127.0.0.1:8787/");
		Assert.assertNotNull(localWithSlash);
		Assert.assertEquals("http://127.0.0.1:8787/sync", localWithSlash.toString());

		// 0.0.0.0 rewrite to 127.0.0.1
		HttpUrl zeroAddr = NetworkDispatcher.normalizeServerUrl("http://0.0.0.0:8787");
		Assert.assertNotNull(zeroAddr);
		Assert.assertEquals("http://127.0.0.1:8787/sync", zeroAddr.toString());

		// Already contains /sync
		HttpUrl alreadySync = NetworkDispatcher.normalizeServerUrl("https://my-server.com/sync");
		Assert.assertNotNull(alreadySync);
		Assert.assertEquals("https://my-server.com/sync", alreadySync.toString());

		// Blank / null
		Assert.assertNull(NetworkDispatcher.normalizeServerUrl(""));
		Assert.assertNull(NetworkDispatcher.normalizeServerUrl(null));
	}

	@Test
	public void testLocalWebSocketServerHttpFallbackAndAuth() throws Exception
	{
		int testPort;
		try (ServerSocket ss = new ServerSocket(0))
		{
			testPort = ss.getLocalPort();
		}

		LocalWebSocketServer server = new LocalWebSocketServer();
		String syncToken = "test-token-uuid-1234";

		try
		{
			server.start(testPort, true, syncToken);
			Assert.assertTrue(server.isRunning());

			// 1. Unauthorized request without token -> 401
			HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + testPort + "/state").openConnection();
			conn.setConnectTimeout(2000);
			conn.setReadTimeout(2000);
			Assert.assertEquals(401, conn.getResponseCode());

			// 2. Authorized request with Bearer header before any state broadcast -> 404
			HttpURLConnection authConn = (HttpURLConnection) new URL("http://127.0.0.1:" + testPort + "/state").openConnection();
			authConn.setRequestProperty("Authorization", "Bearer " + syncToken);
			authConn.setConnectTimeout(2000);
			authConn.setReadTimeout(2000);
			Assert.assertEquals(404, authConn.getResponseCode());

			// 3. Broadcast state and verify 200 OK
			String sampleStateJson = "{\"data\":{\"inventory\":[{\"id\":4151,\"quantity\":1}]},\"updated_at\":1000}";
			server.broadcast(sampleStateJson);

			HttpURLConnection stateConn = (HttpURLConnection) new URL("http://127.0.0.1:" + testPort + "/state?token=" + syncToken).openConnection();
			stateConn.setConnectTimeout(2000);
			stateConn.setReadTimeout(2000);
			Assert.assertEquals(200, stateConn.getResponseCode());

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stateConn.getInputStream(), StandardCharsets.UTF_8)))
			{
				String body = reader.readLine();
				Assert.assertEquals(sampleStateJson, body);
			}

			// 4. Test POST /sync endpoint
			HttpURLConnection syncConn = (HttpURLConnection) new URL("http://127.0.0.1:" + testPort + "/sync?token=" + syncToken).openConnection();
			syncConn.setRequestMethod("POST");
			syncConn.setConnectTimeout(2000);
			syncConn.setReadTimeout(2000);
			Assert.assertEquals(200, syncConn.getResponseCode());
		}
		finally
		{
			server.stop();
			Assert.assertFalse(server.isRunning());
		}
	}

	@Test
	public void testWebSocketTextFrameEncoding()
	{
		// Short payload (< 126 bytes)
		String shortMsg = "{\"test\":1}";
		byte[] shortFrame = LocalWebSocketServer.encodeTextFrame(shortMsg);
		Assert.assertEquals((byte) 0x81, shortFrame[0]);
		Assert.assertEquals((byte) shortMsg.length(), shortFrame[1]);

		// Medium payload (>= 126 bytes)
		StringBuilder mediumBuilder = new StringBuilder();
		for (int i = 0; i < 200; i++)
		{
			mediumBuilder.append("A");
		}
		String mediumMsg = mediumBuilder.toString();
		byte[] mediumFrame = LocalWebSocketServer.encodeTextFrame(mediumMsg);
		Assert.assertEquals((byte) 0x81, mediumFrame[0]);
		Assert.assertEquals((byte) 126, mediumFrame[1]);
		int len = ((mediumFrame[2] & 0xFF) << 8) | (mediumFrame[3] & 0xFF);
		Assert.assertEquals(200, len);
	}

	private static class MethodHandlesLookupHelper
	{
		static Object invokeDefault(Object proxy, Method method, Object[] args) throws Throwable
		{
			java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
			return java.lang.invoke.MethodHandles.privateLookupIn(method.getDeclaringClass(), lookup)
				.unreflectSpecial(method, method.getDeclaringClass())
				.bindTo(proxy)
				.invokeWithArguments(args != null ? args : new Object[0]);
		}
	}
}
