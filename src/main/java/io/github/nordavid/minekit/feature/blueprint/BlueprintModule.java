package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.goal.GoalBridge;
import io.github.nordavid.minekit.core.input.KeybindManager;
import io.github.nordavid.minekit.core.module.MinekitModule;
import io.github.nordavid.minekit.core.module.ModuleContext;
import io.github.nordavid.minekit.core.module.ModuleMetadata;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.screen.DashboardPages;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import io.github.nordavid.minekit.core.world.WorldContext;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * B5/B6 Blueprints and building tools: imports {@code .litematic} and {@code .schem} files, shows them as a building
 * guide in the world, compares them with the loaded world, lists the materials, and measures distances. Minekit never
 * places a block; the player builds.
 */
public final class BlueprintModule implements MinekitModule {
	public static final ModuleMetadata METADATA = new ModuleMetadata("blueprints",
			"minekit.module.blueprints.name", "minekit.module.blueprints.description", true);
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");

	/** Positions compared with the world per tick: a large structure is checked a slice at a time. */
	static final int CHECKS_PER_TICK = 4096;
	/** "Around me" shows the missing and wrong blocks within a sphere of this radius around the player. */
	static final int GHOST_RADIUS = 10;
	static final int MAX_GHOSTS = 1500;

	static final int COLOR_BOX = 0xFF4CC9A6;
	static final int COLOR_RANGE = 0xFFE8B75A;
	static final int COLOR_MISSING_STROKE = 0x904CC9A6;
	static final int COLOR_MISSING_FILL = 0x284CC9A6;
	static final int COLOR_WRONG_STROKE = 0xD0E5716F;
	static final int COLOR_WRONG_FILL = 0x38E5716F;
	static final int COLOR_NEXT = 0xFFE8B75A;
	static final int COLOR_MEASURE = 0xFFE8EAED;

	private final Path directory;
	private final JsonFileStore<BlueprintSettings> settings;
	private final KeybindManager keybinds;
	private final NotificationService notifications;
	private final GhostBlocks ghost = new GhostBlocks();
	private final QuickActionRegistry quickActions;
	private final DashboardPages dashboardPages;
	private final GoalBridge goals;
	private final StorageBridge storage;
	private ModuleContext context;

	private @Nullable ResolvedBlueprint active;
	private @Nullable String loading;
	private @Nullable String failedFile;
	private @Nullable Component failureReason;
	private @Nullable BlueprintProgress progress;
	private @Nullable Placement progressPlacement;
	private @Nullable String progressWorld;
	private @Nullable BlockPos nextBlock;
	private @Nullable BlockPos measureA;
	private @Nullable BlockPos measureB;
	private long ticks;

	public BlueprintModule(Path directory, JsonFileStore<BlueprintSettings> settings, KeybindManager keybinds, NotificationService notifications,
			QuickActionRegistry quickActions, DashboardPages dashboardPages, GoalBridge goals, StorageBridge storage) {
		this.directory = Objects.requireNonNull(directory, "directory");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.keybinds = Objects.requireNonNull(keybinds, "keybinds");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
		this.quickActions = Objects.requireNonNull(quickActions, "quickActions");
		this.dashboardPages = Objects.requireNonNull(dashboardPages, "dashboardPages");
		this.goals = Objects.requireNonNull(goals, "goals");
		this.storage = Objects.requireNonNull(storage, "storage");
	}

	@Override
	public ModuleMetadata metadata() {
		return METADATA;
	}

