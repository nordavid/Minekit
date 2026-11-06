package io.github.nordavid.minekit.core.action;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Ordered set of Quick Actions. Modules add fixed actions during initialization, and providers for actions that
 * come and go (one per saved loadout, for example). Provided actions follow the fixed ones.
 */
public final class QuickActionRegistry {
	private final List<QuickAction> actions = new ArrayList<>();
	private final List<Supplier<List<QuickAction>>> providers = new ArrayList<>();

	public void register(QuickAction action) {
		Objects.requireNonNull(action, "action");
		if (actions.stream().anyMatch(existing -> existing.id().equals(action.id()))) {
			throw new IllegalArgumentException("Duplicate quick action id: " + action.id());
		}
		actions.add(action);
	}

	/** Adds a source of actions that is asked again every time the list is shown. Its ids must not clash. */
	public void registerProvider(Supplier<List<QuickAction>> provider) {
		providers.add(Objects.requireNonNull(provider, "provider"));
	}

	/** Fixed actions, then provided ones. A provided action whose id is already taken is left out. */
	public List<QuickAction> actions() {
		List<QuickAction> all = new ArrayList<>(actions);
		Set<String> ids = new HashSet<>();
		actions.forEach(action -> ids.add(action.id()));
		for (Supplier<List<QuickAction>> provider : providers) {
			for (QuickAction action : provider.get()) {
				if (ids.add(action.id())) {
					all.add(action);
				}
			}
		}
		return List.copyOf(all);
	}
}
