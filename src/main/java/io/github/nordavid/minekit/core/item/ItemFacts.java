package io.github.nordavid.minekit.core.item;

/** Properties of items that pure logic needs. Provided by the Minecraft adapter, or by fakes in tests. */
public interface ItemFacts {
	int maxStackSize(ItemKey key);

	ItemCategory category(ItemKey key);
}
