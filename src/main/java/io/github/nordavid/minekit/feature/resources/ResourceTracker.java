package io.github.nordavid.minekit.feature.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * State of the Resource Tracker: the open session (if any) and finished sessions, newest first. Pure and immutable.
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
 * @param current  the open session, running or paused
 * @param history  finished sessions, newest first, at most {@link #MAX_HISTORY}
 */
public record ResourceTracker(@Nullable ResourceSession current, List<ResourceSession> history) {
	public static final int MAX_HISTORY = 30;
	public static final ResourceTracker EMPTY = new ResourceTracker(null, List.of());

	public ResourceTracker {
		history = List.copyOf(history);
	}

	public Optional<ResourceSession> currentSession() {
		return Optional.ofNullable(current);
	}

	/** Starts a new session, ending an open one first. */
	public ResourceTracker start(String id, String name, String world, long now) {
		ResourceTracker base = current != null ? stop(now) : this;
		ResourceSession session = new ResourceSession(id, name, world, now, 0, now, -1, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
		return new ResourceTracker(session, base.history);
	}

	public ResourceTracker pause(long now) {
		if (current == null || !current.isRunning()) {
			return this;
		}
		return new ResourceTracker(current.with(current.durationMillis(now), -1, -1), history);
	}

	public ResourceTracker resume(long now) {
		if (current == null || current.isRunning()) {
			return this;
		}
		return new ResourceTracker(current.with(current.activeMillis(), now, -1), history);
	}

	/** Ends the open session and moves it to the history. */
	public ResourceTracker stop(long now) {
		if (current == null) {
			return this;
		}
		ResourceSession ended = current.with(current.durationMillis(now), -1, now);
		List<ResourceSession> next = new ArrayList<>();
		next.add(ended);
		next.addAll(history);
		return new ResourceTracker(null, next.subList(0, Math.min(next.size(), MAX_HISTORY)));
	}

	/**
	 * Applies the change between two inventory observations to the running session.
	 *
	 * @param before    item totals at the previous observation
	 * @param after     item totals now
	 * @param ambiguous whether the cause of changes cannot be told (a screen was open at either observation, creative
	 *                  mode, death, world change)
	 */
	public ResourceTracker observe(Map<String, Integer> before, Map<String, Integer> after, boolean ambiguous) {
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
		return new ResourceTracker(current.withCounts(collected, consumed, unclearIn, unclearOut), history);
	}

	public ResourceTracker withCurrent(ResourceSession session) {
		return new ResourceTracker(Objects.requireNonNull(session), history);
	}

	/** Renames the open or a finished session. */
	public ResourceTracker renamed(String id, String name) {
		if (current != null && current.id().equals(id)) {
			return withCurrent(current.withName(name));
		}
		return new ResourceTracker(current, history.stream().map(session -> session.id().equals(id) ? session.withName(name) : session).toList());
	}

	public ResourceTracker deleted(String id) {
		return new ResourceTracker(current, history.stream().filter(session -> !session.id().equals(id)).toList());
	}

	/** Adds goals to the open session, keeping the larger target where one exists. */
	public ResourceTracker withGoals(Map<String, Integer> goals) {
		if (current == null) {
			return this;
		}
		ResourceSession session = current;
		for (Map.Entry<String, Integer> goal : goals.entrySet()) {
			session = session.withGoal(goal.getKey(), Math.max(goal.getValue(), session.goals().getOrDefault(goal.getKey(), 0)));
		}
		return withCurrent(session);
	}
}
