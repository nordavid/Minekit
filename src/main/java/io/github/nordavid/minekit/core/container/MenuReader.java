package io.github.nordavid.minekit.core.container;

import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/** Reads the client's current view of a container menu into the pure {@link ContainerState}. Client thread only. */
public final class MenuReader {
	private MenuReader() {
	}

	public static ContainerState read(AbstractContainerMenu menu, ItemKeyIndex keys) {
		List<SlotStack> slots = new ArrayList<>(menu.slots.size());
		for (Slot slot : menu.slots) {
			slots.add(keys.toSlotStack(slot.getItem()));
		}
		return new ContainerState(slots, keys.toSlotStack(menu.getCarried()));
	}
}
