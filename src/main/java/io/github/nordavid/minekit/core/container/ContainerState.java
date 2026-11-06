package io.github.nordavid.minekit.core.container;

import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of a container menu: every slot (by menu slot index) plus the stack on the cursor.
 * Pure model; built from Minecraft state by an adapter.
 */
public final class ContainerState {
	private final @Nullable SlotStack[] slots;
	private final @Nullable SlotStack carried;

	public ContainerState(List<@Nullable SlotStack> slots, @Nullable SlotStack carried) {
		this(slots.toArray(new SlotStack[0]), carried);
	}

	private ContainerState(@Nullable SlotStack[] slots, @Nullable SlotStack carried) {
		this.slots = slots;
		this.carried = carried;
	}

	public int size() {
		return slots.length;
	}

	public @Nullable SlotStack slot(int index) {
		return slots[index];
	}

	public @Nullable SlotStack carried() {
		return carried;
	}

	/** Copy with one slot and the cursor replaced. */
	public ContainerState with(int index, @Nullable SlotStack slot, @Nullable SlotStack newCarried) {
		SlotStack[] copy = slots.clone();
		copy[index] = slot;
		return new ContainerState(copy, newCarried);
	}

	/**
	 * Item counts over all slots and the cursor. Equal totals before and after an operation mean nothing was lost or
	 * duplicated.
	 */
	public Map<ItemKey, Integer> totals() {
		Map<ItemKey, Integer> totals = new TreeMap<>();
		for (SlotStack stack : slots) {
			if (stack != null) {
				totals.merge(stack.key(), stack.count(), Integer::sum);
			}
		}
		if (carried != null) {
			totals.merge(carried.key(), carried.count(), Integer::sum);
		}
		return totals;
	}

	/** Indices whose contents differ between two states of the same size, for diagnostics. */
	public List<Integer> differingSlots(ContainerState other) {
		if (other.size() != size()) {
			throw new IllegalArgumentException("different container sizes");
		}
		return IntStream.range(0, size()).filter(i -> !Objects.equals(slots[i], other.slots[i])).boxed().toList();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof ContainerState that && Arrays.equals(slots, that.slots) && Objects.equals(carried, that.carried);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(slots) + Objects.hashCode(carried);
	}

	@Override
	public String toString() {
		return "ContainerState" + Arrays.toString(slots) + " carried=" + carried;
	}
}
