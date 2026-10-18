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
	private final IntToLongFunction geLookup;
	private final IntToLongFunction haLookup;
	private final Map<Integer, Long> knownGe = new ConcurrentHashMap<>();
	private final Map<Integer, Long> knownHa = new ConcurrentHashMap<>();

	PriceBook(IntToLongFunction geLookup)
	{
		this(geLookup, id -> 0L);
	}

	PriceBook(IntToLongFunction geLookup, IntToLongFunction haLookup)
	{
		this.geLookup = geLookup;
		this.haLookup = haLookup;
	}

	/** Looks up and remembers the price of each id; an id with no price (untradeable) is forgotten. */
	void record(Collection<Integer> ids)
	{
		for (int id : ids)
		{
			long price = geLookup.applyAsLong(id);
			if (price > 0)
			{
				knownGe.put(id, price);
			}
			else
			{
				knownGe.remove(id);
			}

			long ha = haLookup.applyAsLong(id);
			if (ha > 0)
			{
				knownHa.put(id, ha);
			}
			else
			{
				knownHa.remove(id);
			}
		}
	}

	/** The remembered prices of exactly the ids still held, so prices of things dropped or sold do not linger. */
	Map<Integer, Long> forHeld(Collection<Integer> heldIds)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (int id : heldIds)
		{
			Long price = knownGe.get(id);
			if (price != null)
			{
				out.put(id, price);
			}
		}
		return out;
	}

	/** The remembered High Alchemy prices of exactly the ids still held. */
	Map<Integer, Long> haForHeld(Collection<Integer> heldIds)
	{
		Map<Integer, Long> out = new HashMap<>();
		for (int id : heldIds)
		{
			Long price = knownHa.get(id);
			if (price != null)
			{
				out.put(id, price);
			}
		}
		return out;
	}

	void clear()
	{
		knownGe.clear();
		knownHa.clear();
	}
}
