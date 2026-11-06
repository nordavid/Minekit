package io.github.nordavid.minekit.feature.inventory;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What one hotbar slot should hold after sorting.
 *
 * @param kind   the kind of rule
 * @param itemId for {@link Kind#ITEM}, the item id (any components); {@code null} for every other kind
 */
public record HotbarRule(Kind kind, @Nullable String itemId) {
	/** Rule kinds, in the order the picker shows them. */
	public enum Kind {
		/** Sorting never touches the slot. */
		KEEP,
		/** The next item in sort order. */
		ANY,
		MELEE,
		RANGED,
		PICKAXE,
		AXE,
		SHOVEL,
		HOE,
		FOOD,
		BLOCKS,
		UTILITY,
		/** One specific item. */
		ITEM,
		/** Kept free; filled only if the rest of the inventory has no room. */
		EMPTY;

		/** Whether the slot takes the best matching stack (all kinds except keep, any and empty). */
		public boolean picksMatch() {
			return this != KEEP && this != ANY && this != EMPTY;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final String ITEM_PREFIX = "item:";

	public static final HotbarRule KEEP = new HotbarRule(Kind.KEEP, null);
	public static final HotbarRule ANY = new HotbarRule(Kind.ANY, null);
	public static final HotbarRule EMPTY = new HotbarRule(Kind.EMPTY, null);

	public HotbarRule {
		Objects.requireNonNull(kind, "kind");
		if ((kind == Kind.ITEM) != (itemId != null)) {
			throw new IllegalArgumentException("an item id is required exactly for ITEM rules: " + kind + " " + itemId);
		}
	}

	/** A rule of a kind other than {@link Kind#ITEM}. */
	public static HotbarRule of(Kind kind) {
		return new HotbarRule(kind, null);
	}

	/** A rule for one specific item, any components. */
	public static HotbarRule item(String itemId) {
		return new HotbarRule(Kind.ITEM, Objects.requireNonNull(itemId, "itemId"));
	}

	/** Stable id for the settings file: the kind's id, or {@code item:<item id>}. */
	public String id() {
		return kind == Kind.ITEM ? ITEM_PREFIX + itemId : kind.id();
	}

	/** The rule with this {@link #id()}, if it is valid. */
	public static Optional<HotbarRule> parse(String id) {
		if (id.startsWith(ITEM_PREFIX)) {
			String itemId = id.substring(ITEM_PREFIX.length());
			return isItemId(itemId) ? Optional.of(item(itemId)) : Optional.empty();
		}
		for (Kind kind : Kind.values()) {
			if (kind != Kind.ITEM && kind.id().equals(id)) {
				return Optional.of(of(kind));
			}
		}
		return Optional.empty();
	}

	public String translationKey() {
		return "minekit.hotbar.rule." + kind.id();
	}

	private static boolean isItemId(String id) {
		int colon = id.indexOf(':');
		return colon > 0 && colon < id.length() - 1 && id.indexOf(':', colon + 1) < 0
				&& id.chars().allMatch(c -> c == ':' || c == '_' || c == '-' || c == '.' || c == '/' || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));
	}
}
