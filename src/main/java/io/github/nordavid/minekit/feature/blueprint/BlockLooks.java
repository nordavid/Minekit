package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How blocks look, taken from the game's own block models and textures (so resource packs apply): for each side a 16 x
 * 16 picture, tinted like in the game (grass, leaves, redstone), its average color, and the block's box in sixteenths
 * of a block. Client thread only; results are cached until the models are reloaded.
 */
public final class BlockLooks {
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");
	static final int SIZE = 16;
	private static final Direction[] DIRECTIONS = Direction.values();

	/**
	 * @param box    minX, minY, minZ, maxX, maxY, maxZ in sixteenths of a block
	 * @param faces  per side (by {@link Direction} ordinal): 16 x 16 ARGB texels, row by row, or {@code null}
	 * @param colors per side: the average color of the picture, or of the whole block if the side has none
	 * @param solid  a full, opaque cube that hides its neighbors' faces
	 */
	public record Look(int[] box, int[][] faces, int[] colors, boolean solid) {
	}

	private static final Map<BlockState, Look> LOOKS = new IdentityHashMap<>();
	private static final Map<Identifier, int @Nullable []> TEXTURES = new HashMap<>();
	private static @Nullable BlockStateModelSet cachedFor;

	private BlockLooks() {
	}

	/** The look of a block state; {@code null} for {@code null} (a block this version does not know). */
	public static @Nullable Look of(@Nullable BlockState state) {
		if (state == null) {
			return null;
		}
		Minecraft minecraft = Minecraft.getInstance();
		BlockStateModelSet models = minecraft.getModelManager().getBlockStateModelSet();
		if (models != cachedFor) {
			LOOKS.clear();
			TEXTURES.clear();
			cachedFor = models;
		}
		Look look = LOOKS.get(state);
		if (look == null) {
			try {
				look = compute(minecraft, models, state);
			} catch (RuntimeException e) {
				// A model this code does not understand: fall back to the map color rather than fail the view.
				LOGGER.warn("Could not read the model of {}", state, e);
				look = flat(state);
			}
			LOOKS.put(state, look);
		}
		return look;
	}

