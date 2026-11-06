package io.github.nordavid.minekit.feature.loadout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.PickupSimulator;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.feature.loadout.LoadoutPlanner.Plan;
import io.github.nordavid.minekit.feature.loadout.LoadoutPlanner.SlotStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class LoadoutPlannerTest {
	static final ItemKey PICKAXE = ItemKey.of("minecraft:diamond_pickaxe");
	static final ItemKey PICKAXE_ENCHANTED = new ItemKey("minecraft:diamond_pickaxe", "{ench}");
	static final ItemKey PICKAXE_ENCHANTED_WORN = new ItemKey("minecraft:diamond_pickaxe", "{damage=9,ench}");
	static final ItemKey SWORD = ItemKey.of("minecraft:iron_sword");
	static final ItemKey TORCH = ItemKey.of("minecraft:torch");
	static final ItemKey BREAD = ItemKey.of("minecraft:bread");
	static final ItemKey DIRT = ItemKey.of("minecraft:dirt");
	static final ItemKey PEARL = ItemKey.of("minecraft:ender_pearl");
	static final ItemKey HELMET = ItemKey.of("minecraft:iron_helmet");
	static final ItemKey BOOTS = ItemKey.of("minecraft:iron_boots");
	static final ItemKey PUMPKIN = ItemKey.of("minecraft:carved_pumpkin");
	static final List<ItemKey> ALL = List.of(PICKAXE, PICKAXE_ENCHANTED, PICKAXE_ENCHANTED_WORN, SWORD, TORCH, BREAD, DIRT, PEARL, HELMET,
			BOOTS, PUMPKIN);

	static final LoadoutFacts FACTS = new LoadoutFacts() {
		@Override
		public int maxStackSize(ItemKey key) {
			return switch (key.itemId()) {
				case "minecraft:torch", "minecraft:bread", "minecraft:dirt", "minecraft:carved_pumpkin" -> 64;
				case "minecraft:ender_pearl" -> 16;
				default -> 1;
			};
		}

		@Override
		public String exactVariant(ItemKey key) {
			return key.variant().replace("damage=9,", "");
		}

		@Override
		public boolean fitsArmorSlot(ItemKey key, int inventoryIndex) {
			return (key.equals(HELMET) && inventoryIndex == LoadoutSlots.HEAD) || (key.equals(BOOTS) && inventoryIndex == LoadoutSlots.FEET);
		}
	};

	/** A chest screen: 27 chest slots, then the main inventory and the hotbar; no armor, no offhand. */
	static LoadoutLayout chestLayout() {
		int[] slots = new int[LoadoutSlots.SIZE];
		java.util.Arrays.fill(slots, -1);
		for (int index = LoadoutSlots.MAIN_START; index < LoadoutSlots.MAIN_END; index++) {
			slots[index] = 27 + index - LoadoutSlots.MAIN_START;
		}
		for (int index = 0; index < 9; index++) {
			slots[index] = 54 + index;
		}
		int[] chest = new int[27];
		for (int slot = 0; slot < 27; slot++) {
			chest[slot] = slot;
		}
		return new LoadoutLayout(slots, chest);
	}

	static List<SlotStack> emptyMenu(int size) {
		return new ArrayList<>(Collections.nCopies(size, null));
	}

	static LoadoutItem item(ItemKey key, int count) {
		return new LoadoutItem(key.itemId(), FACTS.exactVariant(key), count);
	}

	static SlotStack stack(ItemKey key, int count) {
		return new SlotStack(key, count);
	}

	private static final LoadoutLayout INVENTORY = LoadoutLayout.inventoryMenu();

	/** Menu slot of an inventory position in the player's own inventory screen. */
	private static int inv(int index) {
		return INVENTORY.menuSlot(index);
	}

	private static SlotStatus status(Plan plan, int index) {
		return plan.slots().stream().filter(slot -> slot.index() == index).findFirst().orElseThrow().status();
	}

	@Test
	void reportsPresentFilledPartialAndMissingAndPlansTheMoves() {
		List<SlotStack> slots = emptyMenu(46);
		slots.set(inv(20), stack(PICKAXE, 1));
		slots.set(inv(8), stack(BREAD, 5));
		slots.set(inv(1), stack(SWORD, 1));
		slots.set(inv(30), stack(DIRT, 64));
		ContainerState state = new ContainerState(slots, null);
		Loadout loadout = Loadout.of("l", "Mining", Map.of(0, item(PICKAXE, 1), 1, item(SWORD, 1), 8, item(BREAD, 16),
				LoadoutSlots.HEAD, item(HELMET, 1)));

		Plan plan = LoadoutPlanner.plan(state, INVENTORY, loadout, FACTS);

		assertEquals(SlotStatus.FILLED, status(plan, 0));
		assertEquals(SlotStatus.PRESENT, status(plan, 1));
		assertEquals(SlotStatus.PARTIAL, status(plan, 8));
		assertEquals(SlotStatus.MISSING, status(plan, LoadoutSlots.HEAD));
		assertEquals(Map.of("minecraft:bread", 11, "minecraft:iron_helmet", 1), plan.missing());
		assertEquals(Map.of("minecraft:dirt", 64), plan.extra());
		ContainerState after = plan.expectedResult();
		assertEquals(stack(PICKAXE, 1), after.slot(inv(0)));
		assertEquals(null, after.slot(inv(20)));
		assertEquals(stack(DIRT, 64), after.slot(inv(30)), "items the loadout does not want stay where they are");
		assertEquals(Optional.empty(), plan.refusal());
	}

	@Test
	void whatAFilledPositionHeldMovesToAFreeSlotAndStaysIfThereIsNone() {
		List<SlotStack> slots = emptyMenu(46);
		slots.set(inv(0), stack(DIRT, 64));
		slots.set(inv(20), stack(PICKAXE, 1));
		Loadout loadout = Loadout.of("l", "Tools", Map.of(0, item(PICKAXE, 1)));

		ContainerState after = LoadoutPlanner.plan(new ContainerState(slots, null), INVENTORY, loadout, FACTS).expectedResult();
		assertEquals(stack(PICKAXE, 1), after.slot(inv(0)));
		assertEquals(stack(DIRT, 64), after.slot(inv(9)), "the dirt goes to the first free slot of the main inventory");

		// Chest screen with a full inventory: the pickaxe is in the chest, the dirt has nowhere to go.
		LoadoutLayout chest = chestLayout();
		List<SlotStack> full = emptyMenu(63);
		for (int index = 0; index < LoadoutSlots.MAIN_END; index++) {
			full.set(chest.menuSlot(index), stack(DIRT, 64));
		}
		full.set(4, stack(PICKAXE, 1));
		Plan plan = LoadoutPlanner.plan(new ContainerState(full, null), chest, loadout, FACTS);
		assertEquals(SlotStatus.NO_ROOM, status(plan, 0));
		assertEquals(Optional.of(LoadoutPlanner.Refusal.NOTHING_TO_DO), plan.refusal());
	}

	@Test
	void theContainerOnlyGivesAndArmorIsNotShownInAChestScreen() {
		LoadoutLayout chest = chestLayout();
		List<SlotStack> slots = emptyMenu(63);
		slots.set(chest.menuSlot(1), stack(TORCH, 10));
		slots.set(3, stack(TORCH, 40));
		slots.set(5, stack(TORCH, 40));
		slots.set(7, stack(BREAD, 64));
		ContainerState state = new ContainerState(slots, null);
		Loadout loadout = Loadout.of("l", "Caves", Map.of(1, item(TORCH, 64), LoadoutSlots.HEAD, item(HELMET, 1)));

		Plan plan = LoadoutPlanner.plan(state, chest, loadout, FACTS);

		ContainerState after = plan.expectedResult();
		assertEquals(stack(TORCH, 64), after.slot(chest.menuSlot(1)));
		assertEquals(null, after.slot(3));
		assertEquals(stack(TORCH, 26), after.slot(5), "the rest of the last stack stays in the chest");
		assertEquals(stack(BREAD, 64), after.slot(7));
		assertEquals(Map.of(TORCH, 54), plan.taken());
		assertEquals(SlotStatus.NOT_HERE, status(plan, LoadoutSlots.HEAD));
		assertTrue(plan.missing().isEmpty(), "positions this screen does not show are not reported as missing");
	}

	@Test
	void exactLoadoutsCompareComponentsButNotWear() {
		List<SlotStack> slots = emptyMenu(46);
		slots.set(inv(9), stack(PICKAXE, 1));
		slots.set(inv(10), stack(PICKAXE_ENCHANTED_WORN, 1));
		ContainerState state = new ContainerState(slots, null);
		Loadout loose = Loadout.of("l", "Any pickaxe", Map.of(0, item(PICKAXE_ENCHANTED, 1)));

		assertEquals(stack(PICKAXE, 1), LoadoutPlanner.plan(state, INVENTORY, loose, FACTS).expectedResult().slot(inv(0)),
				"without exact matching any diamond pickaxe will do");
		assertEquals(stack(PICKAXE_ENCHANTED_WORN, 1),
				LoadoutPlanner.plan(state, INVENTORY, loose.withExact(true), FACTS).expectedResult().slot(inv(0)),
				"exact matching takes the enchanted one, even though it is worn");
	}

	@Test
	void lockedPositionsAreNeverTouchedAndArmorTakesOnlyWhatFits() {
		List<SlotStack> slots = emptyMenu(46);
		slots.set(inv(3), stack(PICKAXE, 1));
		slots.set(inv(12), stack(PUMPKIN, 1));
		slots.set(inv(13), stack(HELMET, 1));
		ContainerState state = new ContainerState(slots, null);
		Loadout loadout = Loadout.of("l", "Locked", Map.of(0, item(PICKAXE, 1), LoadoutSlots.HEAD, item(HELMET, 1))).withLocked(3);

		Plan plan = LoadoutPlanner.plan(state, INVENTORY, loadout, FACTS);

		assertEquals(SlotStatus.MISSING, status(plan, 0), "the only pickaxe is in a locked position");
		assertEquals(SlotStatus.FILLED, status(plan, LoadoutSlots.HEAD));
		assertEquals(stack(PICKAXE, 1), plan.expectedResult().slot(inv(3)));
		assertTrue(!plan.clicks().contains(inv(3)), "a locked position is never clicked");
	}

	@Test
	void anItemOnTheCursorRefuses() {
		ContainerState state = new ContainerState(emptyMenu(46), stack(DIRT, 1));
		Plan plan = LoadoutPlanner.plan(state, INVENTORY, Loadout.of("l", "x", Map.of(0, item(DIRT, 1))), FACTS);
		assertEquals(Optional.of(LoadoutPlanner.Refusal.CURSOR_NOT_EMPTY), plan.refusal());
	}

	private record Run(ContainerState end, int clicks) {
	}

	/** Executes the plan click by click with the simulator, checking the safety rules after every click. */
	private static Run run(ContainerState start, Plan plan, LoadoutLayout layout, Loadout loadout, String context) {
		ClickPlanner planner = plan.clickPlanner(FACTS::maxStackSize);
		ContainerState state = start;
		int limit = plan.clicks().size() + 1;
		for (int clicks = 0; clicks <= limit; clicks++) {
			if (planner.isComplete(state)) {
				return new Run(state, clicks);
			}
			OptionalInt next = planner.nextClick(state);
			if (next.isEmpty()) {
				return fail("stuck after " + clicks + " clicks: " + context);
			}
			state = PickupSimulator.leftClick(state, next.getAsInt(), FACTS::maxStackSize);
			for (int slot : layout.containerSlots()) {
				SlotStack now = state.slot(slot);
				SlotStack before = start.slot(slot);
				if (now != null && (before == null || !now.key().equals(before.key()))) {
					fail("something was put into container slot " + slot + ": " + now + " in " + context);
				}
			}
			for (int index = LoadoutSlots.FEET; index <= LoadoutSlots.HEAD; index++) {
				int slot = layout.menuSlot(index);
				SlotStack now = slot < 0 ? null : state.slot(slot);
				if (now != null && !now.equals(start.slot(slot)) && !FACTS.fitsArmorSlot(now.key(), index)) {
					fail("armor position " + index + " got " + now + " in " + context);
				}
			}
		}
		return fail("no result after " + limit + " clicks: " + context);
	}

	@Test
	void randomInventoriesAndLoadouts() {
		Random random = new Random(20261107L);
		for (int i = 0; i < 3_000; i++) {
			boolean chest = random.nextBoolean();
			LoadoutLayout layout = chest ? chestLayout() : INVENTORY;
			int size = chest ? 63 : 46;
			List<SlotStack> slots = emptyMenu(size);
			double fill = random.nextDouble();
			for (int slot = 0; slot < size; slot++) {
				if (slot >= 0 && slot <= 4 && !chest) {
					continue; // crafting slots of the inventory screen
				}
				if (random.nextDouble() < fill) {
					ItemKey key = ALL.get(random.nextInt(ALL.size()));
					if (!chest && slot >= 5 && slot <= 8) {
						key = slot == 5 ? HELMET : slot == 8 ? BOOTS : null;
						if (key == null) {
							continue;
						}
					}
					slots.set(slot, stack(key, 1 + random.nextInt(FACTS.maxStackSize(key))));
				}
			}
			TreeMap<Integer, LoadoutItem> items = new TreeMap<>();
			TreeSet<Integer> locked = new TreeSet<>();
			int wanted = random.nextInt(15);
			for (int n = 0; n < wanted; n++) {
				int index = random.nextInt(LoadoutSlots.SIZE);
				ItemKey key = ALL.get(random.nextInt(ALL.size()));
				items.put(index, item(key, 1 + random.nextInt(FACTS.maxStackSize(key))));
			}
			if (random.nextInt(3) == 0) {
				locked.add(random.nextInt(LoadoutSlots.SIZE));
			}
			Loadout loadout = new Loadout("l" + i, "Random", random.nextBoolean(), items, locked);
			ContainerState start = new ContainerState(slots, null);
			String context = "case #" + i + " " + loadout + " " + start;

			Plan plan = LoadoutPlanner.plan(start, layout, loadout, FACTS);
			Run result = run(start, plan, layout, loadout, context);

			assertEquals(plan.expectedResult(), result.end(), context);
			assertNull(result.end().carried(), context);
			assertEquals(start.totals(), result.end().totals(), "items lost or created in " + context);
			java.util.Set<Integer> clickable = new java.util.HashSet<>();
			for (int index = 0; index < LoadoutSlots.SIZE; index++) {
				if (layout.menuSlot(index) >= 0 && !loadout.locked().contains(index)) {
					clickable.add(layout.menuSlot(index));
				}
			}
			for (int slot : layout.containerSlots()) {
				clickable.add(slot);
			}
			for (int slot : plan.clicks()) {
				assertTrue(clickable.contains(slot), "clicked slot " + slot + " that is locked or not shown in " + context);
			}
			Plan again = LoadoutPlanner.plan(result.end(), layout, loadout, FACTS);
			assertEquals(Optional.of(LoadoutPlanner.Refusal.NOTHING_TO_DO), again.refusal(), "applying twice must change nothing: " + context);
		}
	}
}
