package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.persistence.LoadResult;
import io.github.nordavid.minekit.core.config.ConfigManager;
import io.github.nordavid.minekit.core.config.HudAnchor;
import io.github.nordavid.minekit.core.notification.NotificationHudElement;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import io.github.nordavid.minekit.core.screen.OverviewPage;
import io.github.nordavid.minekit.core.screen.SettingsPage;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsModule;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsScreen;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/**
 * Drives the real game client through the Phase 1 workflows: open the dashboard with its shortcut, navigate, change
 * settings, check that they reach the config file, and use Quick Actions. Screenshots are written to
 * {@code build/run/clientGameTest/screenshots}.
 *
 * <p>Runs with {@code gradlew runClientGameTest}. The game language is English (fresh run directory), which the
 * button lookups by translation key rely on.
 */
public final class MinekitClientGameTest implements FabricClientGameTest {
	private static final String QUICK_ACTIONS = QuickActionsModule.METADATA.id();

	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		check(runtime.dashboardKey().getDefaultKey().getValue() == InputConstants.KEY_K, "dashboard default key is K");
		check(runtime.modules().isEnabled(QUICK_ACTIONS), "Quick Actions is enabled by default");

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();

			dashboardOpensClosesAndNavigates(context, runtime);
			moduleToggleIsPersisted(context, runtime);
			disabledQuickActionsShortcutOnlyShowsHint(context, runtime);
			quickActionsSearchAndRun(context, runtime);
			hudSettingsArePersisted(context, runtime);
			leaveNonDefaultStateForRestartCheck(context, runtime);
		}
	}

	/**
	 * Ends with Quick Actions switched off (and notifications bottom left from the previous step), so a game started
	 * afterwards with this run's {@code config/minekit/config.json} can show that UI changes survive a restart.
	 */
	private static void leaveNonDefaultStateForRestartCheck(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		pressToggle(context, QuickActionsModule.METADATA.nameKey());
		context.waitTick();
		check(!runtime.modules().isEnabled(QUICK_ACTIONS), "Quick Actions left off for the restart check");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void dashboardOpensClosesAndNavigates(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		check(dashboard(context).pageId().equals(OverviewPage.ID), "dashboard opens on the overview page");
		context.takeScreenshot("minekit-01-dashboard-overview");

		context.clickScreenButton("minekit.dashboard.page.settings");
		context.waitTick();
		check(dashboard(context).pageId().equals(SettingsPage.ID), "sidebar navigates to settings");
		context.takeScreenshot("minekit-02-dashboard-settings");

		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(null);

		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		check(dashboard(context).pageId().equals(SettingsPage.ID), "dashboard reopens on the last page");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void moduleToggleIsPersisted(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);

		pressToggle(context, QuickActionsModule.METADATA.nameKey());
		context.waitTick();
		check(!runtime.modules().isEnabled(QUICK_ACTIONS), "toggle switches Quick Actions off");
		check(storedModuleState(runtime).equals(Optional.of(false)), "the off state is in the config file on disk");
		context.takeScreenshot("minekit-03-module-off-notification");

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void disabledQuickActionsShortcutOnlyShowsHint(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitTicks(5);
		check(currentScreen(context) == null, "the shortcut of a disabled module opens nothing");
		context.takeScreenshot("minekit-04-disabled-hint");

		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		pressToggle(context, QuickActionsModule.METADATA.nameKey());
		context.waitTick();
		check(runtime.modules().isEnabled(QUICK_ACTIONS), "toggle switches Quick Actions back on");
		check(storedModuleState(runtime).equals(Optional.of(true)), "the on state is in the config file on disk");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void quickActionsSearchAndRun(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitForScreen(QuickActionsScreen.class);
		List<String> all = context.computeOnClient(client -> ids(((QuickActionsScreen) client.gui.screen()).matches()));
		check(all.equals(List.of("open_dashboard", "open_settings", "open_keybinds", "sort_inventory", "open_crafting", "plan_held_item",
				"resource_session", "goal_held_item", "open_storage_finder", "find_held_item", "open_loadouts", "save_loadout")),
				"all actions listed, got " + all);

		context.getInput().typeChars("settings");
		context.waitTick();
		List<String> filtered = context.computeOnClient(client -> ids(((QuickActionsScreen) client.gui.screen()).matches()));
		check(filtered.equals(List.of("open_settings")), "search narrows to the settings action, got " + filtered);
		context.takeScreenshot("minekit-05-quick-actions-search");

		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitForScreen(DashboardScreen.class);
		check(dashboard(context).pageId().equals(SettingsPage.ID), "Enter runs the selected action");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitForScreen(QuickActionsScreen.class);
		context.getInput().typeChars("zzz");
		context.waitTick();
		check(context.computeOnClient(client -> ((QuickActionsScreen) client.gui.screen()).matches().isEmpty()), "unknown query lists nothing");
		context.takeScreenshot("minekit-06-quick-actions-empty");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTick();
		check(currentScreen(context) instanceof QuickActionsScreen, "Enter with no match does nothing");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void hudSettingsArePersisted(ClientGameTestContext context, MinekitRuntime runtime) {
		NotificationHudElement notifications = (NotificationHudElement) runtime.hud().elements().stream()
				.filter(element -> element.id().equals(NotificationHudElement.ID)).findFirst().orElseThrow();
		check(runtime.hud().settings(notifications).anchor() == HudAnchor.TOP_RIGHT, "notifications start top right");

		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		// The settings page is taller than the small test window: scroll down with the mouse wheel first, so the button
		// is visible (hidden widgets ignore input).
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			screen.mouseScrolled(screen.width * 0.65, screen.height / 2.0, 0, -20);
		});
		context.waitTick();
		// The label contains the current corner ("Position: Top right"), so select the button by prefix and press it
		// with the keyboard like a player tabbing through the screen would.
		String positionPrefix = I18n.get("minekit.settings.hud.position", "");
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			AbstractWidget button = screen.children().stream()
					.filter(child -> child instanceof AbstractWidget widget && widget.getMessage().getString().startsWith(positionPrefix))
					.map(AbstractWidget.class::cast)
					.findFirst().orElseThrow(() -> new AssertionError("HUD position button not found"));
			screen.setFocused(button);
		});
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTick();
		check(runtime.hud().settings(notifications).anchor() == HudAnchor.BOTTOM_LEFT, "position cycles to the next corner");
		pressToggle(context, "minekit.module.quick_actions.name");
		context.waitTick();
		context.takeScreenshot("minekit-07-notification-bottom-left");

		ConfigManager reloaded = reload(runtime);
		check(reloaded.current().hudElement(NotificationHudElement.ID).map(c -> c.anchor()).equals(Optional.of(HudAnchor.BOTTOM_LEFT)),
				"HUD position is in the config file on disk");
		pressToggle(context, "minekit.module.quick_actions.name");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * Presses the switch with this label. Fabric's {@code clickScreenButton} matches the visible text, and the sidebar
	 * entry of the Quick Actions page has the same text as the module switch.
	 */
	private static void pressToggle(ClientGameTestContext context, String translationKey) {
		context.runOnClient(client -> {
			String text = net.minecraft.network.chat.Component.translatable(translationKey).getString();
			io.github.nordavid.minekit.core.screen.widget.ToggleButton toggle = client.gui.screen().children().stream()
					.filter(child -> child instanceof io.github.nordavid.minekit.core.screen.widget.ToggleButton button
							&& button.getMessage().getString().equals(text))
					.map(io.github.nordavid.minekit.core.screen.widget.ToggleButton.class::cast)
					.findFirst().orElseThrow(() -> new AssertionError("no switch '" + text + "'"));
			toggle.onPress(new net.minecraft.client.input.MouseButtonEvent(0, 0,
					new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
		});
	}

	private static DashboardScreen dashboard(ClientGameTestContext context) {
		Screen screen = currentScreen(context);
		check(screen instanceof DashboardScreen, "dashboard is open, found " + screen);
		return (DashboardScreen) screen;
	}

	private static Screen currentScreen(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.gui.screen());
	}

	private static List<String> ids(List<QuickAction> actions) {
		return actions.stream().map(QuickAction::id).toList();
	}

	/** Reads the config file the way the next game start would. */
	private static ConfigManager reload(MinekitRuntime runtime) {
		ConfigManager reloaded = new ConfigManager(runtime.config().file(), Clock.systemUTC());
		LoadResult result = reloaded.load();
		check(result.status() == LoadResult.Status.LOADED && result.warnings().isEmpty(), "config file reloads cleanly: " + result);
		return reloaded;
	}

	private static Optional<Boolean> storedModuleState(MinekitRuntime runtime) {
		return reload(runtime).current().moduleEnabled(QUICK_ACTIONS);
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("Minekit gametest failed: " + description);
		}
	}
}
