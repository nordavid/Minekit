package io.github.nordavid.minekit.feature.loadout;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A saved arrangement of equipment and inventory.
 *
 * @param id     stable id, e.g. {@code loadout-3}
 * @param name   shown name, at most {@link #MAX_NAME} characters
 * @param exact  whether items must match exactly (enchantments, names and other components, but not damage);
 *               otherwise the item id is enough
 * @param items  wanted item per inventory position ({@link LoadoutSlots}); positions without an entry are free
 * @param locked positions applying never touches, not even to make room
 */
public record Loadout(String id, String name, boolean exact, SortedMap<Integer, LoadoutItem> items, SortedSet<Integer> locked) {
	public static final int MAX_NAME = 32;

	public Loadout {
		Objects.requireNonNull(id, "id");
		name = cleanName(name);
		TreeSet<Integer> validLocked = new TreeSet<>();
		locked.stream().filter(LoadoutSlots::isValid).forEach(validLocked::add);
		TreeMap<Integer, LoadoutItem> validItems = new TreeMap<>();
		for (Map.Entry<Integer, LoadoutItem> entry : items.entrySet()) {
			if (LoadoutSlots.isValid(entry.getKey()) && !validLocked.contains(entry.getKey())) {
				validItems.put(entry.getKey(), Objects.requireNonNull(entry.getValue(), "item"));
			}
		}
		items = Collections.unmodifiableSortedMap(validItems);
		locked = Collections.unmodifiableSortedSet(validLocked);
	}

	public static Loadout of(String id, String name, Map<Integer, LoadoutItem> items) {
		return new Loadout(id, name, false, new TreeMap<>(items), new TreeSet<>());
	}

	public static String cleanName(String name) {
		String trimmed = name == null ? "" : name.strip();
		return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
	}

	public Loadout withName(String newName) {
		return new Loadout(id, newName, exact, items, locked);
	}

	public Loadout withExact(boolean newExact) {
		return new Loadout(id, name, newExact, items, locked);
	}

	public Loadout withId(String newId) {
		return new Loadout(newId, name, exact, items, locked);
	}

	/** Replaces all items (for example with the current inventory). Locked positions stay locked and empty. */
	public Loadout withItems(Map<Integer, LoadoutItem> newItems) {
		return new Loadout(id, name, exact, new TreeMap<>(newItems), locked);
	}

	public Loadout withItem(int index, LoadoutItem item) {
		TreeMap<Integer, LoadoutItem> next = new TreeMap<>(items);
		next.put(index, item);
		TreeSet<Integer> nextLocked = new TreeSet<>(locked);
		nextLocked.remove(index);
		return new Loadout(id, name, exact, next, nextLocked);
	}

	/** Makes a position free: no item wanted, not locked. */
	public Loadout withFree(int index) {
		TreeMap<Integer, LoadoutItem> next = new TreeMap<>(items);
		next.remove(index);
		TreeSet<Integer> nextLocked = new TreeSet<>(locked);
		nextLocked.remove(index);
		return new Loadout(id, name, exact, next, nextLocked);
	}

	/** Locks a position: applying never touches it. Removes a wanted item there. */
	public Loadout withLocked(int index) {
		TreeMap<Integer, LoadoutItem> next = new TreeMap<>(items);
		next.remove(index);
		TreeSet<Integer> nextLocked = new TreeSet<>(locked);
		nextLocked.add(index);
		return new Loadout(id, name, exact, next, nextLocked);
	}
}
