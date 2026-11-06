package io.github.nordavid.minekit.core.container;

import java.util.OptionalInt;

/**
 * Decides, one step at a time, which slot to left-click to move a container towards a goal. Re-planning from the
 * actual state on every step (instead of following a precomputed list) keeps the executor correct when a click
 * lands differently than expected: the executor stops on any deviation, and the planner never relies on stale state.
 */
public interface ClickPlanner {
	/** Slot index (menu index) to left-click next, or empty if no further click helps. */
	OptionalInt nextClick(ContainerState state);

	/** Whether {@code state} is the goal. Empty {@link #nextClick} with an incomplete state means the planner is stuck. */
	boolean isComplete(ContainerState state);
}