	@Override
	public void initialize(ModuleContext moduleContext) {
		this.context = moduleContext;
		keybinds.register("blueprints", InputConstants.UNKNOWN.getValue(), () -> {
			if (isEnabled()) {
				openScreen();
			}
		});
		keybinds.register("measure", InputConstants.UNKNOWN.getValue(), () -> {
			if (isEnabled()) {
				measurePoint();
			}
		});
		quickActions.register(new QuickAction("open_blueprints", Component.translatable("minekit.action.open_blueprints"),
				"blueprint schematic litematic schem bauplan bauen build", this::unavailableReason, this::openScreen));
		quickActions.register(new QuickAction("blueprint_place_here", Component.translatable("minekit.action.blueprint_place_here"),
				"blueprint place here move bauplan hier platzieren", this::placeUnavailableReason, this::placeInFront));
		quickActions.register(new QuickAction("blueprint_toggle_show", Component.translatable("minekit.action.blueprint_toggle_show"),
				"blueprint show hide ghost bauplan zeigen verbergen", this::unavailableReason, () -> setShow(!settings.current().show())));
		quickActions.register(new QuickAction("blueprint_toggle_whole", Component.translatable("minekit.action.blueprint_toggle_whole"),
				"blueprint whole structure around ghost view bauplan ganz bauwerk ansicht", this::unavailableReason,
				() -> setWhole(!settings.current().whole())));
		quickActions.register(new QuickAction("measure_point", Component.translatable("minekit.action.measure_point"),
				"measure distance size messen abstand größe", this::unavailableReason, this::measurePoint));
		quickActions.register(new QuickAction("measure_clear", Component.translatable("minekit.action.measure_clear"),
				"measure clear messen löschen", () -> measureA == null ? Optional.of(Component.translatable("minekit.measure.nothing"))
						: unavailableReason(), this::measureClear));
		dashboardPages.register(new BlueprintDashboardPage(this));
		ClientTickEvents.END_CLIENT_TICK.register(client -> context.guard("tick", () -> tick(client)));
		LevelRenderEvents.COLLECT_SUBMITS.register(render -> context.guard("render", () -> {
			if (isEnabled()) {
				ghost.submit(render);
			}
		}));
	}

