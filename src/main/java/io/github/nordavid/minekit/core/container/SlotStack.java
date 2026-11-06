package io.github.nordavid.minekit.core.container;

import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.Objects;

/** A non-empty stack in a slot or on the cursor. Empty slots are represented by {@code null}. */
public record SlotStack(ItemKey key, int count) {
	public SlotStack {
		Objects.requireNonNull(key, "key");
		if (count < 1) {
			throw new IllegalArgumentException("count must be positive: " + count);
		}
	}

	public SlotStack withCount(int newCount) {
		return new SlotStack(key, newCount);
	}

	@Override
	public String toString() {
		return count + "x" + key;
	}
}
