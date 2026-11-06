package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** The Minecraft side of loadouts: item facts, capturing the inventory, and finding positions in an open menu. */
final class MinecraftLoadouts {
	private MinecraftLoadouts() {
	}

	/** {@link LoadoutFacts} backed by an {@link ItemKeyIndex}. */
	static LoadoutFacts facts(ItemKeyIndex keys) {
		Objects.requireNonNull(keys, "keys");
		return new LoadoutFacts() {
			@Override
			public int maxStackSize(ItemKey key) {
				return keys.maxStackSize(key);
			}

			@Override
			public String exactVariant(ItemKey key) {
				return keys.exactVariant(key);
			}

			@Override
			public boolean fitsArmorSlot(ItemKey key, int inventoryIndex) {
				ItemStack stack = keys.displayStack(key);
				Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
				return keys.maxStackSize(key) == 1 && equippable != null && equippable.slot() == armorSlot(inventoryIndex);
			}
		};
	}

	static EquipmentSlot armorSlot(int inventoryIndex) {
		return switch (inventoryIndex) {
			case LoadoutSlots.FEET -> EquipmentSlot.FEET;
			case LoadoutSlots.LEGS -> EquipmentSlot.LEGS;
			case LoadoutSlots.CHEST -> EquipmentSlot.CHEST;
			case LoadoutSlots.HEAD -> EquipmentSlot.HEAD;
			default -> throw new IllegalArgumentException("not an armor position: " + inventoryIndex);
		};
	}

	/** The player's whole inventory (hotbar, main, armor, offhand) as loadout items. */
	static Map<Integer, LoadoutItem> capture(Inventory inventory, ItemKeyIndex keys) {
		Map<Integer, LoadoutItem> items = new TreeMap<>();
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			ItemStack stack = inventory.getItem(index);
			if (!stack.isEmpty()) {
				items.put(index, item(stack, keys));
			}
		}
		return items;
	}

	static LoadoutItem item(ItemStack stack, ItemKeyIndex keys) {
		ItemKey key = keys.keyOf(stack);
		return new LoadoutItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), keys.exactVariant(key), stack.getCount());
	}

	/** Where each inventory position is in {@code menu}; {@code containerSlots} are the open container's slots, if any. */
	static LoadoutLayout layout(AbstractContainerMenu menu, int[] containerSlots) {
		int[] positions = new int[LoadoutSlots.SIZE];
		Arrays.fill(positions, -1);
		for (Slot slot : menu.slots) {
			int index = slot.getContainerSlot();
			if (slot.container instanceof Inventory && LoadoutSlots.isValid(index) && positions[index] < 0) {
				positions[index] = slot.index;
			}
		}
		return new LoadoutLayout(positions, containerSlots);
	}
}
