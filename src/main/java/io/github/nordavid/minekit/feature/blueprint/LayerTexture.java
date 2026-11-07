package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

/**
 * One layer of a blueprint seen from above, as a small texture: one pixel per block in its map color, tinted by build
 * status once the blueprint is placed. Drawn scaled up, so even a large layer is a single draw call. North is up.
 */
final class LayerTexture implements AutoCloseable {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("minekit", "blueprint_layer");
	static final int EMPTY = 0xFF1A1D22;
	static final int UNKNOWN = 0xFFB04CA0;
	static final int CORRECT = 0xFF6BCB8B;
	static final int WRONG = 0xFFE5716F;
	/** Blocks without a map color (glass, torches, ...): a pale blue-gray instead of black. */
	static final int SEE_THROUGH = 0xFFB9D3DE;

	private @Nullable DynamicTexture texture;
	/** Largest plan drawn with textures, in pixels per side. */
	private static final int MAX_TEXTURED_SIZE = 1024;
	private int width;
	private int height;
	private int texelsPerBlock = 1;
	private int drawnX;
	private int drawnY;

	int width() {
		return width;
	}

	int height() {
		return height;
	}

	/**
	 * Paints layer {@code y}. With a placement and progress, the layer is shown as it stands in the world (turned and
	 * mirrored), correct blocks in green, wrong ones in red, missing ones in their own color but dimmed, unchecked ones
	 * darker still. Without, in the blueprint's own orientation.
	 */
	void paint(ResolvedBlueprint resolved, int y, @Nullable Placement placement, @Nullable BlueprintProgress progress) {
		Blueprint blueprint = resolved.blueprint();
		int blocksWide = placement == null ? blueprint.sizeX() : placement.worldSizeX(blueprint);
		int blocksDeep = placement == null ? blueprint.sizeZ() : placement.worldSizeZ(blueprint);
		// Small plans show the blocks' textures from above, large ones a pixel per block.
		texelsPerBlock = Math.max(blocksWide, blocksDeep) * BlockLooks.SIZE <= MAX_TEXTURED_SIZE ? BlockLooks.SIZE : 1;
		int newWidth = blocksWide * texelsPerBlock;
		int newHeight = blocksDeep * texelsPerBlock;
		if (texture == null || newWidth != width || newHeight != height) {
			close();
			width = newWidth;
			height = newHeight;
			texture = new DynamicTexture(() -> "minekit blueprint layer", new NativeImage(width, height, true));
			Minecraft.getInstance().getTextureManager().register(ID, texture);
		}
		NativeImage image = texture.getPixels();
		for (int z = 0; z < blueprint.sizeZ(); z++) {
			for (int x = 0; x < blueprint.sizeX(); x++) {
				int px = placement == null ? x : placement.worldX(blueprint, x, z) - placement.x();
				int pz = placement == null ? z : placement.worldZ(blueprint, x, z) - placement.z();
				int index = blueprint.index(x, y, z);
				if (texelsPerBlock == 1) {
					image.setPixel(px, pz, color(resolved, index, progress));
				} else {
					paintBlock(image, px * texelsPerBlock, pz * texelsPerBlock, resolved, index, placement, progress);
				}
			}
		}
		texture.upload();
	}

	/** One block from above: its top texture within its footprint (a torch is small), tinted by build status. */
	private static void paintBlock(NativeImage image, int left, int top, ResolvedBlueprint resolved, int index, @Nullable Placement placement,
			@Nullable BlueprintProgress progress) {
		int palette = resolved.blueprint().paletteIndexAt(index);
		BlockLooks.Look look = null;
		if (!resolved.blueprint().palette().get(palette).isAir()) {
			look = BlockLooks.of(placement == null ? resolved.state(palette) : resolved.expected(palette, placement));
		}
		int overlay = progress != null && progress.isTracked(index) ? overlay(progress.status(index)) : 0;
		boolean unknown = look == null && !resolved.blueprint().palette().get(palette).isAir();
		int[] texels = look == null ? null : look.faces()[net.minecraft.core.Direction.UP.ordinal()];
		for (int tz = 0; tz < BlockLooks.SIZE; tz++) {
			for (int tx = 0; tx < BlockLooks.SIZE; tx++) {
				int color;
				if (unknown) {
					color = UNKNOWN;
				} else if (look == null || tx < look.box()[0] || tx >= look.box()[3] || tz < look.box()[2] || tz >= look.box()[5]) {
					color = EMPTY;
				} else {
					int texel = texels == null ? look.colors()[net.minecraft.core.Direction.UP.ordinal()] : texels[tx + tz * BlockLooks.SIZE];
					color = texel >>> 24 < 128 ? EMPTY : texel | 0xFF000000;
				}
				if (overlay >>> 24 != 0 && color != EMPTY) {
					color = mix(color, overlay, (overlay >>> 24) / 255.0);
				}
				image.setPixel(left + tx, top + tz, color);
			}
		}
	}

