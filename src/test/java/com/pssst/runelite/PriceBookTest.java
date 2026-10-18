package com.pssst.runelite;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class PriceBookTest
{
	private final Map<Integer, Long> game = new HashMap<>();

	private PriceBook book()
	{
		return new PriceBook(id -> game.getOrDefault(id, 0L));
	}

	@Test
	public void remembersPricesOfRecordedIdsAndOnlyReturnsWhatIsStillHeld()
	{
		game.put(4151, 1_500_000L);
		game.put(995, 1L);
		PriceBook book = book();
		book.record(Arrays.asList(4151, 995));

		Map<Integer, Long> held = book.forHeld(Collections.singletonList(4151));
		Assert.assertEquals(Collections.singletonMap(4151, 1_500_000L), held);
	}

	@Test
	public void untradeableItemsHaveNoEntry()
	{
		PriceBook book = book();
		book.record(Collections.singletonList(6000)); // the game says 0 for an item with no GE price
		Assert.assertTrue(book.forHeld(Collections.singletonList(6000)).isEmpty());
	}

	@Test
	public void aRefreshReplacesTheOldPriceAndDropsOnesThatLoseTheirs()
	{
		game.put(4151, 1_000_000L);
		PriceBook book = book();
		book.record(Collections.singletonList(4151));

		game.put(4151, 1_100_000L);
		book.record(Collections.singletonList(4151));
		Assert.assertEquals(Long.valueOf(1_100_000L), book.forHeld(Collections.singletonList(4151)).get(4151));

		game.put(4151, 0L);
		book.record(Collections.singletonList(4151));
		Assert.assertTrue(book.forHeld(Collections.singletonList(4151)).isEmpty());
	}

	@Test
	public void notedItemsArePricedUnderTheirUnnotedId()
	{
		StateDTO.ItemEntry noted = new StateDTO.ItemEntry(4152, 3, 4151);
		StateDTO.ItemEntry plain = new StateDTO.ItemEntry(4151, 1);
		Assert.assertEquals(4151, ItemIds.pricingId(noted));
		Assert.assertEquals(4151, ItemIds.pricingId(plain));
	}

	@Test
	public void remembersHighAlchemyPrices()
	{
		Map<Integer, Long> haGame = new HashMap<>();
		haGame.put(4151, 72_000L);
		PriceBook book = new PriceBook(id -> game.getOrDefault(id, 0L), id -> haGame.getOrDefault(id, 0L));
		book.record(Collections.singletonList(4151));

		Assert.assertEquals(Collections.singletonMap(4151, 72_000L), book.haForHeld(Collections.singletonList(4151)));
	}
}
