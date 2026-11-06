package io.github.nordavid.minekit.feature.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One {@link HotbarRule} per hotbar slot, left to right.
 *
 * @param slots exactly {@link #SIZE} rules
 */
public record HotbarLayout(List<HotbarRule> slots) {
	public static final int SIZE = 9;

	/** Sorting leaves the whole hotbar alone. The default (PROJECT_PLAN.md: hotbar protected by default). */
	public static final HotbarLayout KEEP_ALL = new HotbarLayout(Collections.nCopies(SIZE, HotbarRule.KEEP));
	/** The hotbar takes the first items in sort order. What "do not protect the hotbar" meant before rules existed. */
	public static final HotbarLayout ANY_ALL = new HotbarLayout(Collections.nCopies(SIZE, HotbarRule.ANY));
	/** A common arrangement to start from. */
	public static final HotbarLayout SUGGESTED = new HotbarLayout(List.of(
			HotbarRule.of(HotbarRule.Kind.MELEE), HotbarRule.of(HotbarRule.Kind.PICKAXE), HotbarRule.of(HotbarRule.Kind.AXE),
			HotbarRule.of(HotbarRule.Kind.SHOVEL), HotbarRule.of(HotbarRule.Kind.RANGED), HotbarRule.of(HotbarRule.Kind.BLOCKS),
			HotbarRule.of(HotbarRule.Kind.BLOCKS), HotbarRule.of(HotbarRule.Kind.UTILITY), HotbarRule.of(HotbarRule.Kind.FOOD)));

	public HotbarLayout {
		slots = List.copyOf(slots);
		if (slots.size() != SIZE) {
			throw new IllegalArgumentException("a hotbar layout has " + SIZE + " slots, not " + slots.size());
		}
	}

	/** Rule of hotbar slot {@code slot} (0 = leftmost). */
	public HotbarRule rule(int slot) {
		return slots.get(slot);
	}

	public HotbarLayout with(int slot, HotbarRule rule) {
		Objects.checkIndex(slot, SIZE);
		List<HotbarRule> next = new ArrayList<>(slots);
		next.set(slot, Objects.requireNonNull(rule, "rule"));
		return new HotbarLayout(next);
	}

	/** Whether sorting leaves every hotbar slot alone. */
	public boolean keepsAll() {
		return slots.stream().allMatch(rule -> rule.kind() == HotbarRule.Kind.KEEP);
	}
}
