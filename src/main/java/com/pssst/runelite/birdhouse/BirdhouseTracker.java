/*
 * The varp decoding, the Fossil Island regions and the 50 minute duration follow RuneLite's timetracking
 * BirdHouseState / BirdHouseTracker (BSD 2-Clause, Copyright (c) 2018 Abex, Adam, et al.).
 * BirdhouseParityTest checks them against the real classes.
 */
package com.pssst.runelite.birdhouse;

import com.pssst.runelite.ObservationStore;
import com.pssst.runelite.StateDTO;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;

/**
 * Records what each Fossil Island birdhouse space looked like the last time the player stood on the island.
 *
 * The four birdhouse varps only carry real values while the player is on Fossil Island (plane 0), so, as with
 * farming patches, they are read only there and the last observation is persisted per RuneScape profile.
 * A house is ready 50 minutes after the moment its seeded value was first observed; that is not a varp state.
 *
 * Threading: update() runs on the client thread; snapshot() is called from the sync executor.
 */
public class BirdhouseTracker
{
	/** RuneLite's figure: average time to harvest 10 birds. */
	static final long DURATION_MS = TimeUnit.MINUTES.toMillis(50);

	// RuneLite's FOSSIL_ISLAND_REGIONS
	private static final int[] FOSSIL_ISLAND_REGIONS = {14650, 14651, 14652, 14906, 14907, 15162, 15163};

	enum Space
	{
		MEADOW_NORTH("Mushroom Meadow (North)", VarPlayerID.BIRDHOUSE_TRANSMIT_A),
		MEADOW_SOUTH("Mushroom Meadow (South)", VarPlayerID.BIRDHOUSE_TRANSMIT_B),
		VALLEY_NORTH("Verdant Valley (Northeast)", VarPlayerID.BIRDHOUSE_TRANSMIT_C),
		VALLEY_SOUTH("Verdant Valley (Southwest)", VarPlayerID.BIRDHOUSE_TRANSMIT_D);

		final String displayName;
		final int varp;

		Space(String displayName, int varp)
		{
			this.displayName = displayName;
			this.varp = varp;
		}
	}

	/** In varp order: the house type of a built or seeded space is (value - 1) / 3. */
	enum House
	{
		NORMAL(ItemID.BIRDHOUSE_NORMAL),
		OAK(ItemID.BIRDHOUSE_OAK),
		WILLOW(ItemID.BIRDHOUSE_WILLOW),
		TEAK(ItemID.BIRDHOUSE_TEAK),
		MAPLE(ItemID.BIRDHOUSE_MAPLE),
		MAHOGANY(ItemID.BIRDHOUSE_MAHOGANY),
		YEW(ItemID.BIRDHOUSE_YEW),
		MAGIC(ItemID.BIRDHOUSE_MAGIC),
		REDWOOD(ItemID.BIRDHOUSE_REDWOOD);

		final int itemId;

		House(int itemId)
		{
			this.itemId = itemId;
		}
	}

	enum State
	{
		SEEDED, BUILT, EMPTY, UNKNOWN;

		static State fromVarp(int value)
		{
			if (value < 0 || value > House.values().length * 3)
			{
				return UNKNOWN;
			}
			if (value == 0)
			{
				return EMPTY;
			}
			// Easy to get backwards: a multiple of 3 is the seeded house, the two values before it are "built"
			return value % 3 == 0 ? SEEDED : BUILT;
		}
	}

	static House houseFromVarp(int value)
	{
		int index = (value - 1) / 3;
		return value <= 0 || index >= House.values().length ? null : House.values()[index];
	}

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

	private final IntUnaryOperator varpReader;
	private final ObservationStore store;
	private final LongSupplier clockMs;
	private final Map<Space, Observation> observations = new ConcurrentHashMap<>();
	private String loadedProfile;

	public BirdhouseTracker(IntUnaryOperator varpReader, ObservationStore store, LongSupplier clockMs)
	{
		this.varpReader = varpReader;
		this.store = store;
		this.clockMs = clockMs;
	}

	/**
	 * Reads the four birdhouse varps if the tile is on Fossil Island's ground level.
	 *
	 * @return true if any observation was added or changed
	 */
	public boolean update(int x, int y, int plane)
	{
		String profile = store.profileKey();
		if (profile == null || plane != 0 || !isFossilIsland(((x >> 6) << 8) | (y >> 6)))
		{
			return false;
		}
		if (!profile.equals(loadedProfile))
		{
			load(profile);
		}

		Map<Space, Observation> changes = new EnumMap<>(Space.class);
		int dropToEmpty = 0;
		long now = clockMs.getAsLong();
		for (Space space : Space.values())
		{
			int value = varpReader.applyAsInt(space.varp);
			Observation previous = observations.get(space);
			int previousValue = previous == null ? -1 : previous.value;
			// The timestamp only moves when the value does, so "first observed seeded" survives later re-reads
			if (value != previousValue)
			{
				changes.put(space, new Observation(value, now));
			}
			if (value <= 0 && previousValue > 0)
			{
				dropToEmpty++;
			}
		}

		// Right after entering the island the varps may not be updated yet and read 0. Players keep 3-4 houses up,
		// so most of them dropping to empty at once is a stale read, not a real change (same guard as RuneLite).
		if (dropToEmpty > 2 || changes.isEmpty())
		{
			return false;
		}
		for (Map.Entry<Space, Observation> e : changes.entrySet())
		{
			observations.put(e.getKey(), e.getValue());
			store.set(key(e.getKey()), e.getValue().value + ":" + e.getValue().observedAtMs);
		}
		return true;
	}

	/** Every space observed so far, in space order. Safe to call from any thread. */
	public List<StateDTO.BirdhouseEntry> snapshot()
	{
		List<StateDTO.BirdhouseEntry> out = new ArrayList<>();
		for (Space space : Space.values())
		{
			Observation o = observations.get(space);
			if (o != null)
			{
				out.add(toEntry(space, o));
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
		for (Space space : Space.values())
		{
			Observation o = parse(store.get(key(space)));
			if (o != null)
			{
				observations.put(space, o);
			}
		}
		loadedProfile = profile;
	}

	private static boolean isFossilIsland(int regionId)
	{
		for (int id : FOSSIL_ISLAND_REGIONS)
		{
			if (id == regionId)
			{
				return true;
			}
		}
		return false;
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

	private static String key(Space space)
	{
		return "birdhouse." + space.varp;
	}

	private static StateDTO.BirdhouseEntry toEntry(Space space, Observation o)
	{
		// Decoded on every snapshot (not stored) so a table fix also corrects observations made before it
		State state = State.fromVarp(o.value);
		House house = houseFromVarp(o.value);
		StateDTO.BirdhouseEntry e = new StateDTO.BirdhouseEntry();
		e.setSpace(space.name());
		e.setSpaceName(space.displayName);
		e.setVarpId(space.varp);
		e.setValue(o.value);
		e.setObservedAt(o.observedAtMs);
		e.setState(state.name());
		if (house != null)
		{
			e.setHouseType(house.name());
			e.setHouseItemId(house.itemId);
		}
		e.setReadyAt(state == State.SEEDED ? o.observedAtMs + DURATION_MS : 0L);
		return e;
	}
}
