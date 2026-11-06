package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.item.ItemCategory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Player settings of the Smart Inventory module, stored in {@code config/minekit/inventory.json}.
 *
 * @param activeProfile id of the selected {@link SortProfile}
 * @param customOrders  category orders the player changed, by profile id; profiles without an entry use their default
 * @param hotbar        what each hotbar slot holds after sorting (default: every slot stays as it is)
 * @param favorites     item ids (e.g. {@code minecraft:diamond_pickaxe}) that are always sorted first, in every world
 */
public record InventorySettings(
		String activeProfile,
		SortedMap<String, List<ItemCategory>> customOrders,
		HotbarLayout hotbar,
		SortedSet<String> favorites) {

	public static final InventorySettings DEFAULT =
			new InventorySettings(SortProfile.GENERAL.id(), new TreeMap<>(), HotbarLayout.KEEP_ALL, new TreeSet<>());

	public InventorySettings {
		Objects.requireNonNull(activeProfile, "activeProfile");
		Objects.requireNonNull(hotbar, "hotbar");
		if (SortProfile.byId(activeProfile).isEmpty()) {
			activeProfile = SortProfile.GENERAL.id();
		}
		TreeMap<String, List<ItemCategory>> orders = new TreeMap<>();
		customOrders.forEach((profile, order) -> {
			if (SortProfile.byId(profile).isPresent() && SortProfile.isCompleteOrder(order)) {
				orders.put(profile, List.copyOf(order));
			}
		});
		customOrders = Collections.unmodifiableSortedMap(orders);
		favorites = Collections.unmodifiableSortedSet(new TreeSet<>(favorites));
	}

	public SortProfile profile() {
		return SortProfile.byId(activeProfile).orElse(SortProfile.GENERAL);
	}

	/** Effective category order of a profile. */
	public List<ItemCategory> categoryOrder(SortProfile profile) {
		return customOrders.getOrDefault(profile.id(), profile.defaultOrder());
	}

	public boolean isCustomized(SortProfile profile) {
		return customOrders.containsKey(profile.id());
	}

	public InventorySettings withActiveProfile(SortProfile profile) {
		return new InventorySettings(profile.id(), customOrders, hotbar, favorites);
	}

	/** The next built-in profile after the active one, wrapping around. */
	public InventorySettings withNextProfile() {
		List<SortProfile> all = SortProfile.ALL;
		return withActiveProfile(all.get((all.indexOf(profile()) + 1) % all.size()));
	}

	/** Moves {@code category} one place up ({@code delta = -1}) or down ({@code +1}) in the profile's order. */
	public InventorySettings withCategoryMoved(SortProfile profile, ItemCategory category, int delta) {
		List<ItemCategory> order = new ArrayList<>(categoryOrder(profile));
		int from = order.indexOf(category);
		int to = from + delta;
		if (from < 0 || to < 0 || to >= order.size()) {
			return this;
		}
		Collections.swap(order, from, to);
		TreeMap<String, List<ItemCategory>> orders = new TreeMap<>(customOrders);
		if (order.equals(profile.defaultOrder())) {
			orders.remove(profile.id());
		} else {
			orders.put(profile.id(), order);
		}
		return new InventorySettings(activeProfile, orders, hotbar, favorites);
	}

	public InventorySettings withOrderReset(SortProfile profile) {
		TreeMap<String, List<ItemCategory>> orders = new TreeMap<>(customOrders);
		orders.remove(profile.id());
		return new InventorySettings(activeProfile, orders, hotbar, favorites);
	}

	public InventorySettings withHotbar(HotbarLayout layout) {
		return new InventorySettings(activeProfile, customOrders, layout, favorites);
	}

	public InventorySettings withHotbarRule(int slot, HotbarRule rule) {
		return withHotbar(hotbar.with(slot, rule));
	}

	public InventorySettings withFavoriteToggled(String itemId) {
		TreeSet<String> next = new TreeSet<>(favorites);
		if (!next.remove(itemId)) {
			next.add(itemId);
		}
		return new InventorySettings(activeProfile, customOrders, hotbar, next);
	}

	/** The rules a sort uses with these settings. */
	public SortRules rules() {
		return new SortRules(categoryOrder(profile()), favorites, hotbar);
	}
}
