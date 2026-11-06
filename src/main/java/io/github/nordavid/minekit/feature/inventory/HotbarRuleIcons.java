package io.github.nordavid.minekit.feature.inventory;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** How hotbar rules look: an item icon (or a short symbol where no item fits), a name and a description. */
final class HotbarRuleIcons {
	private HotbarRuleIcons() {
	}

	/** Icon of the rule, or {@code null} for rules shown as a {@linkplain #symbol symbol}. */
	static @Nullable ItemStack icon(HotbarRule rule) {
		return switch (rule.kind()) {
			case MELEE -> new ItemStack(Items.IRON_SWORD);
			case RANGED -> new ItemStack(Items.BOW);
			case PICKAXE -> new ItemStack(Items.IRON_PICKAXE);
			case AXE -> new ItemStack(Items.IRON_AXE);
			case SHOVEL -> new ItemStack(Items.IRON_SHOVEL);
			case HOE -> new ItemStack(Items.IRON_HOE);
			case FOOD -> new ItemStack(Items.COOKED_BEEF);
			case BLOCKS -> new ItemStack(Items.BRICKS);
			case UTILITY -> new ItemStack(Items.WATER_BUCKET);
			case ITEM -> item(rule.itemId());
			case KEEP, ANY, EMPTY -> null;
		};
	}

	/** Symbol for rules without an item icon. */
	static String symbol(HotbarRule rule) {
		return switch (rule.kind()) {
			case KEEP -> "–";
			case ANY -> "*";
			case EMPTY -> "○";
			default -> "";
		};
	}

	static Component name(HotbarRule rule) {
		if (rule.kind() == HotbarRule.Kind.ITEM) {
			ItemStack stack = item(rule.itemId());
			return stack.isEmpty() ? Component.literal(String.valueOf(rule.itemId())) : stack.getHoverName();
		}
		return Component.translatable(rule.translationKey());
	}

	static Component description(HotbarRule rule) {
		if (rule.kind() == HotbarRule.Kind.ITEM) {
			return Component.translatable("minekit.hotbar.rule.item.description", name(rule));
		}
		return Component.translatable(rule.translationKey() + ".description");
	}

	private static ItemStack item(@Nullable String itemId) {
		Identifier id = itemId == null ? null : Identifier.tryParse(itemId);
		if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
			return ItemStack.EMPTY;
		}
		return new ItemStack(BuiltInRegistries.ITEM.getValue(id));
	}
}
