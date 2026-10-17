package com.pssst.runelite;

final class ItemIds
{
	private ItemIds()
	{
	}

	/**
	 * Same rule as RuneLite's ItemManager.canonicalize for noted items: an item is noted when its definition's
	 * {@code note} field is set (not -1), and {@code linkedNoteId} is then the unnoted item.
	 *
	 * @return the unnoted item id, or null when the item is not noted
	 */
	static Integer notedBaseId(int note, int linkedNoteId)
	{
		return note != -1 && linkedNoteId > 0 ? linkedNoteId : null;
	}
}
