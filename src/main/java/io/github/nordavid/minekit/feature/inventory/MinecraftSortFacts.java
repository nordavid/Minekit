package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Objects;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;

/**
 * {@link SortFacts} from item tags and components the client has. Hotbar rule kinds are matched like this:
 *
 * <ul>
 *   <li>melee weapon: swords, spears, maces and tridents, but not axes (they have their own rule);
 *   <li>bow or crossbow: anything that shoots projectiles;
 *   <li>pickaxe, axe, shovel, hoe: the vanilla tool tags, which modded tools usually join;
 *   <li>food, blocks, utility: the item's {@link ItemCategory}.
 * </ul>
 *
 * Quality is the maximum durability for tools and weapons (it follows the material tier) and nutrition for food.
 */
final class MinecraftSortFacts implements SortFacts {
	private final ItemKeyIndex keys;

	MinecraftSortFacts(ItemKeyIndex keys) {
		this.keys = Objects.requireNonNull(keys, "keys");
	}

	@Override
	public int maxStackSize(ItemKey key) {
		return keys.maxStackSize(key);
	}

	@Override
	public ItemCategory category(ItemKey key) {
		return keys.category(key);
	}

	@Override
	public boolean fits(HotbarRule.Kind kind, ItemKey key) {
		ItemStack stack = keys.displayStack(key);
		return switch (kind) {
			case MELEE -> !stack.is(ItemTags.AXES) && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)
					|| stack.is(ConventionalItemTags.MACE_TOOLS) || stack.is(ConventionalItemTags.TRIDENT_TOOLS)
					|| stack.is(ConventionalItemTags.MELEE_WEAPON_TOOLS));
			case RANGED -> stack.getItem() instanceof ProjectileWeaponItem || stack.is(ConventionalItemTags.BOW_TOOLS)
					|| stack.is(ConventionalItemTags.CROSSBOW_TOOLS);
			case PICKAXE -> stack.is(ItemTags.PICKAXES);
			case AXE -> stack.is(ItemTags.AXES);
			case SHOVEL -> stack.is(ItemTags.SHOVELS);
			case HOE -> stack.is(ItemTags.HOES);
			case FOOD -> keys.category(key) == ItemCategory.FOOD && stack.has(DataComponents.FOOD);
			case BLOCKS -> keys.category(key) == ItemCategory.BLOCKS;
			case UTILITY -> keys.category(key) == ItemCategory.UTILITY;
			case KEEP, ANY, ITEM, EMPTY -> false;
		};
	}

	@Override
	public int quality(HotbarRule.Kind kind, ItemKey key) {
		ItemStack stack = keys.displayStack(key);
		return switch (kind) {
			case MELEE, RANGED, PICKAXE, AXE, SHOVEL, HOE -> stack.getMaxDamage();
			case FOOD -> {
				FoodProperties food = stack.get(DataComponents.FOOD);
				yield food == null ? 0 : food.nutrition();
			}
			default -> 0;
		};
	}
}
