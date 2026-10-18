package com.pssst.runelite;

import okhttp3.HttpUrl;

/**
 * Turns what the player typed into "Phone Address" into the URL the plugin sends to. Only addresses on the player's
 * own network are accepted (home Wi-Fi ranges, Tailscale's 100.64.0.0/10, link-local, and this PC for development), so
 * the setting cannot be pointed at a server on the internet: that is what "Send to My Server" is for.
 */
final class PhoneAddress
{
	static final int DEFAULT_PORT = 8789;

	/** Either a URL or the reason there is none. */
	static final class Result
	{
		final HttpUrl url;
		final String problem;

		private Result(HttpUrl url, String problem)
		{
			this.url = url;
			this.problem = problem;
		}
	}

	private PhoneAddress()
	{
	}

	static Result parse(String typed)
	{
		String text = typed == null ? "" : typed.trim();
		if (text.isEmpty())
		{
			return new Result(null, "No Phone Address yet. The Pssst app shows it on its setup screen.");
		}
		if (text.startsWith("https://"))
		{
			return new Result(null, "Type the address exactly as the Pssst app shows it (no https).");
		}
		if (text.startsWith("http://"))
		{
			text = text.substring("http://".length());
		}
		int slash = text.indexOf('/');
		if (slash >= 0)
		{
			text = text.substring(0, slash);
		}

		String host = text;
		int port = DEFAULT_PORT;
		int colon = text.lastIndexOf(':');
		if (colon >= 0)
		{
			host = text.substring(0, colon);
			try
			{
				port = Integer.parseInt(text.substring(colon + 1));
			}
			catch (NumberFormatException e)
			{
				return new Result(null, "The port after ':' is not a number.");
			}
			if (port < 1 || port > 65535)
			{
				return new Result(null, "The port must be between 1 and 65535.");
			}
		}

		int[] octets = ipv4(host);
		if (octets == null)
		{
			return new Result(null, "Use the phone's numeric address, like 192.168.1.42, as the Pssst app shows it.");
		}
		if (!isOwnNetwork(octets))
		{
			return new Result(null, "That is not a home-network or Tailscale address. Use the address the Pssst app shows.");
		}
		return new Result(new HttpUrl.Builder().scheme("http").host(host).port(port).addPathSegment("sync").build(), null);
	}

	/** 10/8, 172.16/12, 192.168/16, 100.64/10 (Tailscale), 169.254/16 (link-local), 127/8 (this PC, for an emulator). */
	static boolean isOwnNetwork(int[] o)
	{
		return o[0] == 10
			|| (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
			|| (o[0] == 192 && o[1] == 168)
			|| (o[0] == 100 && o[1] >= 64 && o[1] <= 127)
			|| (o[0] == 169 && o[1] == 254)
			|| o[0] == 127;
	}

	private static int[] ipv4(String host)
	{
		String[] parts = host.split("\\.", -1);
		if (parts.length != 4)
		{
			return null;
		}
		int[] octets = new int[4];
		for (int i = 0; i < 4; i++)
		{
			if (parts[i].isEmpty() || parts[i].length() > 3)
			{
				return null;
			}
			for (char c : parts[i].toCharArray())
			{
				if (c < '0' || c > '9')
				{
					return null;
				}
			}
			octets[i] = Integer.parseInt(parts[i]);
			if (octets[i] > 255)
			{
				return null;
			}
		}
		return octets;
	}
}
