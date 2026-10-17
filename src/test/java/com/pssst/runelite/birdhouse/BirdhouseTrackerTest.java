package com.pssst.runelite.birdhouse;

import com.pssst.runelite.ObservationStore;
import com.pssst.runelite.StateDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.gameval.VarPlayerID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BirdhouseTrackerTest
{
	private static final int[] FOSSIL_ISLAND = {3700, 3800, 0};          // region 14651, ground level
	private static final int[] FOSSIL_ISLAND_UPSTAIRS = {3700, 3800, 1}; // same tile, plane 1
	private static final int[] LUMBRIDGE = {3222, 3218, 0};
	private static final long FIFTY_MIN = TimeUnit.MINUTES.toMillis(50);

	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<String, String> config = new HashMap<>();
	private final AtomicLong now = new AtomicLong(5_000_000L);
	private String profile = "profileA";
	private BirdhouseTracker tracker;

	private ObservationStore store()
	{
		return new ObservationStore()
		{
			@Override
			public String profileKey()
			{
				return profile;
			}

			@Override
			public String get(String key)
			{
				return config.get(profile + "/" + key);
			}

			@Override
			public void set(String key, String value)
			{
				config.put(profile + "/" + key, value);
			}
		};
	}

	@Before
	public void setUp()
	{
		tracker = new BirdhouseTracker(id -> varps.getOrDefault(id, 0), store(), now::get);
	}

	private boolean update(int[] p)
	{
		return tracker.update(p[0], p[1], p[2]);
	}

	private StateDTO.BirdhouseEntry find(String space)
	{
		for (StateDTO.BirdhouseEntry e : tracker.snapshot())
		{
			if (e.getSpace().equals(space))
			{
				return e;
			}
		}
		return null;
	}

	@Test
	public void onlyReadsOnFossilIslandGroundLevel()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27);
		Assert.assertFalse(update(LUMBRIDGE));
		Assert.assertFalse(update(FOSSIL_ISLAND_UPSTAIRS));
		Assert.assertTrue(tracker.snapshot().isEmpty());
	}

	@Test
	public void multipleOfThreeIsSeededAndTheTwoBeforeItAreBuilt()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27); // redwood, seeded
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_B, 26); // redwood, built only
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_C, 4);  // oak, built only
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_D, 0);  // nothing
		Assert.assertTrue(update(FOSSIL_ISLAND));

		StateDTO.BirdhouseEntry seeded = find("MEADOW_NORTH");
		Assert.assertEquals("SEEDED", seeded.getState());
		Assert.assertEquals("REDWOOD", seeded.getHouseType());
		Assert.assertEquals(22204, seeded.getHouseItemId());
		Assert.assertEquals("Mushroom Meadow (North)", seeded.getSpaceName());
		Assert.assertEquals(5_000_000L + FIFTY_MIN, seeded.getReadyAt());

		StateDTO.BirdhouseEntry built = find("MEADOW_SOUTH");
		Assert.assertEquals("BUILT", built.getState());
		Assert.assertEquals(0L, built.getReadyAt());

		Assert.assertEquals("OAK", find("VALLEY_NORTH").getHouseType());
		Assert.assertEquals("BUILT", find("VALLEY_NORTH").getState());

		StateDTO.BirdhouseEntry empty = find("VALLEY_SOUTH");
		Assert.assertEquals("EMPTY", empty.getState());
		Assert.assertNull(empty.getHouseType());
	}

	@Test
	public void readyTimeCountsFromWhenTheSeededValueWasFirstObserved()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 26); // built
		update(FOSSIL_ISLAND);

		now.addAndGet(10 * 60_000L);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27); // now seeded
		Assert.assertTrue(update(FOSSIL_ISLAND));
		long seededAt = now.get();
		Assert.assertEquals(seededAt + FIFTY_MIN, find("MEADOW_NORTH").getReadyAt());

		now.addAndGet(20 * 60_000L);
		Assert.assertFalse("re-reading the same value is not a change", update(FOSSIL_ISLAND));
		Assert.assertEquals(seededAt, find("MEADOW_NORTH").getObservedAt());
		Assert.assertEquals(seededAt + FIFTY_MIN, find("MEADOW_NORTH").getReadyAt());
	}

	@Test
	public void ignoresAStaleReadWhereMostHousesLookEmptied()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_B, 27);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_C, 27);
		update(FOSSIL_ISLAND);

		// Just after entering the island the varps can still read 0
		varps.clear();
		now.addAndGet(60_000L);
		Assert.assertFalse(update(FOSSIL_ISLAND));
		Assert.assertEquals("SEEDED", find("MEADOW_NORTH").getState());
		Assert.assertEquals("SEEDED", find("MEADOW_SOUTH").getState());
	}

	@Test
	public void aSingleHarvestedHouseIsAcceptedAsEmpty()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27);
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_B, 27);
		update(FOSSIL_ISLAND);

		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 0);
		Assert.assertTrue(update(FOSSIL_ISLAND));
		Assert.assertEquals("EMPTY", find("MEADOW_NORTH").getState());
		Assert.assertEquals("SEEDED", find("MEADOW_SOUTH").getState());
	}

	@Test
	public void valuesOutsideTheKnownRangeAreUnknown()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 28);
		update(FOSSIL_ISLAND);
		Assert.assertEquals("UNKNOWN", find("MEADOW_NORTH").getState());
		Assert.assertNull(find("MEADOW_NORTH").getHouseType());
	}

	@Test
	public void observationsSurviveARestartAndAreKeptPerProfile()
	{
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27);
		update(FOSSIL_ISLAND);

		BirdhouseTracker restarted = new BirdhouseTracker(id -> 0, store(), now::get);
		restarted.update(FOSSIL_ISLAND[0], FOSSIL_ISLAND[1], FOSSIL_ISLAND[2]);
		// The restart reads 0 from a fresh client; only one house changed, so it is accepted as emptied. The point
		// is the tracker loaded the saved value first (it did not treat the space as never seen).
		Assert.assertEquals("EMPTY", restarted.snapshot().get(0).getState());

		profile = "profileB";
		BirdhouseTracker other = new BirdhouseTracker(id -> 27, store(), now::get);
		other.update(FOSSIL_ISLAND[0], FOSSIL_ISLAND[1], FOSSIL_ISLAND[2]);
		List<StateDTO.BirdhouseEntry> entries = other.snapshot();
		Assert.assertEquals(4, entries.size());
		Assert.assertEquals("SEEDED", entries.get(0).getState());
	}

	@Test
	public void waitsForAProfileBeforeRecordingAnything()
	{
		profile = null;
		varps.put(VarPlayerID.BIRDHOUSE_TRANSMIT_A, 27);
		Assert.assertFalse(update(FOSSIL_ISLAND));
		Assert.assertTrue(tracker.snapshot().isEmpty());
	}
}
