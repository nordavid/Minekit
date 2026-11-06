package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.item.ItemCategory;
import java.util.List;
import java.util.Optional;

/**
 * Built-in sorting profiles. Each defines a default category order; players can change the order per profile.
 *
 * @param id              stable id, used in the settings file
 * @param defaultOrder    every category exactly once, most important first
 */
public record SortProfile(String id, List<ItemCategory> defaultOrder) {
	public static final SortProfile GENERAL = new SortProfile("general", List.of(
			ItemCategory.TOOLS, ItemCategory.WEAPONS, ItemCategory.ARMOR, ItemCategory.FOOD,
			ItemCategory.UTILITY, ItemCategory.RESOURCES, ItemCategory.BLOCKS, ItemCategory.MISC));
	public static final SortProfile MINING = new SortProfile("mining", List.of(
			ItemCategory.TOOLS, ItemCategory.RESOURCES, ItemCategory.BLOCKS, ItemCategory.FOOD,
			ItemCategory.UTILITY, ItemCategory.WEAPONS, ItemCategory.ARMOR, ItemCategory.MISC));
	public static final SortProfile BUILDING = new SortProfile("building", List.of(
			ItemCategory.BLOCKS, ItemCategory.TOOLS, ItemCategory.UTILITY, ItemCategory.RESOURCES,
			ItemCategory.FOOD, ItemCategory.WEAPONS, ItemCategory.ARMOR, ItemCategory.MISC));
	public static final SortProfile ADVENTURE = new SortProfile("adventure", List.of(
			ItemCategory.WEAPONS, ItemCategory.ARMOR, ItemCategory.FOOD, ItemCategory.UTILITY,
			ItemCategory.TOOLS, ItemCategory.RESOURCES, ItemCategory.BLOCKS, ItemCategory.MISC));

	public static final List<SortProfile> ALL = List.of(GENERAL, MINING, BUILDING, ADVENTURE);

	public SortProfile {
		defaultOrder = List.copyOf(defaultOrder);
		if (!isCompleteOrder(defaultOrder)) {
			throw new IllegalArgumentException("order must contain every category exactly once: " + defaultOrder);
		}
	}

	public static Optional<SortProfile> byId(String id) {
		return ALL.stream().filter(profile -> profile.id.equals(id)).findFirst();
	}

	public String translationKey() {
		return "minekit.sort.profile." + id;
	}

	/** Whether {@code order} names every category exactly once. */
	public static boolean isCompleteOrder(List<ItemCategory> order) {
		return order.size() == ItemCategory.values().length && order.stream().distinct().count() == order.size();
	}
}
