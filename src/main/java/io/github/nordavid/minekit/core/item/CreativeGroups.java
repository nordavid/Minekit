package io.github.nordavid.minekit.core.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

/**
 * The categories of the creative inventory (Building Blocks, Redstone Blocks, Tools & Utilities, ...), with their items
 * in the creative inventory's order. Players know this structure, so it is the natural way to browse item lists.
 */
public final class CreativeGroups {
	/**
	 * @param id    the creative tab's registry id
	 * @param items item ids, each once, in the tab's order
	 */
	public record Group(String id, Component name, ItemStack icon, List<String> items) {
		public Group {
			items = List.copyOf(items);
		}
	}

	private CreativeGroups() {
	}

	/** The category tabs with their items, or none outside a world. */
	public static List<Group> current() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return List.of();
		}
		// Built the same way, and with the same parameters, as the creative inventory builds them. The tab contents are
		// shared with it, so its search is refreshed too, exactly as it would do itself.
		FeatureFlagSet features = player.connection.enabledFeatures();
		boolean permissions = player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get();
		HolderLookup.Provider holders = player.level().registryAccess();
		if (CreativeModeTabs.tryRebuildTabContents(features, permissions, holders)) {
			List<ItemStack> searchItems = List.copyOf(CreativeModeTabs.searchTab().getDisplayItems());
			player.connection.searchTrees().updateCreativeTooltips(holders, searchItems);
			player.connection.searchTrees().updateCreativeTags(searchItems);
		}
		List<Group> groups = new ArrayList<>();
		for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
			if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
				continue;
			}
			Set<String> items = new LinkedHashSet<>();
			for (ItemStack stack : tab.getDisplayItems()) {
				items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
			}
			String id = String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
			groups.add(new Group(id, tab.getDisplayName(), tab.getIconItem(), new ArrayList<>(items)));
		}
		return groups;
	}
}
