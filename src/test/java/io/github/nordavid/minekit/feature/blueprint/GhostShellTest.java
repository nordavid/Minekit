package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GhostShellTest {
	private static final BlockSpec STONE = BlockSpec.parse("minecraft:stone");
	private static final BlockSpec GLASS = BlockSpec.parse("minecraft:glass");
	/** Air, stone (solid), glass (drawn, but see-through). */
	private static final boolean[] DRAWN = {false, true, true};
	private static final boolean[] SOLID = {false, true, false};

	private static Blueprint filled(int sizeX, int sizeY, int sizeZ, short block) {
		short[] cells = new short[sizeX * sizeY * sizeZ];
		java.util.Arrays.fill(cells, block);
		return new Blueprint("b", "", Blueprint.Format.SPONGE_V3, 0, sizeX, sizeY, sizeZ, List.of(BlockSpec.AIR, STONE, GLASS), cells);
	}

	private static BlueprintSettings.LayerRange all(Blueprint blueprint) {
		return new BlueprintSettings.LayerRange(0, blueprint.sizeY() - 1);
	}

	@Test
	void aSolidBoxShowsOnlyItsOutside() {
		Blueprint box = filled(3, 3, 3, (short) 1);
		long[] faces = GhostShell.faces(box, all(box), DRAWN, SOLID);
		assertEquals(6 * 9, faces.length, "six sides of nine faces each");
		for (long face : faces) {
			assertTrue(GhostShell.index(face) != box.index(1, 1, 1), "the middle block has no visible face");
		}
	}

	@Test
	void seeThroughBlocksAndAirHideNothing() {
		// Stone, glass, stone in a row: the glass hides no stone face, and stone hides the glass faces next to it.
		Blueprint row = new Blueprint("r", "", Blueprint.Format.SPONGE_V3, 0, 3, 1, 1, List.of(BlockSpec.AIR, STONE, GLASS), new short[] {1, 2, 1});
		long[] faces = GhostShell.faces(row, all(row), DRAWN, SOLID);
		assertEquals(6 + 4 + 6, faces.length);
	}

	@Test
	void theLayerRangeCutsTheStructureOpen() {
		Blueprint box = filled(2, 4, 2, (short) 1);
		long[] faces = GhostShell.faces(box, new BlueprintSettings.LayerRange(0, 1), DRAWN, SOLID);
		// Two layers of a 2 x 2 column: four sides of 2 x 2 faces, top and bottom 2 x 2 each.
		assertEquals(4 * 4 + 4 + 4, faces.length);
		for (long face : faces) {
			assertTrue(GhostShell.index(face) < 2 * 2 * 2, "only the two layers in the range");
		}
	}

	@Test
	void worldSidesFollowTurnsAndMirrors() {
		int[][] offsets = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
		Random random = new Random(5);
		for (int i = 0; i < 200; i++) {
			Blueprint box = filled(2 + random.nextInt(4), 2, 2 + random.nextInt(4), (short) 1);
			Placement placement = new Placement(random.nextInt(50), 60, random.nextInt(50), random.nextInt(4), random.nextBoolean());
			int x = 1;
			int z = 1;
			for (int side = 2; side < 6; side++) {
				int nx = x + offsets[side][0];
				int nz = z + offsets[side][2];
				int world = GhostShell.worldSide(side, placement);
				// The neighbor on that side, placed in the world, is one step from the block in the world side's direction.
				assertEquals(placement.worldX(box, nx, nz), placement.worldX(box, x, z) + offsets[world][0], placement + " side " + side);
				assertEquals(placement.worldZ(box, nx, nz), placement.worldZ(box, x, z) + offsets[world][2], placement + " side " + side);
			}
			assertEquals(GhostShell.UP, GhostShell.worldSide(GhostShell.UP, placement));
		}
	}

	@Test
	void theNearestFacesAreKeptWhenThereAreTooMany() {
		Blueprint wall = filled(20, 1, 1, (short) 1);
		long[] faces = GhostShell.faces(wall, all(wall), DRAWN, SOLID);
		Placement placement = new Placement(100, 64, 0, 0, false);
		long[] near = GhostShell.nearest(faces, wall, placement, 100, 64, 0, 10);
		assertEquals(10, near.length);
		Set<Integer> blocks = new HashSet<>();
		for (long face : near) {
			blocks.add(GhostShell.index(face));
		}
		assertTrue(blocks.stream().allMatch(index -> index <= 2), "faces of the first blocks only, got " + blocks);
		assertEquals(faces, GhostShell.nearest(faces, wall, placement, 0, 0, 0, faces.length), "nothing to drop");
	}
}
