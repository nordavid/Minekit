package io.github.nordavid.minekit.feature.storage;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * What a container held when the player last had it open.
 *
 * @param dimension  dimension id, e.g. {@code minecraft:overworld}; empty for ender chests (same contents everywhere)
 * @param type       block id of the container, e.g. {@code minecraft:chest}
 * @param positions  block positions; two for a double chest, none for an ender chest
 * @param items      item id to count, as last observed (only what was actually shown to the client)
 * @param slots      number of container slots
 * @param usedSlots  slots that held something
 * @param observedAt epoch milliseconds of the observation
 */
public record ContainerRecord(String dimension, String type, List<Pos> positions, Map<String, Integer> items, int slots, int usedSlots,
		long observedAt) {

	/** A block position. */
	public record Pos(int x, int y, int z) implements Comparable<Pos> {
		@Override
		public int compareTo(Pos other) {
			int byX = Integer.compare(x, other.x);
			if (byX != 0) {
				return byX;
			}
			int byY = Integer.compare(y, other.y);
			return byY != 0 ? byY : Integer.compare(z, other.z);
		}

		@Override
		public String toString() {
			return x + " " + y + " " + z;
		}

		public double distanceTo(double px, double py, double pz) {
			double dx = x + 0.5 - px;
			double dy = y + 0.5 - py;
			double dz = z + 0.5 - pz;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}

	public ContainerRecord {
		Objects.requireNonNull(dimension, "dimension");
		Objects.requireNonNull(type, "type");
		positions = positions.stream().sorted().toList();
		items = Collections.unmodifiableMap(new TreeMap<>(items));
	}

	/** Stable identity: the same container gets the same key every time it is observed. */
	public String key() {
		return dimension + "|" + type + "|" + positions.stream().map(Pos::toString).collect(Collectors.joining(";"));
	}

	public boolean isEnderChest() {
		return positions.isEmpty();
	}

	public int count(String item) {
		return items.getOrDefault(item, 0);
	}

	/** How trustworthy an observation is, by age. */
	public enum Freshness {
		/** Observed within the last 10 minutes. */
		RECENT,
		/** Observed within the last 24 hours. */
		OLDER,
		/** Older than 24 hours: likely changed since. */
		STALE;

		public static Freshness of(long observedAt, long now) {
			long age = now - observedAt;
			if (age < 10 * 60_000L) {
				return RECENT;
			}
			return age < 24 * 3_600_000L ? OLDER : STALE;
		}
	}

	public Freshness freshness(long now) {
		return Freshness.of(observedAt, now);
	}
}
