package com.pssst.runelite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class PhoneAddressTest
{
	private static String url(String typed)
	{
		PhoneAddress.Result result = PhoneAddress.parse(typed);
		Assert.assertNull(typed + " -> " + result.problem, result.problem);
		return result.url().toString();
	}

	private static void refused(String typed)
	{
		PhoneAddress.Result result = PhoneAddress.parse(typed);
		Assert.assertNull(typed, result.url());
		Assert.assertNotNull(typed, result.problem);
	}

	@Test
	public void theAddressTheAppShowsBecomesItsSyncUrl()
	{
		Assert.assertEquals("http://192.168.1.42:8789/sync", url("192.168.1.42"));
		Assert.assertEquals("http://192.168.1.42:9000/sync", url(" 192.168.1.42:9000 "));
		Assert.assertEquals("http://10.0.0.7:8789/sync", url("http://10.0.0.7/"));
		Assert.assertEquals("http://100.101.102.103:8789/sync", url("100.101.102.103"));
		Assert.assertEquals("http://172.20.1.2:8789/sync", url("172.20.1.2"));
		Assert.assertEquals("emulator through adb forward", "http://127.0.0.1:8789/sync", url("127.0.0.1"));
	}

	@Test
	public void onlyAddressesOnYourOwnNetworkAreAccepted()
	{
		refused("");
		refused(null);
		refused("8.8.8.8");
		refused("172.32.0.1");
		refused("100.128.0.1");
		refused("bad_name!");
		refused("-pixel");
		refused("https://192.168.1.42");
		refused("192.168.1");
		refused("192.168.1.300");
		refused("192.168.1.42:notaport");
		refused("192.168.1.42:70000");
	}

	private static java.net.InetAddress ip(String literal)
	{
		try
		{
			return java.net.InetAddress.getByName(literal);
		}
		catch (java.net.UnknownHostException e)
		{
			throw new AssertionError(e);
		}
	}

	@Test
	public void aNameIsLookedUpAndSentToTheCheckedAddress()
	{
		PhoneAddress.Result parsed = PhoneAddress.parse("Pixel-8.tail1234.ts.net");
		Assert.assertNull(parsed.problem);
		Assert.assertTrue(parsed.isName);
		Assert.assertNull("a name has no URL until it is looked up", parsed.url());

		PhoneAddress.Target target = PhoneAddress.target(parsed, host ->
		{
			Assert.assertEquals("pixel-8.tail1234.ts.net", host);
			return new java.net.InetAddress[]{ip("100.101.102.103")};
		});
		Assert.assertNull(target.problem);
		Assert.assertEquals("http://100.101.102.103:8789/sync", target.url.toString());
	}

	@Test
	public void aRouterNameWithAPortWorksToo()
	{
		PhoneAddress.Target target = PhoneAddress.target(PhoneAddress.parse("galaxy-s23.lan:9000"),
			host -> new java.net.InetAddress[]{ip("fe80::1"), ip("192.168.1.57")});
		Assert.assertEquals("http://192.168.1.57:9000/sync", target.url.toString());
	}

	@Test
	public void aNamePointingAtTheInternetIsRefused()
	{
		PhoneAddress.Target target = PhoneAddress.target(PhoneAddress.parse("example.com"),
			host -> new java.net.InetAddress[]{ip("93.184.216.34")});
		Assert.assertNull(target.url);
		Assert.assertNotNull(target.problem);

		PhoneAddress.Target mixed = PhoneAddress.target(PhoneAddress.parse("sneaky.lan"),
			host -> new java.net.InetAddress[]{ip("192.168.1.5"), ip("8.8.8.8")});
		Assert.assertNull("every address must be on your own network", mixed.url);
	}

	@Test
	public void aNameThatCanNotBeFoundRightNowIsJustNotReachable()
	{
		PhoneAddress.Target target = PhoneAddress.target(PhoneAddress.parse("pixel-8"), host ->
		{
			throw new java.net.UnknownHostException(host);
		});
		Assert.assertNull(target.url);
		Assert.assertNull("not a settings problem: retried like a closed app", target.problem);
	}

	@Test
	public void bankOffLeavesTheBankAndItsPricesBehind()
	{
		Map<Integer, Long> prices = new HashMap<>();
		prices.put(995, 1L);      // coins in the inventory
		prices.put(11802, 9L);    // in the bank only
		prices.put(4151, 2L);     // on the GE
		prices.put(4151, 2L);
		StateDTO state = StateDTO.builder()
			.observedAt(10)
			.bankObservedAt(5)
			.inventory(Collections.singletonList(new StateDTO.ItemEntry(995, 1)))
			.bank(Arrays.asList(new StateDTO.ItemEntry(11802, 1)))
			.potionStorage(Collections.singletonList(new StateDTO.ItemEntry(12913, 3)))
			.geOffers(Collections.singletonList(new StateDTO.GeOfferEntry(1, 4151, 0, 1, 2L, 0L, "BUYING")))
			.prices(prices)
			.build();

		StateDTO sent = PayloadFilter.withoutBank(state);

		Assert.assertNull(sent.getBank());
		Assert.assertNull(sent.getPotionStorage());
		Assert.assertEquals(0, sent.getBankObservedAt());
		Assert.assertEquals(10, sent.getObservedAt());
		Assert.assertTrue(sent.getPrices().containsKey(995));
		Assert.assertTrue(sent.getPrices().containsKey(4151));
		Assert.assertFalse("a bank-only price would reveal the bank", sent.getPrices().containsKey(11802));
		Assert.assertNotNull("the original is untouched", state.getBank());
	}
}
