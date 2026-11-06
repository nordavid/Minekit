package io.github.nordavid.minekit.core.screen;

import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.config.HudAnchor;
import io.github.nordavid.minekit.core.hud.MinekitHudElement;
import io.github.nordavid.minekit.core.module.ModuleManager.ModuleStatus;
import io.github.nordavid.minekit.core.module.ModuleState;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;

/** Dashboard page with module switches, HUD settings and the shortcut editor. */
public final class SettingsPage implements DashboardPage {
	public static final String ID = "settings";

	private final MinekitRuntime runtime;

	public SettingsPage(MinekitRuntime runtime) {
		this.runtime = Objects.requireNonNull(runtime, "runtime");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component title() {
		return Component.translatable("minekit.dashboard.page.settings");
	}

	@Override
	public void build(Builder builder) {
		ContentPane content = builder.content();
		content.heading(Component.translatable("minekit.settings.modules"));
		for (ModuleStatus module : runtime.modules().modules()) {
			Component name = Component.translatable(module.metadata().nameKey());
			ToggleButton toggle = builder.widget(new ToggleButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, name,
					module.isEnabled(), requested -> applyModuleState(builder, module.metadata().id(), name, requested)));
			if (module.state() == ModuleState.FAILED) {
				toggle.active = false;
				toggle.setTooltip(Tooltip.create(Component.translatable("minekit.module.failed", module.failure().orElse("?"))));
			}
			content.text(Component.translatable(module.metadata().descriptionKey()), MinekitTheme.TEXT_MUTED);
		}

		content.heading(Component.translatable("minekit.settings.hud"));
		for (MinekitHudElement element : runtime.hud().elements()) {
			builder.widget(new ToggleButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, element.displayName(),
					runtime.hud().settings(element).visible(), requested -> applyHudVisible(element, requested)));
			builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT,
					positionLabel(runtime.hud().settings(element).anchor()), button -> cycleHudAnchor(element, button)));
		}

		content.heading(Component.translatable("minekit.settings.keys"));
		builder.widget(new FlatButton(0, 0, content.width(), MinekitTheme.ROW_HEIGHT, Component.translatable("minekit.settings.keys.open"),
				button -> Minecraft.getInstance().gui.setScreen(new KeyBindsScreen(builder.screen(), Minecraft.getInstance().options))));

		content.spacer(6);
		content.text(Component.translatable("minekit.settings.storage", "config/minekit/"), MinekitTheme.TEXT_MUTED);
	}

	private boolean applyModuleState(Builder builder, String moduleId, Component name, boolean requested) {
		ModuleState state = runtime.modules().setEnabled(moduleId, requested);
		if (state == ModuleState.FAILED) {
			runtime.notifications().error(Component.translatable("minekit.notification.module_failed", name));
		} else {
			runtime.notifications().success(Component.translatable(
					state == ModuleState.ENABLED ? "minekit.notification.module_enabled" : "minekit.notification.module_disabled", name));
		}
		// Module pages appear or disappear with their module, and a failed module's switch becomes inactive.
		builder.rebuildLater();
		reportSaveFailure();
		return state == ModuleState.ENABLED;
	}

	private boolean applyHudVisible(MinekitHudElement element, boolean requested) {
		runtime.hud().updateSettings(element, settings -> settings.withVisible(requested));
		reportSaveFailure();
		return runtime.hud().settings(element).visible();
	}

	private void cycleHudAnchor(MinekitHudElement element, FlatButton button) {
		runtime.hud().updateSettings(element, settings -> settings.withAnchor(settings.anchor().next()));
		button.setMessage(positionLabel(runtime.hud().settings(element).anchor()));
		reportSaveFailure();
	}

	private void reportSaveFailure() {
		if (runtime.config().lastSaveFailed()) {
			runtime.notifications().error(Component.translatable("minekit.notification.save_failed"));
		}
	}

	private static Component positionLabel(HudAnchor anchor) {
		return Component.translatable("minekit.settings.hud.position",
				Component.translatable("minekit.hud.anchor." + anchor.name().toLowerCase(Locale.ROOT)));
	}
}
