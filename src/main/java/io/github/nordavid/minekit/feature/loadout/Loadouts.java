package io.github.nordavid.minekit.feature.loadout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * All saved loadouts, stored in {@code config/minekit/loadouts.json}. They belong to the player, not to a world.
 *
 * @param loadouts in the order the player sees them
 * @param nextId   number for the next new loadout's id
 */
public record Loadouts(List<Loadout> loadouts, int nextId) {
	public static final int MAX_LOADOUTS = 50;
	public static final Loadouts EMPTY = new Loadouts(List.of(), 1);

	public Loadouts {
		List<Loadout> unique = new ArrayList<>();
		for (Loadout loadout : loadouts) {
			if (unique.size() < MAX_LOADOUTS && unique.stream().noneMatch(other -> other.id().equals(loadout.id()))) {
				unique.add(loadout);
			}
		}
		loadouts = List.copyOf(unique);
		nextId = Math.max(1, nextId);
	}

	public Optional<Loadout> byId(String id) {
		return loadouts.stream().filter(loadout -> loadout.id().equals(id)).findFirst();
	}

	public boolean isFull() {
		return loadouts.size() >= MAX_LOADOUTS;
	}

	/** The id the next added loadout gets. */
	public String peekId() {
		return "loadout-" + nextId;
	}

	/** Adds a new loadout at the end with the next free id. Unchanged when full. */
	public Loadouts withAdded(String name, Map<Integer, LoadoutItem> items) {
		if (isFull()) {
			return this;
		}
		List<Loadout> next = new ArrayList<>(loadouts);
		next.add(Loadout.of(peekId(), name, items));
		return new Loadouts(next, nextId + 1);
	}

	/** A copy right after the original, with a new id and name. */
	public Loadouts withDuplicate(String id, String name) {
		Optional<Loadout> original = byId(id);
		if (original.isEmpty() || isFull()) {
			return this;
		}
		List<Loadout> next = new ArrayList<>(loadouts);
		next.add(next.indexOf(original.get()) + 1, original.get().withId(peekId()).withName(name));
		return new Loadouts(next, nextId + 1);
	}

	public Loadouts withReplaced(Loadout loadout) {
		List<Loadout> next = new ArrayList<>(loadouts);
		next.replaceAll(existing -> existing.id().equals(loadout.id()) ? loadout : existing);
		return new Loadouts(next, nextId);
	}

	public Loadouts without(String id) {
		List<Loadout> next = new ArrayList<>(loadouts);
		next.removeIf(loadout -> loadout.id().equals(id));
		return new Loadouts(next, nextId);
	}
}
