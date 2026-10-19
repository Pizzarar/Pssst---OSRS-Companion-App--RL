package com.pssst.runelite.slayer;

import com.pssst.runelite.StateDTO;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SlayerReaderTest
{
	private static final int ABYSSAL_DEMONS_ROW = 500;
	private static final int ABYSSAL_DEMONS_ID = 12;
	private static final int BOSS_SUBLIST_ROW = 900;
	private static final int CERBERUS_ROW = 501;

	private final Map<Integer, Integer> varps = new HashMap<>();
	private final Map<Integer, Integer> varbits = new HashMap<>();
	private final Map<String, List<Integer>> rows = new HashMap<>();
	private final Map<String, Object> fields = new HashMap<>();

	private final SlayerReader.Game game = new SlayerReader.Game()
	{
		@Override
		public int varp(int id)
		{
			return varps.getOrDefault(id, 0);
		}

		@Override
		public int varbit(int id)
		{
			return varbits.getOrDefault(id, 0);
		}

		@Override
		public List<Integer> dbRowsByValue(int table, int column, Object value)
		{
			return rows.getOrDefault(table + "/" + column + "/" + value, Collections.emptyList());
		}

		@Override
		public Object dbField(int row, int column)
		{
			return fields.get(row + "/" + column);
		}
	};

	@Before
	public void setUp()
	{
		rows.put(DBTableID.SlayerTask.ID + "/" + DBTableID.SlayerTask.COL_ID + "/" + ABYSSAL_DEMONS_ID,
			Collections.singletonList(ABYSSAL_DEMONS_ROW));
		fields.put(ABYSSAL_DEMONS_ROW + "/" + DBTableID.SlayerTask.COL_NAME_UPPERCASE, "ABYSSAL DEMONS");
		fields.put(CERBERUS_ROW + "/" + DBTableID.SlayerTask.COL_NAME_UPPERCASE, "CERBERUS");

		rows.put(DBTableID.SlayerArea.ID + "/" + DBTableID.SlayerArea.COL_AREA_ID + "/3", Collections.singletonList(700));
		fields.put("700/" + DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER, "Catacombs of Kourend");

		rows.put(DBTableID.SlayerTaskSublist.ID + "/" + DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID + "/7",
			Collections.singletonList(BOSS_SUBLIST_ROW));
		fields.put(BOSS_SUBLIST_ROW + "/" + DBTableID.SlayerTaskSublist.COL_TASK, CERBERUS_ROW);

		varbits.put(VarbitID.SLAYER_POINTS, 1234);
		varbits.put(VarbitID.SLAYER_TASKS_COMPLETED, 45);
	}

	@Test
	public void noTaskIsAmountZeroWithNoNamesButStillCarriesPointsAndStreak()
	{
		StateDTO.SlayerEntry e = SlayerReader.read(game);
		Assert.assertEquals(0, e.getAmount());
		Assert.assertNull(e.getTaskName());
		Assert.assertNull(e.getTaskLocation());
		Assert.assertEquals(1234, e.getPoints());
		Assert.assertEquals(45, e.getStreak());
	}

	@Test
	public void resolvesNameAndLocationFromTheGameTables()
	{
		varps.put(VarPlayerID.SLAYER_COUNT, 87);
		varps.put(VarPlayerID.SLAYER_TARGET, ABYSSAL_DEMONS_ID);
		varps.put(VarPlayerID.SLAYER_AREA, 3);
		varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 120);

		StateDTO.SlayerEntry e = SlayerReader.read(game);

		Assert.assertEquals("Abyssal demons", e.getTaskName());
		Assert.assertEquals("Catacombs of Kourend", e.getTaskLocation());
		Assert.assertEquals(87, e.getAmount());
		Assert.assertEquals(120, e.getInitialAmount());
	}

	@Test
	public void bossTasksGoThroughTheSublist()
	{
		varps.put(VarPlayerID.SLAYER_COUNT, 30);
		varps.put(VarPlayerID.SLAYER_TARGET, 98);
		varbits.put(VarbitID.SLAYER_TARGET_BOSSID, 7);

		Assert.assertEquals("Cerberus", SlayerReader.read(game).getTaskName());
	}

	@Test
	public void initialAmountAppliesTheTaskModifierOnlyWhenItIsTheAdditiveKind()
	{
		varps.put(VarPlayerID.SLAYER_COUNT, 50);
		varps.put(VarPlayerID.SLAYER_TARGET, ABYSSAL_DEMONS_ID);
		varps.put(VarPlayerID.SLAYER_COUNT_ORIGINAL, 100);
		varbits.put(VarbitID.SLAYER_MODIFIER_VALUE, 30);

		varbits.put(VarbitID.SLAYER_MODIFIER_ID, 1);
		Assert.assertEquals(100, SlayerReader.read(game).getInitialAmount());

		varbits.put(VarbitID.SLAYER_MODIFIER_ID, 2);
		Assert.assertEquals(130, SlayerReader.read(game).getInitialAmount());

		varbits.put(VarbitID.SLAYER_MODIFIER_NEGATIVE, 1);
		Assert.assertEquals(70, SlayerReader.read(game).getInitialAmount());
	}

	@Test
	public void streakDependsOnTheMaster()
	{
		varbits.put(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, 8);
		varps.put(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED, 3);

		varbits.put(VarbitID.SLAYER_MASTER, 5);
		Assert.assertEquals(45, SlayerReader.read(game).getStreak());
		varbits.put(VarbitID.SLAYER_MASTER, 7); // Krystilia
		Assert.assertEquals(8, SlayerReader.read(game).getStreak());
		varbits.put(VarbitID.SLAYER_MASTER, 10); // Mortimer
		Assert.assertEquals(3, SlayerReader.read(game).getStreak());
	}

	@Test
	public void returnsNullWhenNamesCannotBeResolvedYet()
	{
		varps.put(VarPlayerID.SLAYER_COUNT, 10);
		varps.put(VarPlayerID.SLAYER_TARGET, 999); // no such row
		Assert.assertNull(SlayerReader.read(game));

		varps.put(VarPlayerID.SLAYER_TARGET, ABYSSAL_DEMONS_ID);
		varps.put(VarPlayerID.SLAYER_AREA, 99); // no such area
		Assert.assertNull(SlayerReader.read(game));
	}

	@Test
	public void readsAndPersistsStoredTaskWhenStorageInterfaceIsOpen()
	{
		final Map<String, String> saved = new HashMap<>();
		com.pssst.runelite.ObservationStore store = new com.pssst.runelite.ObservationStore()
		{
			@Override
			public String profileKey()
			{
				return "profile-1";
			}

			@Override
			public String get(String key)
			{
				return saved.get(key);
			}

			@Override
			public void set(String key, String value)
			{
				saved.put(key, value);
			}
		};

		varbits.put(VarbitID.SLAYER_UNLOCK_STORAGE, 1);
		varps.put(VarPlayerID.IF4, 145);
		varps.put(VarPlayerID.IF5, ABYSSAL_DEMONS_ID);
		varps.put(VarPlayerID.IF6, 0);

		SlayerReader.Game openInterfaceGame = new SlayerReader.Game()
		{
			@Override
			public int varp(int id)
			{
				return game.varp(id);
			}

			@Override
			public int varbit(int id)
			{
				return game.varbit(id);
			}

			@Override
			public List<Integer> dbRowsByValue(int table, int column, Object value)
			{
				return game.dbRowsByValue(table, column, value);
			}

			@Override
			public Object dbField(int row, int column)
			{
				return game.dbField(row, column);
			}

			@Override
			public boolean isTaskStorageInterfaceOpen()
			{
				return true;
			}
		};

		StateDTO.SlayerEntry openRead = SlayerReader.read(openInterfaceGame, store);
		Assert.assertTrue(openRead.isStorageUnlocked());
		Assert.assertEquals("Abyssal demons", openRead.getStoredTaskName());
		Assert.assertEquals(145, openRead.getStoredAmount());
		Assert.assertEquals(4149, openRead.getStoredItemId());

		// After the interface closes and IF4..IF6 reset to 0, the stored task is restored from ObservationStore
		varps.put(VarPlayerID.IF4, 0);
		varps.put(VarPlayerID.IF5, 0);
		StateDTO.SlayerEntry closedRead = SlayerReader.read(game, store);
		Assert.assertTrue(closedRead.isStorageUnlocked());
		Assert.assertEquals("Abyssal demons", closedRead.getStoredTaskName());
		Assert.assertEquals(145, closedRead.getStoredAmount());
		Assert.assertEquals(4149, closedRead.getStoredItemId());
	}

	@Test
	public void taskItemIdsMatchRuneLiteTaskEnum() throws Exception
	{
		Class<?> taskClass = Class.forName("net.runelite.client.plugins.slayer.Task");
		java.lang.reflect.Method getName = taskClass.getDeclaredMethod("getName");
		getName.setAccessible(true);
		java.lang.reflect.Method getItemSpriteId = taskClass.getDeclaredMethod("getItemSpriteId");
		getItemSpriteId.setAccessible(true);

		Object[] tasks = taskClass.getEnumConstants();
		Assert.assertTrue(tasks.length > 100);
		for (Object task : tasks)
		{
			String name = (String) getName.invoke(task);
			int expectedSpriteId = (Integer) getItemSpriteId.invoke(task);
			Assert.assertEquals("Mismatch for task " + name, expectedSpriteId, SlayerReader.itemIdForTask(name));
		}
		Assert.assertEquals(SlayerReader.ENCHANTED_GEM_ITEM_ID, SlayerReader.itemIdForTask(null));
		Assert.assertEquals(SlayerReader.ENCHANTED_GEM_ITEM_ID, SlayerReader.itemIdForTask("Unknown monster"));
	}
}
