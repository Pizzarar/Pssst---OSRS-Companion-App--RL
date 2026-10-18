package com.pssst.runelite;

/**
 * Decides when to send the snapshot to one target (the phone, or the player's own server). Pure logic with an
 * injected clock, so it is unit-tested; the plugin asks {@link #shouldSend} on a short timer and reports each outcome.
 *
 * Rules:
 * - Send only something newer than what the target already has (by the snapshot's observedAt).
 * - While playing, at most one successful send every {@link #MIN_SEND_INTERVAL_MS}, so it is a periodic batch and not
 *   a live stream. A flush (logout, RuneLite closing) skips the wait.
 * - If the target could not be reached, try again every {@link #RETRY_INTERVAL_MS} until it lands. A refused
 *   connection carries no data, and a phone in a short background listening window gets it within one retry.
 * - A rejected token stops sending until the settings change. 403/429 pause for the time the target asked.
 */
final class DeliveryPolicy
{
	static final long MIN_SEND_INTERVAL_MS = 120_000L;
	static final long RETRY_INTERVAL_MS = 5_000L;
	/** While item icons are waiting to go out, the next send may follow this soon after the last. */
	static final long ICON_CATCH_UP_INTERVAL_MS = 3_000L;

	enum State
	{
		/** Nothing attempted yet this session. */
		IDLE,
		/** The last attempt reached the target. */
		DELIVERED,
		/** The last attempt could not connect; retrying. */
		WAITING,
		/** The target refused the Sync Token; retrying while the user updates the phone token. */
		REJECTED,
		/** The target asked for a pause (403/429) or answered with an error. */
		PAUSED
	}

	private final java.util.function.LongSupplier clock;

	private long deliveredObservedAt;
	private long lastDeliveredAt;
	private long waitingSince;
	private long lastSuccessfulSendAt = Long.MIN_VALUE;
	private long notBefore;
	private boolean inFlight;
	private boolean flush;
	private State state = State.IDLE;

	DeliveryPolicy(java.util.function.LongSupplier clock)
	{
		this.clock = clock;
	}

	synchronized boolean shouldSend(long observedAt)
	{
		return shouldSend(observedAt, false);
	}

	/**
	 * @param hasExtras item icons are waiting to go out: they justify a send even if the game state hasn't changed,
	 *                  and a shorter wait after the last one
	 * @return true when a snapshot observed at {@code observedAt} should be sent now (and marks a send in flight)
	 */
	synchronized boolean shouldSend(long observedAt, boolean hasExtras)
	{
		long now = clock.getAsLong();
		boolean newer = observedAt > deliveredObservedAt;
		if (inFlight || observedAt <= 0 || !(newer || hasExtras) || now < notBefore)
		{
			return false;
		}
		long wait = hasExtras ? ICON_CATCH_UP_INTERVAL_MS : MIN_SEND_INTERVAL_MS;
		boolean paced = state == State.WAITING || state == State.REJECTED || flush
			|| lastSuccessfulSendAt == Long.MIN_VALUE || now - lastSuccessfulSendAt >= wait;
		if (!paced)
		{
			return false;
		}
		inFlight = true;
		return true;
	}

	synchronized void onOutcome(NetworkDispatcher.Outcome outcome, long sentObservedAt)
	{
		long now = clock.getAsLong();
		inFlight = false;
		switch (outcome.kind)
		{
			case DELIVERED:
				waitingSince = 0;
				deliveredObservedAt = Math.max(deliveredObservedAt, sentObservedAt);
				lastDeliveredAt = now;
				lastSuccessfulSendAt = now;
				flush = false;
				state = State.DELIVERED;
				break;
			case UNREACHABLE:
				if (waitingSince == 0)
				{
					waitingSince = now;
				}
				notBefore = now + RETRY_INTERVAL_MS;
				state = State.WAITING;
				break;
			case REJECTED:
				notBefore = now + RETRY_INTERVAL_MS;
				state = State.REJECTED;
				break;
			case BACKOFF:
				notBefore = now + outcome.pauseMs;
				state = State.PAUSED;
				break;
			default:
				notBefore = now + RETRY_INTERVAL_MS * 2;
				state = State.PAUSED;
				break;
		}
	}

	/** Logout or RuneLite closing: send the latest without waiting for the pacing interval. */
	synchronized void requestFlush()
	{
		flush = true;
	}

	/** The settings changed (address, token) or Send Now clicked: start fresh and send the latest snapshot. */
	synchronized void reset()
	{
		state = State.IDLE;
		deliveredObservedAt = 0;
		waitingSince = 0;
		notBefore = 0;
		inFlight = false;
		flush = true;
		lastSuccessfulSendAt = Long.MIN_VALUE;
	}

	/** Restores what was delivered before RuneLite restarted (from the saved snapshot). */
	synchronized void restoreDelivered(long observedAt)
	{
		deliveredObservedAt = Math.max(deliveredObservedAt, observedAt);
	}

	synchronized long deliveredObservedAt()
	{
		return deliveredObservedAt;
	}

	synchronized long lastDeliveredAt()
	{
		return lastDeliveredAt;
	}

	/** When the current run of failed attempts began (epoch ms), or 0 while the phone is being reached. */
	synchronized long waitingSince()
	{
		return waitingSince;
	}

	synchronized State state()
	{
		return state;
	}
}
