package com.pssst.runelite;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends the snapshot to a target the player owns: the Pssst app on their phone, or their own server. Outbound only,
 * asynchronous ({@code enqueue}) on RuneLite's injected client. The plugin never listens on a port. Whether and when to
 * send is {@link DeliveryPolicy}'s decision; this class only performs one POST and reports how it went.
 */
@Slf4j
@Singleton
public class NetworkDispatcher
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private static final long DEFAULT_BACKOFF_SECONDS = 60;
	private static final long MAX_BACKOFF_SECONDS = 3600;
	private static final int CONNECT_TIMEOUT_SECONDS = 3;

	/** How one POST ended. */
	static final class Outcome
	{
		enum Kind
		{
			/** 2xx: the target has the snapshot. */
			DELIVERED,
			/** No connection (refused, timed out, no route): the phone app is not listening right now. */
			UNREACHABLE,
			/** 401: the target does not accept this Sync Token. */
			REJECTED,
			/** 403 or 429: told to stop for {@link #pauseMs}. */
			BACKOFF,
			/** Any other answer. */
			FAILED
		}

		final Kind kind;
		final long pauseMs;
		final int code;

		Outcome(Kind kind, long pauseMs, int code)
		{
			this.kind = kind;
			this.pauseMs = pauseMs;
			this.code = code;
		}
	}

	private final OkHttpClient okHttpClient;

	@Inject
	public NetworkDispatcher(OkHttpClient okHttpClient)
	{
		// Derived from RuneLite's client (shares its pool and dispatcher), only to fail fast when the phone isn't listening
		this.okHttpClient = okHttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
	}

	/** POSTs {@code json} to {@code url} with the token in the Authorization header; {@code done} runs on OkHttp's thread. */
	void post(HttpUrl url, String syncToken, String json, Consumer<Outcome> done)
	{
		Request request = new Request.Builder()
			.url(url)
			.header("Authorization", "Bearer " + syncToken.trim())
			.post(RequestBody.create(JSON, json))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				// Normal whenever the app is closed or the phone is away: not worth more than a debug line
				log.debug("POST {} not delivered: {}", url.host(), e.getClass().getSimpleName());
				done.accept(new Outcome(Outcome.Kind.UNREACHABLE, 0, 0));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					done.accept(classify(response.code(), response.header("Retry-After")));
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	static Outcome classify(int code, String retryAfter)
	{
		if (code >= 200 && code < 300)
		{
			return new Outcome(Outcome.Kind.DELIVERED, 0, code);
		}
		if (code == 401)
		{
			log.warn("Pssst target rejected the Sync Token (401)");
			return new Outcome(Outcome.Kind.REJECTED, 0, code);
		}
		if (code == 403 || code == 429)
		{
			// Told to stop: no retrying around it
			long pause = backoffMillis(retryAfter);
			log.warn("Pssst target answered HTTP {}; pausing sends for {} s", code, pause / 1000);
			return new Outcome(Outcome.Kind.BACKOFF, pause, code);
		}
		log.warn("Pssst target answered HTTP {}", code);
		return new Outcome(Outcome.Kind.FAILED, 0, code);
	}

	/** Retry-After in seconds (default 60 s, at most an hour); the HTTP-date form is treated as unparsable. */
	static long backoffMillis(String retryAfter)
	{
		long seconds = DEFAULT_BACKOFF_SECONDS;
		if (retryAfter != null)
		{
			try
			{
				seconds = Long.parseLong(retryAfter.trim());
			}
			catch (NumberFormatException ignored)
			{
				// keep the default
			}
		}
		return Math.max(1, Math.min(seconds, MAX_BACKOFF_SECONDS)) * 1000;
	}

	/**
	 * Normalizes a server address the player typed into an HTTP/HTTPS endpoint: accepts bare IPs (e.g. 100.64.0.1:8787),
	 * strips trailing slashes, and appends /sync if not already present.
	 */
	public static HttpUrl normalizeServerUrl(String serverUrl)
	{
		if (serverUrl == null)
		{
			return null;
		}

		String trimmed = serverUrl.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}

		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://"))
		{
			trimmed = "http://" + trimmed;
		}

		while (trimmed.endsWith("/"))
		{
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}

		HttpUrl parsed = HttpUrl.parse(trimmed);
		if (parsed == null)
		{
			return null;
		}

		if ("0.0.0.0".equals(parsed.host()))
		{
			parsed = parsed.newBuilder().host("127.0.0.1").build();
		}

		return parsed.encodedPath().endsWith("/sync")
			? parsed
			: parsed.newBuilder().addPathSegment("sync").build();
	}
}
