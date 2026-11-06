package io.github.nordavid.minekit;

import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.config.ConfigManager;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.goal.GoalBridge;
import io.github.nordavid.minekit.core.hud.HudManager;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.module.ModuleManager;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import java.nio.file.Path;
import java.util.Objects;
import net.minecraft.client.KeyMapping;

/**
 * The shared core services, created once by {@link MinekitClient}. Screens and modules receive it (or the parts they
 * need) explicitly; {@link #get()} exists for entry points that cannot, such as client gametests.
 *
 * @param dataDirectory  {@code config/minekit}, where modules keep their own files
 * @param dashboardKey   the shortcut that opens (and closes) the dashboard
 * @param dashboardPages pages shown in the dashboard; modules add theirs during initialization
 * @param quickActions   actions offered by Quick Actions; modules add theirs during initialization
 * @param executor       runs inventory click sequences safely, one job at a time
 * @param goals          hands collection goals from one module to the resource tracker
 * @param containers     identifies container screens the player opened by right-clicking a container block
 * @param storage        answers what the storage index knows about an item
 */
public record MinekitRuntime(
		Versions versions,
		Path dataDirectory,
		ConfigManager config,
		ModuleManager modules,
		KeybindManager keybinds,
		HudManager hud,
		NotificationService notifications,
		KeyMapping dashboardKey,
		DashboardPages dashboardPages,
		QuickActionRegistry quickActions,
		ContainerActionExecutor executor,
		GoalBridge goals,
		ContainerTracker containers,
		StorageBridge storage) {

	/** Versions shown in the UI. */
	public record Versions(String mod, String minecraft) {
	}

	private static volatile MinekitRuntime current;

	public MinekitRuntime {
		Objects.requireNonNull(versions, "versions");
		Objects.requireNonNull(dataDirectory, "dataDirectory");
		Objects.requireNonNull(config, "config");
		Objects.requireNonNull(modules, "modules");
		Objects.requireNonNull(keybinds, "keybinds");
		Objects.requireNonNull(hud, "hud");
		Objects.requireNonNull(notifications, "notifications");
		Objects.requireNonNull(dashboardKey, "dashboardKey");
		Objects.requireNonNull(dashboardPages, "dashboardPages");
		Objects.requireNonNull(quickActions, "quickActions");
		Objects.requireNonNull(executor, "executor");
		Objects.requireNonNull(goals, "goals");
		Objects.requireNonNull(containers, "containers");
		Objects.requireNonNull(storage, "storage");
	}

	public static MinekitRuntime get() {
		MinekitRuntime runtime = current;
		if (runtime == null) {
			throw new IllegalStateException("Minekit is not initialized yet");
		}
		return runtime;
	}

	static void install(MinekitRuntime runtime) {
		if (current != null) {
			throw new IllegalStateException("Minekit is already initialized");
		}
		current = Objects.requireNonNull(runtime, "runtime");
	}
}
