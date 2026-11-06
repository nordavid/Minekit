package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Dashboard page of the Loadout Manager: what it does, the saved loadouts, and the way to the manager. */
final class LoadoutDashboardPage implements DashboardPage {
	static final String ID = "loadouts";

	private final LoadoutModule module;

	LoadoutDashboardPage(LoadoutModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.loadouts");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		content.text(Component.translatable("minekit.loadout.how_to"), MinekitTheme.TEXT_MUTED);
		FlatButton open = builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.loadout.open_manager"), button -> module.openManager(null)));
		open.active = module.unavailableReason().isEmpty();

		Loadouts loadouts = module.store().current();
		content.heading(Component.translatable("minekit.loadout.saved_heading", loadouts.loadouts().size(), Loadouts.MAX_LOADOUTS));
		if (loadouts.loadouts().isEmpty()) {
			content.text(Component.translatable("minekit.loadout.none"), MinekitTheme.TEXT_MUTED);
		}
		for (Loadout loadout : loadouts.loadouts()) {
			FlatButton show = new FlatButton(0, 0, 60, MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.loadout.show"),
					button -> module.openManager(loadout.id()));
			show.active = module.unavailableReason().isEmpty();
			ItemStack icon = loadout.items().isEmpty() ? ItemStack.EMPTY : ItemIds.icon(loadout.items().values().iterator().next().itemId());
			builder.line(icon, Component.literal(loadout.name()),
					Component.translatable("minekit.loadout.positions", loadout.items().size()), MinekitTheme.TEXT_MUTED, show);
		}
	}
}
