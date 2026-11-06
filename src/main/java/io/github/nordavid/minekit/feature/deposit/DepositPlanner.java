package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.PickupSimulator;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.item.ItemFacts;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Plans a Smart Deposit: decides which stacks are eligible and simulates the whole deposit with
 * {@link DepositClickPlanner} and {@link PickupSimulator}, so the preview shows exactly what the execution will do as
 * long as nothing else changes the container. Pure.
 */
public final class DepositPlanner {
	private DepositPlanner() {
	}

	/**
	 * Menu indices of the open container screen.
	 *
	 * @param container the container's own slots
	 * @param main      the player's main inventory (27 slots)
	 * @param hotbar    the player's hotbar (9 slots)
	 */
	public record Layout(int[] container, int[] main, int[] hotbar) {
		public Layout {
			container = container.clone();
			main = main.clone();
			hotbar = hotbar.clone();
		}

		/** Player slots that may be emptied, in deposit order: main inventory, then the hotbar if it is included. */
		public int[] sources(boolean includeHotbar) {
			return includeHotbar ? IntStream.concat(IntStream.of(main), IntStream.of(hotbar)).toArray() : main.clone();
		}
	}

	/**
	 * What the player chose for this run.
	 *
	 * @param includeFavorites explicit per-run override of the favorite protection (off by default, never saved)
	 */
	public record Request(DepositSettings settings, Set<String> favorites, boolean includeFavorites) {
		public Request {
			Objects.requireNonNull(settings, "settings");
			favorites = Set.copyOf(favorites);
		}
	}

	/** Facts from the Minecraft adapter. */
	public interface Rules extends ItemFacts {
		/** Seeds, crops, saplings, fertilizers. */
		boolean isFarming(ItemKey key);

		/** Whether the container's slots accept the item (a shulker box rejects shulker boxes). */
		boolean containerAccepts(ItemKey key);
	}

	public enum Refusal {
		CURSOR_NOT_EMPTY,
		NOTHING_ELIGIBLE,
		CONTAINER_FULL
	}

	/**
	 * @param moved     items that end up in the container, by key
	 * @param remaining eligible items that stay in the player inventory because the container is full
	 * @param kept      protected items (favorites, hotbar) that a profile would otherwise have matched
	 * @param clicks    clicks the simulation needed
	 * @param expected  predicted menu state after the deposit
	 */
	public record Plan(Layout layout, int[] sources, Set<ItemKey> eligible, SortedMap<ItemKey, Integer> moved,
			SortedMap<ItemKey, Integer> remaining, SortedMap<ItemKey, Integer> kept, int clicks, ContainerState expected,
			Optional<Refusal> refusal) {

		public DepositClickPlanner planner(Rules rules) {
			return new DepositClickPlanner(layout.container(), sources, eligible::contains, rules::maxStackSize);
		}

		public int movedTotal() {
			return moved.values().stream().mapToInt(Integer::intValue).sum();
		}

		public int maxClicks() {
			return DepositClickPlanner.maxClicks(layout.container().length, sources.length);
		}

		/** Container slots that change, for highlighting in the preview. */
		public Set<Integer> changedContainerSlots(ContainerState before) {
			Set<Integer> changed = new HashSet<>();
			for (int slot : layout.container()) {
				if (!Objects.equals(before.slot(slot), expected.slot(slot))) {
					changed.add(slot);
				}
			}
			return changed;
		}
	}

