package com.pssst.runelite;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** What leaves the PC when the player turns Include Bank off. */
final class PayloadFilter
{
	private PayloadFilter()
	{
	}

	/** The state without the bank and potion storage, and without the prices only they needed. */
	static StateDTO withoutBank(StateDTO state)
	{
		Set<Integer> kept = new HashSet<>();
		if (state.getInventory() != null)
		{
			for (StateDTO.ItemEntry entry : state.getInventory())
			{
				kept.add(ItemIds.pricingId(entry));
			}
		}
		if (state.getGeOffers() != null)
		{
			for (StateDTO.GeOfferEntry offer : state.getGeOffers())
			{
				kept.add(offer.getItemId());
			}
		}
		Map<Integer, Long> prices = new HashMap<>();
		if (state.getPrices() != null)
		{
			state.getPrices().forEach((id, price) ->
			{
				if (kept.contains(id))
				{
					prices.put(id, price);
				}
			});
		}
		return state.toBuilder().bank(null).potionStorage(null).bankObservedAt(0).prices(prices).build();
	}
}
