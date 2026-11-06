package io.github.nordavid.minekit.feature.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StorageViewTest {
	private static final StorageView.Viewer VIEWER = new StorageView.Viewer("minecraft:overworld", 0, 64, 0);

	private static ContainerRecord at(String dimension, String type, int x, Map<String, Integer> items) {
		return new ContainerRecord(dimension, type, List.of(new ContainerRecord.Pos(x, 64, 0)), items, 27, items.size(), 1);
	}

	private final ContainerRecord near = at("minecraft:overworld", "minecraft:chest", 3, Map.of("minecraft:iron_ingot", 5));
	private final ContainerRecord far = at("minecraft:overworld", "minecraft:barrel", 50, Map.of("minecraft:iron_ingot", 64));
	private final ContainerRecord nether = at("minecraft:the_nether", "minecraft:chest", 1, Map.of("minecraft:iron_ingot", 1));
	private final ContainerRecord shulker = at("minecraft:overworld", "minecraft:red_shulker_box", 20, Map.of("minecraft:dirt", 9));
	private final ContainerRecord ender = new ContainerRecord("", "minecraft:ender_chest", List.of(), Map.of("minecraft:iron_ingot", 2), 27, 1, 1);
	private final StorageIndex index = StorageIndex.empty("World").withObserved(far).withObserved(nether).withObserved(near)
			.withObserved(shulker).withObserved(ender);

	@Test
	void containersAreOrderedEnderChestThenByDistanceThenOtherDimensions() {
		assertEquals(List.of(ender, near, far, nether), StorageView.containers(index, "minecraft:iron_ingot", StorageView.Filter.ALL, VIEWER));
		assertEquals(List.of(ender, near, shulker, far, nether), StorageView.containers(index, null, StorageView.Filter.ALL, VIEWER));
		assertEquals(-1, VIEWER.distanceTo(nether));
	}

	@Test
	void filtersByTypeAndDimension() {
		StorageView.Filter chests = new StorageView.Filter(StorageView.TypeFilter.CHESTS, false);
		assertEquals(List.of(near, nether), StorageView.containers(index, null, chests, VIEWER));
		StorageView.Filter here = new StorageView.Filter(StorageView.TypeFilter.ALL, true);
		assertEquals(List.of(ender, near, shulker, far), StorageView.containers(index, null, here, VIEWER));
		assertEquals(Map.of("minecraft:dirt", 9L), StorageView.itemTotals(index, new StorageView.Filter(StorageView.TypeFilter.SHULKER_BOXES, false), VIEWER));
		assertEquals(72L, StorageView.itemTotals(index, StorageView.Filter.ALL, VIEWER).get("minecraft:iron_ingot"));
		assertEquals(StorageView.TypeFilter.OTHER, StorageView.TypeFilter.groupOf("minecraft:hopper"));
	}

	@Test
	void agesAreRounded() {
		assertEquals(new StorageView.Age("minekit.storage.age.now", 0), StorageView.Age.of(30_000));
		assertEquals(new StorageView.Age("minekit.storage.age.minutes", 5), StorageView.Age.of(5 * 60_000 + 59_000));
		assertEquals(new StorageView.Age("minekit.storage.age.hours", 47), StorageView.Age.of(47 * 3_600_000L));
		assertEquals(new StorageView.Age("minekit.storage.age.days", 3), StorageView.Age.of(80 * 3_600_000L));
	}
}
