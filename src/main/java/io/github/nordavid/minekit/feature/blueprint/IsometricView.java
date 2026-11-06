package io.github.nordavid.minekit.feature.blueprint;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Paints a block volume seen from above at an angle (2:1 isometric), one shaded cube per block, so the whole structure
 * can be seen at once. Pure: no Minecraft classes, so it can run on a background thread and be tested.
 *
 * <p>The volume is given in view space: {@code u} runs to the lower right of the picture, {@code v} to the lower left,
 * {@code y} up; the camera looks from the {@code +u, +v} corner, so the top, {@code +u} and {@code +v} faces of a cube
 * are visible. Cubes are painted from far to near (by {@code u + y + v}, the depth along the viewing direction), so
 * nearer cubes cover farther ones. Cubes whose three visible faces are covered by opaque neighbors are skipped.
 */
final class IsometricView {
	/** The smallest cube width in pixels is {@code 2 * MIN_TILE}; below that the cube shape is lost. */
	static final int MIN_TILE = 2;
	static final int MAX_TILE = 48;
	private static final double LEFT_SHADE = 0.80;
	private static final double RIGHT_SHADE = 0.64;
	private static final double EDGE_SHADE = 0.86;
	/** Edges are drawn only for cubes large enough that they do not turn into a grid of lines. */
	private static final int MIN_EDGE_TILE = 6;

	private static final byte NONE = 0;
	private static final byte TOP = 1;
	private static final byte LEFT = 2;
	private static final byte RIGHT = 3;
	private static final byte EDGE = 4;

	/**
	 * The painted picture: ARGB pixels (alpha 0 where nothing is) and, per pixel, the cell painted there last, or -1.
	 */
	record Picture(int width, int height, int[] pixels, int[] cells, int tile) {
		int cellAt(int x, int y) {
			return x < 0 || y < 0 || x >= width || y >= height ? -1 : cells[x + y * width];
		}
	}

	/**
	 * A volume in view space.
	 *
	 * @param column for each column {@code u + v * sizeU}, the cell id of its lowest block
	 * @param layerStride how much the cell id grows from one layer to the next
	 * @param color ARGB color of a cell id; alpha 0 for nothing, below 255 for blocks one can see through
	 */
	record Volume(int sizeU, int sizeV, int layers, int[] column, int layerStride, IntUnaryOperator color) {
		Volume {
			if (sizeU <= 0 || sizeV <= 0 || layers <= 0 || column.length != sizeU * sizeV) {
				throw new IllegalArgumentException("invalid volume " + sizeU + "x" + layers + "x" + sizeV);
			}
		}

		int cell(int u, int y, int v) {
			return column[u + v * sizeU] + y * layerStride;
		}

		boolean opaque(int u, int y, int v) {
			return u < sizeU && y < layers && v < sizeV && color.applyAsInt(cell(u, y, v)) >>> 24 == 255;
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
	 * The largest even cube size that fits the picture into {@code maxWidth} x {@code maxHeight} pixels; the smallest
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
		int[] pixels = new int[width * height];
		int[] cells = new int[width * height];
		Arrays.fill(cells, -1);
		byte[] sprite = sprite(tile);
		int size = 2 * tile;

		int maxDepth = (sizeU - 1) + (sizeV - 1) + (layers - 1);
		for (int depth = 0; depth <= maxDepth; depth++) {
			for (int y = Math.max(0, depth - (sizeU - 1) - (sizeV - 1)); y <= Math.min(layers - 1, depth); y++) {
				int rest = depth - y;
				for (int u = Math.max(0, rest - (sizeV - 1)); u <= Math.min(sizeU - 1, rest); u++) {
					int v = rest - u;
					int cell = volume.cell(u, y, v);
					int color = volume.color().applyAsInt(cell);
					if (color >>> 24 == 0) {
						continue;
					}
					if (volume.opaque(u + 1, y, v) && volume.opaque(u, y + 1, v) && volume.opaque(u, y, v + 1)) {
						continue;
					}
					int left = (sizeV + u - v - 1) * tile;
					int top = (layers - y - 1) * tile + (u + v) * tile / 2;
					int[] shades = {0, color, shade(color, LEFT_SHADE), shade(color, RIGHT_SHADE)};
					for (int sy = 0; sy < size; sy++) {
						int row = (top + sy) * width + left;
						for (int sx = 0; sx < size; sx++) {
							byte mask = sprite[sx + sy * size];
							if (mask == NONE) {
								continue;
							}
							int face = shades[mask & 3];
							if ((mask & EDGE) != 0) {
								face = shade(face, EDGE_SHADE);
							}
							int target = row + sx;
							pixels[target] = over(face, pixels[target]);
							cells[target] = cell;
						}
					}
				}
			}
		}
		return new Picture(width, height, pixels, cells, tile);
	}

	/**
	 * Which face each pixel of a cube belongs to, for a cube {@code 2 * tile} pixels wide and high: the top diamond, the
	 * left ({@code +v}) and the right ({@code +u}) face, decided at the pixel centers. Pixels at the border of a face are
	 * marked as edges.
	 */
	static byte[] sprite(int tile) {
		int size = 2 * tile;
		byte[] faces = new byte[size * size];
		double half = tile / 2.0;
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				double cx = px + 0.5;
				double cy = py + 0.5;
				byte face = NONE;
				if (Math.abs(cx - tile) / tile + Math.abs(cy - half) / half <= 1) {
					face = TOP;
				} else if (cx < tile && cy >= half + cx / 2 && cy <= 3 * half + cx / 2) {
					face = LEFT;
				} else if (cx >= tile && cy >= tile - (cx - tile) / 2 && cy <= 2 * tile - (cx - tile) / 2) {
					face = RIGHT;
				}
				faces[px + py * size] = face;
			}
		}
		if (tile < MIN_EDGE_TILE) {
			return faces;
		}
		byte[] marked = faces.clone();
		for (int py = 0; py < size; py++) {
			for (int px = 0; px < size; px++) {
				byte face = faces[px + py * size];
				if (face != NONE && (differs(faces, size, px - 1, py, face) || differs(faces, size, px + 1, py, face)
						|| differs(faces, size, px, py - 1, face) || differs(faces, size, px, py + 1, face))) {
					marked[px + py * size] = (byte) (face | EDGE);
				}
			}
		}
		return marked;
	}

	private static boolean differs(byte[] faces, int size, int x, int y, byte face) {
		return x < 0 || y < 0 || x >= size || y >= size || faces[x + y * size] != face;
	}

	static int shade(int argb, double factor) {
		int red = (int) (((argb >> 16) & 255) * factor);
		int green = (int) (((argb >> 8) & 255) * factor);
		int blue = (int) ((argb & 255) * factor);
		return (argb & 0xFF000000) | (red << 16) | (green << 8) | blue;
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
