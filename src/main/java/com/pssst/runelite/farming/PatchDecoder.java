package com.pssst.runelite.farming;

import com.pssst.runelite.farming.FarmingData.Crop;
import com.pssst.runelite.farming.FarmingData.PatchType;
import com.pssst.runelite.farming.FarmingData.Run;
import java.util.EnumMap;
import java.util.Map;

/** Decodes a farming varbit value into crop, state and stage, using the generated run tables. */
final class PatchDecoder
{
	static final class Decoded
	{
		final Crop crop;
		final CropState state;
		final int stage;
		/** Stages in the current phase: growth stages, or lives while harvestable/filling. */
		final int stages;
		/** Minutes per tick for the current phase; 0 when nothing is advancing (diseased, dead, empty, no regrow). */
		final int tickMinutes;

		Decoded(Crop crop, CropState state, int stage)
		{
			this.crop = crop;
			this.state = state;
			this.stage = stage;
			boolean harvest = state == CropState.HARVESTABLE || state == CropState.FILLING;
			this.stages = harvest ? crop.harvestStages : crop.stages;
			switch (state)
			{
				case HARVESTABLE:
					this.tickMinutes = crop.regrowTickMinutes;
					break;
				case GROWING:
					this.tickMinutes = crop.tickMinutes;
					break;
				default:
					this.tickMinutes = 0;
			}
		}
	}

	private static final Map<PatchType, Run[]> RUNS = new EnumMap<>(PatchType.class);

	static
	{
		RUNS.put(PatchType.MUSHROOM, FarmingData.RUNS_MUSHROOM);
		RUNS.put(PatchType.HESPORI, FarmingData.RUNS_HESPORI);
		RUNS.put(PatchType.ALLOTMENT, FarmingData.RUNS_ALLOTMENT);
		RUNS.put(PatchType.HERB, FarmingData.RUNS_HERB);
		RUNS.put(PatchType.FLOWER, FarmingData.RUNS_FLOWER);
		RUNS.put(PatchType.BUSH, FarmingData.RUNS_BUSH);
		RUNS.put(PatchType.FRUIT_TREE, FarmingData.RUNS_FRUIT_TREE);
		RUNS.put(PatchType.HOPS, FarmingData.RUNS_HOPS);
		RUNS.put(PatchType.TREE, FarmingData.RUNS_TREE);
		RUNS.put(PatchType.HARDWOOD_TREE, FarmingData.RUNS_HARDWOOD_TREE);
		RUNS.put(PatchType.REDWOOD, FarmingData.RUNS_REDWOOD);
		RUNS.put(PatchType.SPIRIT_TREE, FarmingData.RUNS_SPIRIT_TREE);
		RUNS.put(PatchType.ANIMA, FarmingData.RUNS_ANIMA);
		RUNS.put(PatchType.BELLADONNA, FarmingData.RUNS_BELLADONNA);
		RUNS.put(PatchType.CACTUS, FarmingData.RUNS_CACTUS);
		RUNS.put(PatchType.CORAL, FarmingData.RUNS_CORAL);
		RUNS.put(PatchType.SEAWEED, FarmingData.RUNS_SEAWEED);
		RUNS.put(PatchType.CALQUAT, FarmingData.RUNS_CALQUAT);
		RUNS.put(PatchType.CELASTRUS, FarmingData.RUNS_CELASTRUS);
		RUNS.put(PatchType.GRAPES, FarmingData.RUNS_GRAPES);
		RUNS.put(PatchType.CRYSTAL_TREE, FarmingData.RUNS_CRYSTAL_TREE);
		RUNS.put(PatchType.COMPOST, FarmingData.RUNS_COMPOST);
		RUNS.put(PatchType.BIG_COMPOST, FarmingData.RUNS_BIG_COMPOST);
	}

	private PatchDecoder()
	{
	}

	/** @return the decoded state, or null if the value matches no known state for this patch type */
	static Decoded decode(PatchType type, int value)
	{
		for (Run run : RUNS.get(type))
		{
			if (value >= run.lo && value <= run.hi)
			{
				return new Decoded(run.crop, run.state, run.stageAtLo + run.step * (value - run.lo));
			}
		}
		return null;
	}
}
