package io.github.nordavid.minekit.feature.inventory;

import static io.github.nordavid.minekit.feature.inventory.TestItems.BREAD;
import static io.github.nordavid.minekit.feature.inventory.TestItems.COBBLE;
import static io.github.nordavid.minekit.feature.inventory.TestItems.DIRT;
import static io.github.nordavid.minekit.feature.inventory.TestItems.FACTS;
import static io.github.nordavid.minekit.feature.inventory.TestItems.HELMET;
import static io.github.nordavid.minekit.feature.inventory.TestItems.PEARL;
import static io.github.nordavid.minekit.feature.inventory.TestItems.PICKAXE;
import static io.github.nordavid.minekit.feature.inventory.TestItems.PICKAXE_ENCHANTED;
import static io.github.nordavid.minekit.feature.inventory.TestItems.STICK;
import static io.github.nordavid.minekit.feature.inventory.TestItems.STONE_PICKAXE;
import static io.github.nordavid.minekit.feature.inventory.TestItems.SWORD;
import static io.github.nordavid.minekit.feature.inventory.TestItems.emptyMenu;
import static io.github.nordavid.minekit.feature.inventory.TestItems.stack;
import static io.github.nordavid.minekit.feature.inventory.TestItems.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.SlotStack;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SortPlannerTest {
	private static final SortRules GENERAL = new SortRules(SortProfile.GENERAL.defaultOrder(), Set.of(), HotbarLayout.KEEP_ALL);

	private static SortPlanner.Planned plan(ContainerState state, SortRules rules) {
		return assertInstanceOf(SortPlanner.Planned.class, SortPlanner.plan(state, rules, FACTS));
	}

	private static List<SlotStack> firstTargets(SortPlanner.Planned plan, int count) {
		return plan.target().subList(0, count);
	}

	@Test
	void ordersByCategoryThenItemAndMergesPartialStacks() {
		List<SlotStack> slots = emptyMenu();
		slots.set(9, stack(COBBLE, 30));
		slots.set(12, stack(STICK, 5));
		slots.set(20, stack(SWORD, 1));
		slots.set(21, stack(COBBLE, 50));
		slots.set(30, stack(PICKAXE, 1));
		slots.set(35, stack(BREAD, 3));

		SortPlanner.Planned plan = plan(state(slots), GENERAL);

		// General: tools, weapons, armor, food, utility, resources, blocks, misc. 30 + 50 cobblestone -> 64 + 16.
		assertEquals(List.of(stack(PICKAXE, 1), stack(SWORD, 1), stack(BREAD, 3), stack(COBBLE, 64), stack(COBBLE, 16), stack(STICK, 5)),
				firstTargets(plan, 6));
		assertEquals(null, plan.target().get(6));
	}

	@Test
	void favoritesComeFirstAndProfileChangesOrder() {
		List<SlotStack> slots = emptyMenu();
		slots.set(9, stack(PICKAXE, 1));
		slots.set(10, stack(COBBLE, 10));
		slots.set(11, stack(STICK, 2));

		SortRules building = new SortRules(SortProfile.BUILDING.defaultOrder(), Set.of("minecraft:stick"), HotbarLayout.KEEP_ALL);

		assertEquals(List.of(stack(STICK, 2), stack(COBBLE, 10), stack(PICKAXE, 1)), firstTargets(plan(state(slots), building), 3));
	}

	@Test
	void componentVariantsStaySeparateAndOrderedDeterministically() {
		List<SlotStack> slots = emptyMenu();
		slots.set(15, stack(PICKAXE_ENCHANTED, 1));
		slots.set(9, stack(PICKAXE, 1));

		assertEquals(List.of(stack(PICKAXE, 1), stack(PICKAXE_ENCHANTED, 1)), firstTargets(plan(state(slots), GENERAL), 2));
	}

	@Test
	void keptHotbarSlotsAreNotPartOfTheRegionAndAnySlotsTakeTheFirstItems() {
		List<SlotStack> slots = emptyMenu();
		slots.set(36, stack(HELMET, 1));
		slots.set(20, stack(PEARL, 4));

		SortPlanner.Planned kept = plan(state(slots), GENERAL);
		assertEquals(27, kept.region().length);
		assertEquals(List.of(stack(PEARL, 4)), firstTargets(kept, 1));

		SortPlanner.Planned any = plan(state(slots), withHotbar(HotbarLayout.ANY_ALL));
		assertEquals(36, any.region().length);
		ContainerState after = any.expectedResult(state(slots));
		// General order: armor before utility. The hotbar gets the first items, the main inventory the rest.
		assertEquals(stack(HELMET, 1), after.slot(36));
		assertEquals(stack(PEARL, 4), after.slot(37));
		assertEquals(null, after.slot(9));
	}

	private static SortRules withHotbar(HotbarLayout layout) {
		return new SortRules(GENERAL.categoryOrder(), GENERAL.favorites(), layout);
	}

	private static HotbarLayout layout(HotbarRule... first) {
		HotbarLayout layout = HotbarLayout.KEEP_ALL;
		for (int slot = 0; slot < first.length; slot++) {
			layout = layout.with(slot, first[slot]);
		}
		return layout;
	}

	@Test
	void ruleSlotsTakeTheBestMatchingItem() {
		List<SlotStack> slots = emptyMenu();
		slots.set(36, stack(STONE_PICKAXE, 1));
		slots.set(37, stack(COBBLE, 20));
		slots.set(12, stack(PICKAXE, 1));
		slots.set(15, stack(SWORD, 1));
		slots.set(16, stack(BREAD, 7));
		slots.set(44, stack(STICK, 3));
		HotbarLayout hotbar = layout(HotbarRule.of(HotbarRule.Kind.PICKAXE), HotbarRule.of(HotbarRule.Kind.MELEE),
				HotbarRule.of(HotbarRule.Kind.FOOD), HotbarRule.of(HotbarRule.Kind.BLOCKS));

		ContainerState after = plan(state(slots), withHotbar(hotbar)).expectedResult(state(slots));

		assertEquals(stack(PICKAXE, 1), after.slot(36), "the diamond pickaxe beats the stone pickaxe");
		assertEquals(stack(SWORD, 1), after.slot(37));
		assertEquals(stack(BREAD, 7), after.slot(38));
		assertEquals(stack(COBBLE, 20), after.slot(39));
		assertEquals(stack(STONE_PICKAXE, 1), after.slot(9), "the replaced pickaxe moves to the main inventory");
		assertEquals(stack(STICK, 3), after.slot(44), "slots to keep are not touched");
	}

	@Test
	void favoritesWinARuleSlotAndAnItemRulePinsThatItem() {
		List<SlotStack> slots = emptyMenu();
		slots.set(12, stack(PICKAXE, 1));
		slots.set(13, stack(STONE_PICKAXE, 1));
		slots.set(14, stack(PEARL, 9));
		SortRules rules = new SortRules(GENERAL.categoryOrder(), Set.of("minecraft:stone_pickaxe"),
				layout(HotbarRule.of(HotbarRule.Kind.PICKAXE), HotbarRule.item("minecraft:ender_pearl")));

		ContainerState after = plan(state(slots), rules).expectedResult(state(slots));

		assertEquals(stack(STONE_PICKAXE, 1), after.slot(36));
		assertEquals(stack(PEARL, 9), after.slot(37));
	}

	@Test
	void slotsWithoutMatchAndSlotsToKeepEmptyStayEmptyUntilTheInventoryIsFull() {
		List<SlotStack> slots = emptyMenu();
		slots.set(36, stack(COBBLE, 10));
		slots.set(37, stack(DIRT, 10));
		HotbarLayout hotbar = layout(HotbarRule.of(HotbarRule.Kind.SHOVEL), HotbarRule.EMPTY);

		ContainerState after = plan(state(slots), withHotbar(hotbar)).expectedResult(state(slots));
		assertEquals(null, after.slot(36));
		assertEquals(null, after.slot(37));
		assertEquals(stack(COBBLE, 10), after.slot(9));

		// Main inventory full: the last items in sort order overflow into the slot without a match, then into the one to
		// keep empty.
		for (int slot = InventorySlots.MAIN_START; slot < InventorySlots.MAIN_END; slot++) {
			slots.set(slot, stack(STICK, 64));
		}
		after = plan(state(slots), withHotbar(hotbar)).expectedResult(state(slots));
		assertEquals(stack(COBBLE, 10), after.slot(9));
		assertEquals(stack(STICK, 64), after.slot(36));
		assertEquals(stack(STICK, 64), after.slot(37));
	}

	@Test
	void aSortedHotbarStaysAsItIs() {
		List<SlotStack> slots = emptyMenu();
		slots.set(36, stack(DIRT, 64));
		slots.set(37, stack(COBBLE, 64));
		ContainerState state = state(slots);
		SortRules rules = withHotbar(layout(HotbarRule.of(HotbarRule.Kind.BLOCKS), HotbarRule.of(HotbarRule.Kind.BLOCKS)));

		// Equally good stacks: each stays in the slot it is in.
		assertEquals(0, plan(state, rules).changedSlots(state));
	}

	@Test
	void sameInventoryGivesSameLayoutAndSortedInventoryNeedsNoChange() {
		List<SlotStack> slots = emptyMenu();
		slots.set(30, stack(COBBLE, 7));
		slots.set(10, stack(BREAD, 2));
		ContainerState state = state(slots);

		SortPlanner.Planned first = plan(state, GENERAL);
		assertEquals(first.target(), plan(state, GENERAL).target());

		ContainerState sorted = first.expectedResult(state);
		assertEquals(0, plan(sorted, GENERAL).changedSlots(sorted));
	}

	@Test
	void refusesWithItemOnCursor() {
		ContainerState withCursor = new ContainerState(emptyMenu(), stack(COBBLE, 1));
		assertEquals(new SortPlanner.Refused(SortPlanner.Refusal.CURSOR_NOT_EMPTY), SortPlanner.plan(withCursor, GENERAL, FACTS));
	}

	@Test
	void refusesOversizedStacks() {
		List<SlotStack> slots = emptyMenu();
		slots.set(9, stack(PEARL, 40));
		assertEquals(new SortPlanner.Refused(SortPlanner.Refusal.OVERSIZED_STACK), SortPlanner.plan(state(slots), GENERAL, FACTS));
	}
}
