package io.github.nordavid.minekit.feature.blueprint;

import java.util.Arrays;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * Paints a block volume seen from above at an angle (2:1 isometric), so the whole structure can be seen at once. Every
 * block is drawn as its box (a slab half as high, a repeater flat) with its three visible faces: the game's textures
 * when the blocks are large enough on screen, their average colors otherwise, shaded per side. Pure: no Minecraft
 * classes, so it can run on a background thread and be tested.
 *
 * <p>The volume is given in view space: {@code u} runs to the lower right of the picture, {@code v} to the lower left,
 * {@code y} up; the camera looks from the {@code +u, +v} corner, so the top, {@code +u} and {@code +v} faces of a box
 * are visible. Blocks are painted from far to near (by {@code u + y + v}, the depth along the viewing direction), so
 * nearer blocks cover farther ones. Blocks whose three visible faces are covered by solid neighbors are skipped.
 */
final class IsometricView {
	/** The smallest block width in pixels is {@code 2 * MIN_TILE}; below that the shape is lost. */
	static final int MIN_TILE = 2;
	static final int MAX_TILE = 48;
	/** From this size on, faces show their textures; smaller ones their average color. */
	static final int TEXTURE_TILE = 8;
	static final int TEXELS = 16;
	static final double TOP_SHADE = 1.0;
	static final double LEFT_SHADE = 0.80;
	static final double RIGHT_SHADE = 0.64;
	private static final double EDGE_SHADE = 0.86;
	/** Edges are drawn only on plain colored faces large enough that they do not turn into a grid of lines. */
	private static final int MIN_EDGE_TILE = 6;

	/** A face: 16 x 16 ARGB texels, row by row from its top left as seen (alpha 0: nothing there), or one color. */
	record Face(int @Nullable [] texels, int color) {
		static Face of(int color) {
			return new Face(null, color);
		}
	}

	/**
	 * How one block is drawn, in view space.
	 *
	 * @param box   minU, minY, minV, maxU, maxY, maxV in sixteenths of a block
	 * @param solid a full, opaque cube that hides the faces of its neighbors
	 */
	record Look(int[] box, Face top, Face plusU, Face plusV, boolean solid) {
		static final int[] FULL = {0, 0, 0, TEXELS, TEXELS, TEXELS};

		/** A full cube in one color; solid unless the color is see-through. */
		static Look cube(int color) {
			return new Look(FULL, Face.of(color), Face.of(color), Face.of(color), color >>> 24 == 255);
		}
	}

	/** The painted picture: ARGB pixels (alpha 0 where nothing is) and, per pixel, the cell painted there last, or -1. */
	record Picture(int width, int height, int[] pixels, int[] cells, int tile) {
		int cellAt(int x, int y) {
			return x < 0 || y < 0 || x >= width || y >= height ? -1 : cells[x + y * width];
		}
	}

	/**
	 * A volume in view space.
	 *
	 * @param column      for each column {@code u + v * sizeU}, the cell id of its lowest block
	 * @param layerStride how much the cell id grows from one layer to the next
	 * @param look        how a cell looks, or {@code null} for nothing
	 * @param overlay     a color laid over a cell, its alpha the share (0: none), for example to show build status
	 */
	record Volume(int sizeU, int sizeV, int layers, int[] column, int layerStride, IntFunction<@Nullable Look> look,
			IntUnaryOperator overlay) {
		Volume {
			if (sizeU <= 0 || sizeV <= 0 || layers <= 0 || column.length != sizeU * sizeV) {
				throw new IllegalArgumentException("invalid volume " + sizeU + "x" + layers + "x" + sizeV);
			}
		}

		int cell(int u, int y, int v) {
			return column[u + v * sizeU] + y * layerStride;
		}

		boolean solid(int u, int y, int v) {
			if (u >= sizeU || y >= layers || v >= sizeV) {
				return false;
			}
			Look neighbor = look.apply(cell(u, y, v));
			return neighbor != null && neighbor.solid();
		}
	}

	private IsometricView() {
	}

	static int width(int sizeU, int sizeV, int tile) {
		return (sizeU + sizeV) * tile;
	}

