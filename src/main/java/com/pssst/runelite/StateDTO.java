package com.pssst.runelite;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateDTO
{
	/**
	 * Epoch ms of the last moment the game itself fed this snapshot: a game event, or the 15-minute heartbeat while
	 * logged in. This is when the data was true in game, not when it was served (the envelope's updated_at), so a
	 * snapshot saved by an earlier session keeps its original value.
	 */
	private long observedAt;

	/**
	 * Epoch ms when the bank and potion storage were last read from the open bank. The game only shows them while the
	 * bank is open, so after a restart they are carried over from the saved snapshot; 0 when never observed.
	 */
	private long bankObservedAt;

	private List<ItemEntry> inventory;
	private List<ItemEntry> bank;

	@SerializedName("potion_storage")
	private List<ItemEntry> potionStorage;

	private List<SkillEntry> skills;

	@SerializedName("ge_offers")
	private List<GeOfferEntry> geOffers;

	private Map<Integer, Integer> varbits;

	/** Raw varp values by varp id. Varps and varbits have separate id spaces, so they must not share a map. */
	private Map<Integer, Integer> varps;

	/**
	 * Grand Exchange price (gp) of each item held or on offer, keyed by the id that has the price (the unnoted id for
	 * noted items), straight from RuneLite's ItemManager so the app needs no third party for prices.
	 */
	private Map<Integer, Long> prices;

	/** Quest name to "NOT_STARTED" | "IN_PROGRESS" | "FINISHED", from the client; see QuestSnapshot. */
	private Map<String, String> quests;

	/** Current Slayer assignment; amount is 0 and the names are absent when there is none. See SlayerReader. */
	private SlayerEntry slayer;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SlayerEntry
	{
		private String taskName;
		private String taskLocation;
		private int amount;
		private int initialAmount;
		private int points;
		private int streak;
	}

	/** Last observation of each farming patch the player has stood next to; see FarmingTracker. */
	private List<FarmingEntry> farming;

	/** Last observation of each Fossil Island birdhouse space; see BirdhouseTracker. */
	private List<BirdhouseEntry> birdhouses;

	/**
	 * One birdhouse space as last seen on Fossil Island. state is SEEDED, BUILT, EMPTY or UNKNOWN. houseType and
	 * houseItemId are absent for empty/unknown. readyAt (epoch ms) is 50 minutes after the seeded value was first
	 * observed and 0 when the space is not seeded. observedAt only moves when value changes.
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BirdhouseEntry
	{
		private String space;
		private String spaceName;
		private int varpId;
		private int value;
		private long observedAt;
		private String state;
		private String houseType;
		private int houseItemId;
		private long readyAt;
	}

	/**
	 * One farming patch as last seen. regionId + varbitId identify the patch (the varbit alone is a per-region slot).
	 * The crop fields are absent when the value matched no known state. observedAt is epoch milliseconds and only
	 * moves when value changes. tickMinutes is 0 when nothing is advancing (diseased, dead, empty, no regrow).
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class FarmingEntry
	{
		private int regionId;
		private String regionName;
		private String patchName;
		private String patchType;
		private int varbitId;
		private int value;
		private long observedAt;
		private String cropName;
		private int itemId;
		private String cropState;
		private int stage;
		private int stages;
		private int tickMinutes;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ItemEntry
	{
		private int id;
		private int quantity;

		/**
		 * For a noted item (only the inventory holds them): the id of the unnoted item it stands for, which is what has
		 * a name and a Grand Exchange price. Absent for everything else, so the app can simply use {@code id}.
		 */
		private Integer baseId;

		public ItemEntry(int id, int quantity)
		{
			this(id, quantity, null);
		}
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SkillEntry
	{
		private String name;
		private int level;
		private int boostedLevel;
		private long xp;
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class GeOfferEntry
	{
		private int slot;
		private int itemId;
		private int quantitySold;
		private int totalQuantity;
		private long price;
		private long spent;
		private String state;
	}
}
