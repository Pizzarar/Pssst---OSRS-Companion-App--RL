package com.pssst.runelite;

import java.util.Arrays;
import java.util.Map;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import org.junit.Assert;
import org.junit.Test;

public class QuestSnapshotTest
{
	@Test
	public void mapsQuestNamesToTheStateNamesTheAppUnderstands()
	{
		Map<String, String> snapshot = QuestSnapshot.of(
			Arrays.asList(Quest.COOKS_ASSISTANT, Quest.DRAGON_SLAYER_I, Quest.DESERT_TREASURE_I),
			quest -> quest == Quest.COOKS_ASSISTANT ? QuestState.FINISHED
				: quest == Quest.DRAGON_SLAYER_I ? QuestState.IN_PROGRESS : QuestState.NOT_STARTED
		);

		Assert.assertEquals("FINISHED", snapshot.get(Quest.COOKS_ASSISTANT.getName()));
		Assert.assertEquals("IN_PROGRESS", snapshot.get(Quest.DRAGON_SLAYER_I.getName()));
		Assert.assertEquals("NOT_STARTED", snapshot.get(Quest.DESERT_TREASURE_I.getName()));
		Assert.assertEquals(3, snapshot.size());
	}

	@Test
	public void skipsQuestsWhoseStateIsUnavailable()
	{
		Map<String, String> snapshot = QuestSnapshot.of(Arrays.asList(Quest.COOKS_ASSISTANT), quest -> null);
		Assert.assertTrue(snapshot.isEmpty());
	}

	@Test
	public void everyStateIsOneOfTheNamesTheAppParses()
	{
		for (QuestState state : QuestState.values())
		{
			Assert.assertTrue(state.name(), Arrays.asList("NOT_STARTED", "IN_PROGRESS", "FINISHED").contains(state.name()));
		}
	}
}