	static int height(int sizeU, int sizeV, int layers, int tile) {
		return layers * tile + (sizeU + sizeV) * tile / 2;
	}

	/**
	 * The largest even block size that fits the picture into {@code maxWidth} x {@code maxHeight} pixels; the smallest
	 * size if none fits; 0 if even the smallest picture would have more than {@code maxPixels} pixels.
	 */
	static int tileFor(int sizeU, int sizeV, int layers, int maxWidth, int maxHeight, long maxPixels) {
		if ((long) width(sizeU, sizeV, MIN_TILE) * height(sizeU, sizeV, layers, MIN_TILE) > maxPixels) {
			return 0;
		}
		for (int tile = MAX_TILE; tile > MIN_TILE; tile -= 2) {
			if (width(sizeU, sizeV, tile) <= maxWidth && height(sizeU, sizeV, layers, tile) <= maxHeight) {
				return tile;
			}
		}
		return MIN_TILE;
	}

	static Picture paint(Volume volume, int tile) {
		if (tile < MIN_TILE || tile % 2 != 0) {
			throw new IllegalArgumentException("tile must be even and at least " + MIN_TILE + ": " + tile);
		}
		int sizeU = volume.sizeU();
		int sizeV = volume.sizeV();
		int layers = volume.layers();
		int width = width(sizeU, sizeV, tile);
		int height = height(sizeU, sizeV, layers, tile);
		Canvas canvas = new Canvas(width, height, sizeV * tile, layers * tile, tile);
		int maxDepth = (sizeU - 1) + (sizeV - 1) + (layers - 1);
		for (int depth = 0; depth <= maxDepth; depth++) {
			for (int y = Math.max(0, depth - (sizeU - 1) - (sizeV - 1)); y <= Math.min(layers - 1, depth); y++) {
				int rest = depth - y;
				for (int u = Math.max(0, rest - (sizeV - 1)); u <= Math.min(sizeU - 1, rest); u++) {
					int v = rest - u;
					int cell = volume.cell(u, y, v);
					Look look = volume.look().apply(cell);
					if (look == null) {
						continue;
					}
					if (volume.solid(u + 1, y, v) && volume.solid(u, y + 1, v) && volume.solid(u, y, v + 1)) {
						continue;
					}
					canvas.box(cell, u, y, v, look, volume.overlay().applyAsInt(cell));
				}
			}
		}
		return new Picture(width, height, canvas.pixels, canvas.cells, tile);
	}

	/** The picture being painted, with the projection: block point (U, Y, V) to pixel (x, y). */
	private static final class Canvas {
		final int width;
		final int height;
		final int[] pixels;
		final int[] cells;
		final double originX;
		final double originY;
		final int tile;
		final boolean textured;

		Canvas(int width, int height, int originX, int originY, int tile) {
			this.width = width;
			this.height = height;
			this.pixels = new int[width * height];
			this.cells = new int[width * height];
			Arrays.fill(cells, -1);
			this.originX = originX;
			this.originY = originY;
			this.tile = tile;
			this.textured = tile >= TEXTURE_TILE;
		}

