package io.github.nordavid.minekit.feature.loadout;

import java.util.Arrays;

/**
 * Where the inventory positions of a loadout are in an open menu, and which slots belong to an open container.
 *
 * @param menuSlots      for each inventory position ({@link LoadoutSlots}), its menu slot, or -1 if this menu does not
 *                       show it (armor and offhand in a chest screen)
 * @param containerSlots menu slots of the open container, which applying may take items from; empty in the player's
 *                       own inventory screen
 */
public record LoadoutLayout(int[] menuSlots, int[] containerSlots) {
	public LoadoutLayout {
		if (menuSlots.length != LoadoutSlots.SIZE) {
			throw new IllegalArgumentException("expected " + LoadoutSlots.SIZE + " positions, got " + menuSlots.length);
		}
		menuSlots = menuSlots.clone();
		containerSlots = containerSlots.clone();
	}

	/**
	 * The player's own inventory screen (vanilla {@code InventoryMenu}): 5-8 armor from head to feet, 9-35 main
	 * inventory, 36-44 hotbar, 45 offhand.
	 */
	public static LoadoutLayout inventoryMenu() {
		int[] slots = new int[LoadoutSlots.SIZE];
		for (int index = 0; index < 9; index++) {
			slots[index] = 36 + index;
		}
		for (int index = LoadoutSlots.MAIN_START; index < LoadoutSlots.MAIN_END; index++) {
			slots[index] = index;
		}
		slots[LoadoutSlots.FEET] = 8;
		slots[LoadoutSlots.LEGS] = 7;
		slots[LoadoutSlots.CHEST] = 6;
		slots[LoadoutSlots.HEAD] = 5;
		slots[LoadoutSlots.OFFHAND] = 45;
		return new LoadoutLayout(slots, new int[0]);
	}

	public int menuSlot(int inventoryIndex) {
		return menuSlots[inventoryIndex];
	}

	@Override
	public int[] menuSlots() {
		return menuSlots.clone();
	}

	@Override
	public int[] containerSlots() {
		return containerSlots.clone();
	}

	public boolean hasContainer() {
		return containerSlots.length > 0;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof LoadoutLayout layout && Arrays.equals(menuSlots, layout.menuSlots)
				&& Arrays.equals(containerSlots, layout.containerSlots);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(menuSlots) + Arrays.hashCode(containerSlots);
	}

	@Override
	public String toString() {
		return "LoadoutLayout" + Arrays.toString(menuSlots) + " container " + Arrays.toString(containerSlots);
	}
}
