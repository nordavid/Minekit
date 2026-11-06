package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlacementTest {
	static Blueprint box(int sizeX, int sizeY, int sizeZ) {
		return new Blueprint("box", "", Blueprint.Format.SPONGE_V3, 0, sizeX, sizeY, sizeZ, List.of(BlockSpec.AIR), new short[sizeX * sizeY * sizeZ]);
	}

	@Test
	void aQuarterTurnClockwiseTurnsNorthIntoEast() {
		Blueprint blueprint = box(3, 1, 2);
		Placement turned = new Placement(10, 64, 20, 1, false);
		// Local (0, 0, 0) is the north-west corner. After a clockwise quarter turn it is the north-east corner.
		assertEquals(10 + 2 - 1, turned.worldX(blueprint, 0, 0));
		assertEquals(20, turned.worldZ(blueprint, 0, 0));
		assertEquals(2, turned.worldSizeX(blueprint));
		assertEquals(3, turned.worldSizeZ(blueprint));
		// Mirroring swaps west and east before turning.
		Placement mirrored = new Placement(10, 64, 20, 0, true);
		assertEquals(12, mirrored.worldX(blueprint, 0, 0));
		assertEquals(10, mirrored.worldX(blueprint, 2, 0));
	}

	@Test
	void everyPlacementMapsTheBoxOntoItselfAndBack() {
		Random random = new Random(11);
		for (int i = 0; i < 500; i++) {
			Blueprint blueprint = box(1 + random.nextInt(6), 1 + random.nextInt(4), 1 + random.nextInt(6));
			Placement placement = new Placement(random.nextInt(200) - 100, random.nextInt(100), random.nextInt(200) - 100, random.nextInt(4),
					random.nextBoolean());
			Set<List<Integer>> seen = new HashSet<>();
			for (int y = 0; y < blueprint.sizeY(); y++) {
				for (int z = 0; z < blueprint.sizeZ(); z++) {
					for (int x = 0; x < blueprint.sizeX(); x++) {
						int wx = placement.worldX(blueprint, x, z);
						int wy = placement.worldY(y);
						int wz = placement.worldZ(blueprint, x, z);
						assertTrue(wx >= placement.x() && wx < placement.x() + placement.worldSizeX(blueprint), "x inside the box");
						assertTrue(wz >= placement.z() && wz < placement.z() + placement.worldSizeZ(blueprint), "z inside the box");
						assertTrue(seen.add(List.of(wx, wy, wz)), "no two positions land on the same block");
						assertArrayEquals(new int[] {x, y, z}, placement.toLocal(blueprint, wx, wy, wz), "toLocal undoes the placement");
					}
				}
			}
			assertNull(placement.toLocal(blueprint, placement.x() - 1, placement.y(), placement.z()));
		}
	}

	@Test
	void fourTurnsAndTwoMirrorsChangeNothing() {
		Placement start = new Placement(1, 2, 3, 0, false);
		Placement turned = start.rotatedClockwise().rotatedClockwise().rotatedClockwise().rotatedClockwise();
		assertEquals(start, turned);
		assertEquals(start, start.mirroredToggled().mirroredToggled());
		assertEquals(new Placement(2, 0, 3, 3, false), new Placement(1, 1, 1, -1, false).movedBy(1, -1, 2));
	}
}