		void box(int cell, int u, int y, int v, Look look, int overlay) {
			int[] b = look.box();
			double u0 = u + b[0] / 16.0;
			double y0 = y + b[1] / 16.0;
			double v0 = v + b[2] / 16.0;
			double u1 = u + b[3] / 16.0;
			double y1 = y + b[4] / 16.0;
			double v1 = v + b[5] / 16.0;
			int left = (int) Math.floor(originX + (u0 - v1) * tile);
			int right = (int) Math.ceil(originX + (u1 - v0) * tile);
			int top = (int) Math.floor(originY + (u0 + v0) * tile / 2.0 - y1 * tile);
			int bottom = (int) Math.ceil(originY + (u1 + v1) * tile / 2.0 - y0 * tile);
			double edge = 1.0 / tile;
			for (int py = Math.max(0, top); py < Math.min(height, bottom); py++) {
				for (int px = Math.max(0, left); px < Math.min(width, right); px++) {
					double x = (px + 0.5 - originX) / tile;
					double screenY = (py + 0.5 - originY) / tile;
					int color = 0;
					boolean drawn = false;
					// Top face at Y = y1: U - V = x, U + V = 2 * (screenY + y1).
					double topU = (x + 2 * (screenY + y1)) / 2;
					double topV = (2 * (screenY + y1) - x) / 2;
					if (topU >= u0 && topU <= u1 && topV >= v0 && topV <= v1) {
						boolean border = topU - u0 < edge || u1 - topU < edge || topV - v0 < edge || v1 - topV < edge;
						color = texel(look.top(), topU - u, topV - v, TOP_SHADE, border);
						drawn = true;
					} else {
						// The +U face at U = u1: V = u1 - x, Y = (u1 + V) / 2 - screenY.
						double sideV = u1 - x;
						double sideY = (u1 + sideV) / 2 - screenY;
						if (sideV >= v0 && sideV <= v1 && sideY >= y0 && sideY <= y1) {
							boolean border = sideV - v0 < edge || v1 - sideV < edge || sideY - y0 < edge || y1 - sideY < edge;
							color = texel(look.plusU(), 1 - (sideV - v), 1 - (sideY - y), RIGHT_SHADE, border);
							drawn = true;
						} else {
							// The +V face at V = v1: U = v1 + x, Y = (U + v1) / 2 - screenY.
							double sideU = v1 + x;
							double frontY = (sideU + v1) / 2 - screenY;
							if (sideU >= u0 && sideU <= u1 && frontY >= y0 && frontY <= y1) {
								boolean border = sideU - u0 < edge || u1 - sideU < edge || frontY - y0 < edge || y1 - frontY < edge;
								color = texel(look.plusV(), sideU - u, 1 - (frontY - y), LEFT_SHADE, border);
								drawn = true;
							}
						}
					}
					if (!drawn || color >>> 24 == 0) {
						continue;
					}
					if (overlay >>> 24 != 0) {
						color = (mix(color, overlay, (overlay >>> 24) / 255.0) & 0xFFFFFF) | (color & 0xFF000000);
					}
					int target = px + py * width;
					pixels[target] = over(color, pixels[target]);
					cells[target] = cell;
				}
			}
		}

		/** The color of a face at (s, t), both 0 to 1 within the block, from the left and from the top as seen. */
		private int texel(Face face, double s, double t, double shade, boolean border) {
			int[] texels = face.texels();
			if (textured && texels != null) {
				int column = Math.clamp((int) (s * TEXELS), 0, TEXELS - 1);
				int row = Math.clamp((int) (t * TEXELS), 0, TEXELS - 1);
				return shade(texels[column + row * TEXELS], shade);
			}
			double factor = border && tile >= MIN_EDGE_TILE ? shade * EDGE_SHADE : shade;
			return shade(face.color(), factor);
		}
	}

	static int shade(int argb, double factor) {
		int red = (int) (((argb >> 16) & 255) * factor);
		int green = (int) (((argb >> 8) & 255) * factor);
		int blue = (int) ((argb & 255) * factor);
		return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
	}

	static int mix(int a, int b, double share) {
		int red = (int) (((a >> 16) & 255) * (1 - share) + ((b >> 16) & 255) * share);
		int green = (int) (((a >> 8) & 255) * (1 - share) + ((b >> 8) & 255) * share);
		int blue = (int) ((a & 255) * (1 - share) + (b & 255) * share);
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	/** Source-over compositing of two non-premultiplied ARGB colors. */
	static int over(int source, int destination) {
		int sourceAlpha = source >>> 24;
		if (sourceAlpha == 255) {
			return source;
		}
		int destinationAlpha = destination >>> 24;
		int outAlpha = sourceAlpha + destinationAlpha * (255 - sourceAlpha) / 255;
		if (outAlpha == 0) {
			return 0;
		}
		int result = outAlpha << 24;
		for (int shift = 0; shift <= 16; shift += 8) {
			int s = (source >> shift) & 255;
			int d = (destination >> shift) & 255;
			int channel = (s * sourceAlpha + d * destinationAlpha * (255 - sourceAlpha) / 255) / outAlpha;
			result |= Math.min(255, channel) << shift;
		}
		return result;
	}
}
