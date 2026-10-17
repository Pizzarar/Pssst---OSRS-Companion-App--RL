package com.pssst.runelite.farming;

import com.google.common.collect.Multimap;
import com.pssst.runelite.farming.FarmingData.Patch;
import com.pssst.runelite.farming.FarmingData.PatchType;
import com.pssst.runelite.farming.FarmingData.Region;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.timetracking.farming.FarmingRegion;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Compares the generated/ported farming tables against RuneLite's own timetracking classes on the test classpath.
 * RuneLite's classes are package-private, so this test reads them reflectively (test-only; plugin code uses none).
 * A failure after a RuneLite update means: run `gradlew generateFarmingData`, re-check FarmingBounds, commit.
 */
public class FarmingParityTest
{
	private static final String RL = "net.runelite.client.plugins.timetracking.farming.";

	private static Multimap<Integer, Object> rlRegions;

	@BeforeClass
	public static void loadRuneLiteWorld() throws Exception
	{
		Class<?> worldClass = Class.forName(RL + "FarmingWorld");
		Constructor<?> ctor = worldClass.getDeclaredConstructor();
		ctor.setAccessible(true);
		Object world = ctor.newInstance();
		Field f = worldClass.getDeclaredField("regions");
		f.setAccessible(true);
		@SuppressWarnings("unchecked")
		Multimap<Integer, Object> m = (Multimap<Integer, Object>) f.get(world);
		rlRegions = m;
	}

	@Test
	public void decodeMatchesRuneLiteForEveryPatchTypeAndValue() throws Exception
	{
		Class<?> implClass = Class.forName(RL + "PatchImplementation");
		Method decode = implClass.getDeclaredMethod("forVarbitValue", int.class);
		decode.setAccessible(true);

		Object[] impls = implClass.getEnumConstants();
		Assert.assertEquals("PatchType set differs from RuneLite's PatchImplementation", impls.length, PatchType.values().length);

		for (Object impl : impls)
		{
			String name = ((Enum<?>) impl).name();
			PatchType type = PatchType.valueOf(name);
			// Past 255 the varbit cannot hold a value, but make sure we agree (both null) well beyond it
			for (int v = 0; v <= 300; v++)
			{
				Object expected = decode.invoke(impl, v);
				PatchDecoder.Decoded actual = PatchDecoder.decode(type, v);
				String where = name + " value " + v;
				if (expected == null)
				{
					Assert.assertNull(where + ": expected no state", actual);
					continue;
				}
				Assert.assertNotNull(where + ": missing state", actual);
				Assert.assertEquals(where + " crop", ((Enum<?>) call(expected, "getProduce")).name(), actual.crop.name());
				Assert.assertEquals(where + " state", ((Enum<?>) call(expected, "getCropState")).name(), actual.state.name());
				Assert.assertEquals(where + " stage", call(expected, "getStage"), actual.stage);
				Assert.assertEquals(where + " stages", call(expected, "getStages"), actual.stages);
				Assert.assertEquals(where + " tickRate", call(expected, "getTickRate"), actual.tickMinutes);
			}
		}
	}

	@Test
	public void cropsMatchRuneLiteProduce() throws Exception
	{
		Object[] produce = Class.forName(RL + "Produce").getEnumConstants();
		Assert.assertEquals(produce.length, FarmingData.Crop.values().length);
		for (Object p : produce)
		{
			FarmingData.Crop c = FarmingData.Crop.valueOf(((Enum<?>) p).name());
			Assert.assertEquals(call(p, "getName"), c.displayName);
			Assert.assertEquals(call(p, "getItemID"), c.itemId);
			Assert.assertEquals(call(p, "getTickrate"), c.tickMinutes);
			Assert.assertEquals(call(p, "getStages"), c.stages);
			Assert.assertEquals(call(p, "getRegrowTickrate"), c.regrowTickMinutes);
			Assert.assertEquals(call(p, "getHarvestStages"), c.harvestStages);
		}
	}

	@Test
	public void regionLayoutMatchesRuneLite() throws Exception
	{
		Map<Object, Set<Integer>> extras = new IdentityHashMap<>();
		Map<Integer, Object> primaries = new java.util.TreeMap<>();
		for (Map.Entry<Integer, Object> e : rlRegions.entries())
		{
			int primary = (Integer) call(e.getValue(), "getRegionID");
			primaries.put(primary, e.getValue());
			extras.computeIfAbsent(e.getValue(), k -> new TreeSet<>());
			if (e.getKey() != primary)
			{
				extras.get(e.getValue()).add(e.getKey());
			}
		}

		Assert.assertEquals("region count", primaries.size(), FarmingData.REGIONS.length);
		for (Region ours : FarmingData.REGIONS)
		{
			Object theirs = primaries.get(ours.regionId);
			Assert.assertNotNull("unknown region " + ours.regionId, theirs);
			Assert.assertEquals(call(theirs, "getName"), ours.name);

			Set<Integer> ourExtras = new TreeSet<>();
			for (int x : ours.extraRegionIds)
			{
				ourExtras.add(x);
			}
			Assert.assertEquals(ours.name + " extra regions", extras.get(theirs), ourExtras);

			Object[] patches = (Object[]) call(theirs, "getPatches");
			Assert.assertEquals(ours.name + " patch count", patches.length, ours.patches.length);
			for (int i = 0; i < patches.length; i++)
			{
				Patch p = ours.patches[i];
				Assert.assertEquals(call(patches[i], "getName"), p.name);
				Assert.assertEquals(call(patches[i], "getVarbit"), p.varbit);
				Assert.assertEquals(((Enum<?>) call(patches[i], "getImplementation")).name(), p.type.name());
			}
		}
	}

	/** Every tile in and around every region, on every plane: our bounds must agree with RuneLite's override (or lack of one). */
	@Test
	public void boundsMatchRuneLiteEverywhere() throws Exception
	{
		for (Object region : primaryRegions())
		{
			FarmingRegion rl = (FarmingRegion) region;
			int id = rl.getRegionID();
			int baseX = (id >> 8) * 64;
			int baseY = (id & 0xFF) * 64;
			for (int x = baseX - 64; x < baseX + 128; x++)
			{
				for (int y = baseY - 64; y < baseY + 128; y++)
				{
					for (int plane = 0; plane < 4; plane++)
					{
						boolean expected = rl.isInBounds(new WorldPoint(x, y, plane));
						boolean actual = FarmingBounds.isInBounds(id, x, y, plane);
						if (expected != actual)
						{
							Assert.fail(rl.getName() + " (" + id + ") at " + x + "," + y + "," + plane + ": expected " + expected);
						}
					}
				}
			}
		}
	}

	private static Set<Object> primaryRegions()
	{
		Set<Object> set = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		set.addAll(rlRegions.values());
		return set;
	}

	/** Calls a no-arg method declared anywhere in o's hierarchy, ignoring package-private visibility. */
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
