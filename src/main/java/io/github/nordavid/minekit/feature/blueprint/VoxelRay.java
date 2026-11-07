package io.github.nordavid.minekit.feature.blueprint;

/**
 * Walks the block positions a ray passes through, in order, like the game's own block picking (Amanatides and Woo).
 * Pure, so it can be tested.
 */
final class VoxelRay {
	/** Called for each block position; returns {@code true} to stop. */
	@FunctionalInterface
	interface Visitor {
		boolean visit(int x, int y, int z);
	}

	private VoxelRay() {
	}

	/**
	 * Visits the positions from the one containing ({@code x}, {@code y}, {@code z}) along the direction, up to
	 * {@code maxDistance} blocks; returns whether a visitor stopped the walk.
	 */
	static boolean walk(double x, double y, double z, double dx, double dy, double dz, double maxDistance, Visitor visitor) {
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (length == 0) {
			return visitor.visit((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
		}
		dx /= length;
		dy /= length;
		dz /= length;
		int bx = (int) Math.floor(x);
		int by = (int) Math.floor(y);
		int bz = (int) Math.floor(z);
		int stepX = (int) Math.signum(dx);
		int stepY = (int) Math.signum(dy);
		int stepZ = (int) Math.signum(dz);
		// Distance along the ray to the next boundary on each axis, and between boundaries.
		double nextX = stepX == 0 ? Double.MAX_VALUE : ((stepX > 0 ? bx + 1 - x : x - bx) / Math.abs(dx));
		double nextY = stepY == 0 ? Double.MAX_VALUE : ((stepY > 0 ? by + 1 - y : y - by) / Math.abs(dy));
		double nextZ = stepZ == 0 ? Double.MAX_VALUE : ((stepZ > 0 ? bz + 1 - z : z - bz) / Math.abs(dz));
		double deltaX = stepX == 0 ? Double.MAX_VALUE : 1 / Math.abs(dx);
		double deltaY = stepY == 0 ? Double.MAX_VALUE : 1 / Math.abs(dy);
		double deltaZ = stepZ == 0 ? Double.MAX_VALUE : 1 / Math.abs(dz);
		double travelled = 0;
		while (travelled <= maxDistance) {
			if (visitor.visit(bx, by, bz)) {
				return true;
			}
			if (nextX <= nextY && nextX <= nextZ) {
				travelled = nextX;
				nextX += deltaX;
				bx += stepX;
			} else if (nextY <= nextZ) {
				travelled = nextY;
				nextY += deltaY;
				by += stepY;
			} else {
				travelled = nextZ;
				nextZ += deltaZ;
				bz += stepZ;
			}
		}
		return false;
	}
}
