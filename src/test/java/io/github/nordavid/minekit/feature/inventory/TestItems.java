package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** A small fake item registry for pure inventory tests. */
final class TestItems {
	static final ItemKey COBBLE = ItemKey.of("minecraft:cobblestone");
	static final ItemKey DIRT = ItemKey.of("minecraft:dirt");
	static final ItemKey TORCH = ItemKey.of("minecraft:torch");
	static final ItemKey PEARL = ItemKey.of("minecraft:ender_pearl");
	static final ItemKey EGG = ItemKey.of("minecraft:egg");
	static final ItemKey PICKAXE = ItemKey.of("minecraft:diamond_pickaxe");
	static final ItemKey PICKAXE_ENCHANTED = new ItemKey("minecraft:diamond_pickaxe", "{enchantments=efficiency:5}");
	static final ItemKey STONE_PICKAXE = ItemKey.of("minecraft:stone_pickaxe");
	static final ItemKey BOW = ItemKey.of("minecraft:bow");
	static final ItemKey SWORD = ItemKey.of("minecraft:iron_sword");
	static final ItemKey BREAD = ItemKey.of("minecraft:bread");
	static final ItemKey IRON = ItemKey.of("minecraft:iron_ingot");
	static final ItemKey HELMET = ItemKey.of("minecraft:iron_helmet");
	static final ItemKey STICK = ItemKey.of("minecraft:stick");

	private record Info(int max, ItemCategory category) {
	}

	private static final Map<ItemKey, Info> INFO = Map.ofEntries(
			Map.entry(COBBLE, new Info(64, ItemCategory.BLOCKS)),
			Map.entry(DIRT, new Info(64, ItemCategory.BLOCKS)),
			Map.entry(TORCH, new Info(64, ItemCategory.BLOCKS)),
			Map.entry(PEARL, new Info(16, ItemCategory.UTILITY)),
			Map.entry(EGG, new Info(16, ItemCategory.RESOURCES)),
			Map.entry(PICKAXE, new Info(1, ItemCategory.TOOLS)),
			Map.entry(PICKAXE_ENCHANTED, new Info(1, ItemCategory.TOOLS)),
			Map.entry(STONE_PICKAXE, new Info(1, ItemCategory.TOOLS)),
			Map.entry(BOW, new Info(1, ItemCategory.WEAPONS)),
			Map.entry(SWORD, new Info(1, ItemCategory.WEAPONS)),
			Map.entry(BREAD, new Info(64, ItemCategory.FOOD)),
			Map.entry(IRON, new Info(64, ItemCategory.RESOURCES)),
			Map.entry(HELMET, new Info(1, ItemCategory.ARMOR)),
			Map.entry(STICK, new Info(64, ItemCategory.MISC)));

	static final List<ItemKey> ALL = List.copyOf(new java.util.TreeSet<>(INFO.keySet()));

	/** Durability of tools and weapons, nutrition of food: what the real facts use as quality. */
	private static final Map<ItemKey, Integer> QUALITY = Map.of(
			PICKAXE, 1561, PICKAXE_ENCHANTED, 1561, STONE_PICKAXE, 131, SWORD, 250, BOW, 384, BREAD, 5);

	static final SortFacts FACTS = new SortFacts() {
		@Override
		public int maxStackSize(ItemKey key) {
			return INFO.get(key).max();
		}

		@Override
		public ItemCategory category(ItemKey key) {
			return INFO.get(key).category();
		}

		@Override
		public boolean fits(HotbarRule.Kind kind, ItemKey key) {
			return switch (kind) {
				case MELEE -> key.equals(SWORD);
				case RANGED -> key.equals(BOW);
				case PICKAXE -> key.itemId().endsWith("_pickaxe");
				case FOOD -> category(key) == ItemCategory.FOOD;
				case BLOCKS -> category(key) == ItemCategory.BLOCKS;
				case UTILITY -> category(key) == ItemCategory.UTILITY;
				default -> false;
			};
		}

		@Override
		public int quality(HotbarRule.Kind kind, ItemKey key) {
			return QUALITY.getOrDefault(key, 0);
		}
	};

	private TestItems() {
	}

	static SlotStack stack(ItemKey key, int count) {
		return new SlotStack(key, count);
	}

	/** An empty player inventory menu (46 slots). */
	static List<SlotStack> emptyMenu() {
		return new ArrayList<>(Collections.nCopies(InventorySlots.MENU_SIZE, null));
	}

	static ContainerState state(List<SlotStack> slots) {
		return new ContainerState(slots, null);
	}
}
