package net.runelite.client.plugins.companion;

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
	private Map<Integer, Integer> varbits;
	private Map<String, Object> slayer;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ItemEntry
	{
		private int id;
		private int quantity;
	}
}
