package io.github.nordavid.minekit.feature.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceTrackerTest {
	private static ResourceTracker started() {
		return ResourceTracker.EMPTY.start("s1", "Mining", "Singleplayer", 1_000);
	}

	@Test
	void gainsAndLossesWithoutScreenAreCollectedAndConsumed() {
		ResourceTracker tracker = started()
				.observe(Map.of("minecraft:cobblestone", 10), Map.of("minecraft:cobblestone", 74, "minecraft:raw_iron", 3), false)
				.observe(Map.of("minecraft:cobblestone", 74), Map.of("minecraft:cobblestone", 70), false);

		ResourceSession session = tracker.current();
		assertEquals(Map.of("minecraft:cobblestone", 64L, "minecraft:raw_iron", 3L), session.collected());
		assertEquals(Map.of("minecraft:cobblestone", 4L), session.consumed());
		assertTrue(session.unclearIn().isEmpty());
	}

	@Test
	void changesWhileAScreenIsOpenAreUnclear() {
		// Crafting planks in the inventory: logs go, planks come. That is neither collecting nor consuming.
		ResourceSession session = started()
				.observe(Map.of("minecraft:oak_log", 2), Map.of("minecraft:oak_log", 1, "minecraft:oak_planks", 4), true)
				.current();

		assertTrue(session.collected().isEmpty());
		assertTrue(session.consumed().isEmpty());
		assertEquals(Map.of("minecraft:oak_planks", 4L), session.unclearIn());
		assertEquals(Map.of("minecraft:oak_log", 1L), session.unclearOut());
	}

	@Test
	void movingItemsBetweenSlotsDoesNotCount() {
		ResourceTracker tracker = started();
		// Totals are equal before and after a rearrangement, whatever slots moved.
		assertSame(tracker, tracker.observe(Map.of("minecraft:dirt", 64), Map.of("minecraft:dirt", 64), false));
	}

	@Test
	void pausedSessionsDoNotCountAndDurationExcludesPauses() {
		ResourceTracker tracker = started().pause(61_000);
		assertSame(tracker, tracker.observe(Map.of(), Map.of("minecraft:dirt", 5), false));

		tracker = tracker.resume(100_000);
		assertEquals(60_000 + 30_000, tracker.current().durationMillis(130_000));
		assertFalse(tracker.pause(130_000).current().isRunning());
	}

	@Test
	void stoppingMovesTheSessionToHistoryNewestFirst() {
		ResourceTracker tracker = started().stop(5_000).start("s2", "Second", "Singleplayer", 6_000).stop(9_000);

		assertNull(tracker.current());
		assertEquals(2, tracker.history().size());
		assertEquals("s2", tracker.history().getFirst().id());
		assertEquals(4_000, tracker.history().get(1).activeMillis());
		assertTrue(tracker.history().get(1).isEnded());
	}

	@Test
	void startingWhileASessionIsOpenEndsItFirst() {
		ResourceTracker tracker = started().start("s2", "Next", "Singleplayer", 2_000);
		assertEquals("s2", tracker.current().id());
		assertEquals("s1", tracker.history().getFirst().id());
	}

	@Test
	void historyIsLimited() {
		ResourceTracker tracker = ResourceTracker.EMPTY;
		for (int i = 0; i < ResourceTracker.MAX_HISTORY + 5; i++) {
			tracker = tracker.start("s" + i, "n", "w", i).stop(i + 1);
		}
		assertEquals(ResourceTracker.MAX_HISTORY, tracker.history().size());
	}

	@Test
	void goalsShowProgressFromCollectedItems() {
		ResourceTracker tracker = started()
				.withGoals(Map.of("minecraft:iron_ingot", 128))
				.observe(Map.of(), Map.of("minecraft:iron_ingot", 40), false)
				.withGoals(Map.of("minecraft:iron_ingot", 64));

		ResourceSession session = tracker.current();
		assertEquals(128, session.goals().get("minecraft:iron_ingot"), "adding a smaller goal keeps the larger one");
		assertEquals(88, session.remaining("minecraft:iron_ingot"));
		assertEquals(0, session.withGoal("minecraft:iron_ingot", 0).goals().size(), "goal 0 removes it");
	}

	@Test
	void renameAndDeleteWorkForOpenAndFinishedSessions() {
		ResourceTracker tracker = started().stop(2_000).start("s2", "Open", "w", 3_000);

		tracker = tracker.renamed("s1", "Old mine").renamed("s2", "New mine");
		assertEquals("Old mine", tracker.history().getFirst().name());
		assertEquals("New mine", tracker.current().name());
		assertTrue(tracker.deleted("s1").history().isEmpty());
	}
}
