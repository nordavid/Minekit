package io.github.nordavid.minekit.feature.storage;

import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.persistence.LoadResult;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A3 Storage Finder: remembers what the containers the player opened held, per world or server, and lets the player
 * search it.
 *
 * <p>Only containers identified by {@link ContainerTracker} are recorded, when they are closed and only if the server
 * had sent their contents. Nothing is known about containers the player never opened, and every record shows how old
 * it is (PROJECT_PLAN.md §6.3). Each world or server has its own file {@code config/minekit/storage-index/<id>.json};
 * the id is a hash, so no server address is stored in plain text.
 */
public final class StorageFinderModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("storage_finder",
			"minekit.module.storage_finder.name", "minekit.module.storage_finder.description", true);
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");

	private final Path directory;
	private final ContainerTracker tracker;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final QuickActionRegistry quickActions;
	private final DashboardPages dashboardPages;
	private final StorageBridge bridge;
	private ModuleContext context;

	private @Nullable JsonFileStore<StorageIndex> store;
	private @Nullable String storeId;
	private boolean saveFailureReported;

	public StorageFinderModule(Path directory, ContainerTracker tracker, KeybindManager keybinds, NotificationService notifications,
			QuickActionRegistry quickActions, DashboardPages dashboardPages, StorageBridge bridge) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.tracker = Objects.requireNonNull(tracker, "tracker");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.quickActions = Objects.requireNonNull(quickActions, "quickActions");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
		this.bridge = Objects.requireNonNull(bridge, "bridge");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		keybinds.register("storage_finder", InputConstants.UNKNOWN.getValue(), this::openFinder);
		quickActions.register(new QuickAction("open_storage_finder", Component.translatable("minekit.action.open_storage_finder"),
				"storage finder lager suchen kisten truhen chest search find", this::unavailableReason, this::openFinder));
		quickActions.register(new QuickAction("find_held_item", Component.translatable("minekit.action.find_held_item"),
				"held hand item storage find where lager gehalten hand wo", () -> unavailableReason().or(StorageFinderModule::noHeldItem),
				() -> ItemIds.held().ifPresent(this::openFinder)));
		dashboardPages.register(new StorageDashboardPage(this));
		tracker.onClosed(this::record);
		bridge.connect(item -> isEnabled() && store != null
				? store.current().known(item).map(known -> new StorageBridge.Known(known.total(), known.containers(), known.oldestObservedAt()))
				: Optional.empty());
		ClientTickEvents.END_CLIENT_TICK.register(this::syncContext);
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	NotificationService notifications() {
		return notifications;
	}

	/** The index of the current world or server, if one is loaded. */
	public Optional<StorageIndex> index() {
		return Optional.ofNullable(store).map(JsonFileStore::current);
	}

	public Optional<Component> unavailableReason() {
		if (!isEnabled()) {
			return Optional.of(Component.translatable("minekit.storage.unavailable.module_off"));
		}
		if (Minecraft.getInstance().player == null) {
			return Optional.of(Component.translatable("minekit.storage.unavailable.no_world"));
		}
		if (store == null) {
			return Optional.of(Component.translatable("minekit.storage.unavailable.no_context"));
		}
		return Optional.empty();
	}

	public void openFinder() {
		openFinder(null);
	}

	/** Opens the finder with an item selected, or with all containers for {@code null}. */
	public void openFinder(@Nullable String item) {
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new StorageFinderScreen(this, item));
	}

	private static Optional<Component> noHeldItem() {
		return ItemIds.held().isPresent() ? Optional.empty() : Optional.of(Component.translatable("minekit.action.unavailable.no_held_item"));
	}

	void forget(String key) {
		change(index -> index.without(key));
	}

	void forgetAll() {
		change(StorageIndex::cleared);
		notifications.info(Component.translatable("minekit.storage.forgot_all"));
	}

	private void change(UnaryOperator<StorageIndex> operation) {
		JsonFileStore<StorageIndex> current = store;
		if (current == null) {
			return;
		}
		current.update(operation);
		if (current.lastSaveFailed() && !saveFailureReported) {
			saveFailureReported = true;
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		}
	}

	private void record(OpenedContainer container) {
		if (!isEnabled() || store == null || !container.isSynced()) {
			return;
		}
		Map<String, Integer> items = new TreeMap<>();
		int[] slots = container.containerSlots();
		int used = 0;
		for (int slot : slots) {
			ItemStack stack = container.menu().slots.get(slot).getItem();
			if (!stack.isEmpty()) {
				used++;
				items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		List<ContainerRecord.Pos> positions = new ArrayList<>();
		for (BlockPos pos : container.positions()) {
			positions.add(new ContainerRecord.Pos(pos.getX(), pos.getY(), pos.getZ()));
		}
		ContainerRecord record = new ContainerRecord(container.dimension(), container.blockId(), positions, items, slots.length, used,
				System.currentTimeMillis());
		change(index -> index.withObserved(record));
	}

	/**
	 * Loads the index of the world or server the player is in, and unloads it when the player leaves. Every change is
	 * saved right away, so nothing is pending then. A world without any recorded container gets no file at all.
	 */
	private void syncContext(Minecraft minecraft) {
		Optional<Context> now = currentContext(minecraft);
		String id = now.map(Context::id).orElse(null);
		if (Objects.equals(id, storeId)) {
			return;
		}
		store = null;
		storeId = id;
		if (now.isPresent()) {
			Context next = now.get();
			JsonFileStore<StorageIndex> loaded = new JsonFileStore<>(directory.resolve(next.id() + ".json"), Clock.systemUTC(),
					new StorageIndexCodec(next.label()));
			if (Files.exists(loaded.file())) {
				LoadResult result = loaded.load();
				if (result.needsAttention()) {
					LOGGER.warn("Storage index {}: {} {}", loaded.file().getFileName(), result.status(), result.warnings());
					notifications.warning(Component.translatable("minekit.storage.index_problem"));
				}
				if (!loaded.current().label().equals(next.label())) {
					loaded.update(index -> index.withLabel(next.label())); // the server list entry or world was renamed
				}
			}
			store = loaded;
			LOGGER.info("Storage index {}: {} containers", loaded.file().getFileName(), loaded.current().containers().size());
		}
	}

	private record Context(String id, String label) {
	}

	private static Optional<Context> currentContext(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return Optional.empty();
		}
		IntegratedServer singleplayer = minecraft.getSingleplayerServer();
		if (singleplayer != null) {
			Path folder = singleplayer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			if (folder == null) {
				return Optional.empty();
			}
			return Optional.of(new Context(ContextIds.singleplayer(folder.toString()), singleplayer.getWorldData().getLevelName()));
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null || server.ip == null || server.ip.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new Context(ContextIds.multiplayer(server.ip), server.name));
	}
}
