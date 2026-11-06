package io.github.nordavid.minekit;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.persistence.LoadResult;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.config.ConfigManager;
import io.github.nordavid.minekit.core.container.ContainerPanels;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.goal.GoalBridge;
import io.github.nordavid.minekit.core.hud.HudManager;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.module.ModuleManager;
import io.github.nordavid.minekit.core.module.ModuleState;
import io.github.nordavid.minekit.core.notification.NotificationHudElement;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import io.github.nordavid.minekit.core.screen.OverviewPage;
import io.github.nordavid.minekit.core.screen.SettingsPage;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import io.github.nordavid.minekit.feature.deposit.DepositSettings;
import io.github.nordavid.minekit.feature.deposit.DepositSettingsCodec;
import io.github.nordavid.minekit.feature.deposit.SmartDepositModule;
import io.github.nordavid.minekit.feature.storage.StorageFinderModule;
import io.github.nordavid.minekit.feature.crafting.CraftingAssistantModule;
import io.github.nordavid.minekit.feature.crafting.SavedPlans;
import io.github.nordavid.minekit.feature.crafting.SavedPlansCodec;
import io.github.nordavid.minekit.feature.inventory.InventorySettings;
import io.github.nordavid.minekit.feature.resources.ResourceTracker;
import io.github.nordavid.minekit.feature.resources.ResourceTrackerCodec;
import io.github.nordavid.minekit.feature.resources.ResourceTrackerModule;
import io.github.nordavid.minekit.feature.inventory.InventorySettingsCodec;
import io.github.nordavid.minekit.feature.inventory.SmartInventoryModule;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.feature.loadout.LoadoutModule;
import io.github.nordavid.minekit.feature.loadout.Loadouts;
import io.github.nordavid.minekit.feature.loadout.LoadoutsCodec;
import io.github.nordavid.minekit.feature.quickactions.QuickActionSettings;
import io.github.nordavid.minekit.feature.quickactions.QuickActionSettingsCodec;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsModule;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint of MINEKIT and composition root: creates the core services, registers modules and hooks
 * everything into Fabric.
 *
 * <p>MINEKIT is client-only by design: {@code fabric.mod.json} declares {@code "environment": "client"} and this is
 * the only entrypoint. There is deliberately no main or server entrypoint and no networking.
 */
public final class MinekitClient implements ClientModInitializer {
	public static final String MOD_ID = "minekit";

	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		MinekitRuntime.Versions versions = new MinekitRuntime.Versions(versionOf(loader, MOD_ID), versionOf(loader, "minecraft"));

		Path dataDirectory = loader.getConfigDir().resolve(MOD_ID);
		ConfigManager config = new ConfigManager(dataDirectory.resolve("config.json"), Clock.systemUTC());
		LoadResult configResult = config.load();
		JsonFileStore<InventorySettings> inventorySettings =
				new JsonFileStore<>(dataDirectory.resolve("inventory.json"), Clock.systemUTC(), InventorySettingsCodec.INSTANCE);
		LoadResult inventoryResult = inventorySettings.load();
		JsonFileStore<SavedPlans> craftingPlans =
				new JsonFileStore<>(dataDirectory.resolve("crafting-plans.json"), Clock.systemUTC(), SavedPlansCodec.INSTANCE);
		LoadResult craftingResult = craftingPlans.load();
		JsonFileStore<ResourceTracker> resourceSessions =
				new JsonFileStore<>(dataDirectory.resolve("resource-sessions.json"), Clock.systemUTC(), ResourceTrackerCodec.INSTANCE);
		LoadResult resourcesResult = resourceSessions.load();
		JsonFileStore<DepositSettings> depositSettings =
				new JsonFileStore<>(dataDirectory.resolve("deposit.json"), Clock.systemUTC(), DepositSettingsCodec.INSTANCE);
		LoadResult depositResult = depositSettings.load();
		JsonFileStore<QuickActionSettings> quickActionSettings =
				new JsonFileStore<>(dataDirectory.resolve("quick-actions.json"), Clock.systemUTC(), QuickActionSettingsCodec.INSTANCE);
		LoadResult quickActionsResult = quickActionSettings.load();
		JsonFileStore<Loadouts> loadouts =
				new JsonFileStore<>(dataDirectory.resolve("loadouts.json"), Clock.systemUTC(), LoadoutsCodec.INSTANCE);
		LoadResult loadoutsResult = loadouts.load();

		NotificationService notifications = new NotificationService();
		HudManager hud = new HudManager(config);
		hud.register(new NotificationHudElement(notifications));

		KeybindManager keybinds = new KeybindManager(MOD_ID);
		ModuleManager modules = new ModuleManager(new ModuleManager.Settings() {
			@Override
			public Optional<Boolean> storedEnabled(String moduleId) {
				return config.current().moduleEnabled(moduleId);
			}

			@Override
			public void storeEnabled(String moduleId, boolean enabled) {
				config.update(current -> current.withModuleEnabled(moduleId, enabled));
			}
		});

