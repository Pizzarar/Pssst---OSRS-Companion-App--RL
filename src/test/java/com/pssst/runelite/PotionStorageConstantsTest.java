package com.pssst.runelite;

import net.runelite.api.EnumID;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Assert;
import org.junit.Test;

/** The potion storage reader used bare numbers before; they equal RuneLite's named constants, and must stay so. */
public class PotionStorageConstantsTest
{
	@Test
	public void namedConstantsEqualTheNumbersThePluginShippedWith()
	{
		Assert.assertEquals(4826, EnumID.POTIONSTORE_POTIONS);
		Assert.assertEquals(4829, EnumID.POTIONSTORE_UNFINISHED_POTIONS);
		Assert.assertEquals(6080, ScriptID.POTIONSTORE_BUILD);
		Assert.assertEquals(6555, ScriptID.POTIONSTORE_DOSE_CHANGE);
		Assert.assertEquals(786490, InterfaceID.Bankmain.POTIONSTORE_ITEMS);
	}
}
