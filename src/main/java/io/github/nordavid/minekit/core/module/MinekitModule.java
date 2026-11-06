package io.github.nordavid.minekit.core.module;

/**
 * A feature module managed by {@link ModuleManager}.
 *
 * <p>Lifecycle: {@link #initialize} runs exactly once at client start, before the module is enabled for the first
 * time. Minecraft key mappings and Fabric event callbacks cannot be unregistered, so a module registers them there
 * once and makes every callback check {@link ModuleContext#isEnabled()}. {@link #onEnable()} and
 * {@link #onDisable()} then only switch state, for example closing the module's screen when it is disabled.
 *
 * <p>All methods are called on the client thread. Exceptions are caught by the manager, which marks the module as
 * failed instead of crashing the game.
 */
public interface MinekitModule {
	ModuleMetadata metadata();

	void initialize(ModuleContext context);

	default void onEnable() {
	}

	default void onDisable() {
	}
}
