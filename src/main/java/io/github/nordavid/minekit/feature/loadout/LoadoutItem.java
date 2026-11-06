package io.github.nordavid.minekit.feature.loadout;

import java.util.Objects;

/**
 * What one loadout slot should hold.
 *
 * @param itemId  registry id, e.g. {@code minecraft:diamond_pickaxe}
 * @param variant the item's components without damage (see {@link LoadoutFacts#exactVariant}); compared only when
 *                the loadout asks for exact matches. Empty for an item without changed components.
 * @param count   how many; a slot counts as complete with at least this many
 */
public record LoadoutItem(String itemId, String variant, int count) {
	public LoadoutItem {
		Objects.requireNonNull(itemId, "itemId");
		Objects.requireNonNull(variant, "variant");
		if (count < 1) {
			throw new IllegalArgumentException("count must be at least 1: " + count);
		}
	}
}
