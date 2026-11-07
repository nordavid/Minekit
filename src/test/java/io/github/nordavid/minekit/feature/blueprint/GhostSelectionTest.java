package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class GhostSelectionTest {
	private static final BlockSpec STONE = BlockSpec.parse("minecraft:stone");
	private static final BlockSpec GLASS = BlockSpec.parse("minecraft:glass");
	/** Air, stone (solid), glass (drawn, not solid). */
	private static final boolean[] DRAWN = {false, true, true};
	private static final boolean[] SOLID = {false, true, false};

	private static Blueprint filled(int sizeX, int sizeY, int sizeZ, short block) {
		short[] cells = new short[sizeX * sizeY * sizeZ];
		Arrays.fill(cells, block);
		return new Blueprint("b", "", Blueprint.Format.SPONGE_V3, 0, sizeX, sizeY, sizeZ, List.of(BlockSpec.AIR, STONE, GLASS), cells);
	}

	private static BlueprintSettings.LayerRange all(Blueprint blueprint) {
		return new BlueprintSettings.LayerRange(0, blueprint.sizeY() - 1);
	}

	@Test
	void candidatesAreTheBlocksOfTheLayersBeingBuilt() {
		Blueprint column = new Blueprint("c", "", Blueprint.Format.SPONGE_V3, 0, 1, 4, 1, List.of(BlockSpec.AIR, STONE, GLASS), new short[] {1, 0, 2, 1});
		assertArrayEquals(new int[] {0, 2, 3}, GhostSelection.candidates(column, all(column), DRAWN));
		assertArrayEquals(new int[] {2}, GhostSelection.candidates(column, new BlueprintSettings.LayerRange(1, 2), DRAWN));
	}

	@Test
	void theNearestCellsComeFirst() {
		Blueprint row = filled(10, 1, 1, (short) 1);
		Placement placement = new Placement(100, 64, 0, 0, false);
		int[] cells = GhostSelection.candidates(row, all(row), DRAWN);
		assertArrayEquals(new int[] {9, 8, 7}, GhostSelection.nearest(cells, row, placement, 110, 64, 0, 3));
		assertEquals(10, GhostSelection.nearest(cells, row, placement, 0, 0, 0, 50).length, "never more than there are");
	}

	@Test
	void sidesTouchingSolidNeighborsAreCovered() {
		Blueprint box = filled(3, 3, 3, (short) 1);
		Placement placement = new Placement(0, 0, 0, 0, false);
		assertEquals(0b111111, GhostSelection.coveredSides(box, placement, all(box), SOLID, box.index(1, 1, 1)), "the middle block is enclosed");
		// The corner (0, 0, 0) touches stone above, to the south and to the east.
		assertEquals(1 << 1 | 1 << 3 | 1 << 5, GhostSelection.coveredSides(box, placement, all(box), SOLID, box.index(0, 0, 0)));
		// Glass hides nothing, and a layer outside the range hides nothing either.
		Blueprint glass = filled(1, 2, 1, (short) 2);
		assertEquals(0, GhostSelection.coveredSides(glass, placement, all(glass), SOLID, 0));
		assertEquals(0, GhostSelection.coveredSides(box, placement, new BlueprintSettings.LayerRange(0, 0), SOLID, box.index(1, 0, 1)) & 1 << 1,
				"the layer above is not being built");
	}

	@Test
	void coveredSidesFollowTurnsAndMirrors() {
		// A 3 x 1 x 2 block of stone with one glass block: whatever the placement, a side is covered exactly when the world
		// position next to it holds stone of the blueprint.
		short[] cells = {1, 1, 2, 1, 1, 1};
		Blueprint blueprint = new Blueprint("t", "", Blueprint.Format.SPONGE_V3, 0, 3, 1, 2, List.of(BlockSpec.AIR, STONE, GLASS), cells);
		int[][] offsets = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
		Random random = new Random(3);
		for (int i = 0; i < 100; i++) {
			Placement placement = new Placement(random.nextInt(20), 5, random.nextInt(20), random.nextInt(4), random.nextBoolean());
			for (int index = 0; index < cells.length; index++) {
				int x = index % 3;
				int z = index / 3;
				int wx = placement.worldX(blueprint, x, z);
				int wz = placement.worldZ(blueprint, x, z);
				int expected = 0;
				for (int side = 0; side < 6; side++) {
					int[] local = placement.toLocal(blueprint, wx + offsets[side][0], 5 + offsets[side][1], wz + offsets[side][2]);
					if (local != null && cells[local[0] + local[2] * 3] == 1) {
						expected |= 1 << side;
					}
				}
				assertEquals(expected, GhostSelection.coveredSides(blueprint, placement, all(blueprint), SOLID, index), placement + " cell " + index);
			}
		}
	}
}
