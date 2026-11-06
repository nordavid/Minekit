package io.github.nordavid.minekit.feature.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StorageIndexTest {
	private static final ContainerRecord.Pos A = new ContainerRecord.Pos(10, 64, -5);
	private static final ContainerRecord.Pos B = new ContainerRecord.Pos(11, 64, -5);

	private static ContainerRecord chest(List<ContainerRecord.Pos> positions, Map<String, Integer> items, long at) {
		return new ContainerRecord("minecraft:overworld", "minecraft:chest", positions, items, 27, items.size(), at);
	}

	@Test
	void reobservingAContainerReplacesItsRecordWhicheverHalfWasClicked() {
		StorageIndex index = StorageIndex.empty("World")
				.withObserved(chest(List.of(A, B), Map.of("minecraft:dirt", 64), 1))
				.withObserved(chest(List.of(B, A), Map.of("minecraft:stone", 3), 2));

		assertEquals(1, index.containers().size(), "a double chest has one key whichever half was clicked");
		assertEquals(Map.of("minecraft:stone", 3), index.containers().values().iterator().next().items());
	}

	@Test
	void aContainerAtAnOverlappingPositionReplacesTheOldRecord() {
		ContainerRecord single = chest(List.of(A), Map.of("minecraft:dirt", 1), 1);
		ContainerRecord barrelElsewhere = new ContainerRecord("minecraft:the_nether", "minecraft:barrel", List.of(A), Map.of(), 27, 0, 1);
		StorageIndex index = StorageIndex.empty("World").withObserved(single).withObserved(barrelElsewhere)
				.withObserved(chest(List.of(A, B), Map.of("minecraft:stone", 2), 2));

		assertEquals(2, index.containers().size(), "the single chest became part of the double chest");
		assertTrue(index.containers().containsKey(barrelElsewhere.key()), "same coordinates in another dimension stay");

		StorageIndex replaced = index.withObserved(new ContainerRecord("minecraft:overworld", "minecraft:barrel", List.of(B), Map.of(), 27, 0, 3));
		assertEquals(2, replaced.containers().size());
		assertTrue(replaced.containersWith("minecraft:stone").isEmpty(), "the double chest is gone");
	}

	@Test
	void contextIdsAreStableHashes() {
		assertEquals(ContextIds.multiplayer("Play.Example.org "), ContextIds.multiplayer("play.example.org"));
		assertTrue(ContextIds.multiplayer("play.example.org").matches("mp-[0-9a-f]{16}"));
		assertTrue(ContextIds.singleplayer("New World").matches("sp-[0-9a-f]{16}"));
		assertTrue(!ContextIds.singleplayer("A").equals(ContextIds.singleplayer("B")));
	}

	@Test
	void copperChestsKeepOneTypeWhileOxidizing() {
		assertEquals("minecraft:copper_chest", io.github.nordavid.minekit.core.container.ContainerTypes.normalize("minecraft:waxed_weathered_copper_chest"));
		assertEquals("minecraft:copper_chest", io.github.nordavid.minekit.core.container.ContainerTypes.normalize("minecraft:exposed_copper_chest"));
		assertEquals("minecraft:chest", io.github.nordavid.minekit.core.container.ContainerTypes.normalize("minecraft:chest"));
	}

	@Test
	void knownTotalsSpanContainersAndReportTheOldestObservation() {
		StorageIndex index = StorageIndex.empty("World")
				.withObserved(chest(List.of(A), Map.of("minecraft:iron_ingot", 20), 100))
				.withObserved(chest(List.of(new ContainerRecord.Pos(0, 0, 0)), Map.of("minecraft:iron_ingot", 5, "minecraft:dirt", 1), 50));

		assertEquals(Optional.of(new StorageIndex.Known(25, 2, 50)), index.known("minecraft:iron_ingot"));
		assertEquals(Optional.empty(), index.known("minecraft:diamond"));
		assertEquals(20, index.containersWith("minecraft:iron_ingot").getFirst().count("minecraft:iron_ingot"), "most first");
		assertEquals(List.of("minecraft:dirt", "minecraft:iron_ingot"), List.copyOf(index.knownItems()));
	}

	@Test
	void forgettingAndClearing() {
		ContainerRecord record = chest(List.of(A), Map.of("minecraft:dirt", 1), 1);
		StorageIndex index = StorageIndex.empty("World").withObserved(record);

		assertTrue(index.without(record.key()).containers().isEmpty());
		assertTrue(index.cleared().containers().isEmpty());
	}

	@Test
	void sizeIsLimitedByDroppingTheOldestObservation() {
		StorageIndex index = StorageIndex.empty("World");
		for (int i = 0; i <= StorageIndex.MAX_CONTAINERS; i++) {
			index = index.withObserved(chest(List.of(new ContainerRecord.Pos(i, 0, 0)), Map.of(), 1_000 + i));
		}
		assertEquals(StorageIndex.MAX_CONTAINERS, index.containers().size());
		assertTrue(index.containers().values().stream().noneMatch(record -> record.observedAt() == 1_000), "oldest dropped");
	}

	@Test
	void freshnessByAge() {
		long now = 100L * 24 * 3_600_000;
		assertEquals(ContainerRecord.Freshness.RECENT, ContainerRecord.Freshness.of(now - 60_000, now));
		assertEquals(ContainerRecord.Freshness.OLDER, ContainerRecord.Freshness.of(now - 2 * 3_600_000, now));
		assertEquals(ContainerRecord.Freshness.STALE, ContainerRecord.Freshness.of(now - 25 * 3_600_000L, now));
	}

	@Test
	void enderChestHasNoPosition() {
		ContainerRecord ender = new ContainerRecord("", "minecraft:ender_chest", List.of(), Map.of("minecraft:diamond", 2), 27, 1, 5);
		assertTrue(ender.isEnderChest());
		assertEquals("|minecraft:ender_chest|", ender.key());
	}

	@Test
	void codecRoundTripAndValidation() throws Exception {
		StorageIndex index = StorageIndex.empty("My world")
				.withObserved(chest(List.of(A, B), Map.of("minecraft:dirt", 64), 1_793_980_000_000L))
				.withObserved(new ContainerRecord("", "minecraft:ender_chest", List.of(), Map.of("minecraft:diamond", 2), 27, 1, 5));
		StorageIndexCodec codec = new StorageIndexCodec("Default");

		JsonCodec.Decoded<StorageIndex> decoded = codec.decode(codec.encode(index));
		assertEquals(index, decoded.value());
		assertTrue(decoded.warnings().isEmpty());

		JsonCodec.Decoded<StorageIndex> invalid = codec.decode(com.google.gson.JsonParser.parseString("""
				{ "schemaVersion": 1, "containers": [ { "dimension": "minecraft:overworld", "type": "bad type" }, 3 ] }
				"""));
		assertEquals("Default", invalid.value().label());
		assertTrue(invalid.value().containers().isEmpty());
		assertEquals(2, invalid.warnings().size());
	}
}
