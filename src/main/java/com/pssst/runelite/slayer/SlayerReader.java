/*
 * The varps, varbits, DB tables and the boss/streak special cases follow RuneLite's SlayerPlugin.updateTask()
 * (BSD 2-Clause, Copyright (c) 2017 Tyler Bochard, et al.).
 */
package com.pssst.runelite.slayer;

import com.pssst.runelite.StateDTO;
import java.util.List;
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
	}

	/** Varps/varbits whose change can alter the result; the plugin re-reads on any of these. */
	public static final int[] WATCHED_VARPS = {
		VarPlayerID.SLAYER_COUNT, VarPlayerID.SLAYER_TARGET, VarPlayerID.SLAYER_AREA,
		VarPlayerID.SLAYER_COUNT_ORIGINAL, VarPlayerID.SLAYER_MORTIMER_TASKS_COMPLETED
	};
	public static final int[] WATCHED_VARBITS = {
		VarbitID.SLAYER_TARGET_BOSSID, VarbitID.SLAYER_MODIFIER_ID, VarbitID.SLAYER_MODIFIER_VALUE,
		VarbitID.SLAYER_MODIFIER_NEGATIVE, VarbitID.SLAYER_POINTS, VarbitID.SLAYER_TASKS_COMPLETED,
		VarbitID.SLAYER_WILDERNESS_TASKS_COMPLETED, VarbitID.SLAYER_MASTER
	};

	// From the [proc,helper_slayer_current_assignment] script, as noted in RuneLite's SlayerPlugin
	private static final int BOSSES_TASK_ID = 98;
	private static final int KRYSTILIA_SLAYER_MASTER = 7;
	private static final int MORTIMER_SLAYER_MASTER = 10;
	private static final int MODIFIER_ADDS_TO_INITIAL_AMOUNT = 2;

	private SlayerReader()
	{
	}

	/**
	 * @return the assignment (amount 0 and no names when there is none), or null if the names could not be
	 * resolved yet (the DB lookup came back empty), in which case the caller should keep its previous value
	 */
	public static StateDTO.SlayerEntry read(Game game)
	{
		StateDTO.SlayerEntry entry = new StateDTO.SlayerEntry();
		entry.setPoints(game.varbit(VarbitID.SLAYER_POINTS));
		entry.setStreak(streak(game));

		int amount = game.varp(VarPlayerID.SLAYER_COUNT);
		entry.setAmount(Math.max(amount, 0));
		if (amount <= 0)
		{
			return entry;
		}

		Integer taskRow = taskRow(game);
		if (taskRow == null)
		{
			return null;
		}
		entry.setTaskName(sentenceCase((String) game.dbField(taskRow, DBTableID.SlayerTask.COL_NAME_UPPERCASE)));

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
		return entry;
	}

	private static Integer taskRow(Game game)
	{
		int taskId = game.varp(VarPlayerID.SLAYER_TARGET);
		if (taskId == BOSSES_TASK_ID)
		{
			List<Integer> bossRows = game.dbRowsByValue(DBTableID.SlayerTaskSublist.ID,
				DBTableID.SlayerTaskSublist.COL_TASK_SUBTABLE_ID, game.varbit(VarbitID.SLAYER_TARGET_BOSSID));
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
}
