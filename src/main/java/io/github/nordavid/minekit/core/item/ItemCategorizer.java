package io.github.nordavid.minekit.core.item;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

/**
 * Assigns an {@link ItemCategory} from information the client legitimately has: item tags (vanilla and Fabric
 * convention tags, which modded items usually join), data components and the item class. The first matching rule
 * wins, so for example an ore block counts as a resource and a cake as food.
 *
 * <p>Tags are synchronized by the server; call this only while in a world.
 */
public final class ItemCategorizer {
	private static final List<TagKey<Item>> ARMOR_TAGS = List.of(
			ConventionalItemTags.ARMORS, ItemTags.HEAD_ARMOR, ItemTags.CHEST_ARMOR, ItemTags.LEG_ARMOR, ItemTags.FOOT_ARMOR);
	private static final List<TagKey<Item>> WEAPON_TAGS = List.of(
			ConventionalItemTags.MELEE_WEAPON_TOOLS, ConventionalItemTags.RANGED_WEAPON_TOOLS, ConventionalItemTags.SHIELD_TOOLS,
			ConventionalItemTags.MACE_TOOLS, ConventionalItemTags.TRIDENT_TOOLS, ConventionalItemTags.BOW_TOOLS,
			ConventionalItemTags.CROSSBOW_TOOLS, ItemTags.SWORDS, ItemTags.SPEARS, ItemTags.ARROWS);
	private static final List<TagKey<Item>> TOOL_TAGS = List.of(
			ConventionalItemTags.TOOLS, ConventionalItemTags.MINING_TOOL_TOOLS, ItemTags.PICKAXES, ItemTags.AXES,
			ItemTags.SHOVELS, ItemTags.HOES);
	private static final List<TagKey<Item>> POTION_TAGS = List.of(ConventionalItemTags.POTIONS);
	private static final List<TagKey<Item>> FOOD_TAGS = List.of(ConventionalItemTags.FOODS, ConventionalItemTags.DRINKS);
	private static final List<TagKey<Item>> RESOURCE_TAGS = List.of(
			ConventionalItemTags.INGOTS, ConventionalItemTags.GEMS, ConventionalItemTags.DUSTS, ConventionalItemTags.NUGGETS,
			ConventionalItemTags.RAW_MATERIALS, ConventionalItemTags.CLUMPS, ConventionalItemTags.ORES,
			ConventionalItemTags.STORAGE_BLOCKS, ConventionalItemTags.STRINGS, ConventionalItemTags.LEATHERS,
			ConventionalItemTags.BONES, ConventionalItemTags.FEATHERS, ConventionalItemTags.GUNPOWDERS,
			ConventionalItemTags.RODS, ConventionalItemTags.SLIME_BALLS, ConventionalItemTags.NETHER_STARS,
			ConventionalItemTags.DYES, ConventionalItemTags.SEEDS, ConventionalItemTags.EGGS, ItemTags.COALS);
	private static final List<TagKey<Item>> UTILITY_TAGS = List.of(ConventionalItemTags.BUCKETS, ConventionalItemTags.ENDER_PEARLS);
	private static final Set<Item> UTILITY_ITEMS = Set.of(
			Items.COMPASS, Items.RECOVERY_COMPASS, Items.CLOCK, Items.MAP, Items.FILLED_MAP, Items.FIREWORK_ROCKET,
			Items.TOTEM_OF_UNDYING, Items.NAME_TAG, Items.LEAD, Items.SADDLE, Items.SPYGLASS, Items.ENDER_EYE,
			Items.EXPERIENCE_BOTTLE, Items.WIND_CHARGE, Items.SNOWBALL, Items.BUNDLE);

	public ItemCategory categorize(ItemStack stack) {
		if (isArmor(stack)) {
			return ItemCategory.ARMOR;
		}
		if (hasAny(stack, WEAPON_TAGS)) {
			return ItemCategory.WEAPONS;
		}
		if (hasAny(stack, TOOL_TAGS) || stack.has(DataComponents.TOOL)) {
			return ItemCategory.TOOLS;
		}
		if (hasAny(stack, POTION_TAGS) || stack.has(DataComponents.POTION_CONTENTS)) {
			return ItemCategory.UTILITY;
		}
		if (stack.has(DataComponents.FOOD) || hasAny(stack, FOOD_TAGS)) {
			return ItemCategory.FOOD;
		}
		if (hasAny(stack, RESOURCE_TAGS)) {
			return ItemCategory.RESOURCES;
		}
		if (hasAny(stack, UTILITY_TAGS) || UTILITY_ITEMS.contains(stack.getItem())) {
			return ItemCategory.UTILITY;
		}
		if (stack.getItem() instanceof BlockItem) {
			return ItemCategory.BLOCKS;
		}
		return ItemCategory.MISC;
	}

	private static boolean isArmor(ItemStack stack) {
		if (hasAny(stack, ARMOR_TAGS)) {
			return true;
		}
		Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
	}

	private static boolean hasAny(ItemStack stack, List<TagKey<Item>> tags) {
		for (TagKey<Item> tag : tags) {
			if (stack.is(tag)) {
				return true;
			}
		}
		return false;
	}
}
