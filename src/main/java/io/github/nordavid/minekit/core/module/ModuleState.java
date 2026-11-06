package io.github.nordavid.minekit.core.module;

public enum ModuleState {
	/** Registered, but {@link ModuleManager#initializeAll()} has not run yet. */
	REGISTERED,
	ENABLED,
	DISABLED,
	/** Threw an exception during initialization or enabling; inactive until the next game start. */
	FAILED
}
