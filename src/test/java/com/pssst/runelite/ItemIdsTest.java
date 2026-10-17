package com.pssst.runelite;

import com.google.gson.Gson;
import org.junit.Assert;
import org.junit.Test;

public class ItemIdsTest
{
	@Test
	public void notedItemsMapToTheirUnnotedId()
	{
		Assert.assertEquals(Integer.valueOf(4151), ItemIds.notedBaseId(799, 4151));
	}

	@Test
	public void ordinaryItemsHaveNoBaseId()
	{
		Assert.assertNull(ItemIds.notedBaseId(-1, -1));
		Assert.assertNull(ItemIds.notedBaseId(-1, 4152)); // an unnoted item that has a noted twin is still unnoted
		Assert.assertNull(ItemIds.notedBaseId(799, -1));  // noted but unresolved: say nothing rather than guess
	}

	@Test
	public void baseIdIsOmittedFromJsonUnlessSet()
	{
		Gson gson = new Gson();
		Assert.assertEquals("{\"id\":4151,\"quantity\":2}", gson.toJson(new StateDTO.ItemEntry(4151, 2)));
		Assert.assertEquals("{\"id\":4152,\"quantity\":5,\"baseId\":4151}", gson.toJson(new StateDTO.ItemEntry(4152, 5, 4151)));
	}
}