	private static Look compute(Minecraft minecraft, BlockStateModelSet models, BlockState state) {
		BlockStateModel model = models.get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(42L), parts);
		int[][] faces = new int[6][];
		boolean translucent = false;
		for (Direction direction : DIRECTIONS) {
			BakedQuad quad = quadFacing(parts, direction);
			if (quad != null) {
				faces[direction.ordinal()] = texture(minecraft, quad.materialInfo().sprite(), tint(minecraft, state, quad));
				translucent |= quad.materialInfo().layer().translucent();
			}
		}
		boolean anyFace = false;
		for (int[] face : faces) {
			anyFace |= face != null;
		}
		if (!anyFace) {
			// Chests, signs, beds and the like are drawn by the game in other ways; their particle texture stands in.
			int[] particle = texture(minecraft, model.particleMaterial().sprite(), -1);
			if (particle != null) {
				for (int i = 0; i < 6; i++) {
					faces[i] = particle;
				}
			}
		}
		int[] box = box(state);
		int whole = average(faces);
		if (whole == 0) {
			whole = LayerTexture.blockColor(state);
		}
		int[] colors = new int[6];
		boolean opaque = !translucent;
		for (int i = 0; i < 6; i++) {
			int color = faces[i] == null ? 0 : average(new int[][] {faces[i]});
			colors[i] = color == 0 ? whole : color;
			opaque &= faces[i] != null && fullyOpaque(faces[i]);
		}
		boolean fullBox = box[0] == 0 && box[1] == 0 && box[2] == 0 && box[3] == SIZE && box[4] == SIZE && box[5] == SIZE;
		return new Look(box, faces, colors, fullBox && opaque);
	}

	/** A look from the map color only, for models that cannot be read. */
	private static Look flat(BlockState state) {
		int color = LayerTexture.blockColor(state);
		int[] colors = {color, color, color, color, color, color};
		return new Look(box(state), new int[6][], colors, false);
	}

	/** The quad on a side: one culled to that side, else the largest one facing that way. */
	private static @Nullable BakedQuad quadFacing(List<BlockStateModelPart> parts, Direction direction) {
		for (BlockStateModelPart part : parts) {
			List<BakedQuad> quads = part.getQuads(direction);
			if (!quads.isEmpty()) {
				return quads.getFirst();
			}
		}
		BakedQuad best = null;
		double bestArea = 0;
		for (BlockStateModelPart part : parts) {
			for (BakedQuad quad : part.getQuads(null)) {
				if (quad.direction() != direction) {
					continue;
				}
				double area = area(quad);
				if (area > bestArea) {
					bestArea = area;
					best = quad;
				}
			}
		}
		return best;
	}

	private static double area(BakedQuad quad) {
		var a = quad.position(0);
		var b = quad.position(1);
		var c = quad.position(2);
		double ux = b.x() - a.x();
		double uy = b.y() - a.y();
		double uz = b.z() - a.z();
		double vx = c.x() - a.x();
		double vy = c.y() - a.y();
		double vz = c.z() - a.z();
		double cx = uy * vz - uz * vy;
		double cy = uz * vx - ux * vz;
		double cz = ux * vy - uy * vx;
		return Math.sqrt(cx * cx + cy * cy + cz * cz);
	}

	private static int tint(Minecraft minecraft, BlockState state, BakedQuad quad) {
		if (!quad.materialInfo().isTinted()) {
			return -1;
		}
		BlockTintSource source = minecraft.getBlockColors().getTintSource(state, quad.materialInfo().tintIndex());
		return source == null ? -1 : source.color(state);
	}

	/** The first frame of a sprite's texture, scaled to 16 x 16 and multiplied by {@code tint}; {@code null} if missing. */
	private static int @Nullable [] texture(Minecraft minecraft, TextureAtlasSprite sprite, int tint) {
		Identifier name = sprite.contents().name();
		int[] texels = TEXTURES.computeIfAbsent(name, id -> load(minecraft, id));
		if (texels == null || tint == -1) {
			return texels;
		}
		int[] tinted = new int[texels.length];
		for (int i = 0; i < texels.length; i++) {
			tinted[i] = multiply(texels[i], tint);
		}
		return tinted;
	}

	private static int @Nullable [] load(Minecraft minecraft, Identifier sprite) {
		Identifier file = Identifier.fromNamespaceAndPath(sprite.getNamespace(), "textures/" + sprite.getPath() + ".png");
		Optional<Resource> resource = minecraft.getResourceManager().getResource(file);
		if (resource.isEmpty()) {
			return null;
		}
		try (InputStream input = resource.get().open(); NativeImage image = NativeImage.read(input)) {
			// Animated textures are a column of square frames: the first one is used.
			int side = Math.min(image.getWidth(), image.getHeight());
			if (side <= 0) {
				return null;
			}
			int[] texels = new int[SIZE * SIZE];
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					texels[x + y * SIZE] = image.getPixel(x * side / SIZE, y * side / SIZE);
				}
			}
			return texels;
		} catch (IOException | RuntimeException e) {
			LOGGER.debug("Could not read texture {}", file, e);
			return null;
		}
	}

	/** The block's box, from its shape without a world around it; a thin plate for blocks without a shape. */
	private static int[] box(BlockState state) {
		VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		if (shape.isEmpty()) {
			return new int[] {0, 0, 0, SIZE, 1, SIZE};
		}
		AABB bounds = shape.bounds();
		int minX = sixteenths(bounds.minX);
		int minY = sixteenths(bounds.minY);
		int minZ = sixteenths(bounds.minZ);
		return new int[] {minX, minY, minZ, Math.max(minX + 1, sixteenths(bounds.maxX)), Math.max(minY + 1, sixteenths(bounds.maxY)),
				Math.max(minZ + 1, sixteenths(bounds.maxZ))};
	}

	private static int sixteenths(double value) {
		return Math.clamp(Math.round(value * SIZE), 0, SIZE);
	}

	/**
	 * Average color of some pictures, weighted by how opaque each texel is, or 0 if nothing is visible. Its alpha is the
	 * average opacity, so glass comes out mostly see-through.
	 */
	static int average(int[][] faces) {
		long red = 0;
		long green = 0;
		long blue = 0;
		long weight = 0;
		long texels = 0;
		for (int[] face : faces) {
			if (face == null) {
				continue;
			}
			texels += face.length;
			for (int texel : face) {
				int alpha = texel >>> 24;
				red += (long) ((texel >> 16) & 255) * alpha;
				green += (long) ((texel >> 8) & 255) * alpha;
				blue += (long) (texel & 255) * alpha;
				weight += alpha;
			}
		}
		if (weight == 0) {
			return 0;
		}
		int alpha = (int) Math.max(1, weight / texels);
		return alpha << 24 | (int) (red / weight) << 16 | (int) (green / weight) << 8 | (int) (blue / weight);
	}

	private static boolean fullyOpaque(int[] face) {
		for (int texel : face) {
			if (texel >>> 24 != 255) {
				return false;
			}
		}
		return true;
	}

	private static int multiply(int texel, int tint) {
		int red = ((texel >> 16) & 255) * ((tint >> 16) & 255) / 255;
		int green = ((texel >> 8) & 255) * ((tint >> 8) & 255) / 255;
		int blue = (texel & 255) * (tint & 255) / 255;
		return (texel & 0xFF000000) | red << 16 | green << 8 | blue;
	}
}
