package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import org.junit.jupiter.api.Test;

class IsometricViewTest {
	private static final int RED = 0xFFC04040;

	/** A volume whose cell ids are {@code u + v * sizeU + y * sizeU * sizeV}, each a cube of {@code color} (0: empty). */
	private static IsometricView.Volume volume(int sizeU, int sizeV, int layers, IntUnaryOperator color) {
		return volume(sizeU, sizeV, layers, color, cell -> 0);
	}

	private static IsometricView.Volume volume(int sizeU, int sizeV, int layers, IntUnaryOperator color, IntUnaryOperator overlay) {
		int[] column = new int[sizeU * sizeV];
		for (int i = 0; i < column.length; i++) {
			column[i] = i;
		}
		return new IsometricView.Volume(sizeU, sizeV, layers, column, sizeU * sizeV,
				cell -> color.applyAsInt(cell) == 0 ? null : IsometricView.Look.cube(color.applyAsInt(cell)), overlay);
	}

	private static Set<Integer> cellsShown(IsometricView.Picture picture) {
		Set<Integer> seen = new HashSet<>();
		for (int cell : picture.cells()) {
			if (cell >= 0) {
				seen.add(cell);
			}
		}
		return seen;
	}

	@Test
	void aSingleCubeShowsItsTopAndTwoShadedSides() {
		IsometricView.Picture picture = IsometricView.paint(volume(1, 1, 1, cell -> RED), 4);
		assertEquals(8, picture.width());
		assertEquals(8, picture.height());
		assertEquals(RED, picture.pixels()[4 + 2 * 8], "the top is in the block's own color");
		assertEquals(IsometricView.shade(RED, 0.80), picture.pixels()[1 + 5 * 8]);
		assertEquals(IsometricView.shade(RED, 0.64), picture.pixels()[6 + 5 * 8]);
		assertEquals(0, picture.pixels()[0], "the corners stay empty");
		assertEquals(-1, picture.cellAt(0, 0));
		assertEquals(0, picture.cellAt(4, 2));
		assertEquals(-1, picture.cellAt(-1, 3));
	}

	@Test
	void nearerCubesCoverFartherOnes() {
		// (0, 0, 0) and (1, 1, 1) project onto the same pixels; the second is nearer the camera.
		int near = 1 + 1 * 2 + 1 * 4;
		IsometricView.Picture picture = IsometricView.paint(volume(2, 2, 2, cell -> cell == 0 || cell == near ? RED : 0), 4);
		assertEquals(Set.of(near), cellsShown(picture));
	}

	@Test
	void hiddenCubesAreSkippedAndEveryVisibleOneShows() {
		IsometricView.Picture picture = IsometricView.paint(volume(3, 3, 3, cell -> RED), 6);
		Set<Integer> seen = cellsShown(picture);
		// The cubes with u, v or y at 2 face the camera: 27 - 8.
		assertEquals(19, seen.size());
		assertFalse(seen.contains(1 + 3 + 9), "the middle cube is enclosed");
	}

	@Test
	void seeThroughBlocksLetWhatIsBehindShow() {
		int glass = 0x80FFFFFF;
		int near = 1 + 2 + 4;
		IsometricView.Picture picture = IsometricView.paint(volume(2, 2, 2, cell -> cell == 0 ? RED : cell == near ? glass : 0), 4);
		int center = picture.pixels()[4 + 6 * picture.width()];
		assertEquals(255, center >>> 24, "an opaque block behind keeps the pixel opaque");
		assertNotEquals(RED, center);
		assertTrue(((center >> 16) & 255) > ((center >> 8) & 255), "still reddish");
		assertEquals(near, picture.cellAt(4, 6), "the nearer block is the one under the mouse");
	}

	@Test
	void aSlabIsHalfAsHigh() {
		int[] slabBox = {0, 0, 0, 16, 8, 16};
		IsometricView.Face face = IsometricView.Face.of(RED);
		IsometricView.Volume slab = new IsometricView.Volume(1, 1, 1, new int[] {0}, 1, cell -> new IsometricView.Look(slabBox, face, face, face, false),
				cell -> 0);
		IsometricView.Picture picture = IsometricView.paint(slab, 8);
		// A full cube's top would start at the first row; the slab's top starts half a block lower.
		int firstRow = -1;
		for (int y = 0; y < picture.height() && firstRow < 0; y++) {
			for (int x = 0; x < picture.width(); x++) {
				if (picture.cellAt(x, y) == 0) {
					firstRow = y;
					break;
				}
			}
		}
		assertEquals(4, firstRow);
		assertEquals(RED, picture.pixels()[8 + 8 * picture.width()], "the top of the slab");
	}

	@Test
	void texturesAreDrawnOnLargeBlocksAndTheirAverageOnSmallOnes() {
		int[] texels = new int[16 * 16];
		Arrays.fill(texels, 0xFF00FF00);
		for (int i = 0; i < 16; i++) {
			texels[i * 16] = 0; // a transparent column on the left
		}
		IsometricView.Face top = new IsometricView.Face(texels, 0xFF0000FF);
		IsometricView.Face side = IsometricView.Face.of(RED);
		IsometricView.Volume block = new IsometricView.Volume(1, 1, 1, new int[] {0}, 1,
				cell -> new IsometricView.Look(IsometricView.Look.FULL, top, side, side, false), cell -> 0);

		IsometricView.Picture large = IsometricView.paint(block, 16);
		assertEquals(0xFF00FF00, large.pixels()[16 + 8 * large.width()], "the texture on the top");
		IsometricView.Picture small = IsometricView.paint(block, 4);
		assertEquals(0xFF0000FF, small.pixels()[4 + 2 * small.width()], "the face's color on small blocks");
		// Texel column 0 of the top is transparent: at (8, 4) the top face is at U = 0.05, in that column, and nothing is drawn.
		assertEquals(0, large.pixels()[8 + 4 * large.width()], "transparent texels leave the pixel empty");
	}

	@Test
	void theOverlayTintsAWholeBlock() {
		int green = 0xFF00FF00;
		IsometricView.Picture picture = IsometricView.paint(volume(1, 1, 1, cell -> RED, cell -> 0x80000000 | (green & 0xFFFFFF)), 4);
		int top = picture.pixels()[4 + 2 * 8];
		assertEquals(IsometricView.mix(RED, green, 0x80 / 255.0), top);
	}

	@Test
	void theTileIsTheLargestEvenSizeThatFits() {
		// 2 x 2 x 2 at tile t: width 4t, height 2t + 2t = 4t.
		assertEquals(10, IsometricView.tileFor(2, 2, 2, 41, 41, Long.MAX_VALUE));
		assertEquals(IsometricView.MAX_TILE, IsometricView.tileFor(2, 2, 2, 1000, 1000, Long.MAX_VALUE));
		assertEquals(IsometricView.MIN_TILE, IsometricView.tileFor(200, 200, 2, 10, 10, Long.MAX_VALUE), "too large: drawn scaled down");
		assertEquals(0, IsometricView.tileFor(4000, 4000, 2, 10, 10, 4_000_000), "far too large: not drawn");
	}

	@Test
	void compositingFollowsSourceOver() {
		assertEquals(RED, IsometricView.over(RED, 0xFF000000));
		assertEquals(0x80FFFFFF, IsometricView.over(0x80FFFFFF, 0));
		int mixed = IsometricView.over(0x80FFFFFF, 0xFF000000);
		assertEquals(255, mixed >>> 24);
		assertTrue(Math.abs(((mixed >> 8) & 255) - 128) <= 1);
	}
}
