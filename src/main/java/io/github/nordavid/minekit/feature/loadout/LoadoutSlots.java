package io.github.nordavid.minekit.feature.loadout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Positions in the player's inventory, numbered like vanilla's {@code Inventory}: 0-8 hotbar, 9-35 main inventory,
 * 36-39 armor (feet, legs, chest, head) and 40 offhand. Loadouts store items by these numbers, which do not depend on
 * the screen; {@link LoadoutLayout} maps them to the slots of an open menu.
 */
public final class LoadoutSlots {
	public static final int SIZE = 41;
	public static final int HOTBAR_START = 0;
	public static final int MAIN_START = 9;
	public static final int MAIN_END = 36;
	public static final int FEET = 36;
	public static final int LEGS = 37;
	public static final int CHEST = 38;
	public static final int HEAD = 39;
	public static final int OFFHAND = 40;

	/** Order in which a loadout is applied: armor from the head down, offhand, hotbar, then the main inventory. */
	public static final List<Integer> APPLY_ORDER;

	static {
		List<Integer> order = new ArrayList<>(List.of(HEAD, CHEST, LEGS, FEET, OFFHAND));
		for (int index = HOTBAR_START; index < MAIN_START; index++) {
			order.add(index);
		}
		for (int index = MAIN_START; index < MAIN_END; index++) {
			order.add(index);
		}
		APPLY_ORDER = Collections.unmodifiableList(order);
	}

	private LoadoutSlots() {
	}

	public static boolean isValid(int index) {
		return index >= 0 && index < SIZE;
	}

	public static boolean isArmor(int index) {
		return index >= FEET && index <= HEAD;
	}

	/** Hotbar and main inventory: slots that take any item. */
	public static boolean isStorage(int index) {
		return index >= HOTBAR_START && index < MAIN_END;
	}
}
