package io.github.nordavid.minekit.feature.inventory;

import static io.github.nordavid.minekit.feature.inventory.TestItems.ALL;
import static io.github.nordavid.minekit.feature.inventory.TestItems.COBBLE;
import static io.github.nordavid.minekit.feature.inventory.TestItems.FACTS;
import static io.github.nordavid.minekit.feature.inventory.TestItems.emptyMenu;
import static io.github.nordavid.minekit.feature.inventory.TestItems.stack;
import static io.github.nordavid.minekit.feature.inventory.TestItems.state;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.PickupSimulator;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link SortClickPlanner} with {@link PickupSimulator} over many random inventories and checks the safety
 * properties every real sort must have.
 */
class SortClickPlannerTest {
	private static final int RANDOM_INVENTORIES = 3_000;

	private record Run(ContainerState end, int clicks) {
	}

	private static Run run(ContainerState start, SortPlanner.Planned plan) {
		SortClickPlanner planner = new SortClickPlanner(plan);
		Set<Integer> region = new HashSet<>();
		for (int slot : plan.region()) {
			region.add(slot);
		}
		int limit = maxClicks(plan);
		ContainerState state = start;
		for (int clicks = 0; clicks <= limit; clicks++) {
			OptionalInt next = planner.nextClick(state);
			if (next.isEmpty()) {
				return new Run(state, clicks);
			}
			assertTrue(region.contains(next.getAsInt()), "clicked outside the sort region: " + next.getAsInt());
			state = PickupSimulator.leftClick(state, next.getAsInt(), FACTS::maxStackSize);
		}
		return fail("no result after " + limit + " clicks, stuck at " + state);
	}

	/** Upper bound used by the executor as well: a few clicks per slot. */
	private static int maxClicks(SortPlanner.Planned plan) {
		return SortJobLimits.maxClicks(plan.region().length);
	}

	private static ContainerState randomInventory(Random random) {
		List<SlotStack> slots = emptyMenu();
		double fill = random.nextDouble();
		for (int slot = 0; slot < slots.size(); slot++) {
			if (random.nextDouble() < fill) {
				ItemKey key = ALL.get(random.nextInt(ALL.size()));
				slots.set(slot, stack(key, 1 + random.nextInt(FACTS.maxStackSize(key))));
			}
		}
		return state(slots);
	}

	private static SortRules randomRules(Random random) {
		List<ItemCategory> order = new ArrayList<>(List.of(ItemCategory.values()));
		Collections.shuffle(order, random);
		Set<String> favorites = new HashSet<>();
		for (ItemKey key : ALL) {
			if (random.nextInt(6) == 0) {
				favorites.add(key.itemId());
			}
		}
		return new SortRules(order, favorites, randomLayout(random));
	}

	/** Every kind of rule, including specific items, keep and empty, in random slots. */
	static HotbarLayout randomLayout(Random random) {
		if (random.nextInt(4) == 0) {
			return random.nextBoolean() ? HotbarLayout.KEEP_ALL : HotbarLayout.ANY_ALL;
		}
		List<HotbarRule> rules = new ArrayList<>();
		HotbarRule.Kind[] kinds = HotbarRule.Kind.values();
		for (int slot = 0; slot < HotbarLayout.SIZE; slot++) {
			HotbarRule.Kind kind = kinds[random.nextInt(kinds.length)];
			rules.add(kind == HotbarRule.Kind.ITEM ? HotbarRule.item(ALL.get(random.nextInt(ALL.size())).itemId()) : HotbarRule.of(kind));
		}
		return new HotbarLayout(rules);
	}

	@Test
	void randomInventoriesReachTheLayoutWithoutLosingOrDuplicatingItems() {
		Random random = new Random(20261106L);
		int worstClicks = 0;
		for (int i = 0; i < RANDOM_INVENTORIES; i++) {
			ContainerState start = randomInventory(random);
			SortRules rules = randomRules(random);
			SortPlanner.Planned plan = assertInstanceOf(SortPlanner.Planned.class, SortPlanner.plan(start, rules, FACTS));

			Run result = run(start, plan);

			String context = "inventory #" + i + " " + start;
			assertEquals(plan.expectedResult(start), result.end(), context);
			assertNull(result.end().carried(), context);
			assertEquals(start.totals(), result.end().totals(), "items lost or duplicated in " + context);
			for (int slot = 0; slot < start.size(); slot++) {
				boolean inRegion = false;
				for (int regionSlot : plan.region()) {
					inRegion |= regionSlot == slot;
				}
				if (!inRegion) {
					assertEquals(start.slot(slot), result.end().slot(slot), "slot " + slot + " outside the region changed in " + context);
				}
			}
			assertEquals(0, run(result.end(), plan).clicks(), "sorting a sorted inventory must not click: " + context);
			SortPlanner.Planned again = assertInstanceOf(SortPlanner.Planned.class, SortPlanner.plan(result.end(), rules, FACTS));
			assertEquals(0, again.changedSlots(result.end()), "planning a sorted inventory again must change nothing: " + context);
			worstClicks = Math.max(worstClicks, result.clicks());
		}
		assertTrue(worstClicks > 0);
	}

	@Test
	void alreadySortedInventoryNeedsNoClicks() {
		List<SlotStack> slots = emptyMenu();
		slots.set(9, stack(COBBLE, 64));
		slots.set(10, stack(COBBLE, 3));
		ContainerState start = state(slots);
		SortRules rules = new SortRules(SortProfile.GENERAL.defaultOrder(), Set.of(), HotbarLayout.KEEP_ALL);

		SortPlanner.Planned plan = assertInstanceOf(SortPlanner.Planned.class, SortPlanner.plan(start, rules, FACTS));

		assertEquals(0, plan.changedSlots(start));
		assertEquals(0, run(start, plan).clicks());
	}

	@Test
	void fullInventoryOfSingleItemsSortsWithinLimit() {
		List<SlotStack> slots = emptyMenu();
		for (int slot = InventorySlots.MAIN_START; slot < InventorySlots.MAIN_END; slot++) {
			// Reverse order of what the layout wants, all slots full: worst case for swaps.
			slots.set(slot, stack(ALL.get((InventorySlots.MAIN_END - slot) % ALL.size()), 1));
		}
		ContainerState start = state(slots);
		SortRules rules = new SortRules(SortProfile.GENERAL.defaultOrder(), Set.of(), HotbarLayout.KEEP_ALL);
		SortPlanner.Planned plan = assertInstanceOf(SortPlanner.Planned.class, SortPlanner.plan(start, rules, FACTS));

		Run result = run(start, plan);

		assertEquals(plan.expectedResult(start), result.end());
		assertTrue(result.clicks() <= maxClicks(plan), "clicks: " + result.clicks());
	}
}
