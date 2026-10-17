package com.pssst.runelite;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;

final class QuestSnapshot
{
	private QuestSnapshot()
	{
	}

	/**
	 * Quest name to state ("NOT_STARTED", "IN_PROGRESS" or "FINISHED"). {@code stateOf} is
	 * {@code quest -> quest.getState(client)}, which must run on the client thread.
	 */
	static Map<String, String> of(Collection<Quest> quests, Function<Quest, QuestState> stateOf)
	{
		Map<String, String> out = new LinkedHashMap<>();
		for (Quest quest : quests)
		{
			QuestState state = stateOf.apply(quest);
			if (state != null)
			{
				out.put(quest.getName(), state.name());
			}
		}
		return out;
	}
}
