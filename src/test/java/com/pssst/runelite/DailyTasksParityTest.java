package com.pssst.runelite;

import java.lang.reflect.Field;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import org.junit.Test;

/**
 * The Android app's DailyTasks evaluator is a port of RuneLite's Daily Tasks plugin. Its constants and the varbits it
 * reads are pinned here, against the real class on the test classpath, because this is the repo that can see it.
 * If this fails after a RuneLite update, re-read DailyTasksPlugin and update DailyTasks.kt in the app to match.
 */
public class DailyTasksParityTest
{
	private static final String RL = "net.runelite.client.plugins.dailytaskindicators.DailyTasksPlugin";

	private static int constant(String name) throws Exception
	{
		Field field = Class.forName(RL).getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(null);
	}

	@Test
	public void constantsMatchWhatTheAppHardcodes() throws Exception
	{
		Assert.assertEquals("NMZ herb boxes per day", 15, constant("HERB_BOX_MAX"));
		Assert.assertEquals("NMZ points per herb box", 9500, constant("HERB_BOX_COST"));
		Assert.assertEquals("bonemeal per Morytania diary tier", 13, constant("BONEMEAL_PER_DIARY"));
		Assert.assertEquals("Hand in the Sand varbit value when done", 160, constant("SAND_QUEST_COMPLETE"));
	}

	@Test
	public void theVarbitsTheAppReadsAreTheOnesRuneLiteReads()
	{
		// DailyTasks.kt uses these numeric ids; the names are what RuneLite's checkX methods use
		Assert.assertEquals(1777, VarbitID.IRONMAN);
		Assert.assertEquals(1527, VarbitID.HANDSAND_QUEST);
		Assert.assertEquals(3961, VarbitID.NZONE_HERBBOXES_PURCHASED);
		Assert.assertEquals(4539, VarbitID.ZAFF_LAST_CLAIMED);
		Assert.assertEquals(4547, VarbitID.ARDOUGNE_FREE_ESSENCE);
		Assert.assertEquals(4540, VarbitID.LUNDAIL_LAST_CLAIMED);
		Assert.assertEquals(4549, VarbitID.YANILLE_SAND_CLAIMED);
		Assert.assertEquals(4559, VarbitID.SEERS_FREE_FLAX);
		Assert.assertEquals(4563, VarbitID.WESTERN_RANTZ_ARROWS);
		Assert.assertEquals(4543, VarbitID.MORYTANIA_SLIME_CLAIMED);
		Assert.assertEquals(7939, VarbitID.KOUREND_FREE_DYNAMITE);
		// the diary gates: Varrock easy, Ardougne medium, Wilderness easy, Kandarin easy, Western easy, Morytania m/h/e, Kourend medium
		Assert.assertEquals(4479, VarbitID.VARROCK_DIARY_EASY_COMPLETE);
		Assert.assertEquals(4459, VarbitID.ARDOUGNE_DIARY_MEDIUM_COMPLETE);
		Assert.assertEquals(4466, VarbitID.WILDERNESS_DIARY_EASY_COMPLETE);
		Assert.assertEquals(4475, VarbitID.KANDARIN_DIARY_EASY_COMPLETE);
		Assert.assertEquals(4471, VarbitID.WESTERN_DIARY_EASY_COMPLETE);
		Assert.assertEquals(4488, VarbitID.MORYTANIA_DIARY_MEDIUM_COMPLETE);
		Assert.assertEquals(4489, VarbitID.MORYTANIA_DIARY_HARD_COMPLETE);
		Assert.assertEquals(4490, VarbitID.MORYTANIA_DIARY_ELITE_COMPLETE);
		Assert.assertEquals(7926, VarbitID.KOUREND_DIARY_MEDIUM_COMPLETE);
	}

	@Test
	public void everyVarbitTheAppNeedsIsTracked()
	{
		int[] needed = {1777, 1527, 3961, 4539, 4547, 4540, 4549, 4559, 4563, 4543, 7939,
			4479, 4459, 4466, 4475, 4471, 4488, 4489, 4490, 7926};
		for (int id : needed)
		{
			Assert.assertTrue("varbit " + id + " must be sent to the app", CompanionPlugin.TRACKED_VARBITS.contains(id));
		}
	}
}
