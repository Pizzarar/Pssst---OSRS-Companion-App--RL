package com.pssst.runelite;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class LanAddressesTest
{
	private static InetAddress ip(String literal)
	{
		try
		{
			return InetAddress.getByName(literal); // literals only: no DNS lookup
		}
		catch (UnknownHostException e)
		{
			throw new AssertionError(e);
		}
	}

	private static LanAddresses.Adapter adapter(String display, String... addresses)
	{
		return adapterWith(display, true, false, false, addresses);
	}

	private static LanAddresses.Adapter adapterWith(String display, boolean up, boolean loopback, boolean virtual, String... addresses)
	{
		InetAddress[] parsed = new InetAddress[addresses.length];
		for (int i = 0; i < addresses.length; i++)
		{
			parsed[i] = ip(addresses[i]);
		}
		return new LanAddresses.Adapter("eth0", display, up, loopback, virtual, Arrays.asList(parsed));
	}

	@Test
	public void theWifiAddressComesFirstThenTailscale()
	{
		List<LanAddresses.Address> ranked = LanAddresses.rank(Arrays.asList(
			adapter("Tailscale Tunnel", "100.101.102.103", "fd7a:115c:a1e0::1"),
			adapter("Intel(R) Wi-Fi 6 AX201", "fe80::1", "192.168.1.20")));

		Assert.assertEquals(2, ranked.size());
		Assert.assertEquals("192.168.1.20", ranked.get(0).host);
		Assert.assertEquals(LanAddresses.LABEL_HOME, ranked.get(0).label);
		Assert.assertEquals("100.101.102.103", ranked.get(1).host);
		Assert.assertEquals(LanAddresses.LABEL_TAILSCALE, ranked.get(1).label);
	}

	@Test
	public void homeRangesAreOrdered()
	{
		List<LanAddresses.Address> ranked = LanAddresses.rank(Arrays.asList(
			adapter("a", "172.20.0.5"), adapter("b", "10.0.0.7"), adapter("c", "192.168.0.9")));

		Assert.assertEquals(Arrays.asList("192.168.0.9", "10.0.0.7", "172.20.0.5"),
			Arrays.asList(ranked.get(0).host, ranked.get(1).host, ranked.get(2).host));
	}

	@Test
	public void adaptersThePhoneCannotReachAreSkipped()
	{
		List<LanAddresses.Address> ranked = LanAddresses.rank(Arrays.asList(
			adapter("Hyper-V Virtual Ethernet Adapter", "172.28.80.1"),
			adapter("vEthernet (WSL)", "172.30.0.1"),
			adapter("VirtualBox Host-Only Ethernet Adapter", "192.168.56.1"),
			adapter("TAP-Windows Adapter V9", "10.8.0.2"),
			adapterWith("Ethernet", false, false, false, "192.168.1.30"),
			adapterWith("Loopback", true, true, false, "127.0.0.1"),
			adapterWith("Ethernet:1", true, false, true, "192.168.1.31")));

		Assert.assertTrue(ranked.isEmpty());
	}

	@Test
	public void addressesOutsideTheHomeRangesAreIgnored()
	{
		List<LanAddresses.Address> ranked = LanAddresses.rank(Collections.singletonList(
			adapter("Ethernet", "169.254.10.10", "8.8.8.8", "172.32.0.1", "100.63.0.1", "100.128.0.1", "2001:db8::1")));

		Assert.assertTrue(ranked.isEmpty());
	}

	@Test
	public void theUrlIsWhatThePhoneTypes()
	{
		Assert.assertEquals("http://192.168.1.20:8788", LanAddresses.url("192.168.1.20", 8788));
	}
}
