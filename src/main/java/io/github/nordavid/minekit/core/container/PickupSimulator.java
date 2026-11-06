package io.github.nordavid.minekit.core.container;

import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.function.ToIntFunction;

/**
 * Predicts the result of a plain left click ({@code ContainerInput.PICKUP}, button 0) on a slot that accepts any item
 * and holds up to the item's max stack size, as the player inventory slots do. This mirrors vanilla's
 * {@code AbstractContainerMenu} behavior for those slots:
 * <ul>
 *   <li>empty cursor, filled slot: pick up the whole stack</li>
 *   <li>filled cursor, empty slot: put the whole cursor stack down</li>
 *   <li>same item on cursor and in slot: move as much as fits, the rest stays on the cursor</li>
 *   <li>different items: swap</li>
 * </ul>
 * MINEKIT compares the real state after each click with this prediction and stops on any difference.
 */
public final class PickupSimulator {
	private PickupSimulator() {
	}

	public static ContainerState leftClick(ContainerState state, int slotIndex, ToIntFunction<ItemKey> maxStackSize) {
		SlotStack slot = state.slot(slotIndex);
		SlotStack cursor = state.carried();
		if (cursor == null) {
			return slot == null ? state : state.with(slotIndex, null, slot);
		}
		if (slot == null) {
			return state.with(slotIndex, cursor, null);
		}
		if (slot.key().equals(cursor.key())) {
			int room = Math.max(0, maxStackSize.applyAsInt(slot.key()) - slot.count());
			int moved = Math.min(room, cursor.count());
			if (moved == 0) {
				return state;
			}
			int left = cursor.count() - moved;
			return state.with(slotIndex, slot.withCount(slot.count() + moved), left == 0 ? null : cursor.withCount(left));
		}
		return state.with(slotIndex, cursor, slot);
	}
}
