package com.pssst.runelite;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Item icons the Pssst app asked for, drawn by the player's own RuneLite client. The phone lists the ids it has no icon
 * for in its reply; this collects them, and the delivery loop attaches the ready ones to the next payload. Each icon is
 * sent once per session. The client-thread half ({@link #process}) starts loading and encodes; the rest is thread-safe.
 */
final class IconBook
{
	static final int MAX_WANTED = 1000;
	static final int MAX_PER_PAYLOAD = 100;
	private static final int REQUESTS_PER_PROCESS = 50;
	private static final int GIVE_UP_AFTER_PROCESSES = 200; // about two minutes of game ticks

	/** The client's image source, or a fake in tests. Called on the client thread only. */
	interface Source
	{
		/** Starts loading the icon; {@code onLoaded} runs once it is ready (immediately if it already is). */
		void request(int id, Runnable onLoaded);

		/** The finished icon (name and PNG), or null if it could not be made. */
		StateDTO.IconEntry encode(int id);
	}

	private final Source source;
	private final ConcurrentLinkedQueue<Integer> wanted = new ConcurrentLinkedQueue<>();
	private final Set<Integer> known = ConcurrentHashMap.newKeySet(); // wanted, loading, ready, sent or failed
	private final ConcurrentLinkedQueue<Integer> loaded = new ConcurrentLinkedQueue<>();
	private final Map<Integer, Integer> loading = new HashMap<>(); // client thread: id -> processes waited
	private final Map<Integer, StateDTO.IconEntry> ready = new ConcurrentHashMap<>();

	IconBook(Source source)
	{
		this.source = source;
	}

	/** Any thread: the ids the phone said it lacks. Ids already handled this session are ignored. */
	void want(Collection<Integer> ids)
	{
		if (ids == null)
		{
			return;
		}
		for (Integer id : ids)
		{
			if (id != null && id > 0 && known.size() < MAX_WANTED && known.add(id))
			{
				wanted.add(id);
			}
		}
	}

	/** Client thread, once per tick: starts loads, then encodes whatever finished. Gives up on icons that never load. */
	void process()
	{
		for (int i = 0; i < REQUESTS_PER_PROCESS; i++)
		{
			Integer id = wanted.poll();
			if (id == null)
			{
				break;
			}
			loading.put(id, 0);
			final int itemId = id;
			source.request(itemId, () -> loaded.add(itemId));
		}
		Integer done;
		while ((done = loaded.poll()) != null)
		{
			if (loading.remove(done) != null)
			{
				StateDTO.IconEntry entry = source.encode(done);
				if (entry != null)
				{
					ready.put(done, entry);
				}
			}
		}
		if (!loading.isEmpty())
		{
			List<Integer> stale = new ArrayList<>();
			for (Map.Entry<Integer, Integer> e : loading.entrySet())
			{
				e.setValue(e.getValue() + 1);
				if (e.getValue() >= GIVE_UP_AFTER_PROCESSES)
				{
					stale.add(e.getKey());
				}
			}
			stale.forEach(loading::remove); // stays in `known`, so it is not asked for again this session
		}
	}

	boolean hasReady()
	{
		return !ready.isEmpty();
	}

	/** Up to {@code max} finished icons to attach to the next payload (they stay ready until {@link #markSent}). */
	Map<Integer, StateDTO.IconEntry> takeReady(int max)
	{
		Map<Integer, StateDTO.IconEntry> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, StateDTO.IconEntry> e : ready.entrySet())
		{
			if (out.size() >= max)
			{
				break;
			}
			out.put(e.getKey(), e.getValue());
		}
		return out;
	}

	/** The phone has these now. */
	void markSent(Collection<Integer> ids)
	{
		new HashSet<>(ids).forEach(ready::remove);
	}

	/** Plugin shutdown: forget everything. */
	void clear()
	{
		wanted.clear();
		loaded.clear();
		loading.clear();
		ready.clear();
		known.clear();
	}
}
