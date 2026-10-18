package com.pssst.runelite;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class IconBookTest
{
	/** Fake client: icons "load" when the test says so. */
	private static final class FakeSource implements IconBook.Source
	{
		final Map<Integer, Runnable> waiting = new HashMap<>();
		final List<Integer> requested = new ArrayList<>();
		boolean loadImmediately;

		@Override
		public void request(int id, Runnable onLoaded)
		{
			requested.add(id);
			if (loadImmediately)
			{
				onLoaded.run();
			}
			else
			{
				waiting.put(id, onLoaded);
			}
		}

		@Override
		public StateDTO.IconEntry encode(int id)
		{
			return id == 13 ? null : new StateDTO.IconEntry("Item " + id, "png" + id);
		}

		void finish(int id)
		{
			waiting.remove(id).run();
		}
	}

	private final FakeSource source = new FakeSource();
	private final IconBook book = new IconBook(source);

	@Test
	public void anIconIsReadyOnceItHasLoaded()
	{
		Assert.assertFalse(book.hasPending());
		book.want(Arrays.asList(5952, 2446));
		Assert.assertTrue("wanted icons are pending", book.hasPending());
		book.process();
		Assert.assertEquals(Arrays.asList(5952, 2446), source.requested);
		Assert.assertFalse("still loading", book.hasReady());
		Assert.assertTrue("loading icons are pending", book.hasPending());

		source.finish(5952);
		book.process();
		Assert.assertTrue(book.hasReady());
		Assert.assertEquals("Item 5952", book.takeReady(10).get(5952).getName());
		Assert.assertFalse("2446 hasn't loaded", book.takeReady(10).containsKey(2446));
		source.finish(2446);
		book.process();
		Assert.assertFalse("all icons encoded", book.hasPending());
	}

	@Test
	public void anIconAlreadyInTheCacheIsReadyOnTheSameTick()
	{
		source.loadImmediately = true;
		book.want(Arrays.asList(995));
		book.process();
		Assert.assertTrue(book.takeReady(10).containsKey(995));
	}

	@Test
	public void eachIdIsHandledOncePerSession()
	{
		source.loadImmediately = true;
		book.want(Arrays.asList(1, 1, 2));
		book.process();
		book.markSent(book.takeReady(10).keySet());
		book.want(Arrays.asList(1, 2)); // the phone asks again before it has stored them
		book.process();
		Assert.assertEquals(Arrays.asList(1, 2), source.requested);
		Assert.assertFalse(book.hasReady());
	}

	@Test
	public void aPayloadCarriesAtMostTheCapAndTheRestWait()
	{
		source.loadImmediately = true;
		List<Integer> ids = new ArrayList<>();
		for (int i = 100; i < 230; i++)
		{
			ids.add(i);
		}
		book.want(ids);
		book.process();
		book.process(); // 50 requests per tick
		book.process();

		Map<Integer, StateDTO.IconEntry> first = book.takeReady(IconBook.MAX_PER_PAYLOAD);
		Assert.assertEquals(IconBook.MAX_PER_PAYLOAD, first.size());
		book.markSent(first.keySet());
		Assert.assertEquals(ids.size() - IconBook.MAX_PER_PAYLOAD, book.takeReady(IconBook.MAX_PER_PAYLOAD).size());
	}

	@Test
	public void anIconThatCannotBeMadeIsDroppedNotRetried()
	{
		source.loadImmediately = true;
		book.want(Arrays.asList(13));
		book.process();
		Assert.assertFalse(book.hasReady());
		book.want(Arrays.asList(13));
		book.process();
		Assert.assertEquals(Arrays.asList(13), source.requested);
	}

	@Test
	public void anIconThatNeverLoadsIsGivenUp()
	{
		book.want(Arrays.asList(7));
		for (int i = 0; i < 250; i++)
		{
			book.process();
		}
		Assert.assertFalse(book.hasReady());
		source.finish(7); // a very late load is ignored
		book.process();
		Assert.assertFalse(book.hasReady());
	}

	@Test
	public void theWantedListIsCapped()
	{
		List<Integer> many = new ArrayList<>();
		for (int i = 1; i <= IconBook.MAX_WANTED + 500; i++)
		{
			many.add(i);
		}
		book.want(many);
		source.loadImmediately = true;
		for (int i = 0; i < 40; i++)
		{
			book.process();
		}
		Assert.assertEquals(IconBook.MAX_WANTED, source.requested.size());
	}

	@Test
	public void theEncoderMakesAPngOrNothing()
	{
		BufferedImage image = new BufferedImage(36, 32, BufferedImage.TYPE_INT_ARGB);
		StateDTO.IconEntry entry = IconEncoder.encode("Abyssal whip", image);
		Assert.assertEquals("Abyssal whip", entry.getName());
		byte[] png = Base64.getDecoder().decode(entry.getPng());
		Assert.assertEquals(0x89, png[0] & 0xFF);
		Assert.assertEquals('P', png[1]);
		Assert.assertNull(IconEncoder.encode("x", null));
	}
}
