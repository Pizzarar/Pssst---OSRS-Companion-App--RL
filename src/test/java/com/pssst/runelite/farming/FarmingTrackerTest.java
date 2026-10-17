package com.pssst.runelite.farming;

import com.pssst.runelite.ObservationStore;
import com.pssst.runelite.StateDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FarmingTrackerTest
{
	// Tiles chosen inside each region: id = ((x >> 6) << 8) | (y >> 6)
	private static final int[] CATHERBY = {2805, 3465, 0};          // 11062, ground level
	private static final int[] CATHERBY_UPSTAIRS = {2830, 3445, 1}; // region 11317 upstairs: fruit tree patch only
	private static final int[] FALADOR = {3050, 3300, 0};           // 12083, y >= 3272
	private static final int[] NOWHERE = {1000, 1000, 0};           // no farming region

	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<String, String> config = new HashMap<>();
	private final AtomicLong now = new AtomicLong(1_000_000L);
	private String profile = "profileA";
	private FarmingTracker tracker;

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
		tracker = new FarmingTracker(id -> varbits.getOrDefault(id, 0), store(), now::get);
	}

	private boolean update(int[] p)
	{
		return tracker.update(p[0], p[1], p[2]);
	}

	private StateDTO.FarmingEntry find(int regionId, int varbit)
	{
		for (StateDTO.FarmingEntry e : tracker.snapshot())
		{
			if (e.getRegionId() == regionId && e.getVarbitId() == varbit)
			{
				return e;
			}
		}
		return null;
	}

	@Test
	public void nothingIsRecordedOutsideFarmingRegions()
	{
		Assert.assertFalse(update(NOWHERE));
		Assert.assertTrue(tracker.snapshot().isEmpty());
	}

	@Test
	public void decodesTheHerbPatchInTheRegionThePlayerIsIn()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 33); // ranarr, growing, stage 1 of 5
		Assert.assertTrue(update(CATHERBY));

		StateDTO.FarmingEntry herb = find(11062, VarbitID.FARMING_TRANSMIT_D);
		Assert.assertNotNull(herb);
		Assert.assertEquals("Catherby", herb.getRegionName());
		Assert.assertEquals("HERB", herb.getPatchType());
		Assert.assertEquals("Ranarr", herb.getCropName());
		Assert.assertEquals("GROWING", herb.getCropState());
		Assert.assertEquals(1, herb.getStage());
		Assert.assertEquals(5, herb.getStages());
		Assert.assertEquals(20, herb.getTickMinutes());
		Assert.assertEquals(33, herb.getValue());
		Assert.assertEquals(1_000_000L, herb.getObservedAt());
	}

	@Test
	public void sameVarbitSlotInTwoRegionsStaysSeparate()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 33);
		update(CATHERBY);
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 8); // Falador herb patch: guam, harvestable
		update(FALADOR);

		Assert.assertEquals("Ranarr", find(11062, VarbitID.FARMING_TRANSMIT_D).getCropName());
		Assert.assertEquals("Guam", find(12083, VarbitID.FARMING_TRANSMIT_D).getCropName());
	}

	@Test
	public void timestampOnlyMovesWhenTheValueChanges()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 33);
		update(CATHERBY);

		now.addAndGet(60_000L);
		Assert.assertFalse("unchanged value is not a change", update(CATHERBY));
		Assert.assertEquals(1_000_000L, find(11062, VarbitID.FARMING_TRANSMIT_D).getObservedAt());

		varbits.put(VarbitID.FARMING_TRANSMIT_D, 34);
		Assert.assertTrue(update(CATHERBY));
		Assert.assertEquals(1_060_000L, find(11062, VarbitID.FARMING_TRANSMIT_D).getObservedAt());
	}

	@Test
	public void respectsRegionBounds()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 33);
		varbits.put(VarbitID.FARMING_TRANSMIT_A, 33);
		Assert.assertTrue(update(CATHERBY_UPSTAIRS));
		Assert.assertNull("allotment/herb region does not cover upstairs", find(11062, VarbitID.FARMING_TRANSMIT_D));
		Assert.assertNotNull("fruit tree region does", find(11317, VarbitID.FARMING_TRANSMIT_A));
	}

	@Test
	public void observationsSurviveARestartAndAreKeptPerProfile()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 33);
		update(CATHERBY);

		FarmingTracker restarted = new FarmingTracker(id -> 0, store(), now::get);
		restarted.update(NOWHERE[0], NOWHERE[1], NOWHERE[2]);
		List<StateDTO.FarmingEntry> restored = restarted.snapshot();
		Assert.assertEquals(1, restored.stream().filter(e -> e.getVarbitId() == VarbitID.FARMING_TRANSMIT_D).count());

		profile = "profileB";
		restarted.update(NOWHERE[0], NOWHERE[1], NOWHERE[2]);
		Assert.assertTrue("a different profile starts empty", restarted.snapshot().isEmpty());
	}

	@Test
	public void waitsForAProfileBeforeRecordingAnything()
	{
		profile = null;
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 33);
		Assert.assertFalse(update(CATHERBY));
		Assert.assertTrue(tracker.snapshot().isEmpty());
	}

	@Test
	public void unknownValuesAreKeptRawWithoutCropFields()
	{
		varbits.put(VarbitID.FARMING_TRANSMIT_D, 120); // gap between the herb growth and disease states
		update(CATHERBY);
		StateDTO.FarmingEntry herb = find(11062, VarbitID.FARMING_TRANSMIT_D);
		Assert.assertEquals(120, herb.getValue());
		Assert.assertNull(herb.getCropName());
		Assert.assertNull(herb.getCropState());
	}
}
