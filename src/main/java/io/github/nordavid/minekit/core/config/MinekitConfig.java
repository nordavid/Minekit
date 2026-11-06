package io.github.nordavid.minekit.core.config;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Immutable snapshot of the user configuration.
 *
 * <p>Only values the user (or a previous version) explicitly stored are present. Defaults belong to the code that
 * owns a setting, for example a module's {@code enabledByDefault}, so changing a default in a later version reaches
 * users who never touched the setting. Entries for modules or HUD elements that are not installed are kept untouched.
 *
 * <p>Maps are sorted by key so the written file is stable and diff-friendly.
 */
public record MinekitConfig(SortedMap<String, Boolean> moduleEnabled, SortedMap<String, HudElementConfig> hudElements) {
	public static final MinekitConfig DEFAULT = new MinekitConfig(new TreeMap<>(), new TreeMap<>());

	public MinekitConfig {
		moduleEnabled = Collections.unmodifiableSortedMap(new TreeMap<>(moduleEnabled));
		hudElements = Collections.unmodifiableSortedMap(new TreeMap<>(hudElements));
	}

	public Optional<Boolean> moduleEnabled(String moduleId) {
		return Optional.ofNullable(moduleEnabled.get(moduleId));
	}

	public MinekitConfig withModuleEnabled(String moduleId, boolean enabled) {
		Objects.requireNonNull(moduleId, "moduleId");
		TreeMap<String, Boolean> modules = new TreeMap<>(moduleEnabled);
		modules.put(moduleId, enabled);
		return new MinekitConfig(modules, hudElements);
	}

	public Optional<HudElementConfig> hudElement(String elementId) {
		return Optional.ofNullable(hudElements.get(elementId));
	}

	public MinekitConfig withHudElement(String elementId, HudElementConfig config) {
		Objects.requireNonNull(elementId, "elementId");
		Objects.requireNonNull(config, "config");
		TreeMap<String, HudElementConfig> hud = new TreeMap<>(hudElements);
		hud.put(elementId, config);
		return new MinekitConfig(moduleEnabled, hud);
	}
}
