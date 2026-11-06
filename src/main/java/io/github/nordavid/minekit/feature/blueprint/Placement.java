package io.github.nordavid.minekit.feature.blueprint;

/**
 * Where a blueprint stands in the world. The structure is first mirrored (x becomes {@code sizeX - 1 - x}, like
 * Minecraft's {@code Mirror.FRONT_BACK}), then turned clockwise around the vertical axis in quarter turns, seen from
 * above. ({@code x}, {@code y}, {@code z}) is the minimum corner of the placed box, so the box never moves when it is
 * turned or mirrored.
 *
 * @param rotation quarter turns clockwise, 0 to 3 (like Minecraft's {@code Rotation.CLOCKWISE_90} and so on)
 */
public record Placement(int x, int y, int z, int rotation, boolean mirrored) {
	public static final Placement ORIGIN = new Placement(0, 0, 0, 0, false);

	public Placement {
		rotation = Math.floorMod(rotation, 4);
	}

	public Placement movedBy(int dx, int dy, int dz) {
		return new Placement(x + dx, y + dy, z + dz, rotation, mirrored);
	}

	public Placement at(int newX, int newY, int newZ) {
		return new Placement(newX, newY, newZ, rotation, mirrored);
	}

	public Placement rotatedClockwise() {
		return new Placement(x, y, z, rotation + 1, mirrored);
	}

	public Placement mirroredToggled() {
		return new Placement(x, y, z, rotation, !mirrored);
	}

	/** Size of the placed box along the world x axis. */
	public int worldSizeX(Blueprint blueprint) {
		return rotation % 2 == 0 ? blueprint.sizeX() : blueprint.sizeZ();
	}

	/** Size of the placed box along the world z axis. */
	public int worldSizeZ(Blueprint blueprint) {
		return rotation % 2 == 0 ? blueprint.sizeZ() : blueprint.sizeX();
	}

	/** World x of a local position. */
	public int worldX(Blueprint blueprint, int localX, int localZ) {
		int mx = mirrored ? blueprint.sizeX() - 1 - localX : localX;
		return x + switch (rotation) {
			case 0 -> mx;
			case 1 -> blueprint.sizeZ() - 1 - localZ;
			case 2 -> blueprint.sizeX() - 1 - mx;
			default -> localZ;
		};
	}

	/** World z of a local position. */
	public int worldZ(Blueprint blueprint, int localX, int localZ) {
		int mx = mirrored ? blueprint.sizeX() - 1 - localX : localX;
		return z + switch (rotation) {
			case 0 -> localZ;
			case 1 -> mx;
			case 2 -> blueprint.sizeZ() - 1 - localZ;
			default -> blueprint.sizeX() - 1 - mx;
		};
	}

	public int worldY(int localY) {
		return y + localY;
	}

	/**
	 * The local position at a world position, or {@code null} if the world position is outside the placed box. The
	 * inverse of {@link #worldX}, {@link #worldY}, {@link #worldZ}.
	 */
	public int[] toLocal(Blueprint blueprint, int worldX, int worldY, int worldZ) {
		int dx = worldX - x;
		int dy = worldY - y;
		int dz = worldZ - z;
		if (dx < 0 || dz < 0 || dy < 0 || dx >= worldSizeX(blueprint) || dz >= worldSizeZ(blueprint) || dy >= blueprint.sizeY()) {
			return null;
		}
		int mx;
		int localZ;
		switch (rotation) {
			case 0 -> {
				mx = dx;
				localZ = dz;
			}
			case 1 -> {
				mx = dz;
				localZ = blueprint.sizeZ() - 1 - dx;
			}
			case 2 -> {
				mx = blueprint.sizeX() - 1 - dx;
				localZ = blueprint.sizeZ() - 1 - dz;
			}
			default -> {
				mx = blueprint.sizeX() - 1 - dz;
				localZ = dx;
			}
		}
		int localX = mirrored ? blueprint.sizeX() - 1 - mx : mx;
		return new int[] {localX, dy, localZ};
	}
}
