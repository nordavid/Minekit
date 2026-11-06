package io.github.nordavid.minekit.feature.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Last observed contents of the containers the player opened in one world or server. Pure and immutable.
 *
 * <p>Only containers the player actually opened are present; nothing is ever inferred about unopened ones. Each record
 * keeps its observation time, so the UI can say how old the information is. At most {@link #MAX_CONTAINERS} records
 * are kept; the oldest observation is dropped first.
 *
 * @param label     human-readable name of the world or server (no addresses)
 * @param containers records by {@link ContainerRecord#key()}
 */
public record StorageIndex(String label, Map<String, ContainerRecord> containers) {
	public static final int MAX_CONTAINERS = 2_000;

	public static StorageIndex empty(String label) {
		return new StorageIndex(label, Map.of());
	}

	public StorageIndex {
		containers = Collections.unmodifiableMap(new LinkedHashMap<>(containers));
	}

	/**
	 * Adds or replaces the record for this container. Older records that share a block position with it are dropped:
	 * the block there is now this container (a single chest that became a double chest, a chest replaced by a barrel).
	 */
	public StorageIndex withObserved(ContainerRecord record) {
		Map<String, ContainerRecord> next = new LinkedHashMap<>(containers);
		next.remove(record.key());
		next.values().removeIf(other -> other.dimension().equals(record.dimension())
				&& other.positions().stream().anyMatch(record.positions()::contains));
		next.put(record.key(), record);
		if (next.size() > MAX_CONTAINERS) {
			next.values().stream().min(Comparator.comparingLong(ContainerRecord::observedAt))
					.ifPresent(oldest -> next.remove(oldest.key()));
		}
		return new StorageIndex(label, next);
	}

	public StorageIndex without(String key) {
		Map<String, ContainerRecord> next = new LinkedHashMap<>(containers);
		next.remove(key);
		return new StorageIndex(label, next);
	}

	public StorageIndex cleared() {
		return new StorageIndex(label, Map.of());
	}

	public StorageIndex withLabel(String newLabel) {
		return new StorageIndex(newLabel, containers);
	}

	/** Every item id found in any known container. */
	public SortedSet<String> knownItems() {
		SortedSet<String> items = new TreeSet<>();
		containers.values().forEach(record -> items.addAll(record.items().keySet()));
		return items;
	}

	/** Containers holding {@code item}, most of it first. */
	public List<ContainerRecord> containersWith(String item) {
		List<ContainerRecord> result = new ArrayList<>();
		for (ContainerRecord record : containers.values()) {
			if (record.count(item) > 0) {
				result.add(record);
			}
		}
		result.sort(Comparator.comparingInt((ContainerRecord record) -> record.count(item)).reversed().thenComparing(ContainerRecord::key));
		return result;
	}

	/**
	 * Total of {@code item} over all known containers, with the oldest observation that contributes to it.
	 *
	 * @return empty if no known container holds the item
	 */
	public Optional<Known> known(String item) {
		long total = 0;
		int count = 0;
		long oldest = Long.MAX_VALUE;
		for (ContainerRecord record : containers.values()) {
			int amount = record.count(item);
			if (amount > 0) {
				total += amount;
				count++;
				oldest = Math.min(oldest, record.observedAt());
			}
		}
		return count == 0 ? Optional.empty() : Optional.of(new Known(total, count, oldest));
	}

	/** Known amount of an item in storage, and how old the oldest contributing observation is. */
	public record Known(long total, int containers, long oldestObservedAt) {
	}
}
