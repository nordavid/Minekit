package io.github.nordavid.minekit.core.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModuleManagerTest {
	private final Map<String, Boolean> stored = new HashMap<>();
	private final ModuleManager manager = new ModuleManager(new ModuleManager.Settings() {
		@Override
		public Optional<Boolean> storedEnabled(String moduleId) {
			return Optional.ofNullable(stored.get(moduleId));
		}

		@Override
		public void storeEnabled(String moduleId, boolean enabled) {
			stored.put(moduleId, enabled);
		}
	});

	/** Records lifecycle calls and can be told to throw in a given phase. */
	private static final class TestModule implements MinekitModule {
		final List<String> calls = new ArrayList<>();
		final ModuleMetadata metadata;
		String failIn = "";
		ModuleContext context;

		TestModule(String id, boolean enabledByDefault) {
			metadata = new ModuleMetadata(id, "name." + id, "desc." + id, enabledByDefault);
		}

		@Override
		public ModuleMetadata metadata() {
			return metadata;
		}

		@Override
		public void initialize(ModuleContext moduleContext) {
			context = moduleContext;
			record("initialize");
		}

		@Override
		public void onEnable() {
			record("enable");
		}

		@Override
		public void onDisable() {
			record("disable");
		}

		private void record(String phase) {
			calls.add(phase);
			if (failIn.equals(phase)) {
				throw new IllegalStateException("boom in " + phase);
			}
		}
	}

	@Test
	void defaultStateAppliesWhenNothingIsStored() {
		TestModule on = new TestModule("on_by_default", true);
		TestModule off = new TestModule("off_by_default", false);
		manager.register(on);
		manager.register(off);

		manager.initializeAll();

		assertEquals(List.of("initialize", "enable"), on.calls);
		assertEquals(List.of("initialize"), off.calls);
		assertTrue(on.context.isEnabled());
		assertFalse(off.context.isEnabled());
	}

	@Test
	void storedChoiceOverridesDefault() {
		stored.put("module", false);
		TestModule module = new TestModule("module", true);
		manager.register(module);

		manager.initializeAll();

		assertEquals(ModuleState.DISABLED, manager.status("module").orElseThrow().state());
	}

	@Test
	void togglingCallsLifecycleAndStoresChoice() {
		TestModule module = new TestModule("module", true);
		manager.register(module);
		manager.initializeAll();

		assertEquals(ModuleState.DISABLED, manager.setEnabled("module", false));
		assertEquals(ModuleState.ENABLED, manager.setEnabled("module", true));
		assertEquals(ModuleState.ENABLED, manager.setEnabled("module", true), "enabling twice is a no-op");

		assertEquals(List.of("initialize", "enable", "disable", "enable"), module.calls);
		assertEquals(Optional.of(true), Optional.ofNullable(stored.get("module")));
	}

	@Test
	void failingInitializeIsolatesTheModule() {
		TestModule broken = new TestModule("broken", true);
		broken.failIn = "initialize";
		TestModule healthy = new TestModule("healthy", true);
		manager.register(broken);
		manager.register(healthy);

		manager.initializeAll();

		ModuleManager.ModuleStatus status = manager.status("broken").orElseThrow();
		assertEquals(ModuleState.FAILED, status.state());
		assertEquals(Optional.of("IllegalStateException: boom in initialize"), status.failure());
		assertEquals(List.of("initialize"), broken.calls, "a module that failed to initialize is never enabled");
		assertTrue(manager.isEnabled("healthy"));
	}

	@Test
	void failingEnableMarksModuleFailedAndItCannotBeReenabled() {
		TestModule module = new TestModule("module", true);
		module.failIn = "enable";
		manager.register(module);
		manager.initializeAll();

		assertEquals(ModuleState.FAILED, manager.status("module").orElseThrow().state());
		assertEquals(ModuleState.FAILED, manager.setEnabled("module", true));
		assertEquals(List.of("initialize", "enable"), module.calls);
	}

	@Test
	void failedModuleCanBeSwitchedOffForNextStart() {
		TestModule module = new TestModule("module", true);
		module.failIn = "enable";
		manager.register(module);
		manager.initializeAll();

		manager.setEnabled("module", false);

		assertEquals(Optional.of(false), Optional.ofNullable(stored.get("module")));
	}

	@Test
	void failingDisableStillDisables() {
		TestModule module = new TestModule("module", true);
		module.failIn = "disable";
		manager.register(module);
		manager.initializeAll();

		assertEquals(ModuleState.DISABLED, manager.setEnabled("module", false));
		assertFalse(module.context.isEnabled());
	}

	@Test
	void registrationRules() {
		manager.register(new TestModule("module", true));
		assertThrows(IllegalArgumentException.class, () -> manager.register(new TestModule("module", false)));
		assertThrows(IllegalArgumentException.class, () -> new ModuleMetadata("Bad Id", "n", "d", true));

		manager.initializeAll();

		assertThrows(IllegalStateException.class, () -> manager.register(new TestModule("late", true)));
		assertThrows(IllegalStateException.class, manager::initializeAll);
		assertThrows(IllegalArgumentException.class, () -> manager.setEnabled("unknown", true));
	}

	@Test
	void modulesAreListedInRegistrationOrder() {
		manager.register(new TestModule("b_module", true));
		manager.register(new TestModule("a_module", true));

		assertEquals(List.of("b_module", "a_module"),
				manager.modules().stream().map(status -> status.metadata().id()).toList());
	}

	@Test
	void aCallbackThatThrowsStopsOnlyItsModule() {
		TestModule broken = new TestModule("broken", true);
		TestModule healthy = new TestModule("healthy", true);
		manager.register(broken);
		manager.register(healthy);
		List<String> reported = new ArrayList<>();
		manager.onFailure(status -> reported.add(status.metadata().id() + " " + status.failure().orElse("")));
		manager.initializeAll();

		int[] runs = {0};
		broken.context.guard("tick", () -> runs[0]++);
		broken.context.guard("tick", () -> {
			throw new IllegalStateException("bad tick");
		});
		assertEquals(ModuleState.FAILED, manager.status("broken").orElseThrow().state());
		assertEquals(List.of("initialize", "enable", "disable"), broken.calls, "its cleanup runs once");
		assertEquals(List.of("broken IllegalStateException: bad tick"), reported);

		broken.context.guard("tick", () -> runs[0]++);
		assertEquals(1, runs[0], "callbacks of a failed module are skipped");
		assertFalse(broken.context.guard("key", () -> true, false), "a question gets the fallback");
		assertEquals(ModuleState.FAILED, manager.setEnabled("broken", true), "it cannot be enabled again this session");

		assertTrue(healthy.context.guard("key", () -> true, false));
		assertTrue(healthy.context.isEnabled(), "the other module keeps running");
	}

	@Test
	void callbacksAreTimed() {
		TestModule module = new TestModule("module", true);
		manager.register(module);
		manager.initializeAll();
		module.context.guard("tick", () -> {
		});
		module.context.guard("key", () -> true, false);
		ModuleManager.Timing timing = manager.timings().getFirst();
		assertEquals("module", timing.moduleId());
		assertEquals(2, timing.calls());
		assertTrue(timing.maxNanos() <= timing.totalNanos());
	}

	@Test
	void aQuestionThatThrowsAnswersTheFallback() {
		TestModule module = new TestModule("module", true);
		manager.register(module);
		manager.initializeAll();

		assertTrue(module.context.guard("key", () -> {
			throw new IllegalArgumentException("bad key");
		}, true));
		assertEquals(ModuleState.FAILED, manager.status("module").orElseThrow().state());
	}
}
