package io.github.nordavid.minekit.feature.inventory;

/** Safety limits for a sort run. */
public final class SortJobLimits {
	private SortJobLimits() {
	}

	/**
	 * Upper bound on clicks for a region of {@code regionSize} slots. Every slot needs at most a pick-up, a
	 * put-down and a top-up, plus a little slack; the random tests confirm real runs stay well below this. Hitting the
	 * limit means something unexpected is happening, so the run stops.
	 */
	public static int maxClicks(int regionSize) {
		return 4 * regionSize + 8;
	}
}
