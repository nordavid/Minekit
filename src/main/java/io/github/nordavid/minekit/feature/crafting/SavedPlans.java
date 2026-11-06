package io.github.nordavid.minekit.feature.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Crafting plans the player saved, newest first; at most one per target item and {@link #MAX_PLANS} in total.
 * Stored in {@code config/minekit/crafting-plans.json}.
 */
public record SavedPlans(List<SavedPlan> plans) {
	public static final int MAX_PLANS = 50;
	public static final SavedPlans EMPTY = new SavedPlans(List.of());

	/** A plan request with the time it was saved (epoch milliseconds). */
	public record SavedPlan(PlanRequest request, long savedAt) {
		public SavedPlan {
			Objects.requireNonNull(request, "request");
		}
	}

	public SavedPlans {
		plans = List.copyOf(plans);
	}

	public Optional<SavedPlan> forTarget(String target) {
		return plans.stream().filter(plan -> plan.request().target().equals(target)).findFirst();
	}

	/** Saves {@code request}, replacing an earlier plan for the same target and dropping the oldest beyond the limit. */
	public SavedPlans withSaved(PlanRequest request, long now) {
		List<SavedPlan> next = new ArrayList<>(plans.size() + 1);
		next.add(new SavedPlan(request, now));
		plans.stream().filter(plan -> !plan.request().target().equals(request.target())).forEach(next::add);
		return new SavedPlans(next.subList(0, Math.min(next.size(), MAX_PLANS)));
	}

	public SavedPlans without(String target) {
		return new SavedPlans(plans.stream().filter(plan -> !plan.request().target().equals(target)).toList());
	}
}
