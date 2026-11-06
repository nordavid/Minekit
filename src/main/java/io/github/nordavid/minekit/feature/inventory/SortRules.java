package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.item.ItemCategory;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everything that determines a sort result besides the inventory itself.
 *
 * @param categoryOrder every category exactly once, first sorted first
 * @param favorites     item ids sorted before everything else
 * @param hotbar        what each hotbar slot holds after sorting; {@link HotbarLayout#KEEP_ALL} leaves the hotbar alone
 */
public record SortRules(List<ItemCategory> categoryOrder, Set<String> favorites, HotbarLayout hotbar) {
	public SortRules {
		categoryOrder = List.copyOf(categoryOrder);
		favorites = Set.copyOf(favorites);
		Objects.requireNonNull(hotbar, "hotbar");
		if (!SortProfile.isCompleteOrder(categoryOrder)) {
			throw new IllegalArgumentException("incomplete category order: " + categoryOrder);
		}
	}
}
