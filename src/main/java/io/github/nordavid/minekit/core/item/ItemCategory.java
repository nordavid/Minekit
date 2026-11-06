package io.github.nordavid.minekit.core.item;

import java.util.Locale;

/** Coarse item groups used for sorting and, later, for deposit profiles (PROJECT_PLAN.md §6.1). */
public enum ItemCategory {
	BLOCKS,
	TOOLS,
	WEAPONS,
	ARMOR,
	FOOD,
	RESOURCES,
	UTILITY,
	MISC;

	public String translationKey() {
		return "minekit.category." + name().toLowerCase(Locale.ROOT);
	}
}
