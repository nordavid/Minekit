package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.item.ItemKey;

/** What loadout planning needs to know about items. Provided by the Minecraft adapter, or by fakes in tests. */
public interface LoadoutFacts {
	int maxStackSize(ItemKey key);

	/** The key's components without damage: what an exact match compares, so wear does not break a match. */
	String exactVariant(ItemKey key);

	/**
	 * Whether the item may go into an armor position (36-39). Stackable items are excluded: armor slots hold one item,
	 * which the click planner does not model.
	 */
	boolean fitsArmorSlot(ItemKey key, int inventoryIndex);

	/** Whether the key matches a wanted item: same item id, and for exact loadouts the same components. */
	default boolean matches(LoadoutItem wanted, ItemKey key, boolean exact) {
		return key.itemId().equals(wanted.itemId()) && (!exact || exactVariant(key).equals(wanted.variant()));
	}
}
