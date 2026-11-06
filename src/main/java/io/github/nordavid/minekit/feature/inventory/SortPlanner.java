package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Computes the sorted layout of the player inventory. Pure and deterministic: the same inventory and rules always
 * give the same layout, and sorting an already sorted inventory changes nothing.
 *
 * <p>Order of stacks: favorites, then category (in the rules' order), then item id, then component variant, then
 * larger stacks first. Partial stacks of the same item are merged as far as the max stack size allows. Items never
 * leave the sort region, so nothing can be lost or duplicated by the layout itself.
 *
 * <p>The hotbar follows the {@link HotbarLayout}: slots to keep are not part of the region; a slot with a rule such as
 * "pickaxe" takes the best matching stack; "any" slots and then the main inventory take the rest in sort order. A slot
 * whose rule matches nothing, or one to keep empty, stays empty unless the rest of the inventory is full.
 */
public final class SortPlanner {
	private SortPlanner() {
	}

	public enum Refusal {
		/** Something is on the cursor; the player must put it down first. */
		CURSOR_NOT_EMPTY,
		/** A stack is larger than its max stack size (only possible with commands or mods); sorting could split it. */
		OVERSIZED_STACK
	}

	/** Result of planning: either a target layout or the reason sorting is not possible. */
	public sealed interface Result permits Planned, Refused {
	}

	public record Refused(Refusal reason) implements Result {
	}

	/**
	 * @param region slots the sort uses (see {@link InventorySlots#sortRegion})
	 * @param target wanted content of each region slot, same order; {@code null} entries are empty slots
	 */
	public record Planned(int[] region, List<@Nullable SlotStack> target) implements Result {
		public Planned {
			region = region.clone();
			target = Collections.unmodifiableList(new ArrayList<>(target));
		}

		@Override
		public int[] region() {
			return region.clone();
		}

		/** Number of region slots whose content changes. 0 means the inventory is already sorted. */
		public int changedSlots(ContainerState state) {
			int changed = 0;
			for (int pos = 0; pos < region.length; pos++) {
				if (!Objects.equals(state.slot(region[pos]), target.get(pos))) {
					changed++;
				}
			}
			return changed;
		}

		/** The state after a successful sort. */
		public ContainerState expectedResult(ContainerState state) {
			ContainerState result = state;
			for (int pos = 0; pos < region.length; pos++) {
				result = result.with(region[pos], target.get(pos), result.carried());
			}
			return result;
		}
	}

	public static Result plan(ContainerState state, SortRules rules, SortFacts facts) {
		if (state.carried() != null) {
			return new Refused(Refusal.CURSOR_NOT_EMPTY);
		}
		HotbarLayout hotbar = rules.hotbar();
		int[] region = InventorySlots.sortRegion(hotbar);

		Map<ItemKey, Integer> totals = new TreeMap<>();
		for (int slot : region) {
			SlotStack stack = state.slot(slot);
			if (stack == null) {
				continue;
			}
			if (stack.count() > facts.maxStackSize(stack.key())) {
				return new Refused(Refusal.OVERSIZED_STACK);
			}
			totals.merge(stack.key(), stack.count(), Integer::sum);
		}

		List<SlotStack> pool = new ArrayList<>();
		totals.forEach((key, total) -> {
			int max = Math.max(1, facts.maxStackSize(key));
			for (int full = 0; full < total / max; full++) {
				pool.add(new SlotStack(key, max));
			}
			if (total % max > 0) {
				pool.add(new SlotStack(key, total % max));
			}
		});
		pool.sort(order(rules, facts));

		Map<Integer, SlotStack> placed = new HashMap<>();
		// 1. Hotbar slots with a rule that picks a match take the best one, left to right.
		List<Integer> unmatched = new ArrayList<>();
		for (int i = 0; i < HotbarLayout.SIZE; i++) {
			HotbarRule rule = hotbar.rule(i);
			if (!rule.kind().picksMatch()) {
				continue;
			}
			int slot = InventorySlots.HOTBAR_START + i;
			int best = bestMatch(pool, rule, state.slot(slot), rules, facts);
			if (best < 0) {
				unmatched.add(slot);
			} else {
				placed.put(slot, pool.remove(best));
			}
		}
		// 2. The rest in sort order: "any" hotbar slots, then the main inventory. Slots reserved for something the
		// player does not have, and slots to keep empty, are used only when nothing else has room.
		List<Integer> fillOrder = new ArrayList<>();
		addHotbarSlots(fillOrder, hotbar, HotbarRule.Kind.ANY);
		for (int slot = InventorySlots.MAIN_START; slot < InventorySlots.MAIN_END; slot++) {
			fillOrder.add(slot);
		}
		fillOrder.addAll(unmatched);
		addHotbarSlots(fillOrder, hotbar, HotbarRule.Kind.EMPTY);
		// Merging never needs more slots than the stacks already occupy, so the pool always fits.
		for (int i = 0; i < pool.size(); i++) {
			placed.put(fillOrder.get(i), pool.get(i));
		}

		List<@Nullable SlotStack> target = new ArrayList<>(region.length);
		for (int slot : region) {
			target.add(placed.get(slot));
		}
		return new Planned(region, target);
	}

	private static void addHotbarSlots(List<Integer> into, HotbarLayout hotbar, HotbarRule.Kind kind) {
		for (int i = 0; i < HotbarLayout.SIZE; i++) {
			if (hotbar.rule(i).kind() == kind) {
				into.add(InventorySlots.HOTBAR_START + i);
			}
		}
	}

	/**
	 * Index in {@code pool} of the best stack for a hotbar slot, or -1 if none fits. Best means: a favorite, then the
	 * highest {@linkplain SortFacts#quality quality}, then the largest stack, then the item already in the slot (so a
	 * sorted hotbar stays as it is), then the earliest in sort order.
	 */
	private static int bestMatch(List<SlotStack> pool, HotbarRule rule, @Nullable SlotStack current, SortRules rules, SortFacts facts) {
		HotbarRule.Kind kind = rule.kind();
		Comparator<SlotStack> better = Comparator.<SlotStack>comparingInt(stack -> rules.favorites().contains(stack.key().itemId()) ? 0 : 1)
				.thenComparing(Comparator.<SlotStack>comparingInt(stack -> kind == HotbarRule.Kind.ITEM ? 0 : facts.quality(kind, stack.key())).reversed())
				.thenComparing(Comparator.comparingInt(SlotStack::count).reversed())
				.thenComparingInt(stack -> current != null && stack.key().equals(current.key()) ? 0 : 1);
		int best = -1;
		for (int i = 0; i < pool.size(); i++) {
			SlotStack candidate = pool.get(i);
			boolean fits = kind == HotbarRule.Kind.ITEM ? candidate.key().itemId().equals(rule.itemId()) : facts.fits(kind, candidate.key());
			if (fits && (best < 0 || better.compare(candidate, pool.get(best)) < 0)) {
				best = i;
			}
		}
		return best;
	}

	static Comparator<SlotStack> order(SortRules rules, SortFacts facts) {
		return Comparator.<SlotStack>comparingInt(stack -> rules.favorites().contains(stack.key().itemId()) ? 0 : 1)
				.thenComparingInt(stack -> rules.categoryOrder().indexOf(facts.category(stack.key())))
				.thenComparing(SlotStack::key)
				.thenComparing(Comparator.comparingInt(SlotStack::count).reversed());
	}
}
