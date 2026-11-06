package io.github.nordavid.minekit.feature.resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * State of the Resource Tracker: the open session of each world (if any) and finished sessions, newest first. Pure
 * and immutable.
 *
 * <p>A session belongs to the world or server it was started in ({@code world} is a
 * {@link io.github.nordavid.minekit.core.world.WorldIds world id}): it counts only there, and another world has its own.
 *
 * <p>Counting rules (shown to the player as well):
 * <ul>
 *   <li>Only changes of item <em>totals</em> are counted, so moving items between slots never counts.</li>
 *   <li>A change between two observations with no screen open counts as <b>collected</b> (gain) or
 *       <b>consumed</b> (loss: placed, eaten, thrown, used up).</li>
 *   <li>A change while a screen was open at either observation (inventory crafting, chests, villagers), or while the
 *       game mode or state makes the cause unknowable, counts as <b>unclear</b> instead of inventing a cause.</li>
 *   <li>While paused, observations only move the baseline.</li>
 * </ul>
 *
 * @param open    world id to that world's open session, running or paused
 * @param history finished sessions of all worlds, newest first, at most {@link #MAX_HISTORY}
 */
public record ResourceTracker(Map<String, ResourceSession> open, List<ResourceSession> history) {
	public static final int MAX_HISTORY = 30;
	public static final ResourceTracker EMPTY = new ResourceTracker(Map.of(), List.of());

	public ResourceTracker {
		open = Collections.unmodifiableMap(new TreeMap<>(open));
		history = List.copyOf(history);
	}

	/** The open session of a world, or {@code null}. */
	public @Nullable ResourceSession current(String world) {
		return open.get(world);
	}

	public Optional<ResourceSession> currentSession(String world) {
		return Optional.ofNullable(open.get(world));
	}

	/** Open sessions of other worlds than {@code world}. */
	public List<ResourceSession> openElsewhere(String world) {
		return open.entrySet().stream().filter(entry -> !entry.getKey().equals(world)).map(Map.Entry::getValue).toList();
	}

	/** Starts a new session in a world, ending that world's open session first. */
	public ResourceTracker start(String world, String id, String name, String label, long now) {
		ResourceTracker base = open.containsKey(world) ? stop(world, now) : this;
		ResourceSession session = new ResourceSession(id, name, label, now, 0, now, -1, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		return base.with(world, session);
	}

	public ResourceTracker pause(String world, long now) {
		ResourceSession current = open.get(world);
		if (current == null || !current.isRunning()) {
			return this;
		}
		return with(world, current.with(current.durationMillis(now), -1, -1));
	}

	/** Pauses every running session, for example when the player leaves a world. */
	public ResourceTracker pauseAll(long now) {
		ResourceTracker result = this;
		for (String world : open.keySet()) {
			result = result.pause(world, now);
		}
		return result;
	}

	public ResourceTracker resume(String world, long now) {
		ResourceSession current = open.get(world);
		if (current == null || current.isRunning()) {
			return this;
		}
		return with(world, current.with(current.activeMillis(), now, -1));
	}

	/** Ends a world's open session and moves it to the history. */
	public ResourceTracker stop(String world, long now) {
		ResourceSession current = open.get(world);
		if (current == null) {
			return this;
		}
		ResourceSession ended = current.with(current.durationMillis(now), -1, now);
		List<ResourceSession> next = new ArrayList<>();
		next.add(ended);
		next.addAll(history);
		Map<String, ResourceSession> remaining = new TreeMap<>(open);
		remaining.remove(world);
		return new ResourceTracker(remaining, next.subList(0, Math.min(next.size(), MAX_HISTORY)));
	}

	/**
	 * Applies the change between two inventory observations to a world's running session.
	 *
	 * @param before    item totals at the previous observation
	 * @param after     item totals now
	 * @param ambiguous whether the cause of changes cannot be told (a screen was open at either observation, creative
	 *                  mode, death, world change)
	 */
	public ResourceTracker observe(String world, Map<String, Integer> before, Map<String, Integer> after, boolean ambiguous) {
		ResourceSession current = open.get(world);
		if (current == null || !current.isRunning() || before.equals(after)) {
			return this;
		}
		Map<String, Long> collected = new TreeMap<>(current.collected());
		Map<String, Long> consumed = new TreeMap<>(current.consumed());
		Map<String, Long> unclearIn = new TreeMap<>(current.unclearIn());
		Map<String, Long> unclearOut = new TreeMap<>(current.unclearOut());
		Set<String> items = new HashSet<>(before.keySet());
		items.addAll(after.keySet());
		for (String item : items) {
			long delta = (long) after.getOrDefault(item, 0) - before.getOrDefault(item, 0);
			if (delta > 0) {
				(ambiguous ? unclearIn : collected).merge(item, delta, Long::sum);
			} else if (delta < 0) {
				(ambiguous ? unclearOut : consumed).merge(item, -delta, Long::sum);
			}
		}
		return with(world, current.withCounts(collected, consumed, unclearIn, unclearOut));
	}

	/** Replaces a world's open session. */
	public ResourceTracker with(String world, ResourceSession session) {
		Map<String, ResourceSession> next = new TreeMap<>(open);
		next.put(Objects.requireNonNull(world, "world"), Objects.requireNonNull(session, "session"));
		return new ResourceTracker(next, history);
	}

	/** Renames an open or a finished session. */
	public ResourceTracker renamed(String id, String name) {
		Map<String, ResourceSession> nextOpen = new TreeMap<>(open);
		nextOpen.replaceAll((world, session) -> session.id().equals(id) ? session.withName(name) : session);
		return new ResourceTracker(nextOpen, history.stream().map(session -> session.id().equals(id) ? session.withName(name) : session).toList());
	}

	public ResourceTracker deleted(String id) {
		return new ResourceTracker(open, history.stream().filter(session -> !session.id().equals(id)).toList());
	}

	/** Adds goals to a world's open session, keeping the larger target where one exists. */
	public ResourceTracker withGoals(String world, Map<String, Integer> goals) {
		ResourceSession session = open.get(world);
		if (session == null) {
			return this;
		}
		for (Map.Entry<String, Integer> goal : goals.entrySet()) {
			session = session.withGoal(goal.getKey(), Math.max(goal.getValue(), session.goals().getOrDefault(goal.getKey(), 0)));
		}
		return with(world, session);
	}
}
