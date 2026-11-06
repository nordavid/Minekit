package io.github.nordavid.minekit.feature.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceTrackerTest {
	private static final String W = "sp-0123456789abcdef";
	private static final String OTHER = "mp-0123456789abcdef";

	@Test
	void eachWorldHasItsOwnSessionAndOnlyThatOneCounts() {
		ResourceTracker tracker = ResourceTracker.EMPTY.start(W, "s1", "Here", "My world", 1_000)
				.start(OTHER, "s2", "There", "Server", 2_000)
				.observe(W, Map.of(), Map.of("minecraft:dirt", 5), false);

		assertEquals(Map.of("minecraft:dirt", 5L), tracker.current(W).collected());
		assertTrue(tracker.current(OTHER).collected().isEmpty(), "the other world's session does not count here");
		assertEquals(List.of("s2"), tracker.openElsewhere(W).stream().map(ResourceSession::id).toList());
		ResourceTracker paused = tracker.pauseAll(3_000);
		assertFalse(paused.current(W).isRunning() || paused.current(OTHER).isRunning());
		assertEquals(1, tracker.stop(W, 4_000).history().size());
		assertEquals("s2", tracker.stop(W, 4_000).current(OTHER).id(), "ending one world's session leaves the other open");
	}
	private static ResourceTracker started() {
		return ResourceTracker.EMPTY.start(W, "s1", "Mining", "Singleplayer", 1_000);
	}

	@Test
	void gainsAndLossesWithoutScreenAreCollectedAndConsumed() {
		ResourceTracker tracker = started()
				.observe(W, Map.of("minecraft:cobblestone", 10), Map.of("minecraft:cobblestone", 74, "minecraft:raw_iron", 3), false)
				.observe(W, Map.of("minecraft:cobblestone", 74), Map.of("minecraft:cobblestone", 70), false);

		ResourceSession session = tracker.current(W);
		assertEquals(Map.of("minecraft:cobblestone", 64L, "minecraft:raw_iron", 3L), session.collected());
		assertEquals(Map.of("minecraft:cobblestone", 4L), session.consumed());
		assertTrue(session.unclearIn().isEmpty());
	}

	@Test
	void changesWhileAScreenIsOpenAreUnclear() {
		// Crafting planks in the inventory: logs go, planks come. That is neither collecting nor consuming.
		ResourceSession session = started()
				.observe(W, Map.of("minecraft:oak_log", 2), Map.of("minecraft:oak_log", 1, "minecraft:oak_planks", 4), true)
				.current(W);

		assertTrue(session.collected().isEmpty());
		assertTrue(session.consumed().isEmpty());
		assertEquals(Map.of("minecraft:oak_planks", 4L), session.unclearIn());
		assertEquals(Map.of("minecraft:oak_log", 1L), session.unclearOut());
	}

	@Test
	void movingItemsBetweenSlotsDoesNotCount() {
		ResourceTracker tracker = started();
		// Totals are equal before and after a rearrangement, whatever slots moved.
		assertSame(tracker, tracker.observe(W, Map.of("minecraft:dirt", 64), Map.of("minecraft:dirt", 64), false));
	}

	@Test
	void pausedSessionsDoNotCountAndDurationExcludesPauses() {
		ResourceTracker tracker = started().pause(W, 61_000);
		assertSame(tracker, tracker.observe(W, Map.of(), Map.of("minecraft:dirt", 5), false));

		tracker = tracker.resume(W, 100_000);
		assertEquals(60_000 + 30_000, tracker.current(W).durationMillis(130_000));
		assertFalse(tracker.pause(W, 130_000).current(W).isRunning());
	}

	@Test
	void stoppingMovesTheSessionToHistoryNewestFirst() {
		ResourceTracker tracker = started().stop(W, 5_000).start(W, "s2", "Second", "Singleplayer", 6_000).stop(W, 9_000);

		assertNull(tracker.current(W));
		assertEquals(2, tracker.history().size());
		assertEquals("s2", tracker.history().getFirst().id());
		assertEquals(4_000, tracker.history().get(1).activeMillis());
		assertTrue(tracker.history().get(1).isEnded());
	}

	@Test
	void startingWhileASessionIsOpenEndsItFirst() {
		ResourceTracker tracker = started().start(W, "s2", "Next", "Singleplayer", 2_000);
		assertEquals("s2", tracker.current(W).id());
		assertEquals("s1", tracker.history().getFirst().id());
	}

	@Test
	void historyIsLimited() {
		ResourceTracker tracker = ResourceTracker.EMPTY;
		for (int i = 0; i < ResourceTracker.MAX_HISTORY + 5; i++) {
			tracker = tracker.start(W, "s" + i, "n", "w", i).stop(W, i + 1);
		}
		assertEquals(ResourceTracker.MAX_HISTORY, tracker.history().size());
	}

	@Test
	void goalsShowProgressFromCollectedItems() {
		ResourceTracker tracker = started()
				.withGoals(W, Map.of("minecraft:iron_ingot", 128))
				.observe(W, Map.of(), Map.of("minecraft:iron_ingot", 40), false)
				.withGoals(W, Map.of("minecraft:iron_ingot", 64));

		ResourceSession session = tracker.current(W);
		assertEquals(128, session.goals().get("minecraft:iron_ingot"), "adding a smaller goal keeps the larger one");
		assertEquals(88, session.remaining("minecraft:iron_ingot"));
		assertEquals(0, session.withGoal("minecraft:iron_ingot", 0).goals().size(), "goal 0 removes it");
	}

	@Test
	void renameAndDeleteWorkForOpenAndFinishedSessions() {
		ResourceTracker tracker = started().stop(W, 2_000).start(W, "s2", "Open", "w", 3_000);

		tracker = tracker.renamed("s1", "Old mine").renamed("s2", "New mine");
		assertEquals("Old mine", tracker.history().getFirst().name());
		assertEquals("New mine", tracker.current(W).name());
		assertTrue(tracker.deleted("s1").history().isEmpty());
	}
}
