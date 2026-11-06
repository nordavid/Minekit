package io.github.nordavid.minekit.feature.loadout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoadoutsTest {
	private static final Map<Integer, LoadoutItem> MINING = Map.of(
			0, new LoadoutItem("minecraft:diamond_pickaxe", "{minecraft:enchantments=efficiency:5}", 1),
			8, new LoadoutItem("minecraft:torch", "", 64),
			LoadoutSlots.HEAD, new LoadoutItem("minecraft:iron_helmet", "", 1));

	@Test
	void addDuplicateRenameAndDelete() {
		Loadouts loadouts = Loadouts.EMPTY.withAdded("Mining", MINING).withAdded("Nether", Map.of());
		assertEquals(List.of("loadout-1", "loadout-2"), loadouts.loadouts().stream().map(Loadout::id).toList());

		Loadouts copied = loadouts.withDuplicate("loadout-1", "Mining 2");
		assertEquals(List.of("Mining", "Mining 2", "Nether"), copied.loadouts().stream().map(Loadout::name).toList());
		assertEquals(MINING, copied.byId("loadout-3").orElseThrow().items());

		Loadouts renamed = copied.withReplaced(copied.byId("loadout-2").orElseThrow().withName("  Hell trip  "));
		assertEquals("Hell trip", renamed.byId("loadout-2").orElseThrow().name());
		assertEquals(List.of("loadout-1", "loadout-3"), renamed.without("loadout-2").loadouts().stream().map(Loadout::id).toList());
	}

	@Test
	void lockingAPositionRemovesItsItemAndFreeingUnlocks() {
		Loadout loadout = Loadout.of("l", "x", MINING).withLocked(8);
		assertTrue(!loadout.items().containsKey(8) && loadout.locked().contains(8));
		Loadout free = loadout.withFree(8);
		assertTrue(!free.items().containsKey(8) && !free.locked().contains(8));
		Loadout again = free.withItem(8, MINING.get(8));
		assertEquals(MINING.get(8), again.items().get(8));
	}

	@Test
	void codecRoundTripAndInvalidEntries() throws Exception {
		Loadouts loadouts = Loadouts.EMPTY.withAdded("Mining", MINING);
		loadouts = loadouts.withReplaced(loadouts.byId("loadout-1").orElseThrow().withExact(true).withLocked(35));
		JsonCodec.Decoded<Loadouts> decoded = LoadoutsCodec.INSTANCE.decode(LoadoutsCodec.INSTANCE.encode(loadouts));
		assertEquals(loadouts, decoded.value());
		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());

		JsonCodec.Decoded<Loadouts> invalid = LoadoutsCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "nextId": 1, "loadouts": [
				  { "id": "loadout-7", "name": "Ok", "items": {
				      "0": { "item": "minecraft:torch", "count": 64 },
				      "41": { "item": "minecraft:torch", "count": 1 },
				      "3": { "item": "Not An Id", "count": 1 } },
				    "locked": [5, 99] },
				  { "name": "no id" },
				  "nonsense" ] }
				"""));
		Loadouts value = invalid.value();
		assertEquals(1, value.loadouts().size());
		assertEquals(Map.of(0, new LoadoutItem("minecraft:torch", "", 64)), value.loadouts().getFirst().items());
		assertEquals(8, value.nextId(), "new ids never reuse a loaded one");
		assertEquals(5, invalid.warnings().size(), invalid.warnings().toString());
	}
}
