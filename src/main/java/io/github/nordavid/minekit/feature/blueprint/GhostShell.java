package io.github.nordavid.minekit.feature.blueprint;

import java.util.Arrays;

/**
 * The faces of a blueprint that can be seen from outside: for every block in the layers being built, each side that
 * does not touch another solid block of those layers. Drawing only these keeps the whole structure cheap enough to
 * show in the world. Pure, so it can run on a background thread and be tested.
 *
 * <p>A face is packed into a {@code long}: the block's {@link Blueprint#index} shifted left by three, plus the side in
 * the blueprint's own orientation, numbered like Minecraft's {@code Direction}: 0 down, 1 up, 2 north, 3 south, 4 west,
 * 5 east.
 */
final class GhostShell {
	static final int DOWN = 0;
	static final int UP = 1;
	static final int NORTH = 2;
	static final int SOUTH = 3;
	static final int WEST = 4;
	static final int EAST = 5;
	private static final int[][] OFFSETS = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
	/** The next side clockwise, seen from above, for the four horizontal sides. */
	private static final int[] CLOCKWISE = {DOWN, UP, EAST, WEST, NORTH, SOUTH};

	private GhostShell() {
	}

	static long face(int index, int side) {
		return (long) index << 3 | side;
	}

	static int index(long face) {
		return (int) (face >>> 3);
	}

	static int side(long face) {
		return (int) (face & 7);
	}

	/**
	 * Every visible face of the layers in {@code range}.
	 *
	 * @param drawn per palette entry: whether the block is drawn at all (not air)
	 * @param solid per palette entry: whether the block hides the faces of its neighbors (not air, not see-through)
	 */
	static long[] faces(Blueprint blueprint, BlueprintSettings.LayerRange range, boolean[] drawn, boolean[] solid) {
		int sizeX = blueprint.sizeX();
		int sizeZ = blueprint.sizeZ();
		long[] faces = new long[1024];
		int count = 0;
		for (int y = range.from(); y <= Math.min(range.to(), blueprint.sizeY() - 1); y++) {
			for (int z = 0; z < sizeZ; z++) {
				for (int x = 0; x < sizeX; x++) {
					int index = blueprint.index(x, y, z);
					if (!drawn[blueprint.paletteIndexAt(index)]) {
						continue;
					}
					for (int side = 0; side < 6; side++) {
						int nx = x + OFFSETS[side][0];
						int ny = y + OFFSETS[side][1];
						int nz = z + OFFSETS[side][2];
						boolean covered = nx >= 0 && nz >= 0 && nx < sizeX && nz < sizeZ && range.contains(ny) && ny < blueprint.sizeY()
								&& solid[blueprint.paletteIndex(nx, ny, nz)];
						if (!covered) {
							if (count == faces.length) {
								faces = Arrays.copyOf(faces, faces.length * 2);
							}
							faces[count++] = face(index, side);
						}
					}
				}
			}
		}
		return Arrays.copyOf(faces, count);
	}

	/** The side in the world: mirroring swaps west and east, then each quarter turn moves it clockwise. */
	static int worldSide(int side, Placement placement) {
		int result = placement.mirrored() && (side == WEST || side == EAST) ? (side == WEST ? EAST : WEST) : side;
		for (int turn = 0; turn < placement.rotation(); turn++) {
			result = CLOCKWISE[result];
		}
		return result;
	}

	/**
	 * The {@code limit} faces nearest to a world position (by the center of their block), in no particular order.
	 * Returns {@code faces} itself if there are not more than that.
	 */
	static long[] nearest(long[] faces, Blueprint blueprint, Placement placement, int px, int py, int pz, int limit) {
		if (faces.length <= limit) {
			return faces;
		}
		int sizeX = blueprint.sizeX();
		int layer = sizeX * blueprint.sizeZ();
		long[] keyed = new long[faces.length];
		for (int i = 0; i < faces.length; i++) {
			int index = index(faces[i]);
			int y = index / layer;
			int x = index % layer % sizeX;
			int z = index % layer / sizeX;
			long dx = placement.worldX(blueprint, x, z) - px;
			long dy = placement.worldY(y) - py;
			long dz = placement.worldZ(blueprint, x, z) - pz;
			long distance = Math.min(Integer.MAX_VALUE, dx * dx + dy * dy + dz * dz);
			keyed[i] = distance << 32 | i;
		}
		Arrays.sort(keyed);
		long[] result = new long[limit];
		for (int i = 0; i < limit; i++) {
			result[i] = faces[(int) keyed[i]];
		}
		return result;
	}
}
