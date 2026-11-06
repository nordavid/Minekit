package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.item.ItemCategory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Smart Deposit settings, stored in {@code config/minekit/deposit.json}.
 *
 * @param profile          selected profile
 * @param customCategories categories the {@link DepositProfile#CUSTOM} profile deposits
 * @param includeHotbar    whether hotbar slots may be emptied (default: no, the hotbar is protected)
 */
public record DepositSettings(DepositProfile profile, Set<ItemCategory> customCategories, boolean includeHotbar) {
	public static final DepositSettings DEFAULT =
			new DepositSettings(DepositProfile.MATCHING, EnumSet.of(ItemCategory.BLOCKS, ItemCategory.RESOURCES), false);

	public DepositSettings {
		Objects.requireNonNull(profile, "profile");
		customCategories = Collections.unmodifiableSet(customCategories.isEmpty()
				? EnumSet.noneOf(ItemCategory.class)
				: EnumSet.copyOf(customCategories));
	}

	public DepositSettings withProfile(DepositProfile newProfile) {
		return new DepositSettings(newProfile, customCategories, includeHotbar);
	}

	public DepositSettings withCategoryToggled(ItemCategory category) {
		EnumSet<ItemCategory> next = customCategories.isEmpty() ? EnumSet.noneOf(ItemCategory.class) : EnumSet.copyOf(customCategories);
		if (!next.remove(category)) {
			next.add(category);
		}
		return new DepositSettings(profile, next, includeHotbar);
	}

	public DepositSettings withIncludeHotbar(boolean include) {
		return new DepositSettings(profile, customCategories, include);
	}
}
