package io.github.nordavid.minekit.feature.quickactions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * How the player arranged the Quick Actions popup, stored in {@code config/minekit/quick-actions.json}.
 *
 * @param pinned actions shown first, in this order; the first nine also run with the keys 1 to 9
 * @param hidden actions left out of the list (search still finds them)
 * @param recent the last actions run, most recent first; shown after the pinned ones
 */
public record QuickActionSettings(List<String> pinned, Set<String> hidden, List<String> recent) {
	public static final int MAX_PINNED = 20;
	public static final int NUMBERED = 9;
	public static final int MAX_RECENT = 5;
	public static final QuickActionSettings DEFAULT = new QuickActionSettings(List.of(), Set.of(), List.of());

	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");

	public QuickActionSettings {
		pinned = cleaned(pinned, MAX_PINNED);
		hidden = Collections.unmodifiableSet(new TreeSet<>(cleaned(List.copyOf(hidden), Integer.MAX_VALUE)));
		recent = cleaned(recent, MAX_RECENT);
	}

	/** Whether {@code id} is a valid action id. */
	public static boolean isId(String id) {
		return ID.matcher(id).matches();
	}

	public boolean isPinned(String id) {
		return pinned.contains(id);
	}

	public boolean isHidden(String id) {
		return hidden.contains(id);
	}

	/** Pins the action at the end, or unpins it. Pinning also shows a hidden action again. */
	public QuickActionSettings withPinToggled(String id) {
		List<String> nextPinned = new ArrayList<>(pinned);
		Set<String> nextHidden = new TreeSet<>(hidden);
		if (!nextPinned.remove(id)) {
			if (nextPinned.size() >= MAX_PINNED) {
				return this;
			}
			nextPinned.add(id);
			nextHidden.remove(id);
		}
		return new QuickActionSettings(nextPinned, nextHidden, recent);
	}

	/** Moves a pinned action one place up ({@code -1}) or down ({@code +1}). */
	public QuickActionSettings withPinnedMoved(String id, int delta) {
		int from = pinned.indexOf(id);
		int to = from + delta;
		if (from < 0 || to < 0 || to >= pinned.size()) {
			return this;
		}
		List<String> next = new ArrayList<>(pinned);
		Collections.swap(next, from, to);
		return new QuickActionSettings(next, hidden, recent);
	}

	/** Hides the action, or shows it again. Hiding also unpins it. */
	public QuickActionSettings withHiddenToggled(String id) {
		List<String> nextPinned = new ArrayList<>(pinned);
		Set<String> nextHidden = new TreeSet<>(hidden);
		if (!nextHidden.remove(id)) {
			nextHidden.add(id);
			nextPinned.remove(id);
		}
		return new QuickActionSettings(nextPinned, nextHidden, recent);
	}

	/** Records that the action was just run. */
	public QuickActionSettings withUsed(String id) {
		List<String> next = new ArrayList<>();
		next.add(id);
		recent.stream().filter(other -> !other.equals(id)).forEach(next::add);
		return new QuickActionSettings(pinned, hidden, next);
	}

	/**
	 * The list without a search: pinned actions in their order, then recently used ones, then all others in their
	 * original order. Hidden actions are left out. Ids that no longer exist (a deleted loadout) are skipped.
	 */
	public <T> List<T> arrange(List<T> actions, Function<T, String> idOf) {
		List<T> result = new ArrayList<>();
		for (String id : pinned) {
			actions.stream().filter(action -> idOf.apply(action).equals(id)).findFirst().ifPresent(result::add);
		}
		for (String id : recent) {
			actions.stream().filter(action -> idOf.apply(action).equals(id) && !isPinned(id) && !isHidden(id))
					.findFirst().ifPresent(result::add);
		}
		for (T action : actions) {
			String id = idOf.apply(action);
			if (!isPinned(id) && !recent.contains(id) && !isHidden(id)) {
				result.add(action);
			}
		}
		return result;
	}

	/** How many of the arranged actions are pinned (they come first). */
	public <T> int pinnedCount(List<T> actions, Function<T, String> idOf) {
		return (int) actions.stream().filter(action -> isPinned(idOf.apply(action))).count();
	}

	private static List<String> cleaned(List<String> ids, int limit) {
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String id : ids) {
			if (id != null && isId(id) && unique.size() < limit) {
				unique.add(id);
			}
		}
		return List.copyOf(unique);
	}
}
