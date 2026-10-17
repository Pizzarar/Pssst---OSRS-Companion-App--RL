package com.pssst.runelite.birdhouse;

import com.pssst.runelite.birdhouse.BirdhouseTracker.House;
import com.pssst.runelite.birdhouse.BirdhouseTracker.Space;
import com.pssst.runelite.birdhouse.BirdhouseTracker.State;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;

/**
 * Compares the birdhouse tables and decoding against RuneLite's own timetracking classes on the test classpath.
 * They are package-private, so this test reads them reflectively (test-only; plugin code uses none).
 */
public class BirdhouseParityTest
{
	private static final String RL = "net.runelite.client.plugins.timetracking.hunter.";

	@Test
	public void spacesMatchRuneLite() throws Exception
	{
		Object[] theirs = Class.forName(RL + "BirdHouseSpace").getEnumConstants();
		Assert.assertEquals(theirs.length, Space.values().length);
		for (Object t : theirs)
		{
			Space ours = Space.valueOf(((Enum<?>) t).name());
			Assert.assertEquals(call(t, "getName"), ours.displayName);
			Assert.assertEquals(call(t, "getVarp"), ours.varp);
		}
	}

	@Test
	public void houseTypesMatchRuneLiteInOrderAndItemId() throws Exception
	{
		Object[] theirs = Class.forName(RL + "BirdHouse").getEnumConstants();
		Assert.assertEquals(theirs.length, House.values().length);
		for (int i = 0; i < theirs.length; i++)
		{
			Assert.assertEquals("order of house types", ((Enum<?>) theirs[i]).name(), House.values()[i].name());
			Assert.assertEquals(call(theirs[i], "getItemID"), House.values()[i].itemId);
		}
	}

	@Test
	public void stateAndHouseDecodingMatchRuneLite() throws Exception
	{
		Class<?> stateClass = Class.forName(RL + "BirdHouseState");
		Method fromVarp = stateClass.getDeclaredMethod("fromVarpValue", int.class);
		fromVarp.setAccessible(true);
		Class<?> houseClass = Class.forName(RL + "BirdHouse");
		Method houseFromVarp = houseClass.getDeclaredMethod("fromVarpValue", int.class);
		houseFromVarp.setAccessible(true);

		for (int v = -5; v <= 40; v++)
		{
			Assert.assertEquals("state of " + v, ((Enum<?>) fromVarp.invoke(null, v)).name(), State.fromVarp(v).name());

			Object theirHouse = houseFromVarp.invoke(null, v);
			House ourHouse = BirdhouseTracker.houseFromVarp(v);
			Assert.assertEquals("house of " + v, theirHouse == null ? null : ((Enum<?>) theirHouse).name(),
				ourHouse == null ? null : ourHouse.name());
		}
	}

	@Test
	public void islandRegionsAndDurationMatchRuneLite() throws Exception
	{
		Class<?> tracker = Class.forName(RL + "BirdHouseTracker");

		Field regions = tracker.getDeclaredField("FOSSIL_ISLAND_REGIONS");
		regions.setAccessible(true);
		@SuppressWarnings("unchecked")
		Set<Integer> theirs = new TreeSet<>((Set<Integer>) regions.get(null));

		// Ours is a private array, so compare behaviourally: the same tiles must be (or not be) on the island
		for (int regionId = 14000; regionId <= 16000; regionId++)
		{
			int x = ((regionId >> 8) * 64) + 10;
			int y = ((regionId & 0xFF) * 64) + 10;
			boolean ours = new BirdhouseTracker(v -> 0, new com.pssst.runelite.ObservationStore()
			{
				@Override
				public String profileKey()
				{
					return "p";
				}

				@Override
				public String get(String key)
				{
					return null;
				}

				@Override
				public void set(String key, String value)
				{
				}
			}, () -> 1L).update(x, y, 0);
			Assert.assertEquals("region " + regionId, theirs.contains(regionId), ours);
		}

		// Ours is the same figure in ms
		Field duration = tracker.getDeclaredField("BIRD_HOUSE_DURATION");
		duration.setAccessible(true);
		Assert.assertEquals(((Integer) duration.get(null)) * 1000L, BirdhouseTracker.DURATION_MS);
	}

	private static Object call(Object o, String name) throws Exception
	{
		for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass())
		{
			try
			{
				Method m = c.getDeclaredMethod(name);
				m.setAccessible(true);
				return m.invoke(o);
			}
			catch (NoSuchMethodException ignored)
			{
			}
		}
		throw new NoSuchMethodException(o.getClass() + "#" + name);
	}
}
