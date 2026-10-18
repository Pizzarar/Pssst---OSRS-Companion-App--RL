package com.pssst.runelite;

import org.junit.Assert;
import org.junit.Test;

public class PairingPanelTest
{
	@Test
	public void theStatusSaysWhatToDoNext()
	{
		Assert.assertTrue(PairingPanel.statusText(false, true, false, false).contains("Enable Sync"));
		Assert.assertTrue(PairingPanel.statusText(true, false, false, false).contains("Enable Local Server"));
		Assert.assertTrue(PairingPanel.statusText(true, true, false, false).contains("not running"));
		Assert.assertTrue(PairingPanel.statusText(true, true, true, true).contains("This PC Only"));
		Assert.assertTrue(PairingPanel.statusText(true, true, false, true).contains("Ready"));
	}

	@Test
	public void thePanelNeverClaimsReadyWhileThisPcOnlyIsOn()
	{
		Assert.assertFalse(PairingPanel.statusText(true, true, true, true).contains("Ready"));
	}
}
