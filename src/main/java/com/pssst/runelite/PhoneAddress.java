package com.pssst.runelite;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import okhttp3.HttpUrl;

/**
 * Turns what the player typed into "Phone Address" into the URL the plugin sends to. It accepts the phone's numeric
 * address (192.168.1.42) or a name that resolves to it (such as a name the home router hands
 * out). Either way it must end up on the player's own network (home ranges, 100.64.0.0/10, link-local, or
 * this PC for development), so the setting cannot point at a server on the internet.
 */
final class PhoneAddress
{
	static final int DEFAULT_PORT = 8789;

	/** Looks a name up; the system resolver in the plugin, a fake in tests. */
	interface Resolver
	{
		InetAddress[] resolve(String host) throws UnknownHostException;
	}

	/** What was typed: a host (numeric or a name) and a port, or the reason it can't be used. */
	static final class Result
	{
		final String host;
		final int port;
		final boolean isName;
		final String problem;

		private Result(String host, int port, boolean isName, String problem)
		{
			this.host = host;
			this.port = port;
			this.isName = isName;
			this.problem = problem;
		}

		/** The URL for a numeric address, or null for a name (resolve it first) or a problem. */
		HttpUrl url()
		{
			return problem == null && !isName ? urlFor(host, port) : null;
		}
	}

	/** Where to send right now: a URL, or why not (a problem to show, or simply not found at the moment). */
	static final class Target
	{
		final HttpUrl url;
		final String problem;

		Target(HttpUrl url, String problem)
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
			return problem("No Phone Address yet. The Pssst app shows it on its setup screen.");
		}
		if (text.startsWith("https://"))
		{
			return problem("Type the address exactly as the Pssst app shows it (no https).");
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
				return problem("The port after ':' is not a number.");
			}
			if (port < 1 || port > 65535)
			{
				return problem("The port must be between 1 and 65535.");
			}
		}

		int[] octets = ipv4(host);
		if (octets != null)
		{
			return isOwnNetwork(octets)
				? new Result(host, port, false, null)
				: problem("That is not a home-network address. Use the address the Pssst app shows.");
		}
		if (looksLikeIpv4(host))
		{
			return problem("That address isn't valid. Use the address the Pssst app shows, like 192.168.1.42.");
		}
		if (!isHostname(host))
		{
			return problem("Use the address the Pssst app shows (like 192.168.1.42), or a name for your phone.");
		}
		return new Result(host.toLowerCase(), port, true, null);
	}

	/** Resolves a name (if it is one) and checks every address it points to is on the player's own network. */
	static Target target(Result parsed, Resolver resolver)
	{
		if (parsed.problem != null)
		{
			return new Target(null, parsed.problem);
		}
		if (!parsed.isName)
		{
			return new Target(parsed.url(), null);
		}
		InetAddress[] found;
		try
		{
			found = resolver.resolve(parsed.host);
		}
		catch (UnknownHostException e)
		{
			return new Target(null, null); // not found right now (phone away): retried like a closed app
		}
		InetAddress chosen = null;
		for (InetAddress address : found)
		{
			if (!isOwnNetwork(address))
			{
				return new Target(null, "That name points outside your home network. Use the address the Pssst app shows.");
			}
			if (chosen == null && address instanceof Inet4Address)
			{
				chosen = address;
			}
		}
		if (chosen == null)
		{
			return new Target(null, null);
		}
		// Send to the address that was checked, not the name, so a second lookup can't point somewhere else
		return new Target(urlFor(chosen.getHostAddress(), parsed.port), null);
	}

	static boolean isOwnNetwork(InetAddress address)
	{
		if (address instanceof Inet4Address)
		{
			byte[] b = address.getAddress();
			return isOwnNetwork(new int[]{b[0] & 0xFF, b[1] & 0xFF, b[2] & 0xFF, b[3] & 0xFF});
		}
		if (address instanceof Inet6Address)
		{
			byte[] b = address.getAddress();
			return address.isLoopbackAddress() || address.isLinkLocalAddress() || (b[0] & 0xFE) == 0xFC;
		}
		return false;
	}

	/** 10/8, 172.16/12, 192.168/16, 100.64/10 (mesh VPN), 169.254/16 (link-local), 127/8 (this PC, for an emulator). */
	static boolean isOwnNetwork(int[] o)
	{
		return o[0] == 10
			|| (o[0] == 172 && o[1] >= 16 && o[1] <= 31)
			|| (o[0] == 192 && o[1] == 168)
			|| (o[0] == 100 && o[1] >= 64 && o[1] <= 127)
			|| (o[0] == 169 && o[1] == 254)
			|| o[0] == 127;
	}

	static HttpUrl urlFor(String host, int port)
	{
		return new HttpUrl.Builder().scheme("http").host(host).port(port).addPathSegment("sync").build();
	}

	private static Result problem(String text)
	{
		return new Result(null, 0, false, text);
	}

	/** Letters, digits, hyphens and dots, like pixel-8, pixel-8.lan or pixel-8.home.arpa. */
	private static boolean isHostname(String host)
	{
		if (host.isEmpty() || host.length() > 253 || host.startsWith(".") || host.endsWith(".") || host.contains(".."))
		{
			return false;
		}
		for (String label : host.split("\\."))
		{
			if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-"))
			{
				return false;
			}
			for (char c : label.toCharArray())
			{
				if (!(Character.isLetterOrDigit(c) && c < 128) && c != '-')
				{
					return false;
				}
			}
		}
		return true;
	}

	/** Digits and dots only: meant as a numeric address, so a malformed one is an error rather than a name. */
	private static boolean looksLikeIpv4(String host)
	{
		return !host.isEmpty() && host.chars().allMatch(c -> (c >= '0' && c <= '9') || c == '.');
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
