package com.pssst.runelite;

import org.junit.Assert;
import org.junit.Test;

public class PairingPanelTest
{
	private static final long NOW = 10_000_000L;

	private static String describe(boolean sync, boolean send, String problem, DeliveryPolicy.State state, boolean data, long last)
	{
		return PairingPanel.describe(sync, send, problem, state, data, last, NOW);
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
	public void aShortHandTypedTokenIsFlagged()
	{
		String text = PairingPanel.describe(true, 3, true, null, DeliveryPolicy.State.DELIVERED, true, 0, NOW);
		Assert.assertTrue(text, text.contains("too short"));
		Assert.assertFalse(PairingPanel.describe(true, 32, true, null, DeliveryPolicy.State.DELIVERED, true, 0, NOW).contains("too short"));
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
