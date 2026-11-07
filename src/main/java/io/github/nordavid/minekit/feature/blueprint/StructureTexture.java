package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntUnaryOperator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The whole blueprint as one 3D picture ({@link IsometricView}): the layers up to a cut, seen from one of four corners,
 * in map colors tinted by build status like {@link LayerTexture}. Large blueprints take a moment to paint, so the
 * picture is painted on a background thread and only uploaded on the client thread; the previous picture stays until
 * the new one is ready.
 */
final class StructureTexture implements AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");
	private static final Identifier ID = Identifier.fromNamespaceAndPath("minekit", "blueprint_structure");
	/** Pixels of the largest picture (2048 x 2048): bounds the memory of a huge, flat blueprint. */
	private static final long MAX_PIXELS = 4L << 20;
	private static final Status[] STATUSES = Status.values();

	/** Everything a picture depends on except the build status, which changes all the time. */
	private record Key(ResolvedBlueprint resolved, int layers, @Nullable Placement placement, int turn, int tile) {
	}

	private @Nullable DynamicTexture texture;
	private int width;
	private int height;
	private int @Nullable [] cells;
	private @Nullable Key shown;
	private @Nullable Key pending;
	private int generation;
	private boolean tooLarge;
	private boolean closed;
	private int drawX;
	private int drawY;
	private int drawWidth;
	private int drawHeight;

	/**
	 * Asks for a picture of layers {@code 0} to {@code layers - 1}. Nothing is painted if the same picture is shown or
	 * on its way, unless {@code refresh} is set (the build status changed).
	 *
	 * @param turn quarter turns of the camera around the structure: 0 looks from the south-east corner once placed
	 * @param maxWidth width of the box it will be drawn into, in screen pixels
	 */
	void request(ResolvedBlueprint resolved, int layers, @Nullable Placement placement, @Nullable BlueprintProgress progress, int turn,
			int maxWidth, int maxHeight, boolean refresh) {
		Blueprint blueprint = resolved.blueprint();
		int across = placement == null ? blueprint.sizeX() : placement.worldSizeX(blueprint);
		int deep = placement == null ? blueprint.sizeZ() : placement.worldSizeZ(blueprint);
		int sizeU = turn % 2 == 0 ? across : deep;
		int sizeV = turn % 2 == 0 ? deep : across;
		int tile = IsometricView.tileFor(sizeU, sizeV, layers, maxWidth, maxHeight, MAX_PIXELS);
		Key key = new Key(resolved, layers, placement, turn, tile);
		if (!refresh && (key.equals(pending) || (pending == null && key.equals(shown)))) {
			return;
		}
		int ticket = ++generation;
		if (tile == 0) {
			tooLarge = true;
			pending = null;
			shown = key;
			release();
			return;
		}
		tooLarge = false;
		pending = key;
		IsometricView.Volume volume = volume(resolved, layers, placement, progress, turn, sizeU, sizeV);
		CompletableFuture.supplyAsync(() -> IsometricView.paint(volume, tile), Util.backgroundExecutor())
				.whenComplete((picture, error) -> Minecraft.getInstance().execute(() -> {
					if (closed || ticket != generation) {
						return;
					}
					pending = null;
					if (error != null) {
						LOGGER.warn("Could not draw the blueprint {}", resolved.file(), error);
						return;
					}
					upload(picture);
					shown = key;
				}));
	}

	/** The cells of the blueprint in view space. Reads everything it needs now, so painting can run elsewhere. */
	private static IsometricView.Volume volume(ResolvedBlueprint resolved, int layers, @Nullable Placement placement,
			@Nullable BlueprintProgress progress, int turn, int sizeU, int sizeV) {
		Blueprint blueprint = resolved.blueprint();
		int across = turn % 2 == 0 ? sizeU : sizeV;
		int deep = turn % 2 == 0 ? sizeV : sizeU;
		int[] column = new int[sizeU * sizeV];
		for (int z = 0; z < blueprint.sizeZ(); z++) {
			for (int x = 0; x < blueprint.sizeX(); x++) {
				int a = placement == null ? x : placement.worldX(blueprint, x, z) - placement.x();
				int b = placement == null ? z : placement.worldZ(blueprint, x, z) - placement.z();
				int u;
				int v;
				switch (turn) {
					case 0 -> {
						u = a;
						v = b;
					}
					case 1 -> {
						u = deep - 1 - b;
						v = a;
					}
					case 2 -> {
						u = across - 1 - a;
						v = deep - 1 - b;
					}
					default -> {
						u = b;
						v = across - 1 - a;
					}
				}
				column[u + v * sizeU] = x + z * blueprint.sizeX();
			}
		}
		IsometricView.Look[] looks = new IsometricView.Look[blueprint.palette().size()];
		for (int i = 0; i < looks.length; i++) {
			if (!blueprint.palette().get(i).isAir()) {
				// Placed, the blocks are turned like in the world; the view then turns around them.
				BlockState state = placement == null ? resolved.state(i) : resolved.expected(i, placement);
				BlockLooks.Look look = BlockLooks.of(state);
				looks[i] = look == null ? IsometricView.Look.cube(LayerTexture.UNKNOWN) : view(look, turn);
			}
		}
		byte[] status = progress == null ? null : progress.statusSnapshot();
		BlueprintSettings.LayerRange range = progress == null ? null : progress.range();
		int stride = blueprint.sizeX() * blueprint.sizeZ();
		IntUnaryOperator overlay = cell -> status == null || !range.contains(cell / stride) || looks[blueprint.paletteIndexAt(cell)] == null ? 0
				: LayerTexture.overlay(STATUSES[status[cell]]);
		return new IsometricView.Volume(sizeU, sizeV, layers, column, stride, cell -> looks[blueprint.paletteIndexAt(cell)], overlay);
	}

	/** Sides of the world the view's +u and +v faces show, for each turn of the camera. */
	private static final Direction[] PLUS_U = {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};
	private static final Direction[] PLUS_V = {Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST};

	/** A block's look turned into view space: its box, its top turned with the view, and the two sides the camera sees. */
	static IsometricView.Look view(BlockLooks.Look look, int turn) {
		int[] b = look.box();
		int s = BlockLooks.SIZE;
		int[] box = switch (turn) {
			case 0 -> new int[] {b[0], b[1], b[2], b[3], b[4], b[5]};
			case 1 -> new int[] {s - b[5], b[1], b[0], s - b[2], b[4], b[3]};
			case 2 -> new int[] {s - b[3], b[1], s - b[5], s - b[0], b[4], s - b[2]};
			default -> new int[] {b[2], b[1], s - b[3], b[5], b[4], s - b[0]};
		};
		int up = Direction.UP.ordinal();
		IsometricView.Face top = new IsometricView.Face(turnTop(look.faces()[up], turn), look.colors()[up]);
		IsometricView.Face plusU = face(look, PLUS_U[turn]);
		IsometricView.Face plusV = face(look, PLUS_V[turn]);
		return new IsometricView.Look(box, top, plusU, plusV, look.solid());
	}

	private static IsometricView.Face face(BlockLooks.Look look, Direction side) {
		return new IsometricView.Face(look.faces()[side.ordinal()], look.colors()[side.ordinal()]);
	}

	/** The top texture as the turned camera sees it: (s, t) in view space read from (x, z) in the world. */
	private static int @Nullable [] turnTop(int @Nullable [] texels, int turn) {
		if (texels == null || turn == 0) {
			return texels;
		}
		int n = BlockLooks.SIZE;
		int[] turned = new int[texels.length];
		for (int t = 0; t < n; t++) {
			for (int s = 0; s < n; s++) {
				int x;
				int z;
				switch (turn) {
					case 1 -> {
						x = t;
						z = n - 1 - s;
					}
					case 2 -> {
						x = n - 1 - s;
						z = n - 1 - t;
					}
					default -> {
						x = n - 1 - t;
						z = s;
					}
				}
				turned[s + t * n] = texels[x + z * n];
			}
		}
		return turned;
	}

	private void upload(IsometricView.Picture picture) {
		if (texture == null || picture.width() != width || picture.height() != height) {
			release();
			width = picture.width();
			height = picture.height();
			texture = new DynamicTexture(() -> "minekit blueprint structure", new NativeImage(width, height, true));
			Minecraft.getInstance().getTextureManager().register(ID, texture);
		}
		NativeImage image = texture.getPixels();
		int[] pixels = picture.pixels();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(x, y, pixels[x + y * width]);
			}
		}
		texture.upload();
		cells = picture.cells();
	}

	/** True once a picture is uploaded. */
	boolean isReady() {
		return texture != null;
	}

	/** True while the first picture is being painted. */
	boolean isPainting() {
		return texture == null && pending != null;
	}

	/** True if the structure has too many columns to paint. */
	boolean isTooLarge() {
		return tooLarge;
	}

	/** Draws the picture centered in a box, as large as fits. */
	void draw(GuiGraphicsExtractor graphics, int x, int y, int maxWidth, int maxHeight) {
		if (texture == null) {
			drawWidth = 0;
			return;
		}
		double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
		drawWidth = Math.max(1, (int) (width * scale));
		drawHeight = Math.max(1, (int) (height * scale));
		drawX = x + (maxWidth - drawWidth) / 2;
		drawY = y + (maxHeight - drawHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, ID, drawX, drawY, 0, 0, drawWidth, drawHeight, width, height, width, height);
	}

	/** The blueprint cell (a {@link Blueprint#index} value) drawn under a screen position, or -1. */
	int cellAt(double mouseX, double mouseY) {
		if (cells == null || drawWidth == 0 || mouseX < drawX || mouseY < drawY || mouseX >= drawX + drawWidth || mouseY >= drawY + drawHeight) {
			return -1;
		}
		int px = (int) ((mouseX - drawX) * width / drawWidth);
		int py = (int) ((mouseY - drawY) * height / drawHeight);
		return px < width && py < height ? cells[px + py * width] : -1;
	}

	private void release() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
			texture = null;
			cells = null;
		}
	}

	@Override
	public void close() {
		closed = true;
		release();
	}
}
