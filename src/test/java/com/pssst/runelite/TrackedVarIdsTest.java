package com.pssst.runelite;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 * The Android app reads these by number, so the ids are the contract. Migrating from the deprecated Varbits/VarPlayer
 * constants to gameval names must not change a single id; this pins them.
 */
public class TrackedVarIdsTest
{
	@Test
	public void trackedVarbitIdsAreExactlyTheOnesTheAppReads()
	{
		Set<Integer> expected = new HashSet<>(Arrays.asList(
			72, 74,                                       // Miscellania approval, coffer
			4539, 3961, 4547, 4540, 4549, 4559, 4563, 4543, 7939, // daily reset claims
			5099, 456,                                    // Tears of Guthix
			4479, 4480, 4481, 4482,                       // Varrock diary
			4487, 4488, 4489, 4490,                       // Morytania diary
			4458, 4459, 4460, 4461,                       // Ardougne diary
			4475, 4476, 4477, 4478,                       // Kandarin diary
			4471, 4472, 4473, 4474,                       // Western diary
			4466, 4467, 4468, 4469,                       // Wilderness diary
			7925, 7926, 7927, 7928,                       // Kourend diary
			5557,                                         // Autoweed
			1777, 1527                                    // Ironman status, Hand in the Sand progress (daily eligibility)
		));
		Assert.assertEquals(expected, CompanionPlugin.TRACKED_VARBITS);
	}

	@Test
	public void trackedVarpIdsAreExactlyNmzRewardPoints()
	{
		Assert.assertEquals(new HashSet<>(Arrays.asList(1060)), CompanionPlugin.TRACKED_VARPS);
	}
}
