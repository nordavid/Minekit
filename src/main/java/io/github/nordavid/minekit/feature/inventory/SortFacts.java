package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.item.ItemFacts;
import io.github.nordavid.minekit.core.item.ItemKey;

/** What sorting needs to know about items beyond stack size and category: which hotbar rules they fit. */
public interface SortFacts extends ItemFacts {
	/**
	 * Whether the item fits a slot of this kind. Only asked for kinds that {@linkplain HotbarRule.Kind#picksMatch pick a
	 * match}, except {@link HotbarRule.Kind#ITEM}, which the planner checks by item id.
	 */
	boolean fits(HotbarRule.Kind kind, ItemKey key);

	/**
	 * How good the item is for a slot of this kind; higher is better. For example durability for tools and weapons
	 * (so a diamond pickaxe beats a stone one) and nutrition for food. 0 when there is nothing to compare.
	 */
	int quality(HotbarRule.Kind kind, ItemKey key);
}
