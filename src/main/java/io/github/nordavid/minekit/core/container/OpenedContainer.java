package io.github.nordavid.minekit.core.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

/**
 * A container screen the player opened by right-clicking a supported container block, as identified by
 * {@link ContainerTracker}.
 *
 * @param menu      the open menu
 * @param kind      what kind of container it is
 * @param blockId   normalized block id of the container (see {@link ContainerTypes#normalize})
 * @param dimension dimension id; empty for an ender chest
 * @param positions block positions; two for a double chest, none for an ender chest
 */
public record OpenedContainer(AbstractContainerMenu menu, Kind kind, String blockId, String dimension, List<BlockPos> positions) {

	/** Supported containers: plain storage whose slots accept items like the player inventory does. */
	public enum Kind {
		CHEST,
		BARREL,
		ENDER_CHEST,
		SHULKER_BOX,
		HOPPER,
		DISPENSER
	}

	public OpenedContainer {
		Objects.requireNonNull(menu, "menu");
		Objects.requireNonNull(kind, "kind");
		positions = List.copyOf(positions);
	}

	/**
	 * Whether the server has sent the contents. A new menu starts with state id 0; the server's first full update
	 * always carries a higher one. Before that the slots are empty and must not be recorded.
	 */
	public boolean isSynced() {
		return menu.getStateId() != 0;
	}

	/** Menu indices of the container's own slots, in menu order. */
	public int[] containerSlots() {
		List<Integer> indices = new ArrayList<>();
		for (int index = 0; index < menu.slots.size(); index++) {
			if (!(menu.slots.get(index).container instanceof Inventory)) {
				indices.add(index);
			}
		}
		return indices.stream().mapToInt(Integer::intValue).toArray();
	}

	/** Menu indices of the player's main inventory (inventory slots 9-35), in inventory order. */
	public int[] mainInventorySlots() {
		return playerSlots(9, 36);
	}

	/** Menu indices of the player's hotbar (inventory slots 0-8), in inventory order. */
	public int[] hotbarSlots() {
		return playerSlots(0, 9);
	}

	private int[] playerSlots(int from, int to) {
		int[] result = new int[to - from];
		int found = 0;
		for (int index = 0; index < menu.slots.size(); index++) {
			Slot slot = menu.slots.get(index);
			if (slot.container instanceof Inventory && slot.getContainerSlot() >= from && slot.getContainerSlot() < to) {
				result[slot.getContainerSlot() - from] = index;
				found++;
			}
		}
		if (found != to - from) {
			throw new IllegalStateException("menu does not contain the player's inventory slots " + from + "-" + (to - 1));
		}
		return result;
	}
}
