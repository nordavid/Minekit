package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Moves eligible stacks from the player's inventory into the open container with plain left clicks, one at a time,
 * always from the actual state.
 *
 * <p>With an empty cursor it picks up the first source stack whose item is eligible and still has room in the
 * container. With an item on the cursor it tops up the first partial stack of that item in the container, then uses the
 * first empty container slot, and if the container is full it puts the rest back into the source slot it came from
 * (or, should that slot have been filled meanwhile, another source slot with room). It never clicks a container slot that holds a different item, so items are never
 * swapped out of the container and bundles are never filled by accident.
 *
 * <p>Every pickup moves at least one item into the container, so the process ends; {@link #maxClicks} bounds it.
 */
public final class DepositClickPlanner implements ClickPlanner {
	private final int[] container;
	private final int[] sources;
	private final Predicate<ItemKey> eligible;
	private final ToIntFunction<ItemKey> maxStackSize;
	/** Source slot of the stack picked up last; a remainder that does not fit goes back there. */
	private int lastPickup = -1;

	/**
	 * @param container    menu indices of the container's slots, in fill order
	 * @param sources      menu indices of the player slots that may be emptied, in deposit order
	 * @param eligible     items that may be deposited; must already exclude items the container's slots reject
	 * @param maxStackSize max stack size per item (container slots hold up to the item's max stack size)
	 */
	public DepositClickPlanner(int[] container, int[] sources, Predicate<ItemKey> eligible, ToIntFunction<ItemKey> maxStackSize) {
		this.container = container.clone();
		this.sources = sources.clone();
		this.eligible = Objects.requireNonNull(eligible, "eligible");
		this.maxStackSize = Objects.requireNonNull(maxStackSize, "maxStackSize");
	}

	/** Enough clicks for any deposit: each source is picked up at most once, each container slot fills up at most once. */
	public static int maxClicks(int containerSlots, int sourceSlots) {
		return 2 * sourceSlots + containerSlots + 8;
	}

	public int maxClicks() {
		return maxClicks(container.length, sources.length);
	}

	@Override
	public boolean isComplete(ContainerState state) {
		return state.carried() == null && nextClick(state).isEmpty();
	}

	@Override
	public OptionalInt nextClick(ContainerState state) {
		SlotStack cursor = state.carried();
		if (cursor == null) {
			for (int source : sources) {
				SlotStack stack = state.slot(source);
				if (stack != null && eligible.test(stack.key()) && hasRoom(state, stack.key())) {
					lastPickup = source;
					return OptionalInt.of(source);
				}
			}
			return OptionalInt.empty();
		}
		int target = placeInContainer(state, cursor.key());
		if (target >= 0) {
			return OptionalInt.of(target);
		}
		return putBack(state, cursor.key());
	}

	private boolean hasRoom(ContainerState state, ItemKey key) {
		return placeInContainer(state, key) >= 0;
	}

	/** First partial stack of {@code key} in the container, else the first empty container slot, else -1. */
	private int placeInContainer(ContainerState state, ItemKey key) {
		int max = maxStackSize.applyAsInt(key);
		int firstEmpty = -1;
		for (int slot : container) {
			SlotStack stack = state.slot(slot);
			if (stack == null) {
				if (firstEmpty < 0) {
					firstEmpty = slot;
				}
			} else if (stack.key().equals(key) && stack.count() < max) {
				return slot;
			}
		}
		return firstEmpty;
	}

	private OptionalInt putBack(ContainerState state, ItemKey key) {
		int max = maxStackSize.applyAsInt(key);
		if (lastPickup >= 0) {
			SlotStack origin = state.slot(lastPickup);
			if (origin == null || origin.key().equals(key) && origin.count() < max) {
				return OptionalInt.of(lastPickup);
			}
		}
		for (int source : sources) {
			SlotStack stack = state.slot(source);
			if (stack != null && stack.key().equals(key) && stack.count() < max) {
				return OptionalInt.of(source);
			}
		}
		for (int source : sources) {
			if (state.slot(source) == null) {
				return OptionalInt.of(source);
			}
		}
		return OptionalInt.empty();
	}
}