		KeyMapping dashboardKey = keybinds.register("dashboard", InputConstants.KEY_K, () -> DashboardScreen.open(MinekitRuntime.get(), null));
		DashboardPages pages = new DashboardPages();
		QuickActionRegistry actions = new QuickActionRegistry();
		ContainerActionExecutor executor = new ContainerActionExecutor();
		GoalBridge goals = new GoalBridge();
		// Installed before any module, so container screens are identified before modules see them.
		ContainerTracker containers = new ContainerTracker();
		containers.install();
		StorageBridge storage = new StorageBridge();
		ContainerPanels panels = new ContainerPanels();
		MinekitRuntime runtime = new MinekitRuntime(versions, dataDirectory, config, modules, keybinds, hud, notifications, dashboardKey,
				pages, actions, executor, goals, containers, storage);
		MinekitRuntime.install(runtime);

		// Sidebar order: Overview, module pages, Settings.
		pages.register(new OverviewPage(runtime));
		registerCoreActions(actions, runtime);
		modules.register(new SmartInventoryModule(inventorySettings, keybinds, notifications, executor, actions, pages));
		modules.register(new CraftingAssistantModule(craftingPlans, keybinds, notifications, actions, pages, goals, storage));
		modules.register(new ResourceTrackerModule(resourceSessions, keybinds, notifications, actions, pages, hud, goals));
		modules.register(new StorageFinderModule(dataDirectory.resolve("storage-index"), containers, keybinds, notifications, actions, pages,
				storage));
		modules.register(new SmartDepositModule(depositSettings, () -> inventorySettings.current().favorites(), containers, panels, keybinds,
				notifications, executor, pages));
		modules.register(new LoadoutModule(loadouts, containers, panels, keybinds, notifications, executor, actions, pages));
		// Last, so its dashboard page sits next to Settings; it adds no actions of its own.
		modules.register(new QuickActionsModule(actions, quickActionSettings, keybinds, notifications, pages));
		modules.initializeAll();
		pages.register(new SettingsPage(runtime));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			keybinds.tick();
			executor.tick(client);
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "hud"), hud::extractHud);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
				ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, tickProgress) -> hud.extractOverScreen(graphics)));

		reportConfigProblems(configResult, notifications);
		reportConfigProblems(inventoryResult, notifications);
		reportConfigProblems(craftingResult, notifications);
		reportConfigProblems(resourcesResult, notifications);
		reportConfigProblems(depositResult, notifications);
		reportConfigProblems(quickActionsResult, notifications);
		reportConfigProblems(loadoutsResult, notifications);
		modules.modules().stream()
				.filter(module -> module.state() == ModuleState.FAILED)
				.forEach(module -> notifications.error(Component.translatable("minekit.notification.module_failed",
						Component.translatable(module.metadata().nameKey()))));

		LOGGER.info("MINEKIT {} initialized (client-only) on Minecraft {} with Fabric Loader {}",
				versions.mod(), versions.minecraft(), versionOf(loader, "fabricloader"));
		LOGGER.info("MINEKIT config {} ({}), modules: {}, hud: {}", config.file(), configResult.status(),
				modules.modules().stream().map(module -> module.metadata().id() + "=" + module.state()).toList(),
				hud.elements().stream().map(element -> {
					var settings = hud.settings(element);
					return element.id() + "=" + (settings.visible() ? settings.anchor() : "HIDDEN");
				}).toList());
	}

	private static void registerCoreActions(QuickActionRegistry actions, MinekitRuntime runtime) {
		actions.register(QuickAction.always("open_dashboard", Component.translatable("minekit.action.open_dashboard"),
				"minekit dashboard menu overview übersicht", () -> DashboardScreen.open(runtime, OverviewPage.ID)));
		actions.register(QuickAction.always("open_settings", Component.translatable("minekit.action.open_settings"),
				"minekit settings options config einstellungen optionen", () -> DashboardScreen.open(runtime, SettingsPage.ID)));
		actions.register(QuickAction.always("open_keybinds", Component.translatable("minekit.action.open_keybinds"),
				"keys keybinds shortcuts controls tasten tastenbelegung steuerung", () -> {
					Minecraft minecraft = Minecraft.getInstance();
					minecraft.gui.setScreen(new KeyBindsScreen(null, minecraft.options));
				}));
	}

	private static void reportConfigProblems(LoadResult result, NotificationService notifications) {
		switch (result.status()) {
			case RECOVERED_CORRUPT -> notifications.warning(Component.translatable("minekit.notification.config_recovered"));
			case READ_FAILED -> notifications.error(Component.translatable("minekit.notification.config_read_failed"));
			case NEWER_SCHEMA -> notifications.warning(Component.translatable("minekit.notification.config_newer"));
			case LOADED -> {
				if (!result.warnings().isEmpty()) {
					notifications.warning(Component.translatable("minekit.notification.config_partial"));
				}
			}
			case CREATED -> {
			}
		}
	}

	private static String versionOf(FabricLoader loader, String modId) {
		return loader.getModContainer(modId)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
