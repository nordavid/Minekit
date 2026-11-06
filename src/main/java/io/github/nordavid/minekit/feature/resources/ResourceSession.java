package io.github.nordavid.minekit.feature.resources;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One resource tracking session. Immutable; {@link ResourceTracker} produces updated copies.
 *
 * @param id           stable id (creation time in milliseconds plus a counter), used to rename or delete
 * @param name         player-chosen name
 * @param world        label of the world or server the session started in (no addresses)
 * @param startedAt    wall-clock start, epoch milliseconds
 * @param activeMillis time spent running, excluding pauses, up to {@code runningSince}
 * @param runningSince wall-clock time the current running stretch began, or -1 while paused or ended
 * @param endedAt      wall-clock end, or -1 while the session is still open
 * @param collected    items gained while no screen was open (picked up, mined, received)
 * @param consumed     items lost while no screen was open (placed, eaten, thrown, used up)
 * @param unclearIn    items gained while a screen was open or the cause could not be told (crafting, containers)
 * @param unclearOut   items lost under the same conditions
 * @param goals        item id to target amount; progress is the collected amount
 */
public record ResourceSession(
		String id,
		String name,
		String world,
		long startedAt,
		long activeMillis,
		long runningSince,
		long endedAt,
		Map<String, Long> collected,
		Map<String, Long> consumed,
		Map<String, Long> unclearIn,
		Map<String, Long> unclearOut,
		Map<String, Integer> goals) {

	public ResourceSession {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(world, "world");
		collected = copy(collected);
		consumed = copy(consumed);
		unclearIn = copy(unclearIn);
		unclearOut = copy(unclearOut);
		goals = Collections.unmodifiableSortedMap(new TreeMap<>(goals));
	}

	private static SortedMap<String, Long> copy(Map<String, Long> map) {
		return Collections.unmodifiableSortedMap(new TreeMap<>(map));
	}

	public boolean isRunning() {
		return runningSince >= 0;
	}

	public boolean isEnded() {
		return endedAt >= 0;
	}

	/** Running time up to {@code now}, excluding pauses. */
	public long durationMillis(long now) {
		return activeMillis + (isRunning() ? Math.max(0, now - runningSince) : 0);
	}

	public long collected(String item) {
		return collected.getOrDefault(item, 0L);
	}

	/** How many of {@code item} are still needed for its goal (0 when reached or when there is no goal). */
	public long remaining(String item) {
		return Math.max(0, goals.getOrDefault(item, 0) - collected(item));
	}

	ResourceSession with(long newActiveMillis, long newRunningSince, long newEndedAt) {
		return new ResourceSession(id, name, world, startedAt, newActiveMillis, newRunningSince, newEndedAt, collected, consumed,
				unclearIn, unclearOut, goals);
	}

	ResourceSession withCounts(Map<String, Long> newCollected, Map<String, Long> newConsumed, Map<String, Long> newUnclearIn,
			Map<String, Long> newUnclearOut) {
		return new ResourceSession(id, name, world, startedAt, activeMillis, runningSince, endedAt, new TreeMap<>(newCollected),
				new TreeMap<>(newConsumed), new TreeMap<>(newUnclearIn), new TreeMap<>(newUnclearOut), goals);
	}

	public ResourceSession withName(String newName) {
		return new ResourceSession(id, newName, world, startedAt, activeMillis, runningSince, endedAt, collected, consumed,
				unclearIn, unclearOut, goals);
	}

	public ResourceSession withGoal(String item, int target) {
		TreeMap<String, Integer> next = new TreeMap<>(goals);
		if (target <= 0) {
			next.remove(item);
		} else {
			next.put(item, target);
		}
		return new ResourceSession(id, name, world, startedAt, activeMillis, runningSince, endedAt, collected, consumed,
				unclearIn, unclearOut, next);
	}
}
