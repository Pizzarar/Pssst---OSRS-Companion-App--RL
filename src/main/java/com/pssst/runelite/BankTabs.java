package com.pssst.runelite;

/**
 * Tab (1..9, 0 = main) for each container slot, from the 9 bank-tab-count varbits. Pure, unit-tested.
 *
 * In RuneLite / OSRS, bank items are laid out in order:
 * Tab 1 items (count given by BANK_TAB_1),
 * Tab 2 items (count given by BANK_TAB_2),
 * ...
 * Tab 9 items (count given by BANK_TAB_9),
 * followed by the Main tab (tab 0) containing all remaining item slots.
 *
 * The counts count all slots in each tab, including empty placeholder slots.
 * Therefore, tabs must be assigned by slot index before dropping placeholders.
 */
final class BankTabs
{
	private BankTabs()
	{
	}

	/**
	 * Assigns a tab number (1..9, or 0 for Main tab) to each slot index from 0 to {@code slotCount - 1}.
	 *
	 * @param slotCount total number of slots in the bank item container
	 * @param tabCounts array containing slot counts for tabs 1 through 9.
	 *                  May be null or contain fewer than 9 elements.
	 * @return an int array of length {@code slotCount}, where each entry is the tab (0..9)
	 */
	static int[] assign(int slotCount, int[] tabCounts)
	{
		if (slotCount <= 0)
		{
			return new int[0];
		}

		int[] out = new int[slotCount];
		if (tabCounts == null || tabCounts.length == 0)
		{
			return out; // all 0 (Main tab)
		}

		int slotIndex = 0;
		int maxTabs = Math.min(tabCounts.length, 9);
		for (int tabIndex = 0; tabIndex < maxTabs; tabIndex++)
		{
			int count = Math.max(0, tabCounts[tabIndex]);
			int tabNumber = tabIndex + 1; // Tab 1 to 9
			for (int i = 0; i < count && slotIndex < slotCount; i++)
			{
				out[slotIndex++] = tabNumber;
			}
		}

		// Any remaining slots belong to Tab 0 (Main tab), which is already 0 in int[]
		return out;
	}
}
