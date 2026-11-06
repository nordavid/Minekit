package io.github.nordavid.minekit.core.item;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Item totals of the player's own inventory, by item id. */
public final class InventoryCounts {
	private InventoryCounts() {
	}

	/**
	 * Main inventory, hotbar, offhand and the stack on the cursor, by item id. Worn armor is not counted. Totals do not
	 * change when items are moved between slots, which is what the Resource Tracker relies on.
	 */
	public static Map<String, Integer> of(LocalPlayer player) {
		Map<String, Integer> counts = new TreeMap<>();
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			add(counts, stack);
		}
		add(counts, player.getOffhandItem());
		add(counts, player.containerMenu.getCarried());
		return counts;
	}

	private static void add(Map<String, Integer> counts, ItemStack stack) {
		if (!stack.isEmpty()) {
			counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
		}
	}
}
