package io.github.nordavid.minekit.feature.inventory;

/**
 * Menu slot indices of the player's own inventory menu (container id 0), as defined by vanilla's
 * {@code InventoryMenu}: 0 crafting result, 1-4 crafting grid, 5-8 armor, 9-35 main inventory, 36-44 hotbar, 45 offhand.
 */
public final class InventorySlots {
	public static final int MENU_SIZE = 46;
	public static final int MAIN_START = 9;
	public static final int MAIN_END = 36;
	public static final int HOTBAR_START = 36;
	public static final int HOTBAR_END = 45;

	private InventorySlots() {
	}

	/**
	 * Slots a sort may use: the main inventory row by row, then every hotbar slot whose rule is not
	 * {@link HotbarRule.Kind#KEEP}. Armor, offhand and crafting slots are never touched.
	 */
	public static int[] sortRegion(HotbarLayout hotbar) {
		int kept = (int) hotbar.slots().stream().filter(rule -> rule.kind() == HotbarRule.Kind.KEEP).count();
		int[] region = new int[(MAIN_END - MAIN_START) + HotbarLayout.SIZE - kept];
		int index = 0;
		for (int slot = MAIN_START; slot < MAIN_END; slot++) {
			region[index++] = slot;
		}
		for (int i = 0; i < HotbarLayout.SIZE; i++) {
			if (hotbar.rule(i).kind() != HotbarRule.Kind.KEEP) {
				region[index++] = HOTBAR_START + i;
			}
		}
		return region;
	}
}
