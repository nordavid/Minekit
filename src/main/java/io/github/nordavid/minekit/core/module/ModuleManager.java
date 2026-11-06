package io.github.nordavid.minekit.core.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the lifecycle and enabled state of all feature modules.
 *
 * <p>A module that throws, while starting or in a callback run through {@link ModuleContext#guard}, is marked
 * {@link ModuleState#FAILED} and stays inactive for the session; the other modules and the game keep running. Not
 * thread-safe; used from the client thread only.
 */
public final class ModuleManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("Minekit");

	/** Where the user's enabled/disabled choice is stored. */
	public interface Settings {
		Optional<Boolean> storedEnabled(String moduleId);

		void storeEnabled(String moduleId, boolean enabled);
	}

	/**
	 * Read-only view of one module for UI code.
	 *
	 * @param failure short reason if the module is {@link ModuleState#FAILED}
	 */
	public record ModuleStatus(ModuleMetadata metadata, ModuleState state, Optional<String> failure) {
		public boolean isEnabled() {
			return state == ModuleState.ENABLED;
		}
	}

	private final Settings settings;
	private final Map<String, Slot> slots = new LinkedHashMap<>();
	private boolean initialized;
	private Consumer<ModuleStatus> failureListener = status -> {
	};

	public ModuleManager(Settings settings) {
		this.settings = Objects.requireNonNull(settings, "settings");
	}

	/** Told when a module fails in a callback while the game runs, for example to show a notification. */
	public void onFailure(Consumer<ModuleStatus> listener) {
		failureListener = Objects.requireNonNull(listener, "listener");
	}

	public void register(MinekitModule module) {
		Objects.requireNonNull(module, "module");
		if (initialized) {
			throw new IllegalStateException("Modules must be registered before initializeAll()");
		}
		ModuleMetadata metadata = Objects.requireNonNull(module.metadata(), "metadata");
		if (slots.containsKey(metadata.id())) {
			throw new IllegalArgumentException("Duplicate module id: " + metadata.id());
		}
		slots.put(metadata.id(), new Slot(this, module, metadata));
	}

	/** Initializes every module once and enables those that are enabled in the settings (or by default). */
	public void initializeAll() {
		if (initialized) {
			throw new IllegalStateException("initializeAll() already ran");
		}
		initialized = true;
		for (Slot slot : slots.values()) {
			try {
				slot.module.initialize(slot);
			} catch (RuntimeException | LinkageError e) {
				markFailed(slot, "initialize", e);
				continue;
			}
			slot.state = ModuleState.DISABLED;
			boolean enable = settings.storedEnabled(slot.metadata.id()).orElse(slot.metadata.enabledByDefault());
			if (enable) {
				activate(slot);
			}
		}
	}

	/**
	 * Enables or disables a module and stores the choice.
	 *
	 * <p>A failed module cannot be enabled again during this session, but it can be switched off so it stays off
	 * after the next start.
	 *
	 * @return the resulting state
	 */
	public ModuleState setEnabled(String moduleId, boolean enabled) {
		Slot slot = require(moduleId);
		if (slot.state == ModuleState.REGISTERED) {
			throw new IllegalStateException("initializeAll() has not run yet");
		}
		if (slot.state == ModuleState.FAILED) {
			if (!enabled) {
				settings.storeEnabled(moduleId, false);
			}
			return slot.state;
		}
		if (enabled && slot.state == ModuleState.DISABLED) {
			activate(slot);
		} else if (!enabled && slot.state == ModuleState.ENABLED) {
			deactivate(slot);
		}
		settings.storeEnabled(moduleId, enabled);
		return slot.state;
	}

	public boolean isEnabled(String moduleId) {
		Slot slot = slots.get(moduleId);
		return slot != null && slot.state == ModuleState.ENABLED;
	}

	/** The module instance with this id, for integrations and tests that need more than its status. */
	public Optional<MinekitModule> module(String moduleId) {
		return Optional.ofNullable(slots.get(moduleId)).map(slot -> slot.module);
	}

	public Optional<ModuleStatus> status(String moduleId) {
		return Optional.ofNullable(slots.get(moduleId)).map(Slot::status);
	}

	/** All modules in registration order. */
	public List<ModuleStatus> modules() {
		List<ModuleStatus> result = new ArrayList<>(slots.size());
		slots.values().forEach(slot -> result.add(slot.status()));
		return result;
	}

	private void activate(Slot slot) {
		try {
			slot.module.onEnable();
			slot.state = ModuleState.ENABLED;
		} catch (RuntimeException | LinkageError e) {
			markFailed(slot, "enable", e);
		}
	}

	private void deactivate(Slot slot) {
		// The module counts as disabled even if its cleanup throws, so its callbacks stop doing work.
		slot.state = ModuleState.DISABLED;
		try {
			slot.module.onDisable();
		} catch (RuntimeException | LinkageError e) {
			LOGGER.error("Minekit module '{}' threw while being disabled", slot.metadata.id(), e);
		}
	}

	private void markFailed(Slot slot, String phase, Throwable error) {
		LOGGER.error("Minekit module '{}' failed in {} and is inactive until the next start", slot.metadata.id(), phase, error);
		slot.state = ModuleState.FAILED;
		slot.failure = error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
	}

	/** A callback threw: the module stops for the session, its cleanup runs, and the listener is told. */
	private void callbackFailed(Slot slot, String what, Throwable error) {
		boolean wasEnabled = slot.state == ModuleState.ENABLED;
		markFailed(slot, what, error);
		if (wasEnabled) {
			try {
				slot.module.onDisable();
			} catch (RuntimeException | LinkageError e) {
				LOGGER.error("Minekit module '{}' threw while being stopped after an error", slot.metadata.id(), e);
			}
		}
		try {
			failureListener.accept(slot.status());
		} catch (RuntimeException e) {
			LOGGER.error("Could not report the failure of module '{}'", slot.metadata.id(), e);
		}
	}

	private Slot require(String moduleId) {
		Slot slot = slots.get(moduleId);
		if (slot == null) {
			throw new IllegalArgumentException("Unknown module: " + moduleId);
		}
		return slot;
	}

	private static final class Slot implements ModuleContext {
		private final ModuleManager manager;
		private final MinekitModule module;
		private final ModuleMetadata metadata;
		private ModuleState state = ModuleState.REGISTERED;
		private String failure;

		private Slot(ModuleManager manager, MinekitModule module, ModuleMetadata metadata) {
			this.manager = manager;
			this.module = module;
			this.metadata = metadata;
		}

		@Override
		public void guard(String what, Runnable work) {
			if (state == ModuleState.FAILED) {
				return;
			}
			try {
				work.run();
			} catch (RuntimeException | LinkageError e) {
				manager.callbackFailed(this, what, e);
			}
		}

		@Override
		public boolean guard(String what, BooleanSupplier work, boolean fallback) {
			if (state == ModuleState.FAILED) {
				return fallback;
			}
			try {
				return work.getAsBoolean();
			} catch (RuntimeException | LinkageError e) {
				manager.callbackFailed(this, what, e);
				return fallback;
			}
		}

		@Override
		public String moduleId() {
			return metadata.id();
		}

		@Override
		public boolean isEnabled() {
			return state == ModuleState.ENABLED;
		}

		private ModuleStatus status() {
			return new ModuleStatus(metadata, state, Optional.ofNullable(failure));
		}
	}
}
