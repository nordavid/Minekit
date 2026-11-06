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

	private @Nullable DynamicTexture texture;
	private int width;
	private int height;

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
		int newWidth = placement == null ? blueprint.sizeX() : placement.worldSizeX(blueprint);
		int newHeight = placement == null ? blueprint.sizeZ() : placement.worldSizeZ(blueprint);
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
				image.setPixel(px, pz, color(resolved, blueprint.index(x, y, z), progress));
			}
		}
		texture.upload();
	}

	private static int color(ResolvedBlueprint resolved, int index, @Nullable BlueprintProgress progress) {
		int palette = resolved.blueprint().paletteIndexAt(index);
		if (resolved.blueprint().palette().get(palette).isAir()) {
			return EMPTY;
		}
		BlockState state = resolved.state(palette);
		if (state == null) {
			return UNKNOWN;
		}
		int base = 0xFF000000 | state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col;
		if (progress == null || !progress.isTracked(index)) {
			return base;
		}
		Status status = progress.status(index);
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

	/** Draws the texture into a box, as large as fits while keeping square blocks; returns the block size used. */
	double draw(GuiGraphicsExtractor graphics, int x, int y, int maxWidth, int maxHeight) {
		if (texture == null) {
			return 0;
		}
		double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
		int drawWidth = Math.max(1, (int) (width * scale));
		int drawHeight = Math.max(1, (int) (height * scale));
		graphics.blit(RenderPipelines.GUI_TEXTURED, ID, x, y, 0, 0, drawWidth, drawHeight, width, height, width, height);
		return scale;
	}

	@Override
	public void close() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
			texture = null;
		}
	}
}
