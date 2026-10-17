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
	private List<ItemEntry> inventory;
	private List<ItemEntry> bank;

	@SerializedName("potion_storage")
	private List<ItemEntry> potionStorage;

	private List<SkillEntry> skills;

	@SerializedName("ge_offers")
	private List<GeOfferEntry> geOffers;

	private Map<Integer, Integer> varbits;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ItemEntry
	{
		private int id;
		private int quantity;
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
