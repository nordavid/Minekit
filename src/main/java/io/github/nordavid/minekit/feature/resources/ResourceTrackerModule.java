package io.github.nordavid.minekit.feature.resources;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.goal.GoalBridge;
import io.github.nordavid.minekit.core.hud.HudManager;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.item.InventoryCounts;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import io.github.nordavid.minekit.core.world.WorldContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * A4 Resource Tracker: sessions that count what the player collects and uses, with goals and a HUD element.
 *
 * <p>Every {@link #OBSERVE_INTERVAL} ticks the item totals of the player's inventory are compared with the previous
 * observation; {@link ResourceTracker} decides how a change counts. Observations are kept in memory and saved every
 * {@link #SAVE_INTERVAL} ticks and on every start, pause, resume and stop. Leaving the world pauses the session.
 */
public final class ResourceTrackerModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("resource_tracker",
			"minekit.module.resource_tracker.name", "minekit.module.resource_tracker.description", true);
	static final int OBSERVE_INTERVAL = 10;
	static final int SAVE_INTERVAL = 600;
	private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("dd.MM. HH:mm");

	private final JsonFileStore<ResourceTracker> store;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final QuickActionRegistry quickActions;
	private final DashboardPages dashboardPages;
	private final HudManager hud;
	private final GoalBridge goals;
	private ModuleContext context;

	private @Nullable Map<String, Integer> lastCounts;
	private boolean lastAmbiguous;
	private @Nullable ClientLevel lastLevel;
	/** Level object the world id was computed for, so it is only recomputed on a change. */
	private @Nullable ClientLevel worldLevel;
	private @Nullable String worldId;
	private long ticks;
	private boolean dirty;
	private int idCounter;
	private @Nullable String editingId;

	public ResourceTrackerModule(JsonFileStore<ResourceTracker> store, KeybindManager keybinds, NotificationService notifications,
			QuickActionRegistry quickActions, DashboardPages dashboardPages, HudManager hud, GoalBridge goals) {
		this.store = Objects.requireNonNull(store, "store");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.quickActions = Objects.requireNonNull(quickActions, "quickActions");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
		this.hud = Objects.requireNonNull(hud, "hud");
		this.goals = Objects.requireNonNull(goals, "goals");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		keybinds.register("resource_session", InputConstants.UNKNOWN.getValue(), () -> {
			if (unavailableReason().isEmpty()) {
				toggleSession();
			}
		});
		quickActions.register(new QuickAction("resource_session", Component.translatable("minekit.action.resource_session"),
				"resources ressourcen sitzung session tracker start stop sammeln", this::unavailableReason, this::toggleSession));
		quickActions.register(new QuickAction("goal_held_item", Component.translatable("minekit.action.goal_held_item"),
				"held hand item goal collect target ziel sammeln gehalten", () -> unavailableReason().or(() -> ItemIds.held().isPresent()
						? Optional.empty() : Optional.of(Component.translatable("minekit.action.unavailable.no_held_item"))),
				this::addHeldItemGoal));
		dashboardPages.register(new ResourcesDashboardPage(this));
		hud.register(new ResourceHudElement(this));
		goals.connect(new GoalBridge.Receiver() {
			@Override
			public boolean isAvailable() {
				return unavailableReason().isEmpty();
			}

			@Override
			public void addGoals(Map<String, Integer> newGoals, String sessionName) {
				String world = world();
				if (world == null) {
					return;
				}
				if (currentSession() == null) {
					start(sessionName);
				}
				change(tracker -> tracker.withGoals(world, newGoals));
				notifications.success(Component.translatable("minekit.resources.goals_added", newGoals.size()));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> context.guard("tick", () -> tick(client)));
	}

	@Override
	public void onDisable() {
		if (tracker().open().values().stream().anyMatch(ResourceSession::isRunning)) {
			change(tracker -> tracker.pauseAll(System.currentTimeMillis()));
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public ResourceTracker tracker() {
		return store.current();
	}

	@Nullable String editingId() {
		return editingId;
	}

	void setEditingId(@Nullable String id) {
		this.editingId = id;
	}

	public Optional<Component> unavailableReason() {
		if (!isEnabled()) {
			return Optional.of(Component.translatable("minekit.resources.unavailable.module_off"));
		}
		if (Minecraft.getInstance().player == null) {
			return Optional.of(Component.translatable("minekit.resources.unavailable.no_world"));
		}
		return Optional.empty();
	}

	/** Id of the world the player is in, or {@code null} outside a world. */
	@Nullable String world() {
		return Minecraft.getInstance().player == null ? null : WorldContext.current(Minecraft.getInstance()).map(WorldContext::id).orElse(null);
	}

	/** The open session of the world the player is in, running or paused, or {@code null}. */
	public @Nullable ResourceSession currentSession() {
		String world = world();
		return world == null ? null : tracker().current(world);
	}

	/** Raises the goal for the held item by a stack, starting a session if none is open. */
	private void addHeldItemGoal() {
		Optional<String> held = ItemIds.held();
		if (held.isEmpty()) {
			return;
		}
		String item = held.get();
		String world = world();
		if (world == null) {
			return;
		}
		if (currentSession() == null) {
			start(I18n.get("minekit.resources.default_name", LocalDateTime.now().format(NAME_TIME)));
		}
		ResourceSession session = currentSession();
		int target = (session == null ? 0 : session.goals().getOrDefault(item, 0)) + 64;
		change(tracker -> tracker.withGoals(world, Map.of(item, target)));
		notifications.success(Component.translatable("minekit.resources.goal_added", ItemIds.name(item), target));
	}

	/** Starts a session if none is open, otherwise ends the open one. */
	public void toggleSession() {
		if (currentSession() == null) {
			start(I18n.get("minekit.resources.default_name", LocalDateTime.now().format(NAME_TIME)));
		} else {
			stop();
		}
	}

	public void start(String name) {
		Optional<WorldContext> world = WorldContext.current(Minecraft.getInstance());
		if (world.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		String id = now + "-" + idCounter++;
		lastCounts = null;
		change(tracker -> tracker.start(world.get().id(), id, name, world.get().label(), now));
		notifications.success(Component.translatable("minekit.resources.started", name));
	}

	public void pause() {
		String world = world();
		if (world == null) {
			return;
		}
		change(tracker -> tracker.pause(world, System.currentTimeMillis()));
		notifications.info(Component.translatable("minekit.resources.paused"));
	}

	public void resume() {
		String world = world();
		if (world == null) {
			return;
		}
		lastCounts = null;
		change(tracker -> tracker.resume(world, System.currentTimeMillis()));
		notifications.info(Component.translatable("minekit.resources.resumed"));
	}

	public void stop() {
		String world = world();
		ResourceSession session = currentSession();
		if (world == null || session == null) {
			return;
		}
		change(tracker -> tracker.stop(world, System.currentTimeMillis()));
		long collected = session.collected().values().stream().mapToLong(Long::longValue).sum();
		notifications.success(Component.translatable("minekit.resources.stopped", session.name(), collected));
	}

	/** Applies and saves a change; reports a failed save. */
	void change(UnaryOperator<ResourceTracker> change) {
		store.updateInMemory(change);
		// Always save: earlier observations may only be in memory so far.
		store.save();
		dirty = false;
		if (store.lastSaveFailed()) {
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		}
	}

	private void tick(Minecraft minecraft) {
		ticks++;
		LocalPlayer player = minecraft.player;
		if (minecraft.level != worldLevel) {
			// Joined, left or changed world or dimension: find out which world this is (hashing only on a change).
			worldLevel = minecraft.level;
			worldId = WorldContext.current(minecraft).map(WorldContext::id).orElse(null);
			lastCounts = null;
		}
		if (player == null || worldId == null) {
			// Outside a world: pause, so time away is not counted.
			lastCounts = null;
			if (tracker().open().values().stream().anyMatch(ResourceSession::isRunning)) {
				change(tracker -> tracker.pauseAll(System.currentTimeMillis()));
			}
			saveIfDirty();
			return;
		}
		String world = worldId;
		ResourceSession session = tracker().current(world);
		if (!isEnabled() || session == null || !session.isRunning()) {
			lastCounts = null;
			saveIfDirty();
			return;
		}
		if (ticks % OBSERVE_INTERVAL != 0) {
			return;
		}
		Map<String, Integer> counts = InventoryCounts.of(player);
		boolean ambiguous = minecraft.gui.screen() != null || player.isCreative() || player.isSpectator() || !player.isAlive();
		if (lastCounts != null && minecraft.level == lastLevel) {
			Map<String, Integer> before = lastCounts;
			boolean unclear = ambiguous || lastAmbiguous;
			dirty |= store.updateInMemory(tracker -> tracker.observe(world, before, counts, unclear));
		}
		lastCounts = counts;
		lastAmbiguous = ambiguous;
		lastLevel = minecraft.level;
		if (ticks % SAVE_INTERVAL == 0) {
			saveIfDirty();
		}
	}

	private void saveIfDirty() {
		if (dirty) {
			store.save();
			dirty = false;
		}
	}
}
