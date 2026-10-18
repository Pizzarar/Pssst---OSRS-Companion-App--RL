package com.pssst.runelite;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.Assert;
import org.junit.Test;

public class DeliveryPolicyTest
{
	private final AtomicLong now = new AtomicLong(1_000_000_000L);
	private final DeliveryPolicy policy = new DeliveryPolicy(now::get);

	private static NetworkDispatcher.Outcome outcome(NetworkDispatcher.Outcome.Kind kind)
	{
		return new NetworkDispatcher.Outcome(kind, kind == NetworkDispatcher.Outcome.Kind.BACKOFF ? 60_000 : 0, 0);
	}

	private void delivered(long observedAt)
	{
		Assert.assertTrue(policy.shouldSend(observedAt));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.DELIVERED), observedAt);
	}

	@Test
	public void theFirstSnapshotGoesOutStraightAway()
	{
		Assert.assertTrue(policy.shouldSend(500));
	}

	@Test
	public void nothingIsSentTwiceOrWithoutData()
	{
		Assert.assertFalse("no snapshot yet", policy.shouldSend(0));
		delivered(500);
		now.addAndGet(10 * 60_000);
		Assert.assertFalse("the target already has this one", policy.shouldSend(500));
	}

	@Test
	public void whilePlayingChangesAreBatchedEveryTwoMinutes()
	{
		delivered(500);

		now.addAndGet(30_000);
		Assert.assertFalse("30 s after a send", policy.shouldSend(600));
		now.addAndGet(60_000);
		Assert.assertFalse("90 s after a send", policy.shouldSend(700));
		now.addAndGet(30_000);
		Assert.assertTrue("2 min after a send, the latest goes out", policy.shouldSend(800));
	}

	@Test
	public void logoutSkipsTheWait()
	{
		delivered(500);
		now.addAndGet(5_000);
		policy.requestFlush();
		Assert.assertTrue(policy.shouldSend(600));
	}

	@Test
	public void aPhoneThatIsNotListeningIsRetriedEvery30sUntilItLands()
	{
		Assert.assertTrue(policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.UNREACHABLE), 500);
		Assert.assertEquals(DeliveryPolicy.State.WAITING, policy.state());

		now.addAndGet(29_000);
		Assert.assertFalse(policy.shouldSend(500));
		now.addAndGet(1_000);
		Assert.assertTrue("30 s later it tries again, without the 2-minute wait", policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.DELIVERED), 500);
		Assert.assertEquals(DeliveryPolicy.State.DELIVERED, policy.state());
		Assert.assertEquals(500, policy.deliveredObservedAt());
	}

	@Test
	public void itRemembersWhenThePhoneWasLastReachable()
	{
		Assert.assertTrue(policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.UNREACHABLE), 500);
		long since = now.get();
		now.addAndGet(30_000);
		Assert.assertTrue(policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.UNREACHABLE), 500);
		Assert.assertEquals("the first failure starts the clock", since, policy.waitingSince());

		now.addAndGet(30_000);
		Assert.assertTrue(policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.DELIVERED), 500);
		Assert.assertEquals(0, policy.waitingSince());
	}

	@Test
	public void onlyOneSendIsInFlight()
	{
		Assert.assertTrue(policy.shouldSend(500));
		now.addAndGet(60_000);
		Assert.assertFalse(policy.shouldSend(600));
	}

	@Test
	public void aRejectedTokenStopsUntilTheSettingsChange()
	{
		Assert.assertTrue(policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.REJECTED), 500);
		now.addAndGet(60 * 60_000);
		Assert.assertFalse(policy.shouldSend(900));
		Assert.assertEquals(DeliveryPolicy.State.REJECTED, policy.state());

		policy.reset();
		Assert.assertTrue(policy.shouldSend(900));
	}

	@Test
	public void beingToldToWaitIsHonoured()
	{
		Assert.assertTrue(policy.shouldSend(500));
		policy.onOutcome(outcome(NetworkDispatcher.Outcome.Kind.BACKOFF), 500);
		now.addAndGet(59_000);
		Assert.assertFalse(policy.shouldSend(500));
		now.addAndGet(1_000);
		Assert.assertTrue(policy.shouldSend(500));
	}

	@Test
	public void whatWasDeliveredBeforeARestartIsNotSentAgain()
	{
		policy.restoreDelivered(500);
		Assert.assertFalse(policy.shouldSend(500));
		Assert.assertTrue("but newer data from the last session is", policy.shouldSend(600));
	}
}
