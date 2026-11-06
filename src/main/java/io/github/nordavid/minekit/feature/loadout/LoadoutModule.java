package io.github.nordavid.minekit.feature.loadout;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.container.ContainerPanels;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.container.MenuReader;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.item.ItemCategorizer;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * D12 Loadout Manager: saves the player's equipment and inventory arrangement, compares the inventory with it, and
 * applies it with ordinary clicks, in the player's own inventory or from an open container (taking only).
 */
public final class LoadoutModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("loadouts",
			"minekit.module.loadouts.name", "minekit.module.loadouts.description", true);
	public static final String ACTION_PREFIX = "loadout.";

	/** A plan together with the key index it was made with; execution must use the same keys. */
	record Planned(ItemKeyIndex keys, LoadoutPlanner.Plan plan) {
	}

	private final JsonFileStore<Loadouts> store;
	private final ContainerTracker tracker;
	private final ContainerPanels panels;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final ContainerActionExecutor executor;
	private final QuickActionRegistry quickActions;
	private final DashboardPages dashboardPages;
	private final ItemCategorizer categorizer = new ItemCategorizer();
	private ModuleContext context;
	private KeyMapping key;
	private @Nullable LoadoutOverlay overlay;
	private @Nullable String lastUsed;

	public LoadoutModule(JsonFileStore<Loadouts> store, ContainerTracker tracker, ContainerPanels panels, KeybindManager keybinds,
			NotificationService notifications, ContainerActionExecutor executor, QuickActionRegistry quickActions, DashboardPages dashboardPages) {
		this.store = Objects.requireNonNull(store, "store");
		this.tracker = Objects.requireNonNull(tracker, "tracker");
		this.panels = Objects.requireNonNull(panels, "panels");
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
		// No default key: reached through Quick Actions, the dashboard and the button next to containers.
		key = keybinds.register("loadouts", InputConstants.UNKNOWN.getValue(), () -> {
			if (isEnabled()) {
				openManager(null);
			}
		});
		quickActions.register(new QuickAction("open_loadouts", Component.translatable("minekit.action.open_loadouts"),
				"loadouts loadout manager equipment gear ausrüstung ausstattung", this::unavailableReason, () -> openManager(null)));
		quickActions.register(new QuickAction("save_loadout", Component.translatable("minekit.action.save_loadout"),
				"save new loadout inventory speichern neues ausrüstung", this::saveUnavailableReason, this::saveCurrentAndOpen));
		quickActions.registerProvider(this::loadoutActions);
		dashboardPages.register(new LoadoutDashboardPage(this));
		tracker.onConfirmed(container -> attach(Minecraft.getInstance().gui.screen()));
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> attach(screen));
	}

	@Override
	public void onDisable() {
		if (isApplying()) {
			executor.cancel(Component.translatable("minekit.loadout.cancel.module_off"));
		}
		if (overlay != null) {
			overlay.close();
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof LoadoutScreen) {
			minecraft.gui.setScreen(null);
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public JsonFileStore<Loadouts> store() {
		return store;
	}

	ContainerPanels panels() {
		return panels;
	}

	NotificationService notifications() {
		return notifications;
	}

	KeyMapping key() {
		return key;
	}

	ItemKeyIndex newKeyIndex() {
		return new ItemKeyIndex(categorizer);
	}

	/** The loadout panel of the open container screen, if there is one. For tests. */
	public Optional<LoadoutOverlay> overlay() {
		Screen screen = Minecraft.getInstance().gui.screen();
		return overlay != null && overlay.screen() == screen ? Optional.of(overlay) : Optional.empty();
	}

	public boolean isApplying() {
		return executor.currentJob().filter(LoadoutJob.class::isInstance).isPresent();
	}

	int runningClicks() {
		return isApplying() ? executor.runningClicks() : 0;
	}

	void cancelApplying() {
		if (isApplying()) {
			executor.cancel(Component.translatable("minekit.loadout.cancel.player"));
		}
	}

	@Nullable String lastUsed() {
		return lastUsed;
	}

	void update(UnaryOperator<Loadouts> change) {
		store.update(change);
		if (store.lastSaveFailed()) {
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		}
	}

	public Optional<Component> unavailableReason() {
		if (!isEnabled()) {
			return Optional.of(Component.translatable("minekit.loadout.unavailable.module_off"));
		}
		if (Minecraft.getInstance().player == null) {
			return Optional.of(Component.translatable("minekit.loadout.unavailable.no_world"));
		}
		return Optional.empty();
	}

	private Optional<Component> saveUnavailableReason() {
		Optional<Component> reason = unavailableReason();
		if (reason.isEmpty() && store.current().isFull()) {
			return Optional.of(Component.translatable("minekit.loadout.full", Loadouts.MAX_LOADOUTS));
		}
		return reason;
	}

	/** Why a loadout cannot be applied in the inventory right now, or empty if it can. */
	Optional<Component> applyUnavailableReason() {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			return reason;
		}
		if (player == null || minecraft.gameMode == null) {
			return Optional.of(Component.translatable("minekit.loadout.unavailable.no_world"));
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

	/** Opens the Loadout Manager, with a loadout selected if {@code id} is given. */
	public void openManager(@Nullable String id) {
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new LoadoutScreen(this, id));
	}

	/** Saves the current inventory as a new loadout. Returns its id, or empty if that was not possible. */
	Optional<String> saveCurrent() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || store.current().isFull()) {
			return Optional.empty();
		}
		Map<Integer, LoadoutItem> items = MinecraftLoadouts.capture(player.getInventory(), newKeyIndex());
		String id = store.current().peekId();
		String name = I18n.get("minekit.loadout.default_name", store.current().nextId());
		update(loadouts -> loadouts.withAdded(name, items));
		notifications.success(Component.translatable("minekit.loadout.saved", name));
		return store.current().byId(id).map(Loadout::id);
	}

	private void saveCurrentAndOpen() {
		saveCurrent().ifPresent(this::openManager);
	}

	/** The current inventory as loadout items, for "update from inventory" and shift-clicks. */
	Map<Integer, LoadoutItem> captureInventory() {
		LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);
		return MinecraftLoadouts.capture(player.getInventory(), newKeyIndex());
	}

	/** One Quick Action per loadout: it opens the manager with that loadout, where the preview is. */
	private List<QuickAction> loadoutActions() {
		if (!isEnabled()) {
			return List.of();
		}
		List<QuickAction> actions = new ArrayList<>();
		for (Loadout loadout : store.current().loadouts()) {
			actions.add(new QuickAction(ACTION_PREFIX + loadout.id(), Component.translatable("minekit.action.loadout", loadout.name()),
					"loadout ausrüstung " + loadout.name(), this::unavailableReason, () -> openManager(loadout.id())));
		}
		return actions;
	}

	/** Plans a loadout for the player's own inventory. */
	Planned planInInventory(Loadout loadout) {
		LocalPlayer player = Objects.requireNonNull(Minecraft.getInstance().player);
		ItemKeyIndex keys = newKeyIndex();
		ContainerState state = MenuReader.read(player.inventoryMenu, keys);
		return new Planned(keys, LoadoutPlanner.plan(state, LoadoutLayout.inventoryMenu(), loadout, MinecraftLoadouts.facts(keys)));
	}

	/** Plans a loadout for an open container: items may only come out of it. */
	Planned planInContainer(Loadout loadout, OpenedContainer container) {
		ItemKeyIndex keys = newKeyIndex();
		ContainerState state = MenuReader.read(container.menu(), keys);
		LoadoutLayout layout = MinecraftLoadouts.layout(container.menu(), container.containerSlots());
		return new Planned(keys, LoadoutPlanner.plan(state, layout, loadout, MinecraftLoadouts.facts(keys)));
	}

	/**
	 * Applies a loadout in the inventory if it still does what {@code shown} showed: opens the inventory screen and
	 * clicks there. Returns {@code false} if the inventory changed, so the caller shows the new preview first.
	 */
	boolean applyInInventory(Loadout loadout, LoadoutPlanner.Plan shown) {
		Optional<Component> reason = applyUnavailableReason();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return true;
		}
		Planned planned = planInInventory(loadout);
		if (!LoadoutPlanner.sameClicks(planned.plan(), shown) || planned.plan().refusal().isPresent()) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = Objects.requireNonNull(minecraft.player);
		lastUsed = loadout.id();
		minecraft.gui.setScreen(new InventoryScreen(player));
		executor.start(new LoadoutJob(player, player.inventoryMenu, null, tracker, planned.keys(), planned.plan(),
				result -> report(result, loadout, planned.plan(), false)));
		return true;
	}

	/** Takes what the loadout needs from the open container. */
	void applyFromContainer(LoadoutOverlay target, Loadout loadout, Planned planned) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || executor.isRunning()) {
			notifications.warning(Component.translatable("minekit.sort.unavailable.busy"));
			return;
		}
		lastUsed = loadout.id();
		executor.start(new LoadoutJob(player, target.container().menu(), target.container(), tracker, planned.keys(), planned.plan(), result -> {
			report(result, loadout, planned.plan(), true);
			target.onApplied();
		}));
	}

	private void report(ContainerActionExecutor.Result result, Loadout loadout, LoadoutPlanner.Plan plan, boolean fromContainer) {
		if (!result.itemTotalsUnchanged()) {
			notifications.error(Component.translatable("minekit.loadout.totals_changed"));
			return;
		}
		switch (result.outcome()) {
			case COMPLETED -> {
				int taken = plan.taken().values().stream().mapToInt(Integer::intValue).sum();
				Component done = fromContainer
						? Component.translatable("minekit.loadout.done_container", taken, loadout.name())
						: Component.translatable("minekit.loadout.done", loadout.name());
				if (plan.missing().isEmpty()) {
					notifications.success(done);
				} else {
					notifications.warning(Component.translatable("minekit.loadout.done_missing", done, missingText(plan.missing())));
				}
			}
			case CANCELLED -> notifications.info(Component.translatable("minekit.loadout.cancelled"));
			case STOPPED -> notifications.warning(Component.translatable("minekit.loadout.stopped",
					result.reason().orElse(Component.translatable("minekit.executor.unexpected_change"))));
		}
	}

	/** "32× Torch, 1× Iron Helmet" for at most three items, then "and n more". */
	static Component missingText(Map<String, Integer> missing) {
		List<Map.Entry<String, Integer>> entries = new ArrayList<>(missing.entrySet());
		var text = Component.empty();
		for (int i = 0; i < Math.min(3, entries.size()); i++) {
			if (i > 0) {
				text.append(", ");
			}
			text.append(entries.get(i).getValue() + "× ").append(ItemIds.name(entries.get(i).getKey()));
		}
		if (entries.size() > 3) {
			text.append(" ").append(Component.translatable("minekit.deposit.more", entries.size() - 3));
		}
		return text;
	}

	/** Adds the loadout button and panel to a confirmed container's screen (again after every re-initialization). */
	private void attach(@Nullable Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen) || tracker.of(screen).isEmpty()) {
			return;
		}
		if (overlay == null || overlay.screen() != screen) {
			overlay = new LoadoutOverlay(this, containerScreen, tracker.of(screen).get());
		}
		LoadoutOverlay current = overlay;
		current.install();
		ScreenKeyboardEvents.allowKeyPress(screen).register((target, event) -> !onKey(current, event));
	}

	/** Returns true if the key press was handled and must not reach the container screen. */
	private boolean onKey(LoadoutOverlay target, KeyEvent event) {
		if (!isEnabled()) {
			return false;
		}
		if (key.matches(event)) {
			target.toggle();
			return true;
		}
		if (target.isOpen() && !isApplying() && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
			target.confirm();
			return true;
		}
		return false;
	}
}
