package io.github.nordavid.minekit.feature.deposit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Smart Deposit in a single chest menu (27 container slots, then 27 main inventory slots, then 9 hotbar slots, as
 * vanilla's {@code ChestMenu} lays them out), checked on hand-made cases and on thousands of random inventories.
 */
class DepositPlannerTest {
	private static final ItemKey COBBLE = ItemKey.of("minecraft:cobblestone");
	private static final ItemKey DIRT = ItemKey.of("minecraft:dirt");
	private static final ItemKey IRON = ItemKey.of("minecraft:iron_ingot");
	private static final ItemKey BREAD = ItemKey.of("minecraft:bread");
	private static final ItemKey SEEDS = ItemKey.of("minecraft:wheat_seeds");
	private static final ItemKey PEARL = ItemKey.of("minecraft:ender_pearl");
	private static final ItemKey PICKAXE = ItemKey.of("minecraft:diamond_pickaxe");
	private static final ItemKey NAMED_PICKAXE = new ItemKey("minecraft:diamond_pickaxe", "{custom_name=Pick}");
	private static final ItemKey SHULKER = ItemKey.of("minecraft:shulker_box");
	private static final List<ItemKey> ALL = List.of(COBBLE, DIRT, IRON, BREAD, SEEDS, PEARL, PICKAXE, NAMED_PICKAXE, SHULKER);

	private record Info(int max, ItemCategory category) {
	}

	private static final Map<ItemKey, Info> INFO = Map.of(
			COBBLE, new Info(64, ItemCategory.BLOCKS),
			DIRT, new Info(64, ItemCategory.BLOCKS),
			IRON, new Info(64, ItemCategory.RESOURCES),
			BREAD, new Info(64, ItemCategory.FOOD),
			SEEDS, new Info(64, ItemCategory.RESOURCES),
			PEARL, new Info(16, ItemCategory.UTILITY),
			PICKAXE, new Info(1, ItemCategory.TOOLS),
			NAMED_PICKAXE, new Info(1, ItemCategory.TOOLS),
			SHULKER, new Info(1, ItemCategory.BLOCKS));

	private static final class Rules implements DepositPlanner.Rules {
		private final boolean shulkerContainer;

		Rules(boolean shulkerContainer) {
			this.shulkerContainer = shulkerContainer;
		}

		@Override
		public int maxStackSize(ItemKey key) {
			return INFO.get(key).max();
		}

		@Override
		public ItemCategory category(ItemKey key) {
			return INFO.get(key).category();
		}

		@Override
		public boolean isFarming(ItemKey key) {
			return key.equals(SEEDS);
		}

		@Override
		public boolean containerAccepts(ItemKey key) {
			return !shulkerContainer || !key.equals(SHULKER);
		}
	}

	private static final Rules CHEST = new Rules(false);
	private static final DepositPlanner.Layout LAYOUT = new DepositPlanner.Layout(
			IntStream.range(0, 27).toArray(), IntStream.range(27, 54).toArray(), IntStream.range(54, 63).toArray());
	private static final int MENU_SIZE = 63;

	private static List<SlotStack> empty() {
		return new ArrayList<>(Collections.nCopies(MENU_SIZE, null));
	}

	private static SlotStack stack(ItemKey key, int count) {
		return new SlotStack(key, count);
	}

	private static DepositPlanner.Request request(DepositProfile profile) {
		return new DepositPlanner.Request(DepositSettings.DEFAULT.withProfile(profile), Set.of(), false);
	}

	/** Runs the planner like the executor does, with {@link PickupSimulator} standing in for the server. */
	private static ContainerState execute(ContainerState start, DepositPlanner.Plan plan, Rules rules) {
		DepositClickPlanner planner = plan.planner(rules);
		ContainerState state = start;
		for (int clicks = 0; clicks <= plan.maxClicks(); clicks++) {
			OptionalInt next = planner.nextClick(state);
			if (next.isEmpty()) {
				assertTrue(planner.isComplete(state), "stuck at " + state);
				return state;
			}
			state = PickupSimulator.leftClick(state, next.getAsInt(), rules::maxStackSize);
		}
		return fail("no result after " + plan.maxClicks() + " clicks");
	}

	@Test
	void matchingTopsUpPartialStacksFirstAndPutsTheRemainderBack() {
		List<SlotStack> slots = empty();
		slots.set(0, stack(COBBLE, 60));
		slots.set(1, stack(IRON, 3));
		for (int i = 2; i < 27; i++) {
			slots.set(i, stack(DIRT, 64)); // full apart from the cobblestone room
		}
		slots.set(30, stack(COBBLE, 10));
		slots.set(31, stack(BREAD, 5));
		ContainerState start = new ContainerState(slots, null);

		DepositPlanner.Plan plan = DepositPlanner.plan(start, LAYOUT, request(DepositProfile.MATCHING), CHEST);
		ContainerState end = execute(start, plan, CHEST);

		assertEquals(Map.of(COBBLE, 4), plan.moved());
		assertEquals(Map.of(COBBLE, 6), plan.remaining());
		assertEquals(stack(COBBLE, 64), end.slot(0));
		assertEquals(stack(COBBLE, 6), end.slot(30), "remainder back in its own slot");
		assertEquals(stack(BREAD, 5), end.slot(31), "not in the chest, so not matching");
		assertEquals(plan.expected(), end, "the preview predicts the execution exactly");
		assertEquals(Optional.empty(), plan.refusal());
	}

	@Test
	void hotbarAndFavoritesAreKeptUnlessOverridden() {
		List<SlotStack> slots = empty();
		slots.set(27, stack(PICKAXE, 1));
		slots.set(28, stack(IRON, 10));
		slots.set(54, stack(IRON, 20)); // hotbar
		ContainerState start = new ContainerState(slots, null);
		DepositPlanner.Request request = new DepositPlanner.Request(DepositSettings.DEFAULT.withProfile(DepositProfile.ALL),
				Set.of("minecraft:diamond_pickaxe"), false);

		DepositPlanner.Plan plan = DepositPlanner.plan(start, LAYOUT, request, CHEST);
		ContainerState end = execute(start, plan, CHEST);
		assertEquals(Map.of(IRON, 10), plan.moved());
		assertEquals(Map.of(PICKAXE, 1, IRON, 20), plan.kept());
		assertEquals(stack(PICKAXE, 1), end.slot(27));
		assertEquals(stack(IRON, 20), end.slot(54));

		DepositPlanner.Request override = new DepositPlanner.Request(
				DepositSettings.DEFAULT.withProfile(DepositProfile.ALL).withIncludeHotbar(true), Set.of("minecraft:diamond_pickaxe"), true);
		DepositPlanner.Plan all = DepositPlanner.plan(start, LAYOUT, override, CHEST);
		assertEquals(Map.of(PICKAXE, 1, IRON, 30), all.moved());
		assertTrue(all.kept().isEmpty());
	}

	@Test
	void profilesSelectByCategory() {
		List<SlotStack> slots = empty();
		slots.set(27, stack(COBBLE, 5));
		slots.set(28, stack(IRON, 5));
		slots.set(29, stack(BREAD, 5));
		slots.set(30, stack(SEEDS, 5));
		slots.set(31, stack(PEARL, 5));
		ContainerState start = new ContainerState(slots, null);

		assertEquals(Set.of(COBBLE), DepositPlanner.plan(start, LAYOUT, request(DepositProfile.BLOCKS), CHEST).moved().keySet());
		assertEquals(Set.of(IRON, SEEDS), DepositPlanner.plan(start, LAYOUT, request(DepositProfile.RESOURCES), CHEST).moved().keySet());
		assertEquals(Set.of(BREAD, SEEDS), DepositPlanner.plan(start, LAYOUT, request(DepositProfile.FOOD), CHEST).moved().keySet());
		DepositPlanner.Request custom = new DepositPlanner.Request(
				new DepositSettings(DepositProfile.CUSTOM, EnumSet.of(ItemCategory.UTILITY), false), Set.of(), false);
		assertEquals(Set.of(PEARL), DepositPlanner.plan(start, LAYOUT, custom, CHEST).moved().keySet());
		assertEquals(Optional.of(DepositPlanner.Refusal.NOTHING_ELIGIBLE),
				DepositPlanner.plan(start, LAYOUT, request(DepositProfile.MATCHING), CHEST).refusal());
	}

	@Test
	void matchingUsesTheItemIdSoVariantsOfAnItemInTheChestCount() {
		List<SlotStack> slots = empty();
		slots.set(0, stack(PICKAXE, 1));
		slots.set(27, stack(NAMED_PICKAXE, 1));
		ContainerState start = new ContainerState(slots, null);

		DepositPlanner.Plan plan = DepositPlanner.plan(start, LAYOUT, request(DepositProfile.MATCHING), CHEST);
		assertEquals(Map.of(NAMED_PICKAXE, 1), plan.moved());
		assertEquals(stack(NAMED_PICKAXE, 1), execute(start, plan, CHEST).slot(1));
	}

	@Test
	void refusesWithItemOnTheCursorAndReportsAFullContainer() {
		List<SlotStack> slots = empty();
		for (int i = 0; i < 27; i++) {
			slots.set(i, stack(DIRT, 64));
		}
		slots.set(27, stack(DIRT, 3));
		ContainerState full = new ContainerState(slots, null);
		assertEquals(Optional.of(DepositPlanner.Refusal.CONTAINER_FULL),
				DepositPlanner.plan(full, LAYOUT, request(DepositProfile.MATCHING), CHEST).refusal());

		ContainerState holding = new ContainerState(empty(), stack(COBBLE, 1));
		DepositPlanner.Plan plan = DepositPlanner.plan(holding, LAYOUT, request(DepositProfile.ALL), CHEST);
		assertEquals(Optional.of(DepositPlanner.Refusal.CURSOR_NOT_EMPTY), plan.refusal());
		assertEquals(0, plan.clicks());
	}

	@Test
	void shulkerBoxesNeverGoIntoAShulkerBox() {
		List<SlotStack> slots = empty();
		slots.set(27, stack(SHULKER, 1));
		slots.set(28, stack(DIRT, 1));
		ContainerState start = new ContainerState(slots, null);
		DepositPlanner.Plan plan = DepositPlanner.plan(start, LAYOUT, request(DepositProfile.ALL), new Rules(true));
		assertEquals(Map.of(DIRT, 1), plan.moved());
	}

	@Test
	void randomInventoriesKeepEverySafetyProperty() {
		Random random = new Random(42);
		for (int run = 0; run < 3_000; run++) {
			List<SlotStack> slots = empty();
			double containerFill = random.nextDouble();
			for (int slot = 0; slot < MENU_SIZE; slot++) {
				double fill = slot < 27 ? containerFill : 0.6;
				if (random.nextDouble() < fill) {
					ItemKey key = ALL.get(random.nextInt(ALL.size()));
					slots.set(slot, stack(key, 1 + random.nextInt(INFO.get(key).max())));
				}
			}
			ContainerState start = new ContainerState(slots, null);
			DepositProfile profile = DepositProfile.ORDER.get(random.nextInt(DepositProfile.ORDER.size()));
			boolean hotbar = random.nextBoolean();
			boolean shulker = random.nextInt(4) == 0;
			Rules rules = new Rules(shulker);
			Set<String> favorites = random.nextBoolean() ? Set.of("minecraft:iron_ingot") : Set.of();
			DepositPlanner.Request request = new DepositPlanner.Request(
					new DepositSettings(profile, EnumSet.of(ItemCategory.BLOCKS, ItemCategory.TOOLS), hotbar), favorites, false);

			DepositPlanner.Plan plan = DepositPlanner.plan(start, LAYOUT, request, rules);
			ContainerState end = execute(start, plan, rules);
			String context = "run " + run + " " + profile + " hotbar=" + hotbar + " shulker=" + shulker;

			assertEquals(plan.expected(), end, context + ": preview equals execution");
			assertEquals(start.totals(), end.totals(), context + ": nothing lost or duplicated");
			assertNull(end.carried(), context + ": cursor empty");
			Set<Integer> sources = new HashSet<>();
			for (int slot : plan.sources()) {
				sources.add(slot);
			}
			for (int slot = 0; slot < MENU_SIZE; slot++) {
				SlotStack before = start.slot(slot);
				SlotStack after = end.slot(slot);
				if (slot < 27) {
					if (before != null) {
						assertEquals(before.key(), after.key(), context + ": container slot " + slot + " kept its item");
						assertTrue(after.count() >= before.count(), context + ": container slot " + slot + " only grew");
					} else if (after != null) {
						assertTrue(plan.eligible().contains(after.key()), context + ": only eligible items enter");
					}
				} else if (!sources.contains(slot) || before == null || !plan.eligible().contains(before.key())) {
					assertEquals(before, after, context + ": protected or ineligible slot " + slot + " untouched");
				} else if (after != null) {
					assertEquals(before.key(), after.key(), context + ": remainder stays in its own slot " + slot);
					assertTrue(after.count() <= before.count(), context);
				}
				if (after != null && slot < 27) {
					assertFalse(shulker && after.key().equals(SHULKER) && before == null, context + ": no shulker into shulker");
				}
			}
			for (int slot : plan.sources()) {
				SlotStack left = end.slot(slot);
				if (left != null && plan.eligible().contains(left.key())) {
					for (int container = 0; container < 27; container++) {
						SlotStack there = end.slot(container);
						assertTrue(there != null && (!there.key().equals(left.key()) || there.count() == rules.maxStackSize(left.key())),
								context + ": " + left + " stayed behind although the container had room");
					}
				}
			}
			assertTrue(plan.clicks() <= plan.maxClicks(), context);
		}
	}
}
