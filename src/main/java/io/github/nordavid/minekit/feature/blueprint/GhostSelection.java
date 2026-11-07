package io.github.nordavid.minekit.feature.blueprint;

import java.util.Arrays;

/**
 * Which blueprint blocks the ghost in the world draws, and which of their sides are hidden. Pure, so it can run on a
 * background thread and be tested.
 *
 * <p>Sides are numbered like Minecraft's {@code Direction} in the world: 0 down, 1 up, 2 north, 3 south, 4 west, 5
 * east.
 */
final class GhostSelection {
	private static final int[][] OFFSETS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

	private GhostSelection() {
	}

	/** Every block of the layers in {@code range} that is drawn at all (not air). */
	static int[] candidates(Blueprint blueprint, BlueprintSettings.LayerRange range, boolean[] drawn) {
		int layer = blueprint.sizeX() * blueprint.sizeZ();
		int from = range.from() * layer;
		int to = (Math.min(range.to(), blueprint.sizeY() - 1) + 1) * layer;
		int[] cells = new int[Math.max(0, to - from)];
		int count = 0;
		for (int index = from; index < to; index++) {
			if (drawn[blueprint.paletteIndexAt(index)]) {
				cells[count++] = index;
			}
		}
		return Arrays.copyOf(cells, count);
	}

	/** The {@code limit} cells nearest to a world position, nearest first. */
	static int[] nearest(int[] cells, Blueprint blueprint, Placement placement, int px, int py, int pz, int limit) {
		int sizeX = blueprint.sizeX();
		int layer = sizeX * blueprint.sizeZ();
		long[] keyed = new long[cells.length];
		for (int i = 0; i < cells.length; i++) {
			int index = cells[i];
			int y = index / layer;
			int x = index % layer % sizeX;
			int z = index % layer / sizeX;
			long dx = placement.worldX(blueprint, x, z) - px;
			long dy = placement.worldY(y) - py;
			long dz = placement.worldZ(blueprint, x, z) - pz;
			keyed[i] = Math.min(Integer.MAX_VALUE, dx * dx + dy * dy + dz * dz) << 32 | i;
		}
		Arrays.sort(keyed);
		int[] result = new int[Math.min(limit, cells.length)];
		for (int i = 0; i < result.length; i++) {
			result[i] = cells[(int) keyed[i]];
		}
		return result;
	}

	/**
	 * The sides of a placed block, in the world, that touch a solid block of the layers being built: bit {@code 1 << side}
	 * is set for each.
	 */
	static int coveredSides(Blueprint blueprint, Placement placement, BlueprintSettings.LayerRange range, boolean[] solid, int index) {
		int sizeX = blueprint.sizeX();
		int layer = sizeX * blueprint.sizeZ();
		int y = index / layer;
		int x = index % layer % sizeX;
		int z = index % layer / sizeX;
		int wx = placement.worldX(blueprint, x, z);
		int wy = placement.worldY(y);
		int wz = placement.worldZ(blueprint, x, z);
		int covered = 0;
		for (int side = 0; side < 6; side++) {
			int[] local = placement.toLocal(blueprint, wx + OFFSETS[side][0], wy + OFFSETS[side][1], wz + OFFSETS[side][2]);
			if (local != null && range.contains(local[1]) && solid[blueprint.paletteIndex(local[0], local[1], local[2])]) {
				covered |= 1 << side;
			}
		}
		return covered;
	}
}
