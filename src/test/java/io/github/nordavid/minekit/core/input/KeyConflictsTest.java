package io.github.nordavid.minekit.core.input;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KeyConflictsTest {
	private static final Set<String> OWN = Set.of("key.minekit.dashboard", "key.minekit.quick_actions");

	private static KeyConflicts.Binding binding(String name, String key) {
		return new KeyConflicts.Binding(name, key);
	}

	@Test
	void noConflictWhenKeysDiffer() {
		List<KeyConflicts.Binding> all = List.of(
				binding("key.minekit.dashboard", "key.keyboard.k"),
				binding("key.minekit.quick_actions", "key.keyboard.j"),
				binding("key.inventory", "key.keyboard.e"));

		assertEquals(List.of(), KeyConflicts.find(OWN, all));
	}

	@Test
	void reportsConflictWithForeignMapping() {
		List<KeyConflicts.Binding> all = List.of(
				binding("key.inventory", "key.keyboard.k"),
				binding("key.minekit.dashboard", "key.keyboard.k"));

		assertEquals(List.of(new KeyConflicts.Conflict("key.minekit.dashboard", "key.inventory", "key.keyboard.k")),
				KeyConflicts.find(OWN, all));
	}

	@Test
	void conflictBetweenTwoOwnMappingsIsReportedOnce() {
		List<KeyConflicts.Binding> all = List.of(
				binding("key.minekit.dashboard", "key.keyboard.k"),
				binding("key.minekit.quick_actions", "key.keyboard.k"));

		assertEquals(List.of(new KeyConflicts.Conflict("key.minekit.dashboard", "key.minekit.quick_actions", "key.keyboard.k")),
				KeyConflicts.find(OWN, all));
	}

	@Test
	void unboundMappingsNeverConflict() {
		List<KeyConflicts.Binding> all = List.of(
				binding("key.minekit.dashboard", KeyConflicts.UNBOUND),
				binding("key.other", KeyConflicts.UNBOUND));

		assertEquals(List.of(), KeyConflicts.find(OWN, all));
	}

	@Test
	void foreignConflictsAmongEachOtherAreIgnored() {
		List<KeyConflicts.Binding> all = List.of(
				binding("key.a", "key.keyboard.x"),
				binding("key.b", "key.keyboard.x"));

		assertEquals(List.of(), KeyConflicts.find(OWN, all));
	}
}
