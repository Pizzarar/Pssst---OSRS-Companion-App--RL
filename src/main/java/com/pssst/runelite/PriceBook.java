package com.pssst.runelite;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntToLongFunction;

/**
 * Grand Exchange prices for the items the player holds, taken from RuneLite's own {@code ItemManager}.
 *
 * Prices ride along with data the plugin already sends, so the app never has to ask a third party (the OSRS Wiki)
 * for them. {@link #record} must be called on the client thread (that is where ItemManager may be asked); the
 * sync thread then only reads the cache with {@link #forHeld}.
 */
final class PriceBook
{
	private final IntToLongFunction lookup;
	private final Map<Integer, Long> known = new ConcurrentHashMap<>();

	PriceBook(IntToLongFunction lookup)
	{
		this.lookup = lookup;
	}

	/** Looks up and remembers the price of each id; an id with no price (untradeable) is forgotten. */
	void record(Collection<Integer> ids)
	{
		for (int id : ids)
		{
			long price = lookup.applyAsLong(id);
			if (price > 0)
			{
				known.put(id, price);
			}
			else
			{
				known.remove(id);
			}
		}
	}

	/** The remembered prices of exactly the ids still held, so prices of things dropped or sold do not linger. */
	Map<Integer, Long> forHeld(Collection<Integer> heldIds)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (int id : heldIds)
		{
			Long price = known.get(id);
			if (price != null)
			{
				out.put(id, price);
			}
		}
		return out;
	}

	void clear()
	{
		known.clear();
	}
}
