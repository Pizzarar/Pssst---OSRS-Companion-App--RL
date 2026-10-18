package com.pssst.runelite;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

public class NetworkDispatcherBackoffTest
{
	private static final HttpUrl PHONE = HttpUrl.parse("http://192.168.1.42:8789/sync");

	@Test
	public void retryAfterIsParsedClampedAndDefaulted()
	{
		Assert.assertEquals(30_000, NetworkDispatcher.backoffMillis("30"));
		Assert.assertEquals(60_000, NetworkDispatcher.backoffMillis(null));
		Assert.assertEquals(60_000, NetworkDispatcher.backoffMillis("Wed, 21 Oct 2026 07:28:00 GMT"));
		Assert.assertEquals(1_000, NetworkDispatcher.backoffMillis("0"));
		Assert.assertEquals(3_600_000, NetworkDispatcher.backoffMillis("999999"));
	}

	@Test
	public void answersAreClassified()
	{
		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.DELIVERED, NetworkDispatcher.classify(200, null).kind);
		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.REJECTED, NetworkDispatcher.classify(401, null).kind);
		NetworkDispatcher.Outcome tooMany = NetworkDispatcher.classify(429, "30");
		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.BACKOFF, tooMany.kind);
		Assert.assertEquals(30_000, tooMany.pauseMs);
		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.BACKOFF, NetworkDispatcher.classify(403, null).kind);
		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.FAILED, NetworkDispatcher.classify(500, null).kind);
	}

	@Test
	public void postsTheBodyWithTheTokenInTheHeaderOnly() throws Exception
	{
		AtomicReference<Request> seen = new AtomicReference<>();
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			seen.set(chain.request());
			return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
				.body(ResponseBody.create(MediaType.parse("application/json"), "{}")).build();
		}).build();
		CompletableFuture<NetworkDispatcher.Outcome> outcome = new CompletableFuture<>();

		new NetworkDispatcher(client).post(PHONE, " 0123456789abcdef ", "{\"observedAt\":1}", outcome::complete);

		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.DELIVERED, outcome.get(5, TimeUnit.SECONDS).kind);
		Assert.assertEquals("POST", seen.get().method());
		Assert.assertEquals("Bearer 0123456789abcdef", seen.get().header("Authorization"));
		Assert.assertNull("the token never goes in the URL", seen.get().url().query());
	}

	@Test
	public void aPhoneThatIsNotListeningIsUnreachableNotAnError() throws Exception
	{
		OkHttpClient client = new OkHttpClient.Builder().addInterceptor(chain ->
		{
			throw new java.net.ConnectException("Connection refused");
		}).build();
		CompletableFuture<NetworkDispatcher.Outcome> outcome = new CompletableFuture<>();

		new NetworkDispatcher(client).post(PHONE, "0123456789abcdef", "{}", outcome::complete);

		Assert.assertEquals(NetworkDispatcher.Outcome.Kind.UNREACHABLE, outcome.get(5, TimeUnit.SECONDS).kind);
	}
}
