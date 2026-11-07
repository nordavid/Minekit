package io.github.nordavid.minekit.feature.blueprint;

import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole blueprint in the world: every block still to place as see-through faces in its own color, shaded by side
 * like Minecraft shades blocks, and wrong blocks in red. Only the outside of the structure is drawn ({@link GhostShell},
 * computed on a background thread), blocks that are already correct are left out, and at most {@link #MAX_FACES} faces
 * are drawn, the ones nearest the player. Real blocks hide the faces behind them.
 */
final class WorldGhost {
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");
	/** Faces drawn per tick at most; beyond that the nearest are chosen again when the player has moved a bit. */
	static final int MAX_FACES = 12_000;
	private static final int RESELECT_DISTANCE = 6;
	private static final int ALPHA_TO_PLACE = 0x90;
	private static final int ALPHA_UNCHECKED = 0x48;
	private static final int COLOR_WRONG = 0xA0E5716F;
	/** Brightness of each side (down, up, north, south, west, east), as in Minecraft's block shading. */
	private static final double[] SHADE = {0.5, 1.0, 0.8, 0.8, 0.6, 0.6};
	private static final Direction[] DIRECTIONS = Direction.values();
	private static final double INSET = 0.004;
	private static final double OUTSET = 0.012;
	private static final int[] FULL_BOX = {0, 0, 0, 16, 16, 16};

	private record Key(Blueprint blueprint, Placement placement, BlueprintSettings.LayerRange range) {
	}

	private @Nullable Key key;
	private long @Nullable [] shell;
	private long @Nullable [] selected;
	/** Per palette entry, in the world's orientation: the color of each side and the block's box in sixteenths. */
	private int[][] colors = new int[0][];
	private int[][] boxes = new int[0][];
	private @Nullable BlockPos selectedAround;
	private boolean selecting;
	private int generation;
	private int drawnLastTick;
	private long drawNanos;
	private final Map<Integer, GizmoStyle> styles = new HashMap<>();

	/** Faces drawn in the last tick, for tests. */
	int drawnLastTick() {
		return drawnLastTick;
	}

	/** How long the last tick took to draw, for tests. */
	long drawNanos() {
		return drawNanos;
	}

	/** Forgets the structure, for example when the world view is switched off. */
	void reset() {
		key = null;
		shell = null;
		selected = null;
		selectedAround = null;
		selecting = false;
		generation++;
		drawnLastTick = 0;
	}

	/** Draws this tick's gizmos; starts computing the faces in the background when the structure changed. */
	void draw(ResolvedBlueprint resolved, BlueprintProgress progress, Placement placement, BlockPos player) {
		Blueprint blueprint = resolved.blueprint();
		Key next = new Key(blueprint, placement, progress.range());
		if (!next.equals(key)) {
			reset();
			key = next;
			prepare(resolved, next, player);
		} else if (shell != null && shell.length > MAX_FACES && !selecting && selectedAround != null
				&& selectedAround.distManhattan(player) >= RESELECT_DISTANCE) {
			select(next, shell, player);
		}
		long[] faces = selected;
		drawnLastTick = 0;
		if (faces == null) {
			return;
		}
		long start = System.nanoTime();
		int sizeX = blueprint.sizeX();
		int layer = sizeX * blueprint.sizeZ();
		for (long face : faces) {
			int index = GhostShell.index(face);
			Status status = progress.status(index);
			if (status == Status.CORRECT) {
				continue;
			}
			int y = index / layer;
			int x = index % layer % sizeX;
			int z = index % layer / sizeX;
			int wx = placement.worldX(blueprint, x, z);
			int wy = placement.worldY(y);
			int wz = placement.worldZ(blueprint, x, z);
			int side = GhostShell.worldSide(GhostShell.side(face), placement);
			int palette = blueprint.paletteIndexAt(index);
			int color;
			double grow;
			int[] box = FULL_BOX;
			if (status == Status.WRONG) {
				// A real block stands there: draw just outside its cell so the red is seen on its surface.
				color = COLOR_WRONG;
				grow = OUTSET;
			} else {
				int base = colors[palette][side];
				int alpha = status == Status.UNCHECKED ? ALPHA_UNCHECKED : ALPHA_TO_PLACE;
				color = (IsometricView.shade(base, SHADE[side]) & 0xFFFFFF) | alpha << 24;
				grow = -INSET;
				box = boxes[palette];
			}
			Gizmos.rect(new Vec3(wx + box[0] / 16.0 - grow, wy + box[1] / 16.0 - grow, wz + box[2] / 16.0 - grow),
					new Vec3(wx + box[3] / 16.0 + grow, wy + box[4] / 16.0 + grow, wz + box[5] / 16.0 + grow), DIRECTIONS[side],
					styles.computeIfAbsent(color, GizmoStyle::fill));
			drawnLastTick++;
		}
		drawNanos = System.nanoTime() - start;
	}

	private void prepare(ResolvedBlueprint resolved, Key next, BlockPos player) {
		Blueprint blueprint = resolved.blueprint();
		int size = blueprint.palette().size();
		boolean[] drawn = new boolean[size];
		boolean[] solid = new boolean[size];
		colors = new int[size][];
		boxes = new int[size][];
		for (int i = 0; i < size; i++) {
			if (blueprint.palette().get(i).isAir()) {
				continue;
			}
			// Turned like in the world, so its sides and box match the world's directions.
			BlockLooks.Look look = BlockLooks.of(resolved.expected(i, next.placement()));
			drawn[i] = true;
			if (look == null) {
				solid[i] = true;
				colors[i] = new int[] {LayerTexture.UNKNOWN, LayerTexture.UNKNOWN, LayerTexture.UNKNOWN, LayerTexture.UNKNOWN, LayerTexture.UNKNOWN,
						LayerTexture.UNKNOWN};
				boxes[i] = FULL_BOX;
			} else {
				solid[i] = look.solid();
				colors[i] = look.colors();
				boxes[i] = look.box();
			}
		}
		int ticket = generation;
		selecting = true;
		CompletableFuture.supplyAsync(() -> {
			long[] faces = GhostShell.faces(blueprint, next.range(), drawn, solid);
			return new long[][] {faces, GhostShell.nearest(faces, blueprint, next.placement(), player.getX(), player.getY(), player.getZ(), MAX_FACES)};
		}, Util.backgroundExecutor()).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
			if (ticket != generation) {
				return;
			}
			selecting = false;
			if (error != null) {
				LOGGER.warn("Could not prepare the blueprint view", error);
				return;
			}
			shell = result[0];
			selected = result[1];
			selectedAround = player;
		}));
	}

	private void select(Key current, long[] faces, BlockPos player) {
		int ticket = generation;
		selecting = true;
		CompletableFuture.supplyAsync(() -> GhostShell.nearest(faces, current.blueprint(), current.placement(), player.getX(), player.getY(),
				player.getZ(), MAX_FACES), Util.backgroundExecutor()).whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
					if (ticket != generation) {
						return;
					}
					selecting = false;
					if (error == null) {
						selected = result;
						selectedAround = player;
					}
				}));
	}
}
