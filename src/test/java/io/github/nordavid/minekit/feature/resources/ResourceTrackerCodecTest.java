package io.github.nordavid.minekit.feature.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceTrackerCodecTest {
	private static final String W = "sp-0123456789abcdef";

	@Test
	void roundTripRestoresARunningSessionAsPaused() throws Exception {
		long now = System.currentTimeMillis();
		ResourceTracker tracker = ResourceTracker.EMPTY
				.start(W, "old", "Old", "Singleplayer", now - 50_000).stop(W, now - 40_000)
				.start(W, "cur", "Current", "Server A", now - 5_000)
				.withGoals(W, Map.of("minecraft:iron_ingot", 128))
				.observe(W, Map.of(), Map.of("minecraft:iron_ingot", 10), false)
				.observe(W, Map.of("minecraft:iron_ingot", 10), Map.of("minecraft:iron_ingot", 9, "minecraft:dirt", 2), true);

		JsonCodec.Decoded<ResourceTracker> decoded = ResourceTrackerCodec.INSTANCE.decode(ResourceTrackerCodec.INSTANCE.encode(tracker));

		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());
		ResourceSession current = decoded.value().current(W);
		assertFalse(current.isRunning(), "restored paused");
		assertTrue(current.activeMillis() >= 5_000);
		assertEquals(tracker.current(W).collected(), current.collected());
		assertEquals(tracker.current(W).unclearIn(), current.unclearIn());
		assertEquals(tracker.current(W).unclearOut(), current.unclearOut());
		assertEquals(tracker.current(W).goals(), current.goals());
		assertEquals(tracker.history(), decoded.value().history());
	}

	@Test
	void invalidEntriesAreIgnored() throws Exception {
		JsonCodec.Decoded<ResourceTracker> decoded = ResourceTrackerCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "current": { "id": "x" },
				  "history": [ { "id": "a", "name": "A", "world": "w", "startedAt": 1, "activeMillis": 5, "endedAt": 6,
				                 "collected": { "minecraft:dirt": 3, "bad id": 4, "minecraft:stone": -1 } }, 5 ] }
				"""));

		assertTrue(decoded.value().open().isEmpty());
		assertEquals(Map.of("minecraft:dirt", 3L), decoded.value().history().getFirst().collected());
		assertEquals(2, decoded.warnings().size());
	}

	@Test
	void theSingleSessionOfSchemaOneIsEndedIntoTheHistory() throws Exception {
		JsonCodec.Decoded<ResourceTracker> decoded = ResourceTrackerCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1,
				  "current": { "id": "c", "name": "Old run", "world": "Server", "startedAt": 1000, "activeMillis": 500, "endedAt": -1,
				               "collected": { "minecraft:dirt": 3 } },
				  "history": [] }
				"""));

		assertTrue(decoded.value().open().isEmpty(), "its world is unknown, so it is open nowhere");
		ResourceSession ended = decoded.value().history().getFirst();
		assertEquals("c", ended.id());
		assertTrue(ended.isEnded());
		assertEquals(1500, ended.endedAt());
		assertEquals(Map.of("minecraft:dirt", 3L), ended.collected());
	}
}
