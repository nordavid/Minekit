package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VoxelRayTest {
	private static List<List<Integer>> walk(double x, double y, double z, double dx, double dy, double dz, double max) {
		List<List<Integer>> visited = new ArrayList<>();
		VoxelRay.walk(x, y, z, dx, dy, dz, max, (bx, by, bz) -> {
			visited.add(List.of(bx, by, bz));
			return false;
		});
		return visited;
	}

	@Test
	void aStraightRayVisitsEachBlockOnce() {
		assertEquals(List.of(List.of(0, 0, 0), List.of(1, 0, 0), List.of(2, 0, 0), List.of(3, 0, 0)), walk(0.5, 0.5, 0.5, 1, 0, 0, 3));
		assertEquals(List.of(List.of(-1, 5, 2), List.of(-1, 4, 2), List.of(-1, 3, 2)), walk(-0.5, 5.5, 2.5, 0, -1, 0, 2));
	}

	@Test
	void aDiagonalRayStepsOneAxisAtATimeAndNeverSkipsABlock() {
		List<List<Integer>> visited = walk(0.2, 0.9, 0.5, 1, -1, 0, 3);
		assertEquals(List.of(0, 0, 0), visited.getFirst());
		for (int i = 1; i < visited.size(); i++) {
			int steps = 0;
			for (int axis = 0; axis < 3; axis++) {
				steps += Math.abs(visited.get(i).get(axis) - visited.get(i - 1).get(axis));
			}
			assertEquals(1, steps, "neighbors share a face: " + visited);
		}
		assertEquals(List.of(1, 0, 0), visited.get(1), "from (0.2, 0.9) going right and down, x = 1 is reached before y = 0");
	}

	@Test
	void theVisitorCanStopTheWalk() {
		List<List<Integer>> visited = new ArrayList<>();
		boolean stopped = VoxelRay.walk(0.5, 0.5, 0.5, 0, 0, 1, 10, (x, y, z) -> {
			visited.add(List.of(x, y, z));
			return z == 2;
		});
		assertTrue(stopped);
		assertEquals(3, visited.size());
	}
}
