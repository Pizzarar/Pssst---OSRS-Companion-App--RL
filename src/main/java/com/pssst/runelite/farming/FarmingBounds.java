/*
 * Hand-ported from the anonymous FarmingRegion.isInBounds overrides in RuneLite's timetracking FarmingWorld
 * (BSD 2-Clause, Copyright (c) 2018 Abex, Adam, et al.). FarmingParityTest checks it against the real classes.
 */
package com.pssst.runelite.farming;

final class FarmingBounds
{
	private static final int CATHERBY = 11062;
	private static final int CATHERBY_FRUIT_TREE = 11317;
	private static final int FALADOR_FARM = 12083;
	private static final int PORT_SARIM = 12082;
	private static final int FOSSIL_ISLAND = 14651;

	private FarmingBounds()
	{
	}

	/** Whether the location counts as inside the region whose primary id is {@code regionId}. Most regions have no extra bounds. */
	static boolean isInBounds(int regionId, int x, int y, int plane)
	{
		switch (regionId)
		{
			case CATHERBY:
				if (x >= 2816 && y < 3456)
				{
					// Upstairs sends different varbits
					return x < 2840 && y >= 3440 && plane == 0;
				}
				return true;
			case CATHERBY_FRUIT_TREE:
				// The fruit tree patch is always sent when upstairs in 11317
				return x >= 2840 || y < 3440 || plane == 1;
			case FALADOR_FARM:
				// Not on region boundary due to Port Sarim spirit tree patch
				return y >= 3272;
			case PORT_SARIM:
				return y < 3272;
			case FOSSIL_ISLAND:
				// Hardwood varbits are sent anywhere on plane 0, but 1 tick early at some ladders and stairs
				if (x == 3753 && y >= 3868 && y <= 3870)
				{
					return false;
				}
				if ((x == 3729 || x == 3728 || x == 3747 || x == 3746) && y <= 3832 && y >= 3830)
				{
					return false;
				}
				return plane == 0;
			default:
				return true;
		}
	}
}
