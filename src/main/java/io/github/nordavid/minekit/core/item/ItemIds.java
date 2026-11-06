package io.github.nordavid.minekit.core.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Display helpers for item ids such as {@code minecraft:piston}. Unknown ids (e.g. a removed mod) still display. */
public final class ItemIds {
	private ItemIds() {
	}

	/** The item for an id, or {@link Items#AIR} if the id is unknown. */
	public static Item item(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier != null && BuiltInRegistries.ITEM.containsKey(identifier) ? BuiltInRegistries.ITEM.getValue(identifier) : Items.AIR;
	}

	/** A one-item stack for drawing; a barrier icon stands in for unknown ids. */
	public static ItemStack icon(String id) {
		Item item = item(id);
		return new ItemStack(item == Items.AIR ? Items.BARRIER : item);
	}

	/** Id of the item in the player's main hand, if a player exists and holds something. */
	public static java.util.Optional<String> held() {
		net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
		if (player == null || player.getMainHandItem().isEmpty()) {
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString());
	}

	/** Translated name, or the raw id if the item is unknown. */
	public static Component name(String id) {
		Item item = item(id);
		return item == Items.AIR ? Component.literal(id) : new ItemStack(item).getHoverName();
	}
}
