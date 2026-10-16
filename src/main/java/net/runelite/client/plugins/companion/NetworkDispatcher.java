package net.runelite.client.plugins.companion;

import com.google.gson.Gson;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
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

	@Inject
	public NetworkDispatcher(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	public void dispatch(String apiEndpoint, String authToken, StateDTO state)
	{
		if (apiEndpoint == null || apiEndpoint.trim().isEmpty())
		{
			log.warn("Cannot dispatch state: apiEndpoint is empty");
			return;
		}

		String json = (gson != null) ? gson.toJson(state) : new Gson().toJson(state);
		RequestBody body = RequestBody.create(JSON, json);

		Request.Builder requestBuilder = new Request.Builder()
			.url(apiEndpoint.trim())
			.header("User-Agent", "RuneLite-Companion")
			.post(body);

		if (authToken != null && !authToken.trim().isEmpty())
		{
			requestBuilder.header("Authorization", "Bearer " + authToken.trim());
		}

		Request request = requestBuilder.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("Failed to dispatch state to {}: {}", apiEndpoint, e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try
				{
					if (!response.isSuccessful())
					{
						log.warn("Dispatch to {} returned unsuccessful HTTP code: {}", apiEndpoint, response.code());
					}
					else
					{
						log.debug("State successfully dispatched to {}", apiEndpoint);
					}
				}
				finally
				{
					response.close();
				}
			}
		});
	}
}
