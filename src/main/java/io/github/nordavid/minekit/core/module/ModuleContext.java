package io.github.nordavid.minekit.core.module;

/** What a module may ask the {@link ModuleManager} about itself. */
public interface ModuleContext {
	String moduleId();

	/** Whether the module is currently enabled. Event callbacks registered in {@code initialize} must check this. */
	boolean isEnabled();
}
