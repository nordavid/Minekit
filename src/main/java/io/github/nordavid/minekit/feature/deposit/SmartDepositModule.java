package io.github.nordavid.minekit.feature.deposit;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.container.ContainerPanels;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.container.MenuReader;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.item.ItemCategorizer;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * D14 Smart Deposit: moves eligible stacks from the player's inventory into the open container with ordinary clicks,
 * after a preview inside the container screen.
 *
 * <p>Offered only in containers {@link ContainerTracker} identified as a container block the player opened, never in
 * screens a server opens on its own (where a click could mean "buy" or "sell"). Favorites (shared with Smart
 * Inventory) and the hotbar are protected; favorites can be included for a single run. The deposit runs on the shared
 * {@link ContainerActionExecutor}: one click at a time, checked against a prediction, stopped by any surprise.
 *
 * <p>There is deliberately no Quick Action: opening the palette would close the container.
 */
public final class SmartDepositModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("smart_deposit",
			"minekit.module.smart_deposit.name", "minekit.module.smart_deposit.description", true);

	private final JsonFileStore<DepositSettings> settings;
	private final Supplier<Set<String>> favorites;
	private final ContainerTracker tracker;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final ContainerActionExecutor executor;
	private final DashboardPages dashboardPages;
	private final ItemCategorizer categorizer = new ItemCategorizer();
	private final ContainerPanels panels;
	private ModuleContext context;
	private KeyMapping depositKey;
	private @Nullable DepositOverlay overlay;

	public SmartDepositModule(JsonFileStore<DepositSettings> settings, Supplier<Set<String>> favorites, ContainerTracker tracker,
			ContainerPanels panels, KeybindManager keybinds, NotificationService notifications, ContainerActionExecutor executor,
			DashboardPages dashboardPages) {
		this.panels = Objects.requireNonNull(panels, "panels");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.favorites = Objects.requireNonNull(favorites, "favorites");
		this.tracker = Objects.requireNonNull(tracker, "tracker");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.executor = Objects.requireNonNull(executor, "executor");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		depositKey = keybinds.register("smart_deposit", InputConstants.KEY_G, () -> {
			if (isEnabled()) {
				notifications.info(Component.translatable("minekit.deposit.open_container_first", depositKey.getTranslatedKeyMessage()));
			}
		});
		// The tracker confirms a container a tick or two after its screen opened; after a resize it is already confirmed
		// when this (later registered) init listener runs.
		tracker.onConfirmed(container -> attach(Minecraft.getInstance().gui.screen()));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> attach(screen));
		dashboardPages.register(new DepositDashboardPage(this));
	}

	@Override
	public void onDisable() {
		if (isDepositing()) {
			executor.cancel(Component.translatable("minekit.deposit.cancel.module_off"));
		}
		if (overlay != null) {
			overlay.close();
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public JsonFileStore<DepositSettings> settings() {
		return settings;
	}

	ContainerPanels panels() {
		return panels;
	}

	KeyMapping depositKey() {
		return depositKey;
	}

	NotificationService notifications() {
		return notifications;
	}

	Set<String> favorites() {
		return favorites.get();
	}

	ItemKeyIndex newKeyIndex() {
		return new ItemKeyIndex(categorizer);
	}

	/** The Smart Deposit overlay of the open container screen, if there is one. For tests. */
	public Optional<DepositOverlay> overlay() {
		Screen screen = Minecraft.getInstance().gui.screen();
		return overlay != null && overlay.screen() == screen ? Optional.of(overlay) : Optional.empty();
	}

	public boolean isDepositing() {
		return executor.currentJob().filter(DepositJob.class::isInstance).isPresent();
	}

	int runningClicks() {
		return isDepositing() ? executor.runningClicks() : 0;
	}

	void cancelDeposit() {
		if (isDepositing()) {
			executor.cancel(Component.translatable("minekit.deposit.cancel.player"));
		}
	}

	void cycleProfile() {
		update(current -> current.withProfile(current.profile().next()));
	}

	void update(UnaryOperator<DepositSettings> change) {
		settings.update(change);
		if (settings.lastSaveFailed()) {
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		}
	}

	/** Adds the Smart Deposit button and panel to a confirmed container's screen (again after every re-initialization). */
	private void attach(@Nullable Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || tracker.of(screen).isEmpty()) {
			return;
		}
		if (overlay == null || overlay.screen() != screen) {
			overlay = new DepositOverlay(this, containerScreen, tracker.of(screen).get());
		}
		DepositOverlay current = overlay;
		current.install();
		ScreenKeyboardEvents.allowKeyPress(screen).register((target, event) -> !onKey(current, event));
	}

	/** Returns true if the key press was handled and must not reach the container screen. */
	private boolean onKey(DepositOverlay target, KeyEvent event) {
		if (!isEnabled()) {
			return false;
		}
		if (depositKey.matches(event)) {
			target.toggle();
			return true;
		}
		if (target.isOpen() && !isDepositing() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			target.confirm();
			return true;
		}
		return false;
	}

	void startDeposit(DepositOverlay target, ItemKeyIndex keys, DepositPlanner.Plan plan, DepositPlanner.Rules rules) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || executor.isRunning()) {
			notifications.warning(Component.translatable("minekit.sort.unavailable.busy"));
			return;
		}
		ContainerState before = MenuReader.read(target.container().menu(), keys);
		executor.start(new DepositJob(player, target.container(), tracker, keys, plan, rules, result -> {
			report(result, before, MenuReader.read(target.container().menu(), keys), plan);
			target.onDepositFinished();
		}));
	}

	private void report(ContainerActionExecutor.Result result, ContainerState before, ContainerState after, DepositPlanner.Plan plan) {
		int moved = 0;
		Map<ItemKey, Integer> kinds = new TreeMap<>();
		for (int slot : plan.layout().container()) {
			int delta = count(after.slot(slot)) - count(before.slot(slot));
			if (delta > 0 && after.slot(slot) != null) {
				moved += delta;
				kinds.merge(after.slot(slot).key(), delta, Integer::sum);
			}
		}
		int remaining = 0;
		for (int slot : plan.sources()) {
			SlotStack stack = after.slot(slot);
			if (stack != null && plan.eligible().contains(stack.key())) {
				remaining += stack.count();
			}
		}
		if (!result.itemTotalsUnchanged()) {
			notifications.error(Component.translatable("minekit.deposit.totals_changed"));
			return;
		}
		switch (result.outcome()) {
			case COMPLETED -> {
				if (remaining > 0) {
					notifications.warning(Component.translatable("minekit.deposit.done_partial", moved, kinds.size(), remaining));
				} else {
					notifications.success(Component.translatable("minekit.deposit.done", moved, kinds.size()));
				}
			}
			case CANCELLED -> notifications.info(Component.translatable("minekit.deposit.cancelled", moved));
			case STOPPED -> notifications.warning(Component.translatable("minekit.deposit.stopped",
					result.reason().orElse(Component.translatable("minekit.executor.unexpected_change")), moved));
		}
	}

	private static int count(@Nullable SlotStack stack) {
		return stack == null ? 0 : stack.count();
	}
}
