package io.github.nordavid.minekit.feature.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.item.ItemCategorizer;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * A1 Smart Inventory Manager: sorts the player's own inventory after a preview the player confirms.
 *
 * <p>Flow: the sort shortcut (in game or in the inventory) or the Quick Action opens {@link SortPreviewScreen}, which
 * shows the current and the sorted layout. On confirmation the vanilla inventory screen opens and the
 * {@link ContainerActionExecutor} performs ordinary left clicks there until the layout is reached. Closing the
 * inventory, changing the game mode or any unexpected change stops it.
 *
 * <p>Not available in creative or spectator mode (creative inventory actions are privileged), while another container
 * is open, or while riding (the server then controls the inventory screen).
 */
public final class SmartInventoryModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("smart_inventory",
			"minekit.module.smart_inventory.name", "minekit.module.smart_inventory.description", true);

	private final JsonFileStore<InventorySettings> settings;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final ContainerActionExecutor executor;
	private final QuickActionRegistry quickActions;
	private final DashboardPages dashboardPages;
	private final ItemCategorizer categorizer = new ItemCategorizer();
	private ModuleContext context;
	private KeyMapping sortKey;

	public SmartInventoryModule(JsonFileStore<InventorySettings> settings, KeybindManager keybinds, NotificationService notifications,
			ContainerActionExecutor executor, QuickActionRegistry quickActions, DashboardPages dashboardPages) {
		this.settings = Objects.requireNonNull(settings, "settings");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.quickActions = Objects.requireNonNull(quickActions, "quickActions");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		sortKey = keybinds.register("sort_inventory", InputConstants.KEY_R, this::onSortKeyInGame);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (screen instanceof InventoryScreen) {
				ScreenKeyboardEvents.allowKeyPress(screen).register((current, event) -> context.guard("key", () -> !onKeyInInventory(current, event), true));
			}
		});
		quickActions.register(new QuickAction("sort_inventory", Component.translatable("minekit.action.sort_inventory"),
				"sort inventory sortieren inventar ordnen aufraeumen", this::unavailableReason, this::openPreview));
		dashboardPages.register(new InventoryDashboardPage(this));
	}

	@Override
	public void onDisable() {
		if (isSorting()) {
			executor.cancel(Component.translatable("minekit.sort.cancel.module_off"));
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof SortPreviewScreen) {
			minecraft.gui.setScreen(null);
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public JsonFileStore<InventorySettings> settings() {
		return settings;
	}

	KeyMapping sortKey() {
		return sortKey;
	}

	NotificationService notifications() {
		return notifications;
	}

	ItemKeyIndex newKeyIndex() {
		return new ItemKeyIndex(categorizer);
	}

	public boolean isSorting() {
		return executor.currentJob().filter(SortJob.class::isInstance).isPresent();
	}

	/** Why sorting cannot start right now, or empty if it can. */
	public Optional<Component> unavailableReason() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (!isEnabled()) {
			return Optional.of(Component.translatable("minekit.sort.unavailable.module_off"));
		}
		if (player == null || minecraft.gameMode == null) {
			return Optional.of(Component.translatable("minekit.sort.unavailable.no_world"));
		}
		if (player.isCreative() || player.isSpectator()) {
			return Optional.of(Component.translatable("minekit.sort.blocked.game_mode"));
		}
		if (minecraft.gameMode.isServerControlledInventory()) {
			return Optional.of(Component.translatable("minekit.sort.unavailable.riding"));
		}
		if (player.containerMenu != player.inventoryMenu) {
			return Optional.of(Component.translatable("minekit.sort.unavailable.container_open"));
		}
		if (executor.isRunning()) {
			return Optional.of(Component.translatable("minekit.sort.unavailable.busy"));
		}
		return Optional.empty();
	}

	/** Opens the sort preview, or explains why it cannot. */
	public void openPreview() {
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new SortPreviewScreen(this));
	}

	/** Called by the preview after the player confirmed: opens the inventory and starts clicking. */
	void startSort(ItemKeyIndex keys, SortPlanner.Planned plan) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		minecraft.gui.setScreen(new InventoryScreen(player));
		executor.start(new SortJob(player, keys, plan, this::onSortFinished));
	}

	private void onSortKeyInGame() {
		if (!isEnabled()) {
			return;
		}
		if (isSorting()) {
			executor.cancel(Component.translatable("minekit.sort.cancel.key"));
			return;
		}
		openPreview();
	}

	/** Returns true if the key press was handled and must not reach the inventory screen. */
	private boolean onKeyInInventory(Screen screen, KeyEvent event) {
		if (!isEnabled() || !sortKey.matches(event)) {
			return false;
		}
		if (isSorting()) {
			executor.cancel(Component.translatable("minekit.sort.cancel.key"));
			return true;
		}
		// Close the inventory the vanilla way first: the server returns crafting-grid and cursor items to the inventory.
		screen.onClose();
		openPreview();
		return true;
	}

	private void onSortFinished(ContainerActionExecutor.Result result) {
		switch (result.outcome()) {
			case COMPLETED -> {
				if (result.itemTotalsUnchanged()) {
					notifications.success(Component.translatable("minekit.sort.done", result.clicks()));
				} else {
					notifications.error(Component.translatable("minekit.sort.totals_changed"));
				}
			}
			case CANCELLED -> notifications.info(Component.translatable("minekit.sort.cancelled"));
			case STOPPED -> notifications.warning(Component.translatable("minekit.sort.stopped",
					result.reason().orElse(Component.translatable("minekit.executor.unexpected_change"))));
		}
	}
}
