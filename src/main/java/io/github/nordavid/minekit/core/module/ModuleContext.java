package io.github.nordavid.minekit.core.module;

import java.util.function.BooleanSupplier;

/** What a module may ask the {@link ModuleManager} about itself. */
public interface ModuleContext {
	String moduleId();

	/** Whether the module is currently enabled. Event callbacks registered in {@code initialize} must check this. */
	boolean isEnabled();

	/**
	 * Runs a callback of this module, such as a tick or a screen event. If it throws, the module stops for the rest of
	 * the session ({@link ModuleState#FAILED}): the error is logged, the player is told, and the game keeps running.
	 * Once the module has failed, its callbacks are skipped.
	 *
	 * @param what a few words for the log, such as "tick"
	 */
	void guard(String what, Runnable work);

	/** Like {@link #guard(String, Runnable)} for a callback that answers a question; {@code fallback} if it cannot. */
	boolean guard(String what, BooleanSupplier work, boolean fallback);
}
