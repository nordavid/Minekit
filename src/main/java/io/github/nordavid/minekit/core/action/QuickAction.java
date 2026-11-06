package io.github.nordavid.minekit.core.action;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/**
 * One entry of the Quick Actions popup.
 *
 * <p>Actions that would move items or otherwise change game state must ask for confirmation themselves (for example by
 * opening a preview screen); Phase 1 only contains navigation actions.
 *
 * @param id                stable identifier
 * @param name              display name; also matched by the search
 * @param keywords          extra search terms (any language), may be empty
 * @param unavailableReason empty if the action can run now, otherwise why not; evaluated when the popup is shown
 * @param run               executed on the client thread after the popup has closed
 */
public record QuickAction(String id, Component name, String keywords, Supplier<Optional<Component>> unavailableReason, Runnable run) {
	public QuickAction {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(keywords, "keywords");
		Objects.requireNonNull(unavailableReason, "unavailableReason");
		Objects.requireNonNull(run, "run");
	}

	/** An action that is always available. */
	public static QuickAction always(String id, Component name, String keywords, Runnable run) {
		return new QuickAction(id, name, keywords, Optional::empty, run);
	}

	/** Text the search matches against: the translated name followed by the keywords. */
	public String searchableText() {
		return name.getString() + " " + keywords;
	}
}
