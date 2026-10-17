package com.pssst.runelite;

import com.google.gson.Gson;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;

public class NetworkDispatcherBackoffTest
{
	@Test
	public void retryAfterIsParsedClampedAndDefaulted()
	{
		Assert.assertEquals(30_000, NetworkDispatcher.backoffMillis("30"));
		Assert.assertEquals(60_000, NetworkDispatcher.backoffMillis(null));
		Assert.assertEquals(60_000, NetworkDispatcher.backoffMillis("Wed, 21 Oct 2026 07:28:00 GMT"));
		Assert.assertEquals(1_000, NetworkDispatcher.backoffMillis("0"));
		Assert.assertEquals(3_600_000, NetworkDispatcher.backoffMillis("999999"));
	}

	/** A client that never touches the network: it answers every request with the given status. */
	private static OkHttpClient answering(int code, AtomicInteger requests, CountDownLatch[] latch)
	{
		return new OkHttpClient.Builder().addInterceptor(chain ->
		{
			requests.incrementAndGet();
			Response response = new Response.Builder()
				.request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(code)
				.message("canned")
				.header("Retry-After", "30")
				.body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
				.build();
			latch[0].countDown();
			return response;
		}).build();
	}

	@Test
	public void a429PausesPushesForTheRetryAfterPeriodThenResumes() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		CountDownLatch[] latch = {new CountDownLatch(1)};
		AtomicLong now = new AtomicLong(1_000_000);
		NetworkDispatcher dispatcher = new NetworkDispatcher(answering(429, requests, latch), new Gson(), null, now::get);
		StateDTO state = StateDTO.builder().build();

		dispatcher.dispatch("http://example.com:8787", "0123456789abcdef", true, state);
		Assert.assertTrue(latch[0].await(5, TimeUnit.SECONDS));
		Thread.sleep(200); // let the callback record the pause
		Assert.assertEquals(1, requests.get());

		now.addAndGet(29_000);
		dispatcher.dispatch("http://example.com:8787", "0123456789abcdef", true, state);
		Thread.sleep(200);
		Assert.assertEquals("still inside the 30 s pause: nothing sent", 1, requests.get());

		now.addAndGet(2_000);
		latch[0] = new CountDownLatch(1);
		dispatcher.dispatch("http://example.com:8787", "0123456789abcdef", true, state);
		Assert.assertTrue(latch[0].await(5, TimeUnit.SECONDS));
		Assert.assertEquals("after the pause the next push goes out", 2, requests.get());
	}

	@Test
	public void ordinaryErrorsDoNotPause() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		CountDownLatch[] latch = {new CountDownLatch(1)};
		NetworkDispatcher dispatcher = new NetworkDispatcher(answering(500, requests, latch), new Gson(), null, () -> 1L);
		StateDTO state = StateDTO.builder().build();

		dispatcher.dispatch("http://example.com:8787", "0123456789abcdef", true, state);
		Assert.assertTrue(latch[0].await(5, TimeUnit.SECONDS));
		Thread.sleep(200);
		latch[0] = new CountDownLatch(1);
		dispatcher.dispatch("http://example.com:8787", "0123456789abcdef", true, state);
		Assert.assertTrue(latch[0].await(5, TimeUnit.SECONDS));
		Assert.assertEquals(2, requests.get());
	}

	@Test
	public void aBlankServerUrlSendsNothing() throws Exception
	{
		AtomicInteger requests = new AtomicInteger();
		CountDownLatch[] latch = {new CountDownLatch(1)};
		NetworkDispatcher dispatcher = new NetworkDispatcher(answering(200, requests, latch), new Gson(), null, () -> 1L);

		dispatcher.dispatch("", "0123456789abcdef", true, StateDTO.builder().build());
		dispatcher.dispatch("   ", "0123456789abcdef", true, StateDTO.builder().build());

		Assert.assertFalse(latch[0].await(500, TimeUnit.MILLISECONDS));
		Assert.assertEquals(0, requests.get());
	}
}
