package com.pssst.runelite.farming;

import com.pssst.runelite.ObservationStore;
import com.pssst.runelite.StateDTO;
import com.pssst.runelite.farming.FarmingData.Patch;
import com.pssst.runelite.farming.FarmingData.Region;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/**
 * Records what each farming patch looked like the last time the player stood in its region.
 *
 * The farming varbits (FARMING_TRANSMIT_A..N) are slots the server fills with the patches of whatever region
 * the player is in, so a value only means something together with a region. Observations are therefore keyed
 * by (region, varbit), only taken while the player is in that region, and persisted per RuneScape profile
 * so a sync from anywhere in the world still carries every patch seen so far.
 *
 * Threading: update()/reload happen on the client thread; snapshot() is called from the sync executor.
 */
public class FarmingTracker
{
	private static final class Observation
	{
		final int value;
		final long observedAtMs;

		Observation(int value, long observedAtMs)
		{
			this.value = value;
			this.observedAtMs = observedAtMs;
		}
	}

	private static final Map<Integer, List<Region>> REGIONS_BY_ID = new HashMap<>();

	static
	{
		for (Region region : FarmingData.REGIONS)
		{
			REGIONS_BY_ID.computeIfAbsent(region.regionId, k -> new ArrayList<>()).add(region);
			for (int extra : region.extraRegionIds)
			{
				REGIONS_BY_ID.computeIfAbsent(extra, k -> new ArrayList<>()).add(region);
			}
		}
	}

	private final IntUnaryOperator varbitReader;
	private final ObservationStore store;
	private final LongSupplier clockMs;
	private final Map<String, Observation> observations = new ConcurrentHashMap<>();
	private String loadedProfile;

	public FarmingTracker(IntUnaryOperator varbitReader, ObservationStore store, LongSupplier clockMs)
	{
		this.varbitReader = varbitReader;
		this.store = store;
		this.clockMs = clockMs;
	}

	/**
	 * Reads the patches of every farming region containing the given tile.
	 *
	 * @return true if any observation was added or changed
	 */
	public boolean update(int x, int y, int plane)
	{
		String profile = store.profileKey();
		if (profile == null)
		{
			return false;
		}
		if (!profile.equals(loadedProfile))
		{
			load(profile);
		}

		int regionId = ((x >> 6) << 8) | (y >> 6);
		List<Region> candidates = REGIONS_BY_ID.get(regionId);
		if (candidates == null)
		{
			return false;
		}

		boolean changed = false;
		for (Region region : candidates)
		{
			if (!FarmingBounds.isInBounds(region.regionId, x, y, plane))
			{
				continue;
			}
			for (Patch patch : region.patches)
			{
				int value = varbitReader.applyAsInt(patch.varbit);
				String key = key(region, patch);
				Observation previous = observations.get(key);
				// The timestamp only moves when the value does. A growing crop changes value every tick, and a
				// re-read of an unchanged value (e.g. while a modal blocks varbit updates) must not look fresh.
				if (previous == null || previous.value != value)
				{
					long now = clockMs.getAsLong();
					observations.put(key, new Observation(value, now));
					store.set(key, value + ":" + now);
					changed = true;
				}
			}
		}
		return changed;
	}

	/** Every patch observed so far, in region-table order. Safe to call from any thread. */
	public List<StateDTO.FarmingEntry> snapshot()
	{
		List<StateDTO.FarmingEntry> out = new ArrayList<>();
		for (Region region : FarmingData.REGIONS)
		{
			for (Patch patch : region.patches)
			{
				Observation o = observations.get(key(region, patch));
				if (o != null)
				{
					out.add(toEntry(region, patch, o));
				}
			}
		}
		return out;
	}

	public void clear()
	{
		observations.clear();
		loadedProfile = null;
	}

	private void load(String profile)
	{
		observations.clear();
		for (Region region : FarmingData.REGIONS)
		{
			for (Patch patch : region.patches)
			{
				String key = key(region, patch);
				Observation o = parse(store.get(key));
				if (o != null)
				{
					observations.put(key, o);
				}
			}
		}
		loadedProfile = profile;
	}

	private static Observation parse(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String[] parts = raw.split(":");
		if (parts.length != 2)
		{
			return null;
		}
		try
		{
			return new Observation(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	private static String key(Region region, Patch patch)
	{
		return "farming." + region.regionId + "." + patch.varbit;
	}

	private static StateDTO.FarmingEntry toEntry(Region region, Patch patch, Observation o)
	{
		// Decoded on every snapshot (not stored) so a table fix also corrects observations made before it
		PatchDecoder.Decoded d = PatchDecoder.decode(patch.type, o.value);
		StateDTO.FarmingEntry e = new StateDTO.FarmingEntry();
		e.setRegionId(region.regionId);
		e.setRegionName(region.name);
		e.setPatchName(patch.name);
		e.setPatchType(patch.type.name());
		e.setVarbitId(patch.varbit);
		e.setValue(o.value);
		e.setObservedAt(o.observedAtMs);
		if (d != null)
		{
			e.setCropName(d.crop.displayName);
			e.setItemId(d.crop.itemId);
			e.setCropState(d.state.name());
			e.setStage(d.stage);
			e.setStages(d.stages);
			e.setTickMinutes(d.tickMinutes);
		}
		return e;
	}
}
