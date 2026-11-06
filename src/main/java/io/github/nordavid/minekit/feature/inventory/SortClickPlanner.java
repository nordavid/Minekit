package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.container.ClickPlanner;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.LayoutClickPlanner;
import java.util.OptionalInt;

/** Clicks the inventory into the sorted layout: a {@link LayoutClickPlanner} over the sort region. */
public final class SortClickPlanner implements ClickPlanner {
	private final LayoutClickPlanner layout;

	public SortClickPlanner(SortPlanner.Planned plan) {
		this.layout = new LayoutClickPlanner(plan.region(), plan.target(), plan.region());
	}

	@Override
	public OptionalInt nextClick(ContainerState state) {
		return layout.nextClick(state);
	}

	@Override
	public boolean isComplete(ContainerState state) {
		return layout.isComplete(state);
	}
}
