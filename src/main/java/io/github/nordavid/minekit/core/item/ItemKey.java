package io.github.nordavid.minekit.core.item;

import java.util.Comparator;
import java.util.Objects;

/**
 * Identity of an item stack for MINEKIT's logic: registry id plus a variant that distinguishes data components.
 *
 * <p>Two stacks have the same key exactly when Minecraft would merge them ({@code ItemStack.isSameItemSameComponents}).
 * The Minecraft adapter guarantees that; pure logic may therefore treat equal keys as mergeable. Display names are
 * never part of the identity.
 *
 * @param itemId  registry id, e.g. {@code minecraft:cobblestone}
 * @param variant empty for a stack without component changes; otherwise a deterministic description of them
 */
public record ItemKey(String itemId, String variant) implements Comparable<ItemKey> {
	private static final Comparator<ItemKey> ORDER = Comparator.comparing(ItemKey::itemId).thenComparing(ItemKey::variant);

	public ItemKey {
		Objects.requireNonNull(itemId, "itemId");
		Objects.requireNonNull(variant, "variant");
	}

	public static ItemKey of(String itemId) {
		return new ItemKey(itemId, "");
	}

	@Override
	public int compareTo(ItemKey other) {
		return ORDER.compare(this, other);
	}

	@Override
	public String toString() {
		return variant.isEmpty() ? itemId : itemId + variant;
	}
}