	/** Texture pixels per block: 16 for small plans (textures), 1 for large ones. */
	int texelsPerBlock() {
		return texelsPerBlock;
	}

	private static int color(ResolvedBlueprint resolved, int index, @Nullable BlueprintProgress progress) {
		int palette = resolved.blueprint().paletteIndexAt(index);
		if (resolved.blueprint().palette().get(palette).isAir()) {
			return EMPTY;
		}
		int base = blockColor(resolved.state(palette));
		return progress == null || !progress.isTracked(index) ? base : tint(base, progress.status(index));
	}

	/** The map color of a block; {@link #UNKNOWN} for blocks this version does not know. */
	static int blockColor(@Nullable BlockState state) {
		if (state == null) {
			return UNKNOWN;
		}
		return seeThrough(state) ? SEE_THROUGH : 0xFF000000 | state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
	}

	/** Blocks without a map color: maps leave them out, so they are mostly glass, plants on walls, torches and the like. */
	static boolean seeThrough(BlockState state) {
		return state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) == MapColor.NONE;
	}

	/**
	 * The build status as a color laid over a block, its alpha the share: correct green, wrong and unknown fully red or
	 * purple, missing dimmed, unchecked dimmed more.
	 */
	static int overlay(Status status) {
		return switch (status) {
			case CORRECT -> share(CORRECT, 0.65);
			case WRONG -> WRONG;
			case MISSING -> share(EMPTY, 0.45);
			case UNCHECKED -> share(EMPTY, 0.75);
			case UNKNOWN -> UNKNOWN;
		};
	}

	private static int share(int color, double share) {
		return (int) Math.round(share * 255) << 24 | (color & 0xFFFFFF);
	}

	/** A block color tinted by build status: correct green, wrong red, missing dimmed, unchecked dimmed more. */
	static int tint(int base, Status status) {
		return switch (status) {
			case CORRECT -> mix(base, CORRECT, 0.65);
			case WRONG -> WRONG;
			case MISSING -> mix(base, EMPTY, 0.45);
			case UNCHECKED -> mix(base, EMPTY, 0.75);
			case UNKNOWN -> UNKNOWN;
		};
	}

	static int mix(int a, int b, double share) {
		int red = (int) (((a >> 16) & 255) * (1 - share) + ((b >> 16) & 255) * share);
		int green = (int) (((a >> 8) & 255) * (1 - share) + ((b >> 8) & 255) * share);
		int blue = (int) ((a & 255) * (1 - share) + (b & 255) * share);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	/**
	 * Draws the texture centered in a box, as large as fits while keeping square blocks; returns the block size used.
	 */
	double draw(GuiGraphicsExtractor graphics, int x, int y, int maxWidth, int maxHeight) {
		if (texture == null) {
			return 0;
		}
		double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
		int drawWidth = Math.max(1, (int) (width * scale));
		int drawHeight = Math.max(1, (int) (height * scale));
		drawnX = x + (maxWidth - drawWidth) / 2;
		drawnY = y + (maxHeight - drawHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ID, drawnX, drawnY, 0, 0, drawWidth, drawHeight, width, height, width, height);
		return scale;
	}

	/** Left edge of the last draw, for finding the block under the mouse. */
	int drawnX() {
		return drawnX;
	}

	int drawnY() {
		return drawnY;
	}

	@Override
	public void close() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
			texture = null;
		}
	}
}
