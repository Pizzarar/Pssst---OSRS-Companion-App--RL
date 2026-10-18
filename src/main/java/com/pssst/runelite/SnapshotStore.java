package com.pssst.runelite;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Keeps the last snapshot on disk, with what each target has already received, so anything not yet delivered when
 * RuneLite closed is sent the next time it runs (even before anyone logs in). One small JSON file in the plugin's own
 * directory, written through RuneLite's Filepath (the Hub forbids raw java.io file APIs). It never holds the Sync Token. Disk I/O only: callers run it off the client thread.
 */
@Slf4j
class SnapshotStore
{
	static final String FILE_NAME = "last-snapshot.json";
	private static final String TEMP_NAME = FILE_NAME + ".tmp";

	/** Where the plugin may write; getPluginDirectory() in the plugin, a temp folder in tests. */
	interface Directory
	{
		Filepath get() throws IOException;
	}

	/**
	 * profileKey ties the snapshot to the RuneLite profile that produced it, so another account's bank is never carried
	 * over. delivered maps each target ("phone", "server") to the observedAt it last confirmed, so data that was not
	 * delivered before RuneLite closed is sent the next time.
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class Snapshot
	{
		private String profileKey;
		private StateDTO data;
		private Map<String, Long> delivered;

		Snapshot(String profileKey, StateDTO data)
		{
			this(profileKey, data, new HashMap<>());
		}

		long deliveredTo(String target)
		{
			Long at = delivered == null ? null : delivered.get(target);
			return at == null ? 0 : at;
		}
	}

	private final Directory directory;
	private final Gson gson;
	private String lastWrittenJson;

	SnapshotStore(Directory directory, Gson gson)
	{
		this.directory = directory;
		this.gson = gson;
	}

	/** @return the saved snapshot, or null when there is none, it is unreadable, or it holds no data */
	synchronized Snapshot read()
	{
		try
		{
			Filepath file = directory.get().joinSegment(FILE_NAME);
			if (!file.exists())
			{
				return null;
			}
			try (Reader reader = file.openBufferedReader())
			{
				Snapshot snapshot = gson.fromJson(reader, Snapshot.class);
				return snapshot != null && snapshot.getData() != null ? snapshot : null;
			}
		}
		catch (IOException | JsonParseException e)
		{
			log.warn("Could not read the saved snapshot: {}", e.toString());
			return null;
		}
	}

	/** Writes atomically (temp file, then move), so a crash mid-write never leaves a half file. Skips an identical snapshot. */
	synchronized void write(Snapshot snapshot)
	{
		if (snapshot == null || snapshot.getData() == null)
		{
			return;
		}
		String json = gson.toJson(snapshot);
		if (json.equals(lastWrittenJson))
		{
			return;
		}
		try
		{
			Filepath dir = directory.get();
			dir.createDirectories();
			Filepath temp = dir.joinSegment(TEMP_NAME);
			Filepath target = dir.joinSegment(FILE_NAME);
			temp.write(json);
			try
			{
				temp.moveTo(target, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException e)
			{
				temp.moveTo(target, StandardCopyOption.REPLACE_EXISTING);
			}
			lastWrittenJson = json;
		}
		catch (IOException e)
		{
			log.warn("Could not save the snapshot: {}", e.toString());
		}
	}

	/** Removes the saved snapshot (Enable Sync turned off). */
	synchronized void delete()
	{
		lastWrittenJson = null;
		try
		{
			Filepath dir = directory.get();
			dir.joinSegment(FILE_NAME).deleteIfExists();
			dir.joinSegment(TEMP_NAME).deleteIfExists();
		}
		catch (IOException e)
		{
			log.warn("Could not delete the saved snapshot: {}", e.toString());
		}
	}
}
