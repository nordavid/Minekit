package io.github.nordavid.minekit.feature.blueprint;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Blueprint settings, stored in {@code config/minekit/blueprints.json}. The blueprint files themselves live in
 * {@code config/minekit/blueprints/}.
 *
 * @param active          file name of the blueprint shown, or {@code null}
 * @param show            whether the active blueprint is drawn in the world
 * @param placements      file name to world id to placement: a blueprint stands in each world where it was placed
 * @param ranges          file name to the layers being built (local y, inclusive); absent means all layers
 * @param maxFileMegabytes largest file Minekit imports
 * @param maxPositions    largest structure (width x height x length) Minekit imports
 */
public record BlueprintSettings(@Nullable String active, boolean show, Map<String, Map<String, Placement>> placements,
		Map<String, LayerRange> ranges, int maxFileMegabytes, long maxPositions) {
	public static final int DEFAULT_MAX_FILE_MEGABYTES = 32;
	public static final long DEFAULT_MAX_POSITIONS = 16_000_000L;
	public static final BlueprintSettings DEFAULT = new BlueprintSettings(null, true, Map.of(), Map.of(), DEFAULT_MAX_FILE_MEGABYTES,
			DEFAULT_MAX_POSITIONS);

	/** Layers {@code from} to {@code to} (local y, inclusive) are the part being built. */
	public record LayerRange(int from, int to) {
		public LayerRange {
			if (from < 0 || to < from) {
				throw new IllegalArgumentException("invalid layer range " + from + ".." + to);
			}
		}

		public boolean contains(int y) {
			return y >= from && y <= to;
		}
	}

	public BlueprintSettings {
		TreeMap<String, Map<String, Placement>> copy = new TreeMap<>();
		placements.forEach((file, byWorld) -> copy.put(file, Collections.unmodifiableMap(new TreeMap<>(byWorld))));
		placements = Collections.unmodifiableMap(copy);
		ranges = Collections.unmodifiableMap(new TreeMap<>(ranges));
		maxFileMegabytes = Math.clamp(maxFileMegabytes, 1, 512);
		maxPositions = Math.clamp(maxPositions, 1_000L, 100_000_000L);
	}

	public BlueprintLimits limits() {
		return new BlueprintLimits((long) maxFileMegabytes << 20, Math.max(256L << 20, (long) maxFileMegabytes << 23), maxPositions, 8_192, 256);
	}

	public @Nullable Placement placement(String file, String world) {
		return placements.getOrDefault(file, Map.of()).get(world);
	}

	public LayerRange range(String file, int sizeY) {
		LayerRange range = ranges.get(file);
		if (range == null || range.to() >= sizeY) {
			return new LayerRange(0, sizeY - 1);
		}
		return range;
	}

	public BlueprintSettings withActive(@Nullable String file) {
		return new BlueprintSettings(file, show, placements, ranges, maxFileMegabytes, maxPositions);
	}

	public BlueprintSettings withShow(boolean value) {
		return new BlueprintSettings(active, value, placements, ranges, maxFileMegabytes, maxPositions);
	}

	public BlueprintSettings withPlacement(String file, String world, @Nullable Placement placement) {
		TreeMap<String, Map<String, Placement>> next = new TreeMap<>(placements);
		TreeMap<String, Placement> byWorld = new TreeMap<>(placements.getOrDefault(file, Map.of()));
		if (placement == null) {
			byWorld.remove(world);
		} else {
			byWorld.put(world, placement);
		}
		if (byWorld.isEmpty()) {
			next.remove(file);
		} else {
			next.put(file, byWorld);
		}
		return new BlueprintSettings(active, show, next, ranges, maxFileMegabytes, maxPositions);
	}

	public BlueprintSettings withRange(String file, @Nullable LayerRange range) {
		TreeMap<String, LayerRange> next = new TreeMap<>(ranges);
		if (range == null) {
			next.remove(file);
		} else {
			next.put(file, range);
		}
		return new BlueprintSettings(active, show, placements, next, maxFileMegabytes, maxPositions);
	}

	/** Forgets everything about a file, for example after it was deleted. */
	public BlueprintSettings without(String file) {
		TreeMap<String, Map<String, Placement>> nextPlacements = new TreeMap<>(placements);
		nextPlacements.remove(file);
		TreeMap<String, LayerRange> nextRanges = new TreeMap<>(ranges);
		nextRanges.remove(file);
		return new BlueprintSettings(Objects.equals(active, file) ? null : active, show, nextPlacements, nextRanges, maxFileMegabytes, maxPositions);
	}
}
