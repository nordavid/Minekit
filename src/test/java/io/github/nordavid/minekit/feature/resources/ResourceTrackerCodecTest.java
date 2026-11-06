package io.github.nordavid.minekit.feature.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceTrackerCodecTest {
	@Test
	void roundTripRestoresARunningSessionAsPaused() throws Exception {
		long now = System.currentTimeMillis();
		ResourceTracker tracker = ResourceTracker.EMPTY
				.start("old", "Old", "Singleplayer", now - 50_000).stop(now - 40_000)
				.start("cur", "Current", "Server A", now - 5_000)
				.withGoals(Map.of("minecraft:iron_ingot", 128))
				.observe(Map.of(), Map.of("minecraft:iron_ingot", 10), false)
				.observe(Map.of("minecraft:iron_ingot", 10), Map.of("minecraft:iron_ingot", 9, "minecraft:dirt", 2), true);

		JsonCodec.Decoded<ResourceTracker> decoded = ResourceTrackerCodec.INSTANCE.decode(ResourceTrackerCodec.INSTANCE.encode(tracker));

		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());
		ResourceSession current = decoded.value().current();
		assertFalse(current.isRunning(), "restored paused");
		assertTrue(current.activeMillis() >= 5_000);
		assertEquals(tracker.current().collected(), current.collected());
		assertEquals(tracker.current().unclearIn(), current.unclearIn());
		assertEquals(tracker.current().unclearOut(), current.unclearOut());
		assertEquals(tracker.current().goals(), current.goals());
		assertEquals(tracker.history(), decoded.value().history());
	}

	@Test
	void invalidEntriesAreIgnored() throws Exception {
		JsonCodec.Decoded<ResourceTracker> decoded = ResourceTrackerCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "current": { "id": "x" },
				  "history": [ { "id": "a", "name": "A", "world": "w", "startedAt": 1, "activeMillis": 5, "endedAt": 6,
				                 "collected": { "minecraft:dirt": 3, "bad id": 4, "minecraft:stone": -1 } }, 5 ] }
				"""));

		assertEquals(null, decoded.value().current());
		assertEquals(Map.of("minecraft:dirt", 3L), decoded.value().history().getFirst().collected());
		assertEquals(2, decoded.warnings().size());
	}
}
