package com.pssst.runelite;

import org.junit.Assert;
import org.junit.Test;

public class PairingPanelTest
{
	private static final long NOW = 10_000_000L;

	private static String describe(boolean sync, boolean send, String problem, DeliveryPolicy.State state, boolean data, long last)
	{
		return PairingPanel.describe(sync, send, problem, state, data, last, 0, NOW);
	}

	@Test
	public void theStatusSaysWhatToDoNext()
	{
		Assert.assertTrue(describe(false, true, null, DeliveryPolicy.State.IDLE, true, 0).contains("Enable Sync"));
		Assert.assertTrue(describe(true, false, null, DeliveryPolicy.State.IDLE, true, 0).contains("Send to My Phone"));
		Assert.assertTrue(describe(true, true, "No Phone Address yet.", DeliveryPolicy.State.IDLE, true, 0).contains("No Phone Address"));
		Assert.assertTrue(describe(true, true, null, DeliveryPolicy.State.REJECTED, true, 0).contains("Sync Token"));
		Assert.assertTrue(describe(true, true, null, DeliveryPolicy.State.WAITING, true, 0).contains("Waiting"));
		Assert.assertTrue(describe(true, true, null, DeliveryPolicy.State.IDLE, false, 0).contains("Log in once"));
	}

	@Test
	public void aShortHandTypedTokenGetsAGentleNote()
	{
		Assert.assertNull(PairingPanel.tokenNote("Goblin"));
		Assert.assertNull(PairingPanel.tokenNote("my-own-sync-token-123"));
		String generated = CompanionPlugin.newToken();
		Assert.assertNull(PairingPanel.tokenNote(generated));
		Assert.assertTrue("expected <monster>-<item>, got " + generated, generated.matches("[a-z]+-[a-z]+"));
	}

	@Test
	public void afterHalfAnHourWithoutThePhoneItSuggestsTheAddressChanged()
	{
		String soon = PairingPanel.describe(true, true, null, DeliveryPolicy.State.WAITING, true, 0, NOW - 5 * 60_000L, NOW);
		Assert.assertTrue(soon, soon.contains("Waiting for your phone"));
		String late = PairingPanel.describe(true, true, null, DeliveryPolicy.State.WAITING, true, 0, NOW - 45 * 60_000L, NOW);
		Assert.assertTrue(late, late.contains("address may have changed"));
		Assert.assertTrue(late, late.contains("45 min"));
	}

	@Test
	public void deliveredShowsHowLongAgo()
	{
		String text = describe(true, true, null, DeliveryPolicy.State.DELIVERED, true, NOW - 5 * 60_000L);
		Assert.assertTrue(text, text.contains("up to date"));
		Assert.assertTrue(text, text.contains("5 min ago"));
		Assert.assertEquals("just now", PairingPanel.ago(10_000));
		Assert.assertEquals("3 h ago", PairingPanel.ago(3 * 3_600_000L));
		Assert.assertEquals("3 days ago", PairingPanel.ago(72 * 3_600_000L));
	}
}
