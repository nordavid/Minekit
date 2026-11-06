package io.github.nordavid.minekit.core.container;

import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * Moves a region of slots towards a target layout using only plain left clicks, one at a time, always from the
 * actual state. The target must hold exactly the items the region holds (same totals per item) and no stack larger
 * than its max stack size; sorting and loadouts produce such layouts.
 *
 * <p>With an empty cursor it looks at the first slot that is not yet right: a wrong or surplus stack there is picked
 * up; a missing or short stack is fetched from a slot that holds surplus of that item. With an item on the cursor it
 * puts it where that item belongs, preferring (in this order) topping up a short stack, an empty target slot with
 * enough room, swapping with a wrong item, and an empty target slot with less room. Only slots of the region are
 * clicked; the cursor ends empty.
 *
 * <p>If an item ever had no place in the layout (impossible while the totals match), it is put down in an empty
 * <em>parking</em> slot, never anywhere else: callers keep container slots out of the parking slots, so a loadout
 * never puts anything into a chest.
 */
public final class LayoutClickPlanner implements ClickPlanner {
	private final int[] region;
	private final List<@Nullable SlotStack> target;
	private final int[] parking;

	/**
	 * @param region  menu slots that may be clicked
	 * @param target  wanted content of each region slot, same order
	 * @param parking menu slots (of the region) where a stray cursor item may be put down
	 */
	public LayoutClickPlanner(int[] region, List<@Nullable SlotStack> target, int[] parking) {
		if (region.length != target.size()) {
			throw new IllegalArgumentException("region and target differ in size");
		}
		this.region = region.clone();
		this.target = Collections.unmodifiableList(new ArrayList<>(target));
		this.parking = parking.clone();
	}

	@Override
	public boolean isComplete(ContainerState state) {
		if (state.carried() != null) {
			return false;
		}
		for (int pos = 0; pos < region.length; pos++) {
			if (!Objects.equals(state.slot(region[pos]), target.get(pos))) {
				return false;
			}
		}
		return true;
	}

	@Override
	public OptionalInt nextClick(ContainerState state) {
		SlotStack cursor = state.carried();
		return cursor == null ? nextWithEmptyCursor(state) : placeCursor(state, cursor);
	}

	private OptionalInt nextWithEmptyCursor(ContainerState state) {
		for (int pos = 0; pos < region.length; pos++) {
			SlotStack current = state.slot(region[pos]);
			SlotStack wanted = target.get(pos);
			if (Objects.equals(current, wanted)) {
				continue;
			}
			if (current != null && (wanted == null || !current.key().equals(wanted.key()) || current.count() > wanted.count())) {
				return OptionalInt.of(region[pos]);
			}
			int source = findSurplus(state, wanted.key(), pos);
			return source < 0 ? OptionalInt.empty() : OptionalInt.of(region[source]);
		}
		return OptionalInt.empty();
	}

	private OptionalInt placeCursor(ContainerState state, SlotStack cursor) {
		int best = -1;
		int bestScore = -1;
		for (int pos = 0; pos < region.length; pos++) {
			SlotStack wanted = target.get(pos);
			if (wanted == null || !wanted.key().equals(cursor.key())) {
				continue;
			}
			SlotStack current = state.slot(region[pos]);
			int score;
			if (current == null) {
				score = wanted.count() >= cursor.count() ? 3 : 1;
			} else if (current.key().equals(cursor.key())) {
				score = current.count() < wanted.count() ? 4 : -1;
			} else {
				score = 2;
			}
			if (score > bestScore) {
				bestScore = score;
				best = pos;
			}
		}
		if (best >= 0) {
			return OptionalInt.of(region[best]);
		}
		// Only reachable if the item has no place in the layout, which conservation of items rules out. Put it into an
		// empty parking slot so it is never left on the cursor.
		for (int slot : parking) {
			if (state.slot(slot) == null) {
				return OptionalInt.of(slot);
			}
		}
		return OptionalInt.empty();
	}

	/** First region position other than {@code exclude} holding more of {@code key} than the layout wants there. */
	private int findSurplus(ContainerState state, ItemKey key, int exclude) {
		for (int pos = 0; pos < region.length; pos++) {
			if (pos == exclude) {
				continue;
			}
			SlotStack current = state.slot(region[pos]);
			if (current == null || !current.key().equals(key)) {
				continue;
			}
			SlotStack wanted = target.get(pos);
			if (wanted == null || !wanted.key().equals(key) || current.count() > wanted.count()) {
				return pos;
			}
		}
		return -1;
	}
}
