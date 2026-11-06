package io.github.nordavid.minekit.feature.quickactions;

import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.screen.DashboardPage;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Dashboard page of Quick Actions: which actions are pinned, in which order, and which are hidden. */
final class QuickActionsDashboardPage implements DashboardPage {
	static final String ID = "quick_actions";
	private static final int SMALL = 20;
	private static final int TOGGLE = 52;

	private final QuickActionsModule module;

	QuickActionsDashboardPage(QuickActionsModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.quick_actions");
	}

	@Override
	public boolean isVisible() {
		return module.isEnabled();
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		QuickActionSettings settings = module.settings().current();
		content.text(Component.translatable("minekit.quick_actions.how_to", module.key().getTranslatedKeyMessage()), MinekitTheme.TEXT_MUTED);
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.quick_actions.open"),
				button -> module.open()));

		List<QuickAction> all = module.registry().actions();
		content.heading(Component.translatable("minekit.quick_actions.pinned"));
		if (settings.pinned().isEmpty()) {
			content.text(Component.translatable("minekit.quick_actions.no_pinned"), MinekitTheme.TEXT_MUTED);
		}
		List<String> pinned = settings.pinned().stream().filter(id -> all.stream().anyMatch(action -> action.id().equals(id))).toList();
		for (int i = 0; i < pinned.size(); i++) {
			String id = pinned.get(i);
			QuickAction action = all.stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElseThrow();
			FlatButton up = small("↑", "minekit.quick_actions.move_up", builder, current -> current.withPinnedMoved(id, -1));
			FlatButton down = small("↓", "minekit.quick_actions.move_down", builder, current -> current.withPinnedMoved(id, +1));
			FlatButton unpin = small("★", "minekit.quick_actions.unpin", builder, current -> current.withPinToggled(id));
			up.active = i > 0;
			down.active = i < pinned.size() - 1;
			Component label = i < QuickActionSettings.NUMBERED
					? Component.literal((i + 1) + "  ").append(action.name()) : action.name();
			builder.labeled(label, up, down, unpin);
		}

		content.heading(Component.translatable("minekit.quick_actions.all"));
		for (QuickAction action : all) {
			if (settings.isPinned(action.id())) {
				continue;
			}
			String id = action.id();
			FlatButton pin = small("☆", "minekit.quick_actions.pin", builder, current -> current.withPinToggled(id));
			boolean hidden = settings.isHidden(id);
			FlatButton visibility = new FlatButton(0, 0, TOGGLE, MinekitTheme.ROW_HEIGHT,
					Component.translatable(hidden ? "minekit.quick_actions.show" : "minekit.quick_actions.hide"),
					button -> update(builder, current -> current.withHiddenToggled(id)));
			visibility.setTooltip(Tooltip.create(Component.translatable("minekit.quick_actions.hide_tooltip")));
			builder.line(null, action.name(), hidden ? Component.translatable("minekit.quick_actions.hidden") : null,
					MinekitTheme.TEXT_MUTED, pin, visibility);
		}
	}

	private FlatButton small(String symbol, String tooltipKey, Builder builder, UnaryOperator<QuickActionSettings> change) {
		FlatButton button = new FlatButton(0, 0, SMALL, MinekitTheme.ROW_HEIGHT, Component.literal(symbol), pressed -> update(builder, change));
		button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		return button;
	}

	private void update(Builder builder, UnaryOperator<QuickActionSettings> change) {
		module.settings().update(change);
		if (module.settings().lastSaveFailed()) {
			module.notifications().error(Component.translatable("minekit.notification.save_failed"));
		}
		builder.rebuildLater();
	}
}