	public static Plan plan(ContainerState state, Layout layout, Request request, Rules rules) {
		DepositSettings settings = request.settings();
		Set<String> inContainer = new HashSet<>();
		for (int slot : layout.container()) {
			SlotStack stack = state.slot(slot);
			if (stack != null) {
				inContainer.add(stack.key().itemId());
			}
		}
		Predicate<ItemKey> matchesProfile = key -> rules.containerAccepts(key) && matches(settings, key, rules, inContainer);
		Predicate<ItemKey> favorite = key -> !request.includeFavorites() && request.favorites().contains(key.itemId());

		// Favorites are item ids, so a key is either a favorite everywhere or nowhere. The same non-favorite key may lie
		// in the main inventory and in the protected hotbar: it is eligible, but only source slots are ever emptied.
		int[] sources = layout.sources(settings.includeHotbar());
		Set<ItemKey> eligible = new HashSet<>();
		SortedMap<ItemKey, Integer> kept = new TreeMap<>();
		for (int slot : sources) {
			SlotStack stack = state.slot(slot);
			if (stack != null && matchesProfile.test(stack.key())) {
				if (favorite.test(stack.key())) {
					kept.merge(stack.key(), stack.count(), Integer::sum);
				} else {
					eligible.add(stack.key());
				}
			}
		}
		if (!settings.includeHotbar()) {
			for (int slot : layout.hotbar()) {
				SlotStack stack = state.slot(slot);
				if (stack != null && matchesProfile.test(stack.key())) {
					kept.merge(stack.key(), stack.count(), Integer::sum);
				}
			}
		}

		if (state.carried() != null) {
			return refused(layout, sources, eligible, kept, state, Refusal.CURSOR_NOT_EMPTY);
		}
		DepositClickPlanner planner = new DepositClickPlanner(layout.container(), sources, eligible::contains, rules::maxStackSize);
		ContainerState simulated = state;
		int clicks = 0;
		int maxClicks = planner.maxClicks();
		while (clicks < maxClicks) {
			OptionalInt next = planner.nextClick(simulated);
			if (next.isEmpty()) {
				break;
			}
			simulated = PickupSimulator.leftClick(simulated, next.getAsInt(), rules::maxStackSize);
			clicks++;
		}
		SortedMap<ItemKey, Integer> moved = countIn(simulated, layout.container());
		countIn(state, layout.container()).forEach((key, before) -> moved.merge(key, -before, Integer::sum));
		moved.values().removeIf(count -> count <= 0);
		SortedMap<ItemKey, Integer> remaining = new TreeMap<>();
		for (int slot : sources) {
			SlotStack stack = simulated.slot(slot);
			if (stack != null && eligible.contains(stack.key())) {
				remaining.merge(stack.key(), stack.count(), Integer::sum);
			}
		}
		Optional<Refusal> refusal = Optional.empty();
		if (moved.isEmpty()) {
			refusal = Optional.of(remaining.isEmpty() ? Refusal.NOTHING_ELIGIBLE : Refusal.CONTAINER_FULL);
		}
		return new Plan(layout, sources, Collections.unmodifiableSet(eligible), Collections.unmodifiableSortedMap(moved),
				Collections.unmodifiableSortedMap(remaining), Collections.unmodifiableSortedMap(kept), clicks, simulated, refusal);
	}

	private static Plan refused(Layout layout, int[] sources, Set<ItemKey> eligible, SortedMap<ItemKey, Integer> kept, ContainerState state,
			Refusal refusal) {
		return new Plan(layout, sources, Set.copyOf(eligible), Collections.emptySortedMap(), Collections.emptySortedMap(),
				Collections.unmodifiableSortedMap(kept), 0, state, Optional.of(refusal));
	}

	static boolean matches(DepositSettings settings, ItemKey key, Rules rules, Set<String> inContainer) {
		ItemCategory category = rules.category(key);
		return switch (settings.profile()) {
			case MATCHING -> inContainer.contains(key.itemId());
			case ALL -> true;
			case BLOCKS -> category == ItemCategory.BLOCKS;
			case RESOURCES -> category == ItemCategory.RESOURCES;
			case FOOD -> category == ItemCategory.FOOD || rules.isFarming(key);
			case CUSTOM -> settings.customCategories().contains(category);
		};
	}

	private static SortedMap<ItemKey, Integer> countIn(ContainerState state, int[] slots) {
		SortedMap<ItemKey, Integer> counts = new TreeMap<>();
		for (int slot : slots) {
			SlotStack stack = state.slot(slot);
			if (stack != null) {
				counts.merge(stack.key(), stack.count(), Integer::sum);
			}
		}
		return counts;
	}
}
