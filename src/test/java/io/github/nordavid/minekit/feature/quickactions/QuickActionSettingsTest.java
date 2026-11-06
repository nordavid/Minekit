package io.github.nordavid.minekit.feature.quickactions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class QuickActionSettingsTest {
	private static final List<String> ACTIONS = List.of("dashboard", "settings", "sort", "craft", "finder", "loadouts");

	private static List<String> arrange(QuickActionSettings settings) {
		return settings.arrange(ACTIONS, Function.identity());
	}

	@Test
	void pinnedComeFirstThenRecentThenTheRestAndHiddenAreLeftOut() {
		QuickActionSettings settings = QuickActionSettings.DEFAULT
				.withPinToggled("finder")
				.withPinToggled("sort")
				.withUsed("craft")
				.withUsed("sort")
				.withHiddenToggled("settings");

		assertEquals(List.of("finder", "sort", "craft", "dashboard", "loadouts"), arrange(settings));
		assertEquals(2, settings.pinnedCount(arrange(settings), Function.identity()));
		assertEquals(List.of("sort", "craft"), settings.recent());
	}

	@Test
	void pinnedOrderChangesAndStopsAtTheEnds() {
		QuickActionSettings settings = QuickActionSettings.DEFAULT.withPinToggled("sort").withPinToggled("craft").withPinToggled("finder");

		assertEquals(List.of("craft", "sort", "finder"), settings.withPinnedMoved("craft", -1).pinned());
		assertEquals(settings, settings.withPinnedMoved("sort", -1));
		assertEquals(settings, settings.withPinnedMoved("finder", +1));
		assertEquals(List.of("sort", "finder"), settings.withPinToggled("craft").pinned());
	}

	@Test
	void hidingUnpinsAndPinningShowsAgain() {
		QuickActionSettings hidden = QuickActionSettings.DEFAULT.withPinToggled("sort").withHiddenToggled("sort");
		assertFalse(hidden.isPinned("sort"));
		assertTrue(hidden.isHidden("sort"));

		QuickActionSettings pinnedAgain = hidden.withPinToggled("sort");
		assertTrue(pinnedAgain.isPinned("sort"));
		assertFalse(pinnedAgain.isHidden("sort"));
	}

	@Test
	void recentKeepsTheLastFiveWithoutDuplicates() {
		QuickActionSettings settings = QuickActionSettings.DEFAULT;
		for (String id : List.of("a", "b", "c", "d", "e", "f", "b")) {
			settings = settings.withUsed(id);
		}
		assertEquals(List.of("b", "f", "e", "d", "c"), settings.recent());
	}

	@Test
	void unknownIdsAreSkippedWhenArranging() {
		QuickActionSettings settings = QuickActionSettings.DEFAULT.withPinToggled("deleted-loadout").withPinToggled("sort");
		assertEquals(List.of("sort", "dashboard", "settings", "craft", "finder", "loadouts"), arrange(settings));
	}

	@Test
	void codecRoundTripAndInvalidEntries() throws Exception {
		QuickActionSettings settings = QuickActionSettings.DEFAULT.withPinToggled("sort").withHiddenToggled("craft").withUsed("finder");
		JsonCodec.Decoded<QuickActionSettings> decoded = QuickActionSettingsCodec.INSTANCE.decode(QuickActionSettingsCodec.INSTANCE.encode(settings));
		assertEquals(settings, decoded.value());
		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());

		JsonCodec.Decoded<QuickActionSettings> invalid = QuickActionSettingsCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "pinned": ["sort", "Not Valid", 3], "hidden": "craft", "recent": ["sort", "sort"] }
				"""));
		assertEquals(List.of("sort"), invalid.value().pinned());
		assertEquals(Set.of(), invalid.value().hidden());
		assertEquals(List.of("sort"), invalid.value().recent());
		assertEquals(3, invalid.warnings().size(), invalid.warnings().toString());
	}
}
