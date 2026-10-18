package com.pssst.runelite;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.client.util.Filepath;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SnapshotStoreTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private File dir;
	private SnapshotStore store;

	@Before
	public void setUp() throws IOException
	{
		dir = folder.newFolder("plugin");
		store = new SnapshotStore(() -> Filepath.Unchecked.getRooted(dir.toPath()), new Gson());
	}

	private static SnapshotStore.Snapshot snapshot(String profile, long observedAt)
	{
		StateDTO state = StateDTO.builder()
			.observedAt(observedAt)
			.bankObservedAt(observedAt - 5)
			.bank(Collections.singletonList(new StateDTO.ItemEntry(11802, 1)))
			.inventory(Arrays.asList(new StateDTO.ItemEntry(995, 100000), new StateDTO.ItemEntry(4152, 3, 4151)))
			.build();
		return new SnapshotStore.Snapshot(profile, state);
	}

	@Test
	public void roundTripsTheSnapshotWithItsObservedTimes()
	{
		store.write(snapshot("rsprofile.abc", 1_700_000_000_000L));

		SnapshotStore.Snapshot read = store.read();
		Assert.assertNotNull(read);
		Assert.assertEquals("rsprofile.abc", read.getProfileKey());
		Assert.assertEquals(1_700_000_000_000L, read.getData().getObservedAt());
		Assert.assertEquals(1_700_000_000_000L - 5, read.getData().getBankObservedAt());
		Assert.assertEquals(11802, read.getData().getBank().get(0).getId());
		Assert.assertEquals(Integer.valueOf(4151), read.getData().getInventory().get(1).getBaseId());
	}

	@Test
	public void nothingSavedYetReadsAsNull()
	{
		Assert.assertNull(store.read());
	}

	@Test
	public void aCorruptFileReadsAsNullInsteadOfThrowing() throws IOException
	{
		File file = new File(dir, SnapshotStore.FILE_NAME);
		Files.write(file.toPath(), "{ not json".getBytes(StandardCharsets.UTF_8));
		Assert.assertNull(store.read());
		Files.write(file.toPath(), "{\"profileKey\":\"x\"}".getBytes(StandardCharsets.UTF_8));
		Assert.assertNull("no data means nothing to serve", store.read());
	}

	@Test
	public void aNewWriteReplacesTheOldOneAndLeavesNoTempFile()
	{
		store.write(snapshot("p", 1000));
		store.write(snapshot("p", 2000));

		Assert.assertEquals(2000, store.read().getData().getObservedAt());
		Assert.assertArrayEquals(new String[]{SnapshotStore.FILE_NAME}, dir.list());
	}

	@Test
	public void deleteRemovesEverything()
	{
		store.write(snapshot("p", 1000));
		store.delete();

		Assert.assertEquals(0, dir.list().length);
		Assert.assertNull(store.read());
		store.write(snapshot("p", 1000)); // the same snapshot can be written again after a delete
		Assert.assertNotNull(store.read());
	}

	@Test
	public void theFileNeverHoldsTheSyncToken() throws IOException
	{
		store.write(snapshot("p", 1000));
		String text = new String(Files.readAllBytes(new File(dir, SnapshotStore.FILE_NAME).toPath()), StandardCharsets.UTF_8);
		Assert.assertFalse(text.toLowerCase().contains("token"));
	}

	@Test
	public void anUnavailableDirectoryDoesNotThrow()
	{
		SnapshotStore broken = new SnapshotStore(() ->
		{
			throw new IOException("no plugin directory");
		}, new Gson());
		broken.write(snapshot("p", 1000));
		Assert.assertNull(broken.read());
		broken.delete();
	}
}
