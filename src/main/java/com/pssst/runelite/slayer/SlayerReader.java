/*
 * The varps, varbits, DB tables and the boss/streak special cases follow RuneLite's SlayerPlugin.updateTask()
 * (BSD 2-Clause, Copyright (c) 2017 Tyler Bochard, et al.).
 */
package com.pssst.runelite.slayer;

import com.pssst.runelite.ObservationStore;
import com.pssst.runelite.StateDTO;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/**
 * Reads the current Slayer assignment. Task and area names live only in the game cache (DB tables), so they are
 * resolved here, in the client, the same way RuneLite's own Slayer plugin does; there is no name table to maintain
 * or to get wrong on the receiving side.
 */
public final class SlayerReader
{
	/** What the reader needs from the game client. Kept narrow so it can be faked in tests. */
	public interface Game
	{
		int varp(int id);

		int varbit(int id);

		List<Integer> dbRowsByValue(int table, int column, Object value);

		Object dbField(int row, int column);

		default boolean isTaskStorageInterfaceOpen()
		{
			return false;
		}
	}

	/** Varps/varbits whose change can alter the result; the plugin re-reads on any of these. */
	public static final int[] WATCHED_VARPS = {
		VarPlayerID.SLAYER_COUNT, VarPlayerID.SLAYER_TARGET, VarPlayerID.SLAYER_AREA,
		VarPlayerID.SLAYER_COUNT_ORIGINAL, VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED,
		VarPlayerID.IF4, VarPlayerID.IF5, VarPlayerID.IF6
	};
	public static final int[] WATCHED_VARBITS = {
		VarbitID.SLAYER_TARGET_BOSSID, VarbitID.SLAYER_MODIFIER_ID, VarbitID.SLAYER_MODIFIER_VALUE,
		VarbitID.SLAYER_MODIFIER_NEGATIVE, VarbitID.SLAYER_POINTS, VarbitID.SLAYER_TASKS_COMPLETED,
		VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, VarbitID.SLAYER_MASTER,
		VarbitID.SLAYER_UNLOCK_STORAGE, VarbitID.SLAYER_TARGET_BOSSID_STORED
	};

	public static final int ENCHANTED_GEM_ITEM_ID = 4155;
	public static final String STORED_TASK_KEY = "slayer.stored";

	// From the [proc,helper_slayer_current_assignment] script, as noted in RuneLite's SlayerPlugin
	private static final int BOSSES_TASK_ID = 98;
	private static final int KRYSTILIA_SLAYER_MASTER = 7;
	private static final int MORTIMER_SLAYER_MASTER = 10;
	private static final int MODIFIER_ADDS_TO_INITIAL_AMOUNT = 2;

	private static final Map<String, Integer> TASK_ITEM_IDS = buildTaskItemIds();

	private SlayerReader()
	{
	}

	public static StateDTO.SlayerEntry read(Game game)
	{
		return read(game, null);
	}

	/**
	 * @return the assignment (amount 0 and no names when there is none), or null if the names could not be
	 * resolved yet (the DB lookup came back empty), in which case the caller should keep its previous value
	 */
	public static StateDTO.SlayerEntry read(Game game, ObservationStore observationStore)
	{
		StateDTO.SlayerEntry entry = new StateDTO.SlayerEntry();
		entry.setPoints(game.varbit(VarbitID.SLAYER_POINTS));
		entry.setStreak(streak(game));
		boolean storageUnlocked = game.varbit(VarbitID.SLAYER_UNLOCK_STORAGE) == 1;
		entry.setStorageUnlocked(storageUnlocked);

		int amount = game.varp(VarPlayerID.SLAYER_COUNT);
		entry.setAmount(Math.max(amount, 0));
		if (amount > 0)
		{
			Integer taskRow = taskRowFor(game, game.varp(VarPlayerID.SLAYER_TARGET), game.varbit(VarbitID.SLAYER_TARGET_BOSSID));
			if (taskRow == null)
			{
				return null;
			}
			String taskName = sentenceCase((String) game.dbField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE));
			entry.setTaskName(taskName);
			entry.setItemId(itemIdForTask(taskName));

			int areaId = game.varp(VarPlayerID.SLAYER_AREA);
			if (areaId > 0)
			{
				List<Integer> areaRows = game.dbRowsByValue(DBTableID.SlayerArea.ID, DBTableID.SlayerArea.COL_AREA_ID, areaId);
				if (areaRows.isEmpty())
				{
					return null;
				}
				entry.setTaskLocation((String) game.dbField(areaRows.get(0), DBTableID.SlayerArea.COL_AREA_NAME_IN_HELPER));
			}

			int initial = game.varp(VarPlayerID.SLAYER_COUNT_ORIGINAL);
			if (game.varbit(VarbitID.SLAYER_MODIFIER_ID) == MODIFIER_ADDS_TO_INITIAL_AMOUNT)
			{
				int modifier = game.varbit(VarbitID.SLAYER_MODIFIER_VALUE);
				initial += game.varbit(VarbitID.SLAYER_MODIFIER_NEGATIVE) == 1 ? -modifier : modifier;
			}
			entry.setInitialAmount(initial);
		}
		else
		{
			entry.setItemId(ENCHANTED_GEM_ITEM_ID);
		}

