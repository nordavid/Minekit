package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole blueprint in the world as ghost blocks: every block still to place is drawn with its own model and
 * textures, translucent, like building mods show their schematics (ADR 0009). Correct blocks are not drawn, wrong ones
 * get a red box, blocks not checked yet are fainter.
 *
 * <p>Each frame the quads of the nearest blocks are handed to the game's renderer (Fabric's {@code COLLECT_SUBMITS}),
 * at most {@link #MAX_QUADS}. Which blocks are nearest, and which of their sides touch a solid neighbor and can be left
 * out, is computed on a background thread when the structure changes or the player has moved a few blocks.
 */
final class GhostBlocks {
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");
	/** Quads written per frame at most: about the cost of a busy particle scene. */
	static final int MAX_QUADS = 24_000;
	/** Blocks considered per frame: more than the quads allow for a filled structure, whose inside is left out. */
	static final int MAX_BLOCKS = 16_000;
	private static final int RESELECT_DISTANCE = 6;
	private static final int MAX_MARKS = 2_000;
	/** The game's render type for translucent things on a texture atlas, per atlas (there is one for blocks). */
	private static final java.util.Map<Identifier, RenderType> RENDER_TYPES = new java.util.HashMap<>();
	private static final int COLOR_WRONG_STROKE = 0xD0E5716F;
	private static final int COLOR_WRONG_FILL = 0x38E5716F;
	private static final int COLOR_UNKNOWN_STROKE = 0xD0B04CA0;

	private record Key(Blueprint blueprint, Placement placement, BlueprintSettings.LayerRange range) {
	}

	/** The blocks drawn, nearest first, with their position relative to the placement and their covered sides. */
	private record Selection(int[] cells, int[] offsets, byte[] covered) {
	}

	private @Nullable Key key;
	private int @Nullable [] candidates;
	private @Nullable Selection selection;
	private GhostModels.Model[] models = new GhostModels.Model[0];
	private boolean[] unknown = new boolean[0];
	private @Nullable BlueprintProgress progress;
	private @Nullable BlockPos selectedAround;
	private boolean selecting;
	private boolean visible;
	private int alpha = 0x80;
	private int generation;
	private int quadsLastFrame;
	private long emitNanos;

	/** Quads written in the last frame, for tests. */
	int quadsLastFrame() {
		return quadsLastFrame;
	}

	/** How long writing them took, for tests. */
	long emitNanos() {
		return emitNanos;
	}

	/** Stops drawing; what was prepared is kept for when the ghost is shown again. */
	void hide() {
		visible = false;
		quadsLastFrame = 0;
	}

	/**
	 * Called every client tick while the whole structure is shown: keeps the selection current and draws the boxes of
	 * wrong and unknown blocks.
	 *
	 * @param opacity percent
	 */
	void update(ResolvedBlueprint resolved, BlueprintProgress current, Placement placement, BlockPos player, int opacity) {
		Blueprint blueprint = resolved.blueprint();
		Key next = new Key(blueprint, placement, current.range());
		if (!next.equals(key)) {
			generation++;
			key = next;
			selection = null;
			candidates = null;
			selectedAround = null;
			prepare(resolved, next, player);
		} else if (candidates != null && !selecting && selectedAround != null && selectedAround.distManhattan(player) >= RESELECT_DISTANCE) {
			select(next, candidates, player);
		}
		progress = current;
		alpha = Math.clamp(Math.round(opacity * 2.55f), 16, 255);
		visible = true;
		drawMarks(current);
	}

	/** Red boxes around wrong blocks and purple ones around blocks this version does not know, nearest first. */
	private void drawMarks(BlueprintProgress current) {
		Selection shown = selection;
		Key shownKey = key;
		if (shown == null || shownKey == null) {
			return;
		}
		Placement placement = shownKey.placement();
		int marks = 0;
		for (int i = 0; i < shown.cells().length && marks < MAX_MARKS; i++) {
			int cell = shown.cells()[i];
			Status status = current.status(cell);
			boolean unknownBlock = unknown[shownKey.blueprint().paletteIndexAt(cell)];
			if (status != Status.WRONG && !unknownBlock) {
				continue;
			}
			BlockPos pos = new BlockPos(placement.x() + shown.offsets()[i * 3], placement.y() + shown.offsets()[i * 3 + 1],
					placement.z() + shown.offsets()[i * 3 + 2]);
			Gizmos.cuboid(pos, 0.02f, unknownBlock ? GizmoStyle.stroke(COLOR_UNKNOWN_STROKE, 1.5f)
					: GizmoStyle.strokeAndFill(COLOR_WRONG_STROKE, 1.0f, COLOR_WRONG_FILL));
			marks++;
		}
	}

	/** Prepares the models (turned like in the world) and starts choosing the blocks in the background. */
	private void prepare(ResolvedBlueprint resolved, Key next, BlockPos player) {
		Blueprint blueprint = resolved.blueprint();
		int size = blueprint.palette().size();
		boolean[] drawn = new boolean[size];
		boolean[] solid = new boolean[size];
		models = new GhostModels.Model[size];
		unknown = new boolean[size];
		for (int i = 0; i < size; i++) {
			models[i] = GhostModels.EMPTY;
			if (blueprint.palette().get(i).isAir()) {
				continue;
			}
			BlockState state = resolved.expected(i, next.placement());
			drawn[i] = true;
			unknown[i] = state == null;
			BlockLooks.Look look = BlockLooks.of(state);
			solid[i] = look != null && look.solid();
			models[i] = GhostModels.of(state);
		}
		int ticket = generation;
		selecting = true;
		CompletableFuture.supplyAsync(() -> GhostSelection.candidates(blueprint, next.range(), drawn), Util.backgroundExecutor())
				.thenApplyAsync(cells -> {
					candidatesFound(ticket, cells);
					return choose(next, cells, solid, player);
				}, Util.backgroundExecutor())
				.whenComplete((chosen, error) -> Minecraft.getInstance().execute(() -> chosen(ticket, chosen, error, player)));
		this.solid = solid;
	}

	private boolean[] solid = new boolean[0];

	private void candidatesFound(int ticket, int[] cells) {
		Minecraft.getInstance().execute(() -> {
			if (ticket == generation) {
				candidates = cells;
			}
		});
	}

	private void select(Key current, int[] cells, BlockPos player) {
		int ticket = generation;
		boolean[] solidNow = solid;
		selecting = true;
		CompletableFuture.supplyAsync(() -> choose(current, cells, solidNow, player), Util.backgroundExecutor())
				.whenComplete((chosen, error) -> Minecraft.getInstance().execute(() -> chosen(ticket, chosen, error, player)));
	}

	private void chosen(int ticket, @Nullable Selection chosen, @Nullable Throwable error, BlockPos player) {
		if (ticket != generation) {
			return;
		}
		selecting = false;
		if (error != null || chosen == null) {
			LOGGER.warn("Could not prepare the blueprint ghost", error);
			return;
		}
		selection = chosen;
		selectedAround = player;
	}

	/** The nearest blocks with their offsets from the placement corner and their covered sides. Background thread. */
	private static Selection choose(Key key, int[] cells, boolean[] solid, BlockPos player) {
		Blueprint blueprint = key.blueprint();
		Placement placement = key.placement();
		int[] nearest = GhostSelection.nearest(cells, blueprint, placement, player.getX(), player.getY(), player.getZ(), MAX_BLOCKS);
		int sizeX = blueprint.sizeX();
		int layer = sizeX * blueprint.sizeZ();
		int[] offsets = new int[nearest.length * 3];
		byte[] covered = new byte[nearest.length];
		for (int i = 0; i < nearest.length; i++) {
			int index = nearest[i];
			int x = index % layer % sizeX;
			int z = index % layer / sizeX;
			offsets[i * 3] = placement.worldX(blueprint, x, z) - placement.x();
			offsets[i * 3 + 1] = index / layer;
			offsets[i * 3 + 2] = placement.worldZ(blueprint, x, z) - placement.z();
			covered[i] = (byte) GhostSelection.coveredSides(blueprint, placement, key.range(), solid, index);
		}
		return new Selection(nearest, offsets, covered);
	}

	/** Fabric's {@code COLLECT_SUBMITS}: hands this frame's ghost blocks to the game's renderer. */
	void submit(LevelRenderContext context) {
		Selection shown = selection;
		Key shownKey = key;
		BlueprintProgress current = progress;
		if (!visible || shown == null || shownKey == null || current == null || current.blueprint() != shownKey.blueprint()) {
			return;
		}
		Identifier atlas = GhostModels.atlas();
		if (atlas == null) {
			return;
		}
		RenderType renderType = RENDER_TYPES.computeIfAbsent(atlas, RenderTypes::entityTranslucent);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Placement placement = shownKey.placement();
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		// Positions are written relative to the placement corner, so they stay small and precise far from the origin.
		poseStack.translate(placement.x() - camera.x, placement.y() - camera.y, placement.z() - camera.z);
		GhostModels.Model[] shownModels = models;
		int shownAlpha = alpha;
		context.submitNodeCollector().submitCustomGeometry(poseStack, renderType,
				(pose, buffer) -> emit(pose, buffer, shown, shownKey.blueprint(), current, shownModels, shownAlpha));
		poseStack.popPose();
	}

	private void emit(PoseStack.Pose pose, VertexConsumer buffer, Selection shown, Blueprint blueprint, BlueprintProgress current,
			GhostModels.Model[] shownModels, int shownAlpha) {
		long start = System.nanoTime();
		Matrix4f matrix = pose.pose();
		Vector3f position = new Vector3f();
		Vector3f normal = new Vector3f();
		int faint = shownAlpha * 2 / 3;
		int quads = 0;
		int[] cells = shown.cells();
		blocks:
		for (int i = 0; i < cells.length; i++) {
			int cell = cells[i];
			Status status = current.status(cell);
			if (status == Status.CORRECT || status == Status.WRONG) {
				continue;
			}
			GhostModels.Model model = shownModels[blueprint.paletteIndexAt(cell)];
			int covered = shown.covered()[i];
			float ox = shown.offsets()[i * 3];
			float oy = shown.offsets()[i * 3 + 1];
			float oz = shown.offsets()[i * 3 + 2];
			int colorAlpha = (status == Status.UNCHECKED ? faint : shownAlpha) << 24;
			float[] positions = model.positions();
			float[] uvs = model.uvs();
			float[] normals = model.normals();
			for (int q = 0; q < model.quads(); q++) {
				int cull = model.cull()[q];
				if (cull >= 0 && (covered & 1 << cull) != 0) {
					continue;
				}
				int color = colorAlpha | model.tints()[q];
				pose.transformNormal(normals[q * 3], normals[q * 3 + 1], normals[q * 3 + 2], normal);
				for (int vertex = 0; vertex < 4; vertex++) {
					int p = q * 12 + vertex * 3;
					matrix.transformPosition(ox + positions[p], oy + positions[p + 1], oz + positions[p + 2], position);
					buffer.addVertex(position.x(), position.y(), position.z(), color, uvs[q * 8 + vertex * 2], uvs[q * 8 + vertex * 2 + 1],
							OverlayTexture.NO_OVERLAY, LightCoordsUtil.FULL_BRIGHT, normal.x(), normal.y(), normal.z());
				}
				if (++quads >= MAX_QUADS) {
					break blocks;
				}
			}
		}
		quadsLastFrame = quads;
		emitNanos = System.nanoTime() - start;
	}
}
