package io.github.nordavid.minekit.feature.resources;

/** Formats session durations as {@code m:ss} or {@code h:mm:ss}. */
final class Durations {
	private Durations() {
	}

	static String format(long millis) {
		long seconds = Math.max(0, millis / 1000);
		long hours = seconds / 3600;
		long minutes = (seconds % 3600) / 60;
		long secs = seconds % 60;
		return hours > 0 ? String.format("%d:%02d:%02d", hours, minutes, secs) : String.format("%d:%02d", minutes, secs);
	}
}