	@Override
	public void onDisable() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.screen() instanceof BlueprintScreen) {
			minecraft.gui.setScreen(null);
		}
	}

	public boolean isEnabled() {
		return context != null && context.isEnabled();
	}

	public JsonFileStore<BlueprintSettings> settings() {
		return settings;
	}

	NotificationService notifications() {
		return notifications;
	}

	GoalBridge goals() {
		return goals;
	}

	StorageBridge storage() {
		return storage;
	}

	public Path directory() {
		return directory;
	}

	/** The blueprint shown, once it is loaded. */
	public Optional<ResolvedBlueprint> active() {
		return Optional.ofNullable(active).filter(resolved -> resolved.file().equals(settings.current().active()));
	}

	public boolean isLoading() {
		return loading != null;
	}

	/** Progress of the active blueprint in this world, if it is placed here. */
	public Optional<BlueprintProgress> progress() {
		return Optional.ofNullable(progress);
	}

	public Optional<BlockPos> nextBlock() {
		return Optional.ofNullable(nextBlock);
	}

	public Optional<BlockPos> measureA() {
		return Optional.ofNullable(measureA);
	}

	public Optional<BlockPos> measureB() {
		return Optional.ofNullable(measureB);
	}

	void update(UnaryOperator<BlueprintSettings> change) {
		settings.update(change);
		if (settings.lastSaveFailed()) {
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		}
	}

	public Optional<Component> unavailableReason() {
		if (!isEnabled()) {
			return Optional.of(Component.translatable("minekit.blueprint.unavailable.module_off"));
		}
		if (Minecraft.getInstance().player == null) {
			return Optional.of(Component.translatable("minekit.blueprint.unavailable.no_world"));
		}
		return Optional.empty();
	}

	private Optional<Component> placeUnavailableReason() {
		return unavailableReason().or(() -> active().isPresent() ? Optional.empty()
				: Optional.of(Component.translatable("minekit.blueprint.unavailable.none_active")));
	}

	public void openScreen() {
		Optional<Component> reason = unavailableReason();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return;
		}
		Minecraft.getInstance().gui.setScreen(new BlueprintScreen(this));
	}

	// ----------------------------------------------------------------------------------------------------------- files

	/** Blueprint files in the folder, by name. */
	public List<String> files() {
		if (!Files.isDirectory(directory)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(directory)) {
			return stream.filter(Files::isRegularFile).filter(BlueprintFiles::isSupported).map(path -> path.getFileName().toString())
					.sorted(String.CASE_INSENSITIVE_ORDER).toList();
		} catch (IOException e) {
			LOGGER.warn("Could not list {}", directory, e);
			return List.of();
		}
	}

	/** Copies dropped files into the blueprint folder. The originals are never changed. Returns the names copied. */
	List<String> importFiles(List<Path> dropped) {
		List<String> imported = new java.util.ArrayList<>();
		long maxBytes = settings.current().limits().maxFileBytes();
		for (Path source : dropped) {
			String name = source.getFileName().toString();
			if (!BlueprintFiles.isSupported(source) || !Files.isRegularFile(source)) {
				notifications.warning(Component.translatable("minekit.blueprint.import.unsupported", name));
				continue;
			}
			try {
				if (Files.size(source) > maxBytes) {
					notifications.warning(Component.translatable("minekit.blueprint.import.too_large", name, settings.current().maxFileMegabytes()));
					continue;
				}
				Files.createDirectories(directory);
				Path target = uniqueTarget(name);
				Files.copy(source, target);
				imported.add(target.getFileName().toString());
			} catch (IOException e) {
				LOGGER.warn("Could not import {}", source, e);
				notifications.error(Component.translatable("minekit.blueprint.import.failed", name));
			}
		}
		if (!imported.isEmpty()) {
			notifications.success(Component.translatable("minekit.blueprint.import.done", imported.size()));
		}
		return imported;
	}

	private Path uniqueTarget(String name) {
		String sanitized = name.replaceAll("[^A-Za-z0-9 ._()-]", "_");
		int dot = sanitized.lastIndexOf('.');
		String base = sanitized.substring(0, dot);
		String extension = sanitized.substring(dot).toLowerCase(Locale.ROOT);
		Path target = directory.resolve(base + extension);
		for (int i = 2; Files.exists(target); i++) {
			target = directory.resolve(base + " (" + i + ")" + extension);
		}
		return target;
	}

	/** Makes a file the active blueprint and loads it in the background. */
	public void select(String file) {
		update(current -> current.withActive(file));
		failedFile = null;
		failureReason = null;
	}

	void delete(String file) {
		try {
			Files.deleteIfExists(directory.resolve(file));
		} catch (IOException e) {
			notifications.error(Component.translatable("minekit.blueprint.delete_failed", file));
			return;
		}
		update(current -> current.without(file));
		if (active != null && active.file().equals(file)) {
			active = null;
			resetProgress();
		}
		notifications.info(Component.translatable("minekit.blueprint.deleted", file));
	}

	/** The active file, if it could not be loaded. */
	public Optional<String> failedFile() {
		return Optional.ofNullable(failedFile);
	}

	/** Why the active file could not be loaded, if it could not. */
	public Optional<Component> failureReason() {
		return Optional.ofNullable(failureReason);
	}

	private void loadActive() {
		String file = settings.current().active();
		if (file == null || file.equals(loading) || file.equals(failedFile) || (active != null && active.file().equals(file))) {
			return;
		}
		Path path = directory.resolve(file);
		BlueprintLimits limits = settings.current().limits();
		loading = file;
		// Parsing a large file takes time: never on the render thread.
		CompletableFuture.supplyAsync(() -> {
			try {
				return BlueprintFiles.read(path, limits);
			} catch (BlueprintException e) {
				throw new java.util.concurrent.CompletionException(e);
			}
		}, Util.backgroundExecutor()).whenComplete((blueprint, error) -> Minecraft.getInstance().execute(() -> {
			loading = null;
			if (!file.equals(settings.current().active())) {
				return;
			}
			if (error != null) {
				failedFile = file;
				Throwable cause = error instanceof java.util.concurrent.CompletionException && error.getCause() != null ? error.getCause() : error;
				if (cause instanceof BlueprintException reason) {
					failureReason = Component.translatable(reason.translationKey(), reason.arguments());
					LOGGER.warn("Could not load blueprint {}: {} {}", file, reason.reason(), java.util.Arrays.toString(reason.arguments()));
				} else {
					failureReason = Component.translatable("minekit.blueprint.error.read_failed", file);
					LOGGER.error("Could not load blueprint {}", file, cause);
				}
				// The blueprint screen shows the whole reason too, in case the card fades before it is read.
				notifications.error(Component.translatable("minekit.blueprint.load_failed", file, failureReason));
				return;
			}
			active = ResolvedBlueprint.resolve(file, blueprint);
			resetProgress();
			if (!active.unknownBlocks().isEmpty()) {
				notifications.warning(Component.translatable("minekit.blueprint.unknown_blocks", active.unknownBlocks().size()));
			}
		}));
	}

	// ------------------------------------------------------------------------------------------------------- placement

	public @Nullable String world() {
		return Minecraft.getInstance().player == null ? null : WorldContext.current(Minecraft.getInstance()).map(WorldContext::id).orElse(null);
	}

	/** Placement of the active blueprint in this world, if it is placed here. */
	public Optional<Placement> placement() {
		String world = world();
		String file = settings.current().active();
		return world == null || file == null ? Optional.empty() : Optional.ofNullable(settings.current().placement(file, world));
	}

	public void setPlacement(@Nullable Placement placement) {
		String world = world();
		String file = settings.current().active();
		if (world != null && file != null) {
			update(current -> current.withPlacement(file, world, placement));
		}
	}

	/** Places the active blueprint two blocks in front of the player, its corner at the player's feet height. */
	public void placeInFront() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || active().isEmpty()) {
			return;
		}
		Direction facing = player.getDirection();
		BlockPos corner = player.blockPosition().relative(facing, 2);
		Placement current = placement().orElse(Placement.ORIGIN);
		setPlacement(current.at(corner.getX(), corner.getY(), corner.getZ()));
		setShow(true);
	}

	void setShow(boolean show) {
		update(current -> current.withShow(show));
	}

	/** Shows the whole structure in the world, or only what is missing or wrong around the player. */
	void setWhole(boolean whole) {
		update(current -> current.withWhole(whole));
		notifications.info(Component.translatable(whole ? "minekit.blueprint.whole_on" : "minekit.blueprint.whole_off"));
	}

	/** Quads of the ghost blocks written in the last frame, for tests. */
	public int ghostQuadsDrawn() {
		return ghost.quadsLastFrame();
	}

	/** Ghost sides left out because a real solid block touches them, for tests. */
	public int ghostSidesAgainstRealBlocks() {
		return ghost.worldCoveredFaces();
	}

	/** Microseconds writing them took, for tests. */
	public long ghostDrawMicros() {
		return ghost.emitNanos() / 1000;
	}

	/** Switches the ghost to the next opacity on offer. */
	void cycleGhostOpacity() {
		int[] offered = BlueprintSettings.GHOST_OPACITIES;
		int current = settings.current().ghostOpacity();
		int next = offered[0];
		for (int i = 0; i < offered.length; i++) {
			if (offered[i] == current) {
				next = offered[(i + 1) % offered.length];
			}
		}
		int chosen = next;
		update(settings -> settings.withGhostOpacity(chosen));
	}

	public void setRange(BlueprintSettings.@Nullable LayerRange range) {
		String file = settings.current().active();
		if (file != null) {
			update(current -> current.withRange(file, range));
		}
	}

	private void resetProgress() {
		ghost.hide();
		progress = null;
		progressPlacement = null;
		progressWorld = null;
		nextBlock = null;
	}

	// ----------------------------------------------------------------------------------------------------------- measure

	/** Sets point A, then point B, at the block the player looks at; a third use starts over. */
	void measurePoint() {
		Minecraft minecraft = Minecraft.getInstance();
		HitResult hit = minecraft.hitResult;
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			notifications.info(Component.translatable("minekit.measure.look_at_block"));
			return;
		}
		BlockPos pos = blockHit.getBlockPos();
		if (measureA == null || measureB != null) {
			measureA = pos;
			measureB = null;
			notifications.info(Component.translatable("minekit.measure.point_a", pos.getX(), pos.getY(), pos.getZ()));
			return;
		}
		measureB = pos;
		notifications.info(measureText(measureA, measureB));
	}

	void measureClear() {
		measureA = null;
		measureB = null;
	}

	/** "5 × 3 × 7 blocks (105) · 8.6 blocks apart" for the box spanned by two corners, both included. */
	static Component measureText(BlockPos a, BlockPos b) {
		int width = Math.abs(a.getX() - b.getX()) + 1;
		int height = Math.abs(a.getY() - b.getY()) + 1;
		int length = Math.abs(a.getZ() - b.getZ()) + 1;
		double distance = Math.sqrt(a.distSqr(b));
		return Component.translatable("minekit.measure.result", width, height, length, (long) width * height * length,
				String.format(Locale.ROOT, "%.1f", distance));
	}

	// ------------------------------------------------------------------------------------------------------------ tick

	private void tick(Minecraft minecraft) {
		ticks++;
		if (!isEnabled()) {
			return;
		}
		loadActive();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			resetProgress();
			return;
		}
		drawMeasure();
		ResolvedBlueprint resolved = active().orElse(null);
		Placement placement = placement().orElse(null);
		String world = world();
		if (resolved == null || placement == null) {
			resetProgress();
			return;
		}
		BlueprintSettings.LayerRange range = settings.current().range(resolved.file(), resolved.blueprint().sizeY());
		if (progress == null || progress.blueprint() != resolved.blueprint() || !placement.equals(progressPlacement)
				|| !Objects.equals(world, progressWorld) || !progress.range().equals(range)) {
			progress = new BlueprintProgress(resolved.blueprint(), range);
			progressPlacement = placement;
			progressWorld = world;
			nextBlock = null;
		}
		for (int index : progress.nextToCheck(CHECKS_PER_TICK)) {
			progress.set(index, resolved.check(level, index, placement));
		}
		if (ticks % 10 == 0) {
			nextBlock = findNextBlock(resolved, progress, placement, player.blockPosition());
		}
		if (settings.current().show()) {
			drawBlueprint(resolved, progress, placement, level, player.blockPosition(), range);
		} else {
			ghost.hide();
		}
	}

	/** The nearest missing or wrong block of the lowest layer that is not finished. */
	private static @Nullable BlockPos findNextBlock(ResolvedBlueprint resolved, BlueprintProgress progress, Placement placement, BlockPos player) {
		int layer = progress.lowestOpenLayer();
		if (layer < 0) {
			return null;
		}
		Blueprint blueprint = resolved.blueprint();
		int layerSize = blueprint.sizeX() * blueprint.sizeZ();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int index = layer * layerSize; index < (layer + 1) * layerSize; index++) {
			Status status = progress.status(index);
			if (status == Status.MISSING || status == Status.WRONG) {
				BlockPos pos = resolved.worldPos(index, placement);
				double distance = pos.distSqr(player);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos;
				}
			}
		}
		return best;
	}

	/**
	 * The building guide for this tick: the box, the layers being built, either the whole structure as ghost blocks
	 * ({@link GhostBlocks}, with red boxes around wrong blocks) or the missing (teal) and wrong (red) blocks within a
	 * sphere around the player, and the next block. Around the player the world is read every tick, so the guide reacts
	 * at once to placed blocks.
	 */
	private void drawBlueprint(ResolvedBlueprint resolved, BlueprintProgress progress, Placement placement, ClientLevel level, BlockPos player,
			BlueprintSettings.LayerRange range) {
		Blueprint blueprint = resolved.blueprint();
		int sizeX = placement.worldSizeX(blueprint);
		int sizeZ = placement.worldSizeZ(blueprint);
		Gizmos.cuboid(new AABB(placement.x(), placement.y(), placement.z(), placement.x() + sizeX, placement.y() + blueprint.sizeY(),
				placement.z() + sizeZ), GizmoStyle.stroke(COLOR_BOX, 2.0f));
		if (range.from() > 0 || range.to() < blueprint.sizeY() - 1) {
			Gizmos.cuboid(new AABB(placement.x(), placement.y() + range.from(), placement.z(), placement.x() + sizeX, placement.y() + range.to() + 1,
					placement.z() + sizeZ), GizmoStyle.stroke(COLOR_RANGE, 1.5f));
		}
		boolean whole = settings.current().whole();
		if (whole) {
			ghost.update(resolved, progress, placement, player, settings.current().ghostOpacity(), level);
		} else {
			ghost.hide();
		}
		int drawn = 0;
		int radiusSquared = GHOST_RADIUS * GHOST_RADIUS;
		for (int dy = -GHOST_RADIUS; dy <= GHOST_RADIUS && drawn < MAX_GHOSTS; dy++) {
			for (int dz = -GHOST_RADIUS; dz <= GHOST_RADIUS && drawn < MAX_GHOSTS; dz++) {
				for (int dx = -GHOST_RADIUS; dx <= GHOST_RADIUS && drawn < MAX_GHOSTS; dx++) {
					if (dx * dx + dy * dy + dz * dz > radiusSquared) {
						continue;
					}
					int[] local = placement.toLocal(blueprint, player.getX() + dx, player.getY() + dy, player.getZ() + dz);
					if (local == null) {
						continue;
					}
					int index = blueprint.index(local[0], local[1], local[2]);
					if (!progress.isTracked(index)) {
						continue;
					}
					// Near the player the world is read every tick, so the guide reacts at once to placed blocks.
					Status status = resolved.check(level, index, placement);
					progress.set(index, status);
					BlockPos pos = new BlockPos(player.getX() + dx, player.getY() + dy, player.getZ() + dz);
					if (status == Status.MISSING && !whole) {
						Gizmos.cuboid(pos, 0.02f, GizmoStyle.strokeAndFill(COLOR_MISSING_STROKE, 1.0f, COLOR_MISSING_FILL));
						drawn++;
					} else if (status == Status.WRONG && !whole) {
						// With the whole structure shown, the ghost draws the wrong blocks itself.
						Gizmos.cuboid(pos, 0.02f, GizmoStyle.strokeAndFill(COLOR_WRONG_STROKE, 1.0f, COLOR_WRONG_FILL));
						drawn++;
					}
				}
			}
		}
		if (nextBlock != null) {
			Gizmos.cuboid(nextBlock, 0.04f, GizmoStyle.strokeAndFill(COLOR_NEXT, 2.5f, 0x40E8B75A));
			int[] local = placement.toLocal(blueprint, nextBlock.getX(), nextBlock.getY(), nextBlock.getZ());
			if (local != null) {
				int palette = blueprint.paletteIndex(local[0], local[1], local[2]);
				var state = resolved.state(palette);
				String name = state == null ? blueprint.palette().get(palette).id() : state.getBlock().getName().getString();
				Gizmos.billboardTextOverBlock(name, nextBlock, 0, COLOR_NEXT, 0.6f);
			}
		}
	}

	private void drawMeasure() {
		if (measureA == null) {
			return;
		}
		Gizmos.cuboid(measureA, 0.03f, GizmoStyle.strokeAndFill(COLOR_MEASURE, 2.0f, 0x30E8EAED));
		if (measureB == null) {
			return;
		}
		Gizmos.cuboid(measureB, 0.03f, GizmoStyle.strokeAndFill(COLOR_MEASURE, 2.0f, 0x30E8EAED));
		int minX = Math.min(measureA.getX(), measureB.getX());
		int minY = Math.min(measureA.getY(), measureB.getY());
		int minZ = Math.min(measureA.getZ(), measureB.getZ());
		AABB box = new AABB(minX, minY, minZ, Math.max(measureA.getX(), measureB.getX()) + 1, Math.max(measureA.getY(), measureB.getY()) + 1,
				Math.max(measureA.getZ(), measureB.getZ()) + 1);
		Gizmos.cuboid(box, GizmoStyle.stroke(COLOR_MEASURE, 1.5f));
		Gizmos.line(net.minecraft.world.phys.Vec3.atCenterOf(measureA), net.minecraft.world.phys.Vec3.atCenterOf(measureB), COLOR_NEXT, 2.0f);
		BlockPos top = BlockPos.containing(box.getCenter().x, box.maxY, box.getCenter().z);
		Gizmos.billboardTextOverBlock(measureText(measureA, measureB).getString(), top.below(), 0, COLOR_MEASURE, 0.6f);
	}
}
