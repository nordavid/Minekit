package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.PickupSimulator;
import io.github.nordavid.minekit.core.container.ScriptClickPlanner;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Compares the inventory with a {@link Loadout} and plans how to apply it with plain left clicks. Pure and
 * deterministic; the plan is simulated click by click, so the preview is exactly what execution does.
 *
 * <p>Each wanted position is handled in {@link LoadoutSlots#APPLY_ORDER}. It keeps what already matches there and gets
 * whole stacks of the same item, taken in this order: from free inventory positions, from positions the loadout wants
 * something else in, and last from the open container. A stack is never split, so a position may get a little more
 * than wanted; what does not fit goes back where it came from. What a position held before moves to a free inventory
 * slot, or swaps places with the first stack taken. If neither is possible, the position stays as it is
 * ({@link SlotStatus#NO_ROOM}).
 *
 * <p>Guarantees, checked by random tests: no item is lost or created; locked positions are never clicked; the
 * container only gives (the only thing that goes into a container slot is the rest of the stack just taken from it);
 * armor positions only receive items that fit them; applying twice changes nothing the second time.
 */
public final class LoadoutPlanner {
	private LoadoutPlanner() {
	}

	public enum SlotStatus {
		/** The position already holds what the loadout wants. */
		PRESENT,
		/** Applying fills the position completely. */
		FILLED,
		/** Applying leaves fewer than wanted in the position. */
		PARTIAL,
		/** Nothing that matches is available. */
		MISSING,
		/** A match is available, but what the position holds now has nowhere to go. */
		NO_ROOM,
		/** The open screen does not show this position (armor and offhand in a container screen). */
		NOT_HERE
	}

	public enum Refusal {
		/** Something is on the cursor; the player must put it down first. */
		CURSOR_NOT_EMPTY,
		/** Applying would change nothing. */
		NOTHING_TO_DO
	}

	/**
	 * @param index inventory position ({@link LoadoutSlots})
	 * @param now   what the position holds now, or {@code null}
	 * @param after how many matching items the position holds after applying
	 */
	public record SlotReport(int index, LoadoutItem wanted, @Nullable SlotStack now, SlotStatus status, int after) {
		/** How many are still missing after applying. */
		public int missing() {
			return status == SlotStatus.PRESENT || status == SlotStatus.FILLED || status == SlotStatus.NOT_HERE
					? 0 : Math.max(0, wanted.count() - after);
		}
	}

	/**
	 * @param start    the state the plan was made for (without a cursor item)
	 * @param clicks   menu slots to left-click, in order
	 * @param result   the state after all clicks
	 * @param recovery menu slots where a stray cursor item may be put down (free inventory, never the container)
	 * @param slots    one report per wanted position, in {@link LoadoutSlots#APPLY_ORDER}
	 * @param missing  item id to how many are still missing after applying
	 * @param taken    items that come out of the container
	 * @param extra    items in the inventory that the loadout does not want, by item id
	 */
	public record Plan(ContainerState start, List<Integer> clicks, ContainerState result, int[] recovery, List<SlotReport> slots,
			Map<String, Integer> missing, Map<ItemKey, Integer> taken, Map<String, Integer> extra, Optional<Refusal> refusal) {
		public Plan {
			clicks = List.copyOf(clicks);
			recovery = recovery.clone();
			slots = List.copyOf(slots);
			missing = Collections.unmodifiableMap(new TreeMap<>(missing));
			taken = Collections.unmodifiableMap(new TreeMap<>(taken));
			extra = Collections.unmodifiableMap(new TreeMap<>(extra));
		}

		@Override
		public int[] recovery() {
			return recovery.clone();
		}

		public long count(SlotStatus status) {
			return slots.stream().filter(slot -> slot.status() == status).count();
		}

		public Optional<SlotReport> report(int index) {
			return slots.stream().filter(slot -> slot.index() == index).findFirst();
		}

		/** Menu slots whose content changes. */
		public List<Integer> changedSlots() {
			return start.differingSlots(result);
		}

		public ContainerState expectedResult() {
			return result;
		}

		/** Plays the clicks back; it must start from {@link #start()}. */
		public ClickPlanner clickPlanner(java.util.function.ToIntFunction<ItemKey> maxStackSize) {
			return new ScriptClickPlanner(start, clicks, maxStackSize);
		}
	}

	private record Candidate(int slot, SlotStack stack, int rank) {
	}

	/** The simulated menu while planning: every click is applied to it and recorded. */
	private static final class Simulation {
		private final LoadoutFacts facts;
		private final List<Integer> clicks = new ArrayList<>();
		private ContainerState state;

		Simulation(ContainerState start, LoadoutFacts facts) {
			this.state = start;
			this.facts = facts;
		}

		@Nullable SlotStack slot(int slot) {
			return state.slot(slot);
		}

		void click(int slot) {
			ContainerState next = PickupSimulator.leftClick(state, slot, facts::maxStackSize);
			if (next.equals(state)) {
				throw new IllegalStateException("planned a click that changes nothing: slot " + slot + " in " + state);
			}
			clicks.add(slot);
			state = next;
		}
	}

	/** Everything that stays the same while one plan is made. */
	private record Context(LoadoutLayout layout, Loadout loadout, LoadoutFacts facts, Map<Integer, Integer> indexOfMenuSlot,
			Set<Integer> containerSlots, Set<Integer> wantedSlots, List<Integer> parking) {
	}

	public static Plan plan(ContainerState state, LoadoutLayout layout, Loadout loadout, LoadoutFacts facts) {
		Map<Integer, Integer> indexOfMenuSlot = new HashMap<>();
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			int menuSlot = layout.menuSlot(index);
			if (menuSlot >= 0 && !loadout.locked().contains(index)) {
				indexOfMenuSlot.put(menuSlot, index);
			}
		}
		Set<Integer> containerSlots = new HashSet<>();
		for (int slot : layout.containerSlots()) {
			containerSlots.add(slot);
		}
		List<Integer> wanted = LoadoutSlots.APPLY_ORDER.stream()
				.filter(index -> loadout.items().containsKey(index) && indexOfMenuSlot.containsValue(index)).toList();
		Set<Integer> wantedSlots = new HashSet<>();
		wanted.forEach(index -> wantedSlots.add(layout.menuSlot(index)));
		List<Integer> parking = new ArrayList<>();
		List<Integer> recovery = new ArrayList<>();
		for (int index : storageOrder()) {
			int menuSlot = layout.menuSlot(index);
			if (menuSlot >= 0 && !loadout.locked().contains(index)) {
				recovery.add(menuSlot);
				if (!wantedSlots.contains(menuSlot)) {
					parking.add(menuSlot);
				}
			}
		}
		Context context = new Context(layout, loadout, facts, indexOfMenuSlot, containerSlots, wantedSlots, parking);

		// A cursor item blocks applying; plan without it so the preview still shows what applying would do.
		List<SlotStack> slots = new ArrayList<>();
		for (int slot = 0; slot < state.size(); slot++) {
			slots.add(state.slot(slot));
		}
		ContainerState start = new ContainerState(slots, null);
		Simulation simulation = new Simulation(start, facts);
		Map<Integer, SlotStatus> statuses = new HashMap<>();
		Set<Integer> done = new HashSet<>();
		for (int index : wanted) {
			int slot = layout.menuSlot(index);
			SlotStack current = start.slot(slot);
			LoadoutItem item = loadout.items().get(index);
			if (current != null && fits(context, item, current.key(), index) && current.count() >= want(context, item, current.key(), index)) {
				statuses.put(index, SlotStatus.PRESENT);
				done.add(slot);
			}
		}
		List<Integer> noRoom = new ArrayList<>();
		for (int index : wanted) {
			if (!statuses.containsKey(index)) {
				SlotStatus status = fill(context, simulation, done, index);
				if (status == SlotStatus.NO_ROOM) {
					noRoom.add(index);
				} else {
					statuses.put(index, status);
				}
			}
		}
		// Positions handled later may have freed a slot: give the ones without room a second chance.
		for (int index : noRoom) {
			statuses.put(index, fill(context, simulation, done, index));
		}
		ContainerState result = simulation.state;
		if (result.carried() != null || !result.totals().equals(start.totals())) {
			throw new IllegalStateException("loadout plan does not end with an empty cursor and the same items: " + start + " -> " + result);
		}

		List<SlotReport> reports = new ArrayList<>();
		Map<String, Integer> missing = new TreeMap<>();
		for (int index : LoadoutSlots.APPLY_ORDER) {
			LoadoutItem item = loadout.items().get(index);
			if (item == null || loadout.locked().contains(index)) {
				continue;
			}
			int menuSlot = layout.menuSlot(index);
			if (menuSlot < 0) {
				reports.add(new SlotReport(index, item, null, SlotStatus.NOT_HERE, 0));
				continue;
			}
			SlotStack after = result.slot(menuSlot);
			int afterCount = after != null && fits(context, item, after.key(), index) ? after.count() : 0;
			SlotReport report = new SlotReport(index, item, start.slot(menuSlot), statuses.get(index), afterCount);
			reports.add(report);
			if (report.missing() > 0) {
				missing.merge(item.itemId(), report.missing(), Integer::sum);
			}
		}

		Map<ItemKey, Integer> taken = new TreeMap<>();
		for (int slot : containerSlots) {
			SlotStack before = start.slot(slot);
			SlotStack after = result.slot(slot);
			int gone = (before == null ? 0 : before.count()) - (after == null ? 0 : after.count());
			if (before != null && gone > 0) {
				taken.merge(before.key(), gone, Integer::sum);
			}
		}

		Set<String> wantedIds = new HashSet<>();
		loadout.items().values().forEach(item -> wantedIds.add(item.itemId()));
		Map<String, Integer> extra = new TreeMap<>();
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			int menuSlot = layout.menuSlot(index);
			SlotStack stack = menuSlot < 0 ? null : start.slot(menuSlot);
			if (stack != null && !wantedIds.contains(stack.key().itemId())) {
				extra.merge(stack.key().itemId(), stack.count(), Integer::sum);
			}
		}

		Optional<Refusal> refusal = Optional.empty();
		if (state.carried() != null) {
			refusal = Optional.of(Refusal.CURSOR_NOT_EMPTY);
		} else if (simulation.clicks.isEmpty()) {
			refusal = Optional.of(Refusal.NOTHING_TO_DO);
		}
		return new Plan(start, simulation.clicks, result, recovery.stream().mapToInt(Integer::intValue).toArray(), reports, missing, taken,
				extra, refusal);
	}

	/** Fills one position, recording the clicks. */
	private static SlotStatus fill(Context context, Simulation simulation, Set<Integer> done, int index) {
		int slot = context.layout().menuSlot(index);
		LoadoutItem item = context.loadout().items().get(index);
		SlotStack current = simulation.slot(slot);
		boolean currentFits = current != null && fits(context, item, current.key(), index);

		List<Candidate> candidates = new ArrayList<>();
		for (Map.Entry<Integer, Integer> entry : context.indexOfMenuSlot().entrySet()) {
			addCandidate(context, simulation, done, index, slot, entry.getKey(), candidates);
		}
		for (int source : context.containerSlots()) {
			addCandidate(context, simulation, done, index, slot, source, candidates);
		}
		candidates.sort(Comparator.comparingInt(Candidate::rank)
				.thenComparing(Comparator.comparingInt((Candidate candidate) -> candidate.stack().count()).reversed())
				.thenComparingInt(Candidate::slot));
		ItemKey key = currentFits ? current.key() : candidates.isEmpty() ? null : candidates.getFirst().stack().key();
		if (key == null) {
			return SlotStatus.MISSING;
		}
		int want = want(context, item, key, index);
		List<Candidate> chosen = new ArrayList<>();
		int total = currentFits ? current.count() : 0;
		for (Candidate candidate : candidates) {
			if (total >= want) {
				break;
			}
			if (candidate.stack().key().equals(key)) {
				chosen.add(candidate);
				total += candidate.stack().count();
			}
		}
		if (chosen.isEmpty()) {
			return currentFits ? SlotStatus.PARTIAL : SlotStatus.MISSING;
		}

		if (current != null && !currentFits) {
			// Make room: move what is there to a free slot, or swap it with the first stack taken.
			Integer free = context.parking().stream().filter(parking -> simulation.slot(parking) == null).findFirst().orElse(null);
			Candidate first = chosen.getFirst();
			if (free != null) {
				simulation.click(slot);
				simulation.click(free);
			} else if (!context.containerSlots().contains(first.slot()) && LoadoutSlots.isStorage(context.indexOfMenuSlot().get(first.slot()))) {
				simulation.click(first.slot());
				simulation.click(slot);
				simulation.click(first.slot());
				chosen.removeFirst();
			} else {
				return SlotStatus.NO_ROOM;
			}
		}
		for (Candidate candidate : chosen) {
			SlotStack there = simulation.slot(slot);
			if (there != null && there.count() >= want) {
				break;
			}
			simulation.click(candidate.slot());
			simulation.click(slot);
			if (simulation.state.carried() != null) {
				simulation.click(candidate.slot());
			}
		}
		done.add(slot);
		SlotStack after = simulation.slot(slot);
		return after != null && after.count() >= want ? SlotStatus.FILLED : SlotStatus.PARTIAL;
	}

	private static void addCandidate(Context context, Simulation simulation, Set<Integer> done, int index, int slot, int source,
			List<Candidate> candidates) {
		SlotStack stack = simulation.slot(source);
		LoadoutItem item = context.loadout().items().get(index);
		if (source == slot || stack == null || done.contains(source) || !fits(context, item, stack.key(), index)) {
			return;
		}
		boolean wantedThere = context.wantedSlots().contains(source);
		if (wantedThere) {
			int other = context.indexOfMenuSlot().get(source);
			if (fits(context, context.loadout().items().get(other), stack.key(), other)) {
				// That position wants this stack itself.
				return;
			}
		}
		int rank = context.containerSlots().contains(source) ? 3 : wantedThere ? 2 : 1;
		candidates.add(new Candidate(source, stack, rank));
	}

	/** Main inventory first, then the hotbar: where displaced stacks go. */
	private static List<Integer> storageOrder() {
		List<Integer> order = new ArrayList<>();
		for (int index = LoadoutSlots.MAIN_START; index < LoadoutSlots.MAIN_END; index++) {
			order.add(index);
		}
		for (int index = LoadoutSlots.HOTBAR_START; index < LoadoutSlots.MAIN_START; index++) {
			order.add(index);
		}
		return order;
	}

	private static boolean fits(Context context, LoadoutItem item, ItemKey key, int index) {
		return context.facts().matches(item, key, context.loadout().exact())
				&& (!LoadoutSlots.isArmor(index) || context.facts().fitsArmorSlot(key, index));
	}

	/** How many count as complete: the wanted count, but never more than the position can hold. */
	private static int want(Context context, LoadoutItem item, ItemKey key, int index) {
		int max = LoadoutSlots.isArmor(index) ? 1 : Math.max(1, context.facts().maxStackSize(key));
		return Math.min(item.count(), max);
	}

	/** Shortcut used by the UI: whether two plans would do the same clicks. */
	public static boolean sameClicks(Plan first, Plan second) {
		return Objects.equals(first.clicks(), second.clicks()) && first.start().equals(second.start());
	}
}
