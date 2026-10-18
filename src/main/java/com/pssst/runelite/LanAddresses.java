package com.pssst.runelite;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds the addresses the phone can type in, so players do not need ipconfig. Pure Java (no subprocess): it only
 * reads the network interfaces. {@link #rank} holds the decisions and is tested without touching the real network.
 */
final class LanAddresses
{
	/** Adapters that hold an address the phone cannot reach (VM switches, containers, VPN tap devices). */
	private static final String[] UNREACHABLE_ADAPTERS = {
		"hyper-v", "vethernet", "virtualbox", "vmware", "virtual", "wsl", "docker", "bluetooth",
		"loopback", "tap-windows", "openvpn", "wireguard", "nordlynx", "pseudo"
	};

	static final String LABEL_HOME = "Home network (same Wi-Fi)";
	static final String LABEL_TAILSCALE = "Tailscale";

	/** One network adapter, reduced to what ranking needs. */
	static final class Adapter
	{
		final String name;
		final String displayName;
		final boolean up;
		final boolean loopback;
		final boolean virtual;
		final List<InetAddress> addresses;

		Adapter(String name, String displayName, boolean up, boolean loopback, boolean virtual, List<InetAddress> addresses)
		{
			this.name = name;
			this.displayName = displayName;
			this.up = up;
			this.loopback = loopback;
			this.virtual = virtual;
			this.addresses = addresses;
		}
	}

	/** An address to show the player. */
	static final class Address
	{
		final String host;
		final String label;

		Address(String host, String label)
		{
			this.host = host;
			this.label = label;
		}
	}

	private LanAddresses()
	{
	}

	/** Reads the machine's adapters. Blocks briefly, so call it off the Swing and client threads. */
	static List<Address> scan()
	{
		List<Adapter> adapters = new ArrayList<>();
		try
		{
			for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
			{
				adapters.add(new Adapter(ni.getName(), ni.getDisplayName(), ni.isUp(), ni.isLoopback(), ni.isVirtual(),
					Collections.list(ni.getInetAddresses())));
			}
		}
		catch (SocketException e)
		{
			return Collections.emptyList();
		}
		return rank(adapters);
	}

	/** Home-network IPv4 addresses first (192.168 before 10.x before 172.16-31), then Tailscale's 100.64.0.0/10. */
	static List<Address> rank(List<Adapter> adapters)
	{
		List<Object[]> found = new ArrayList<>(); // {order, host, label}
		for (Adapter adapter : adapters)
		{
			if (!adapter.up || adapter.loopback || adapter.virtual || isUnreachableAdapter(adapter))
			{
				continue;
			}
			for (InetAddress address : adapter.addresses)
			{
				if (!(address instanceof Inet4Address))
				{
					continue;
				}
				byte[] b = address.getAddress();
				int first = b[0] & 0xFF;
				int second = b[1] & 0xFF;
				String host = address.getHostAddress();
				if (first == 100 && (second & 0xC0) == 0x40)
				{
					found.add(new Object[]{3, host, LABEL_TAILSCALE});
				}
				else if (first == 192 && second == 168)
				{
					found.add(new Object[]{0, host, LABEL_HOME});
				}
				else if (first == 10)
				{
					found.add(new Object[]{1, host, LABEL_HOME});
				}
				else if (first == 172 && second >= 16 && second <= 31)
				{
					found.add(new Object[]{2, host, LABEL_HOME});
				}
			}
		}
		found.sort(Comparator.comparingInt(o -> (Integer) o[0]));
		List<Address> result = new ArrayList<>();
		for (Object[] row : found)
		{
			result.add(new Address((String) row[1], (String) row[2]));
		}
		return result;
	}

	private static boolean isUnreachableAdapter(Adapter adapter)
	{
		String text = ((adapter.displayName == null ? "" : adapter.displayName) + " "
			+ (adapter.name == null ? "" : adapter.name)).toLowerCase(Locale.ROOT);
		for (String marker : UNREACHABLE_ADAPTERS)
		{
			if (text.contains(marker))
			{
				return true;
			}
		}
		return false;
	}

	/** The address as the phone should type it. */
	static String url(String host, int port)
	{
		return "http://" + host + ":" + port;
	}
}
