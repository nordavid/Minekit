package io.github.nordavid.minekit.core.goal;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lets one module hand collection goals ("gather 48 oak logs") to whichever module tracks resources, without the two
 * depending on each other. The Resource Tracker connects itself; the Crafting Assistant offers its missing materials.
 */
public final class GoalBridge {
	/** The module that accepts goals. */
	public interface Receiver {
		/** Whether goals can be accepted right now (e.g. the module is enabled and a world is loaded). */
		boolean isAvailable();

		/**
		 * Adds goals (item id to amount), starting a session named {@code sessionName} if none is open.
		 */
		void addGoals(Map<String, Integer> goals, String sessionName);
	}

	private @Nullable Receiver receiver;

	public void connect(Receiver newReceiver) {
		this.receiver = Objects.requireNonNull(newReceiver, "receiver");
	}

	public boolean isAvailable() {
		return receiver != null && receiver.isAvailable();
	}

	/** Returns {@code false} if no receiver is available. */
	public boolean addGoals(Map<String, Integer> goals, String sessionName) {
		if (!isAvailable() || goals.isEmpty()) {
			return false;
		}
		Objects.requireNonNull(receiver).addGoals(goals, sessionName);
		return true;
	}
}
