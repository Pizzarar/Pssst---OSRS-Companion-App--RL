package com.pssst.runelite;

import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;

public class BankCarryTest
{
	private static SnapshotStore.Snapshot saved(String profile, long observedAt, long bankObservedAt)
	{
		return new SnapshotStore.Snapshot(profile, StateDTO.builder()
			.observedAt(observedAt)
			.bankObservedAt(bankObservedAt)
			.bank(Collections.singletonList(new StateDTO.ItemEntry(11802, 1)))
			.potionStorage(Collections.singletonList(new StateDTO.ItemEntry(12913, 1183)))
			.build());
	}

	@Test
	public void theSameProfileKeepsItsBankAndItsOwnObservedTime()
	{
		BankCarry carry = BankCarry.from(saved("rsprofile.a", 5000, 3000), "rsprofile.a");

		Assert.assertNotNull(carry);
		Assert.assertEquals(11802, carry.bank.get(0).getId());
		Assert.assertEquals(12913, carry.potionStorage.get(0).getId());
		Assert.assertEquals("the bank is as old as when it was last opened, not as the last heartbeat", 3000, carry.observedAt);
	}

	@Test
	public void anOlderSnapshotWithoutABankTimeFallsBackToItsObservedTime()
	{
		Assert.assertEquals(5000, BankCarry.from(saved("rsprofile.a", 5000, 0), "rsprofile.a").observedAt);
	}

	@Test
	public void anotherProfileNeverGetsThisBank()
	{
		Assert.assertNull(BankCarry.from(saved("rsprofile.a", 5000, 3000), "rsprofile.b"));
	}

	@Test
	public void noSnapshotOrNoProfileCarriesNothing()
	{
		Assert.assertNull(BankCarry.from(null, "rsprofile.a"));
		Assert.assertNull(BankCarry.from(saved("rsprofile.a", 5000, 3000), null));
		Assert.assertNull(BankCarry.from(saved(null, 5000, 3000), "rsprofile.a"));
		Assert.assertNull(BankCarry.from(new SnapshotStore.Snapshot("rsprofile.a", null), "rsprofile.a"));
	}

	@Test
	public void aSnapshotWithNoBankCarriesEmptyLists()
	{
		BankCarry carry = BankCarry.from(new SnapshotStore.Snapshot("p", StateDTO.builder().observedAt(10).build()), "p");
		Assert.assertTrue(carry.bank.isEmpty());
		Assert.assertTrue(carry.potionStorage.isEmpty());
	}
}
