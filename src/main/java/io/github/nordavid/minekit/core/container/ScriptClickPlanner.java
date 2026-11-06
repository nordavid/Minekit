package io.github.nordavid.minekit.core.container;

import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;

/**
 * Plays back a click sequence that was planned in advance by simulation, so the preview shows exactly what execution
 * does. Each step is chosen by the actual state: the next click is the one whose simulated state before it equals the
 * actual state. If the actual state matches none (something changed), there is no next click and the executor stops.
 */
public final class ScriptClickPlanner implements ClickPlanner {
	private final List<Integer> clicks;
	private final List<ContainerState> before;
	private final ContainerState goal;
	private int position;

	/**
	 * @param start        the state the script starts from
	 * @param clicks       menu slots to left-click, in order
	 * @param maxStackSize as used by the executor's prediction
	 */
	public ScriptClickPlanner(ContainerState start, List<Integer> clicks, ToIntFunction<ItemKey> maxStackSize) {
		this.clicks = List.copyOf(clicks);
		List<ContainerState> states = new ArrayList<>();
		ContainerState state = start;
		for (int slot : clicks) {
			states.add(state);
			ContainerState next = PickupSimulator.leftClick(state, slot, maxStackSize);
			if (next.equals(state)) {
				throw new IllegalArgumentException("click on slot " + slot + " changes nothing in " + state);
			}
			state = next;
		}
		this.before = List.copyOf(states);
		this.goal = state;
	}

	public ContainerState goal() {
		return goal;
	}

	@Override
	public OptionalInt nextClick(ContainerState state) {
		for (int step = position; step < clicks.size(); step++) {
			if (before.get(step).equals(state)) {
				position = step;
				return OptionalInt.of(clicks.get(step));
			}
		}
		return OptionalInt.empty();
	}

	@Override
	public boolean isComplete(ContainerState state) {
		return state.equals(goal);
	}
}
