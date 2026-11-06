package io.github.nordavid.minekit.core.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TextSearchTest {
	private static final List<String> ACTIONS = List.of(
			"Dashboard öffnen minekit dashboard menu",
			"Einstellungen öffnen settings options",
			"Tastenbelegung ändern keys shortcuts",
			"Inventar sortieren sort");

	private static List<String> search(String query) {
		return TextSearch.filter(ACTIONS, Function.identity(), query);
	}

	@Test
	void emptyOrBlankQueryReturnsEverythingInOriginalOrder() {
		assertEquals(ACTIONS, search(""));
		assertEquals(ACTIONS, search("   "));
	}

	@Test
	void matchingIgnoresCaseAndAccents() {
		assertEquals(List.of(ACTIONS.get(2)), search("ANDERN"));
		assertEquals(List.of(ACTIONS.get(0), ACTIONS.get(1)), search("offnen"));
	}

	@Test
	void prefixOfWholeTextRanksFirstThenWordStartsThenSubstrings() {
		List<String> items = List.of("open the settings", "settings", "reset tings");

		assertEquals(List.of("settings", "open the settings", "reset tings"),
				TextSearch.filter(items, Function.identity(), "set"));
	}

	@Test
	void everyQueryWordMustMatch() {
		assertEquals(List.of(ACTIONS.get(1)), search("öffnen settings"));
		assertEquals(List.of(), search("öffnen sort"));
	}

	@Test
	void keywordsAreSearchable() {
		assertEquals(List.of(ACTIONS.get(2)), search("shortcuts"));
	}

	@Test
	void unknownTermFindsNothing() {
		assertEquals(List.of(), search("xyz"));
	}

	@Test
	void normalizeCollapsesWhitespaceAndStripsMarks() {
		assertEquals("uber sicht", TextSearch.normalize("  Über \t Sicht "));
	}
}
