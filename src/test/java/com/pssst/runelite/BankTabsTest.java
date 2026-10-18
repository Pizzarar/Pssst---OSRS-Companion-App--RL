package com.pssst.runelite;

import org.junit.Assert;
import org.junit.Test;

public class BankTabsTest
{
	@Test
	public void assignTabsCorrectly()
	{
		// 10 items total: 3 in tab 1, 2 in tab 2, 0 in 3-9, 5 in main (tab 0)
		int[] tabCounts = new int[]{3, 2, 0, 0, 0, 0, 0, 0, 0};
		int[] assigned = BankTabs.assign(10, tabCounts);

		int[] expected = new int[]{1, 1, 1, 2, 2, 0, 0, 0, 0, 0};
		Assert.assertArrayEquals(expected, assigned);
	}

	@Test
	public void mainOnlyWhenAllCountsZero()
	{
		int[] tabCounts = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0};
		int[] assigned = BankTabs.assign(5, tabCounts);

		Assert.assertArrayEquals(new int[]{0, 0, 0, 0, 0}, assigned);
	}

	@Test
	public void nullOrEmptyTabCountsDefaultToMain()
	{
		Assert.assertArrayEquals(new int[]{0, 0, 0}, BankTabs.assign(3, null));
		Assert.assertArrayEquals(new int[]{0, 0, 0}, BankTabs.assign(3, new int[0]));
	}

	@Test
	public void handlesZeroOrNegativeSlotCount()
	{
		Assert.assertEquals(0, BankTabs.assign(0, new int[]{1, 2}).length);
		Assert.assertEquals(0, BankTabs.assign(-1, new int[]{1, 2}).length);
	}

	@Test
	public void clampsWhenTabCountsExceedSlotCount()
	{
		// 3 slots total, but tab 1 wants 5
		int[] tabCounts = new int[]{5, 2, 0, 0, 0, 0, 0, 0, 0};
		int[] assigned = BankTabs.assign(3, tabCounts);

		Assert.assertArrayEquals(new int[]{1, 1, 1}, assigned);
	}

	@Test
	public void allTabsUsed()
	{
		int[] tabCounts = new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1}; // 9 tabs with 1 each
		int[] assigned = BankTabs.assign(10, tabCounts); // 10th slot is main tab 0

		int[] expected = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
		Assert.assertArrayEquals(expected, assigned);
	}
}
