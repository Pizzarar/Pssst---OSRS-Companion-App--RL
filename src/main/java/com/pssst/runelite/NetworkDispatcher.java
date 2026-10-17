package com.pssst.runelite;

import com.google.gson.Gson;
import java.io.IOException;
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

@Slf4j
@Singleton
public class NetworkDispatcher
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final CompanionConfig config;

	@Inject
	public NetworkDispatcher(OkHttpClient okHttpClient, Gson gson, CompanionConfig config)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.config = config;
	}

	public NetworkDispatcher(OkHttpClient okHttpClient, Gson gson)
	{
		this(okHttpClient, gson, null);
	}

	public void dispatch(StateDTO state)
	{
		if (config == null || !config.enableSync() || !config.enableByosPush())
		{
			return;
		}

		dispatch(config.serverUrl(), config.syncToken(), true, state);
	}

	public void dispatch(String serverUrl, String syncToken, StateDTO state)
	{
		boolean enable = (config == null || (config.enableSync() && config.enableByosPush()));
		dispatch(serverUrl, syncToken, enable, state);
	}

	public void dispatch(String serverUrl, String syncToken, boolean enableSync, StateDTO state)
	{
		if (!enableSync)
		{
			log.debug("Dispatch skipped: sync disabled");
			return;
		}

		if (serverUrl == null || serverUrl.trim().isEmpty())
		{
			log.warn("Dispatch skipped: serverUrl blank");
			return;
		}

		if (syncToken == null || syncToken.trim().isEmpty())
		{
			log.warn("Dispatch skipped: syncToken blank");
			return;
		}

		if (state == null)
		{
			log.warn("Dispatch skipped: state is null");
			return;
		}

		if (okHttpClient == null || gson == null)
		{
			log.warn("Dispatch skipped: okHttpClient or gson is null");
			return;
		}

		HttpUrl finalUrl = normalizeServerUrl(serverUrl);
		if (finalUrl == null)
		{
			log.warn("Dispatch skipped: invalid URL or host: {}", serverUrl);
			return;
		}

		String json = gson.toJson(state);
		final int jsonLength = json.length();
		RequestBody body = RequestBody.create(JSON, json);

		Request request = new Request.Builder()
			.url(finalUrl)
			.header("Authorization", "Bearer " + syncToken.trim())
			.post(body)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("POST {} failed: {}", finalUrl, e.toString());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					if (!response.isSuccessful())
					{
						log.warn("POST {} -> HTTP {}", finalUrl, response.code());
					}
					else
					{
						log.info("POST {} -> HTTP {} ({} bytes)", finalUrl, response.code(), jsonLength);
					}
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	/**
	 * Normalizes user input into a valid HTTP/HTTPS endpoint.
	 * Automatically accepts bare IPs (e.g., 100.64.0.1:8787), strips trailing slashes,
	 * and appends /sync if not already present.
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
			log.info("serverUrl uses 0.0.0.0 bind address; rewriting to 127.0.0.1 for outbound dispatch");
			parsed = parsed.newBuilder().host("127.0.0.1").build();
		}

		return parsed.encodedPath().endsWith("/sync")
			? parsed
			: parsed.newBuilder().addPathSegment("sync").build();
	}
}
