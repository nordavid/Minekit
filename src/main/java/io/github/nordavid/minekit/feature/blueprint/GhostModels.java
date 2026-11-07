package io.github.nordavid.minekit.feature.blueprint;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The quads of block models, prepared for drawing ghost blocks: positions within the block, texture coordinates on the
 * block atlas, normals, tint colors and the side each quad is culled on. Client thread only; cached until the models
 * are reloaded.
 */
final class GhostModels {
	/**
	 * @param positions 12 per quad: x, y, z of its four corners, 0 to 1 within the block
	 * @param uvs       8 per quad: u, v of its four corners on the block atlas
	 * @param normals   3 per quad
	 * @param tints     1 per quad: RGB the texture is multiplied with
	 * @param cull      1 per quad: the {@link Direction} ordinal it is hidden on when a solid block is there, or -1
	 */
	record Model(float[] positions, float[] uvs, float[] normals, int[] tints, byte[] cull) {
		int quads() {
			return cull.length;
		}
	}

	static final Model EMPTY = new Model(new float[0], new float[0], new float[0], new int[0], new byte[0]);
	private static final Map<BlockState, Model> MODELS = new IdentityHashMap<>();
	private static @Nullable BlockStateModelSet cachedFor;
	/** The texture atlas the block models' sprites are on, as soon as one model was read. */
	private static @Nullable Identifier atlas;

	private GhostModels() {
	}

	static @Nullable Identifier atlas() {
		return atlas;
	}

	static Model of(@Nullable BlockState state) {
		if (state == null) {
			return EMPTY;
		}
		Minecraft minecraft = Minecraft.getInstance();
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		if (models != cachedFor) {
			MODELS.clear();
			cachedFor = models;
		}
		return MODELS.computeIfAbsent(state, key -> build(minecraft, models, key));
	}

	private static Model build(Minecraft minecraft, BlockStateModelSet models, BlockState state) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		models.get(state).collectParts(RandomSource.create(42L), parts);
		List<BakedQuad> quads = new ArrayList<>();
		List<Byte> cull = new ArrayList<>();
		for (BlockStateModelPart part : parts) {
			for (BakedQuad quad : part.getQuads(null)) {
				quads.add(quad);
				cull.add((byte) -1);
			}
			for (Direction direction : Direction.values()) {
				for (BakedQuad quad : part.getQuads(direction)) {
					quads.add(quad);
					cull.add((byte) direction.ordinal());
				}
			}
		}
		int count = quads.size();
		float[] positions = new float[count * 12];
		float[] uvs = new float[count * 8];
		float[] normals = new float[count * 3];
		int[] tints = new int[count];
		byte[] culled = new byte[count];
		for (int q = 0; q < count; q++) {
			BakedQuad quad = quads.get(q);
			for (int vertex = 0; vertex < 4; vertex++) {
				var position = quad.position(vertex);
				positions[q * 12 + vertex * 3] = position.x();
				positions[q * 12 + vertex * 3 + 1] = position.y();
				positions[q * 12 + vertex * 3 + 2] = position.z();
				long uv = quad.packedUV(vertex);
				uvs[q * 8 + vertex * 2] = UVPair.unpackU(uv);
				uvs[q * 8 + vertex * 2 + 1] = UVPair.unpackV(uv);
			}
			var normal = quad.direction().getUnitVec3f();
			normals[q * 3] = normal.x();
			normals[q * 3 + 1] = normal.y();
			normals[q * 3 + 2] = normal.z();
			tints[q] = tint(minecraft, state, quad);
			atlas = quad.materialInfo().sprite().atlasLocation();
			culled[q] = cull.get(q);
		}
		return new Model(positions, uvs, normals, tints, culled);
	}

	private static int tint(Minecraft minecraft, BlockState state, BakedQuad quad) {
		if (!quad.materialInfo().isTinted()) {
			return 0xFFFFFF;
		}
		BlockTintSource source = minecraft.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
		return source == null ? 0xFFFFFF : source.color(state) & 0xFFFFFF;
	}
}
