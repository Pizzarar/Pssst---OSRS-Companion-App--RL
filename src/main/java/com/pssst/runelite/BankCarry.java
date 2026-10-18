package com.pssst.runelite;

import java.util.ArrayList;
import java.util.List;

/**
 * What to remember about the bank across a RuneLite restart. The game only shows the bank and potion storage while the
 * bank is open, so a fresh session knows neither until the player opens it. Rather than tell the phone the bank is
 * empty, the plugin carries over the previous session's copy, but only for the same RS profile: another account's
 * bank is never shown as this one's.
 */
final class BankCarry
{
	final List<StateDTO.ItemEntry> bank;
	final List<StateDTO.ItemEntry> potionStorage;
	/** Epoch ms the carried bank was last really read from the game. */
	final long observedAt;

	private BankCarry(List<StateDTO.ItemEntry> bank, List<StateDTO.ItemEntry> potionStorage, long observedAt)
	{
		this.bank = bank;
		this.potionStorage = potionStorage;
		this.observedAt = observedAt;
	}

	/** @return the bank to carry over, or null when there is no saved snapshot for {@code profileKey} */
	static BankCarry from(SnapshotStore.Snapshot saved, String profileKey)
	{
		if (saved == null || saved.getData() == null || profileKey == null || !profileKey.equals(saved.getProfileKey()))
		{
			return null;
		}
		StateDTO data = saved.getData();
		long at = data.getBankObservedAt() > 0 ? data.getBankObservedAt() : data.getObservedAt();
		return new BankCarry(
			data.getBank() == null ? new ArrayList<>() : new ArrayList<>(data.getBank()),
			data.getPotionStorage() == null ? new ArrayList<>() : new ArrayList<>(data.getPotionStorage()),
			at);
	}
}
