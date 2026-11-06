package io.github.nordavid.minekit.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigCodecTest {
	private static JsonCodec.Decoded<MinekitConfig> decode(String json) throws JsonCodec.FormatException {
		return ConfigCodec.INSTANCE.decode(JsonParser.parseString(json));
	}

	@Test
	void roundTripKeepsAllValues() throws Exception {
		MinekitConfig config = MinekitConfig.DEFAULT
				.withModuleEnabled("quick_actions", false)
				.withModuleEnabled("not_installed_module", true)
				.withHudElement("notifications", new HudElementConfig(false, HudAnchor.BOTTOM_LEFT, 12, 30));

		JsonCodec.Decoded<MinekitConfig> decoded = ConfigCodec.INSTANCE.decode(ConfigCodec.INSTANCE.encode(config));

		assertEquals(config, decoded.value());
		assertEquals(ConfigCodec.SCHEMA_VERSION, decoded.schemaVersion());
		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());
	}

	@Test
	void invalidEntriesAreDroppedWithWarningsAndValidOnesKept() throws Exception {
		JsonCodec.Decoded<MinekitConfig> decoded = decode("""
				{
				  "schemaVersion": 1,
				  "modules": {
				    "quick_actions": { "enabled": false },
				    "broken": { "enabled": "yes" },
				    "Not Valid!": { "enabled": true }
				  },
				  "hud": {
				    "notifications": { "visible": true, "anchor": "NOWHERE", "offsetX": 1, "offsetY": 1 },
				    "other": { "visible": true, "anchor": "top_left", "offsetX": 3, "offsetY": 4 }
				  }
				}
				""");

		assertEquals(Optional.of(false), decoded.value().moduleEnabled("quick_actions"));
		assertEquals(Optional.empty(), decoded.value().moduleEnabled("broken"));
		assertEquals(Optional.empty(), decoded.value().hudElement("notifications"));
		assertEquals(Optional.of(new HudElementConfig(true, HudAnchor.TOP_LEFT, 3, 4)), decoded.value().hudElement("other"));
		assertEquals(3, decoded.warnings().size(), decoded.warnings().toString());
	}

	@Test
	void missingSchemaVersionIsAssumedCurrentWithWarning() throws Exception {
		JsonCodec.Decoded<MinekitConfig> decoded = decode("{ \"modules\": {} }");

		assertEquals(ConfigCodec.SCHEMA_VERSION, decoded.schemaVersion());
		assertEquals(1, decoded.warnings().size());
	}

	@Test
	void newerSchemaVersionIsReported() throws Exception {
		assertEquals(7, decode("{ \"schemaVersion\": 7 }").schemaVersion());
	}

	@Test
	void outOfRangeOffsetsAreClamped() throws Exception {
		JsonCodec.Decoded<MinekitConfig> decoded = decode("""
				{ "hud": { "notifications": { "visible": true, "anchor": "TOP_RIGHT", "offsetX": -50, "offsetY": 99999 } } }
				""");

		HudElementConfig element = decoded.value().hudElement("notifications").orElseThrow();
		assertEquals(0, element.offsetX());
		assertEquals(HudElementConfig.MAX_OFFSET, element.offsetY());
	}

	@Test
	void nonObjectRootIsAFormatError() {
		assertThrows(JsonCodec.FormatException.class, () -> decode("[1, 2, 3]"));
	}

	@Test
	void wrongSectionTypeIsIgnoredWithWarning() throws Exception {
		JsonCodec.Decoded<MinekitConfig> decoded = decode("{ \"schemaVersion\": 1, \"modules\": [] }");

		assertTrue(decoded.value().moduleEnabled().isEmpty());
		assertEquals(1, decoded.warnings().size());
	}
}
