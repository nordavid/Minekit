package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import org.junit.jupiter.api.Test;

class BlueprintSettingsCodecTest {
	private static final BlueprintSettingsCodec CODEC = BlueprintSettingsCodec.INSTANCE;

	@Test
	void everythingSurvivesARoundTrip() throws JsonCodec.FormatException {
		BlueprintSettings settings = BlueprintSettings.DEFAULT.withActive("house.schematic").withShow(false).withWhole(false)
				.withPlacement("house.schematic", "sp-0123456789abcdef", new Placement(10, 64, -3, 1, true))
				.withRange("house.schematic", new BlueprintSettings.LayerRange(2, 5));
		BlueprintSettings decoded = CODEC.decode(CODEC.encode(settings)).value();
		assertEquals(settings, decoded);
		assertFalse(decoded.whole());
	}

	@Test
	void filesFromBeforeTheWorldViewShowTheWholeStructure() throws JsonCodec.FormatException {
		// Written by 0.7.x: no "wholeStructure".
		String old = """
				{"schemaVersion": 1, "active": "tower.litematic", "show": true, "placements": {}, "ranges": {}, "maxFileMegabytes": 32,
				 "maxPositions": 16000000}
				""";
		JsonCodec.Decoded<BlueprintSettings> decoded = CODEC.decode(JsonParser.parseString(old));
		assertTrue(decoded.value().whole());
		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());
	}

	@Test
	void invalidEntriesAreIgnoredWithAWarning() throws JsonCodec.FormatException {
		String broken = """
				{"schemaVersion": 1, "active": "../evil.litematic", "wholeStructure": false,
				 "placements": {"a.schem": {"not-a-world": {"x": 1, "y": 2, "z": 3, "rotation": 0, "mirrored": false},
				                            "sp-0123456789abcdef": {"x": 1, "y": 2, "z": 3, "rotation": 7, "mirrored": false}}},
				 "ranges": {"a.schem": [4, 2]}}
				""";
		JsonCodec.Decoded<BlueprintSettings> decoded = CODEC.decode(JsonParser.parseString(broken));
		assertNull(decoded.value().active());
		assertFalse(decoded.value().whole());
		assertTrue(decoded.value().placements().isEmpty());
		assertTrue(decoded.value().ranges().isEmpty());
		assertEquals(4, decoded.warnings().size(), decoded.warnings().toString());
	}
}