		populateStoredTask(game, observationStore, storageUnlocked, entry);
		return entry;
	}

	private static void populateStoredTask(Game game, ObservationStore store, boolean storageUnlocked, StateDTO.SlayerEntry entry)
	{
		if (!storageUnlocked)
		{
			return;
		}
		int storedTaskId = 0;
		int storedBossId = 0;
		int storedAmount = 0;

		if (game.isTaskStorageInterfaceOpen())
		{
			storedAmount = Math.max(game.varp(VarPlayerID.IF4), 0);
			storedTaskId = game.varp(VarPlayerID.IF5);
			storedBossId = game.varp(VarPlayerID.IF6);
			if (storedBossId <= 0)
			{
				storedBossId = game.varbit(VarbitID.SLAYER_TARGET_BOSSID_STORED);
			}
			if (store != null && store.profileKey() != null)
			{
				store.set(STORED_TASK_KEY, storedTaskId + ":" + storedBossId + ":" + storedAmount);
			}
		}
		else if (store != null && store.profileKey() != null)
		{
			String saved = store.get(STORED_TASK_KEY);
			if (saved != null && !saved.isEmpty())
			{
				String[] parts = saved.split(":");
				if (parts.length == 3)
				{
					try
					{
						storedTaskId = Integer.parseInt(parts[0]);
						storedBossId = Integer.parseInt(parts[1]);
						storedAmount = Integer.parseInt(parts[2]);
					}
					catch (NumberFormatException ignored)
					{
					}
				}
			}
		}

		if (storedAmount > 0 && storedTaskId > 0)
		{
			Integer row = taskRowFor(game, storedTaskId, storedBossId);
			if (row != null)
			{
				String name = sentenceCase((String) game.dbField(row, DBTableID.SlayerTask.COL_NAME_UPPERCASE));
				if (name != null)
				{
					entry.setStoredTaskName(name);
					entry.setStoredAmount(storedAmount);
					entry.setStoredItemId(itemIdForTask(name));
				}
			}
		}
	}

	private static Integer taskRowFor(Game game, int taskId, int bossId)
	{
		if (taskId == BOSSES_TASK_ID)
		{
			List<Integer> bossRows = game.dbRowsByValue(DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, bossId);
			if (bossRows.isEmpty())
			{
				return null;
			}
			return (Integer) game.dbField(bossRows.get(0), DBTableID.SlayerTaskSublist.COL_TASK);
		}
		List<Integer> rows = game.dbRowsByValue(DBTableID.SlayerTask.ID, DBTableID.SlayerTask.COL_ID, taskId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	/** The streak counter depends on which master gave the task. */
	private static int streak(Game game)
	{
		switch (game.varbit(VarbitID.SLAYER_MASTER))
		{
			case KRYSTILIA_SLAYER_MASTER:
				return game.varbit(VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED);
			case MORTIMER_SLAYER_MASTER:
				return game.varp(VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED);
			default:
				return game.varbit(VarbitID.SLAYER_TASKS_COMPLETED);
		}
	}

	/** The cache stores names upper case ("ABYSSAL DEMONS"); RuneLite shows them as "Abyssal demons". */
	static String sentenceCase(String upper)
	{
		if (upper == null || upper.isEmpty())
		{
			return upper;
		}
		return upper.substring(0, 1).toUpperCase() + upper.substring(1).toLowerCase();
	}

	/** Returns RuneLite's item sprite id for the given task name, or Enchanted gem (4155) as fallback. */
	public static int itemIdForTask(String taskName)
	{
		if (taskName == null || taskName.isEmpty())
		{
			return ENCHANTED_GEM_ITEM_ID;
		}
		Integer id = TASK_ITEM_IDS.get(taskName.toLowerCase(Locale.ROOT));
		return id != null ? id : ENCHANTED_GEM_ITEM_ID;
	}

	private static Map<String, Integer> buildTaskItemIds()
	{
		Map<String, Integer> m = new HashMap<>();
		m.put("aberrant spectres", 4144);
		m.put("abyssal demons", 4149);
		m.put("the abyssal sire", 13262);
		m.put("the alchemical hydra", 22746);
		m.put("ankou", 20095);
		m.put("aquanites", 32885);
		m.put("araxxor", 29836);
		m.put("araxytes", 29788);
		m.put("aviansies", 13504);
		m.put("bandits", 4625);
		m.put("banshees", 4135);
		m.put("barrows brothers", 4732);
		m.put("basilisks", 4139);
		m.put("bats", 20875);
		m.put("bears", 13462);
		m.put("birds", 314);
		m.put("black demons", 20026);
		m.put("black dragons", 12524);
		m.put("black knights", 1165);
		m.put("bloodveld", 4141);
		m.put("blue dragons", 12520);
		m.put("brine rats", 11047);
		m.put("callisto", 13178);
		m.put("catablepon", 9008);
		m.put("cave bugs", 4521);
		m.put("cave crawlers", 4134);
		m.put("cave horrors", 8900);
		m.put("cave kraken", 3272);
		m.put("cave slimes", 4520);
		m.put("cerberus", 13247);
		m.put("chaos druids", 20595);
		m.put("the chaos elemental", 11995);
		m.put("the chaos fanatic", 4675);
		m.put("cockatrice", 4137);
		m.put("cows", 11919);
		m.put("crabs", 7537);
		m.put("crawling hands", 4133);
		m.put("crazy archaeologists", 11990);
		m.put("crocodiles", 10149);
		m.put("custodian stalkers", 30994);
		m.put("dagannoth", 8141);
		m.put("dagannoth kings", 12644);
		m.put("dark beasts", 6637);
		m.put("dark warriors", 1151);
		m.put("deranged archaeologist", 21629);
		m.put("dogs", 8132);
		m.put("drakes", 23041);
		m.put("duke sucellus", 28250);
		m.put("dust devils", 4145);
		m.put("dwarves", 11200);
		m.put("earth warriors", 12221);
		m.put("elves", 6105);
		m.put("ents", 8174);
		m.put("fever spiders", 6709);
		m.put("fire giants", 30622);
		m.put("fleshcrawlers", 13459);
		m.put("fossil island wyverns", 21507);
		m.put("frost dragons", 31729);
		m.put("gargoyles", 4147);
		m.put("general graardor", 12650);
		m.put("ghosts", 552);
		m.put("ghouls", 6722);
		m.put("the giant mole", 12646);
		m.put("goblins", 13447);
		m.put("greater demons", 20023);
		m.put("green dragons", 12518);
		m.put("the grotesque guardians", 21750);
		m.put("gryphons", 32884);
		m.put("harpie bug swarms", 7050);
		m.put("hellhounds", 8137);
		m.put("hill giants", 13474);
		m.put("hobgoblins", 8133);
		m.put("hydras", 23042);
		m.put("icefiends", 4671);
		m.put("ice giants", 30624);
		m.put("ice warriors", 12293);
		m.put("infernal mages", 4140);
		m.put("tztok-jad", 13225);
		m.put("jellies", 4142);
		m.put("jungle horrors", 13486);
		m.put("kalphites", 8139);
		m.put("the kalphite queen", 12647);
		m.put("killerwatts", 7160);
		m.put("the king black dragon", 12653);
		m.put("the cave kraken boss", 12655);
		m.put("kree'arra", 12649);
		m.put("k'ril tsutsaroth", 12652);
		m.put("kurask", 4146);
		m.put("lava dragons", 11992);
		m.put("lesser demons", 20020);
		m.put("lesser nagua", 28823);
		m.put("lizardmen", 13391);
		m.put("lizards", 6695);
		m.put("the maggot king", 33642);
		m.put("magic axes", 1363);
		m.put("mammoths", 10516);
		m.put("metal dragons", 8142);
		m.put("minotaurs", 13456);
		m.put("mogres", 6661);
		m.put("molanisks", 10997);
		m.put("monkeys", 13450);
		m.put("moss giants", 22374);
		m.put("mutated zygomites", 7420);
		m.put("nechryael", 4148);
		m.put("ogres", 13477);
		m.put("otherworldly beings", 6109);
		m.put("the phantom muspah", 27590);
		m.put("pirates", 8950);
		m.put("pyrefiends", 4138);
		m.put("rats", 300);
		m.put("red dragons", 8134);
		m.put("revenants", 21816);
		m.put("rockslugs", 4136);
		m.put("rogues", 5554);
		m.put("sarachnis", 23495);
		m.put("scabarites", 9028);
		m.put("scorpia", 13181);
		m.put("scorpions", 13459);
		m.put("sea snakes", 7576);
		m.put("shades", 546);
		m.put("shadow warriors", 1165);
		m.put("the shellbane gryphon", 31287);
		m.put("skeletal wyverns", 6811);
		m.put("skeletons", 8131);
		m.put("smoke devils", 5349);
		m.put("sourhogs", 24944);
		m.put("spiders", 8135);
		m.put("spiritual creatures", 11840);
		m.put("suqahs", 9079);
		m.put("terror dogs", 10591);
		m.put("the leviathan", 28252);
		m.put("the whisperer", 28246);
		m.put("the thermonuclear smoke devil", 12648);
		m.put("trolls", 8136);
		m.put("turoth", 4143);
		m.put("tzhaar", 13498);
		m.put("vampyres", 1549);
		m.put("vardorvis", 28248);
		m.put("venators", 33826);
		m.put("venenatis", 13177);
		m.put("vet'ion", 13179);
		m.put("vorkath", 21992);
		m.put("wall beasts", 4519);
		m.put("warped creatures", 28582);
		m.put("waterfiends", 571);
		m.put("werewolves", 2952);
		m.put("wolves", 958);
		m.put("wyrms", 23040);
		m.put("commander zilyana", 12651);
		m.put("zombies", 6722);
		m.put("tzkal-zuk", 22319);
		m.put("zulrah", 12921);
		return Collections.unmodifiableMap(m);
	}
}
