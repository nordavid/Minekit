package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Dashboard page of the Smart Inventory module: profile, hotbar layout, category order and favorites. */
final class InventoryDashboardPage implements DashboardPage {
	static final String ID = "inventory";
	private static final int SMALL_BUTTON = 20;

	private final SmartInventoryModule module;

	InventoryDashboardPage(SmartInventoryModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.inventory");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		JsonFileStore<InventorySettings> store = module.settings();
		InventorySettings settings = store.current();
		SortProfile profile = settings.profile();

		content.text(Component.translatable("minekit.inventory.how_to", module.sortKey().getTranslatedKeyMessage()), MinekitTheme.TEXT_MUTED);
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.inventory.open_preview"),
				button -> module.openPreview()));
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.sort.profile_button", Component.translatable(profile.translationKey())),
				button -> update(builder, InventorySettings::withNextProfile)));

		content.heading(Component.translatable("minekit.inventory.hotbar"));
		HotbarLayout hotbar = settings.hotbar();
		if (hotbar.keepsAll()) {
			content.text(Component.translatable("minekit.inventory.hotbar_kept"), MinekitTheme.TEXT_MUTED);
		} else {
			// Only the slots that have a rule; the ones left as they are share one line.
			content.text(Component.translatable("minekit.inventory.hotbar_layout"), MinekitTheme.TEXT_MUTED);
			boolean anyKept = false;
			for (int slot = 0; slot < HotbarLayout.SIZE; slot++) {
				HotbarRule rule = hotbar.rule(slot);
				if (rule.kind() == HotbarRule.Kind.KEEP) {
					anyKept = true;
					continue;
				}
				ItemStack icon = HotbarRuleIcons.icon(rule);
				Component name = icon == null ? Component.literal(HotbarRuleIcons.symbol(rule) + "  ").append(HotbarRuleIcons.name(rule))
						: HotbarRuleIcons.name(rule);
				content.line(icon == null ? ItemStack.EMPTY : icon, Component.translatable("minekit.inventory.hotbar_slot", slot + 1), name,
						MinekitTheme.TEXT);
			}
			if (anyKept) {
				content.line(ItemStack.EMPTY, Component.translatable("minekit.inventory.hotbar_others"),
						HotbarRuleIcons.name(HotbarRule.KEEP), MinekitTheme.TEXT_MUTED);
			}
		}
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.inventory.edit_hotbar"),
				button -> module.openPreview()));
		FlatButton suggested = builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.inventory.hotbar_suggested"), button -> update(builder, current -> current.withHotbar(HotbarLayout.SUGGESTED))));
		suggested.active = !hotbar.equals(HotbarLayout.SUGGESTED);
		FlatButton reset = builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.inventory.hotbar_reset"), button -> update(builder, current -> current.withHotbar(HotbarLayout.KEEP_ALL))));
		reset.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("minekit.inventory.hotbar_reset_tooltip")));
		reset.active = !hotbar.keepsAll();

		content.heading(Component.translatable("minekit.inventory.order", Component.translatable(profile.translationKey())));
		List<ItemCategory> order = settings.categoryOrder(profile);
		for (int i = 0; i < order.size(); i++) {
			ItemCategory category = order.get(i);
			FlatButton up = new FlatButton(0, 0, SMALL_BUTTON, MinekitTheme.ROW_HEIGHT, Component.literal("↑"),
					button -> update(builder, current -> current.withCategoryMoved(profile, category, -1)));
			FlatButton down = new FlatButton(0, 0, SMALL_BUTTON, MinekitTheme.ROW_HEIGHT, Component.literal("↓"),
					button -> update(builder, current -> current.withCategoryMoved(profile, category, +1)));
			up.active = i > 0;
			down.active = i < order.size() - 1;
			builder.labeled(Component.literal((i + 1) + ". ").append(Component.translatable(category.translationKey())), up, down);
		}
		if (settings.isCustomized(profile)) {
			builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.inventory.reset_order"),
					button -> update(builder, current -> current.withOrderReset(profile))));
		}

		content.heading(Component.translatable("minekit.inventory.favorites"));
		if (settings.favorites().isEmpty()) {
			content.text(Component.translatable("minekit.inventory.no_favorites"), MinekitTheme.TEXT_MUTED);
		}
		for (String itemId : settings.favorites()) {
			builder.labeled(itemName(itemId), new FlatButton(0, 0, 70, MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.inventory.remove"),
					button -> update(builder, current -> current.withFavoriteToggled(itemId))));
		}
	}

	private void update(Builder builder, java.util.function.UnaryOperator<InventorySettings> change) {
		module.settings().update(change);
		if (module.settings().lastSaveFailed()) {
			module.notifications().error(Component.translatable("minekit.notification.save_failed"));
		}
		builder.rebuildLater();
	}

	private static Component itemName(String itemId) {
		Identifier id = Identifier.tryParse(itemId);
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
			return new ItemStack(BuiltInRegistries.ITEM.getValue(id)).getHoverName();
		}
		return Component.literal(itemId);
	}
}
