package io.github.nordavid.minekit.core.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Finds Minekit shortcuts that share a key with another shortcut. Pure logic, independent of Minecraft. */
public final class KeyConflicts {
	/** Key name Minecraft uses for an unbound mapping. */
	public static final String UNBOUND = "key.keyboard.unknown";

	/**
	 * @param mappingName translation key that identifies the mapping
	 * @param key         saved key name, e.g. {@code key.keyboard.k}
	 */
	public record Binding(String mappingName, String key) {
	}

	/** {@code ownMapping} (a Minekit shortcut) is bound to the same {@code key} as {@code otherMapping}. */
	public record Conflict(String ownMapping, String otherMapping, String key) {
	}

	private KeyConflicts() {
	}

	/**
	 * @param ownMappings names of the Minekit mappings
	 * @param all         every registered mapping, including the Minekit ones
	 * @return one entry per conflicting pair; a pair of two Minekit mappings is reported once
	 */
	public static List<Conflict> find(Set<String> ownMappings, List<Binding> all) {
		List<Conflict> conflicts = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			Binding own = all.get(i);
			if (!ownMappings.contains(own.mappingName()) || UNBOUND.equals(own.key())) {
				continue;
			}
			for (int j = 0; j < all.size(); j++) {
				Binding other = all.get(j);
				if (i == j || !own.key().equals(other.key())) {
					continue;
				}
				boolean otherIsOwn = ownMappings.contains(other.mappingName());
				if (otherIsOwn && j < i) {
					continue;
				}
				conflicts.add(new Conflict(own.mappingName(), other.mappingName(), own.key()));
			}
		}
		return conflicts;
	}
}
