package io.github.nordavid.minekit.core.input;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central registry for MINEKIT shortcuts.
 *
 * <p>All shortcuts live in one "MINEKIT" category of the vanilla controls screen, so players rebind them where they
 * rebind everything else. Shortcuts must be registered during client initialization, before Minecraft loads its
 * options. Handlers run on the client thread at the end of each client tick; as in vanilla, presses only count
 * while no screen is open.
 */
public final class KeybindManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	private record Binding(KeyMapping mapping, Runnable onPress) {
	}

	private final String namespace;
	private final KeyMapping.Category category;
	private final List<Binding> bindings = new ArrayList<>();

	public KeybindManager(String namespace) {
		this.namespace = Objects.requireNonNull(namespace, "namespace");
		this.category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(namespace, "main"));
	}

	/**
	 * Registers a shortcut with the translation key {@code key.<namespace>.<id>}.
	 *
	 * @param defaultKey a keyboard key from {@link InputConstants}, e.g. {@link InputConstants#KEY_K}
	 */
	public KeyMapping register(String id, int defaultKey, Runnable onPress) {
		Objects.requireNonNull(onPress, "onPress");
		KeyMapping mapping = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + namespace + "." + id, InputConstants.Type.KEYBOARD, defaultKey, category));
		bindings.add(new Binding(mapping, onPress));
		return mapping;
	}

	public List<KeyMapping> mappings() {
		List<KeyMapping> result = new ArrayList<>(bindings.size());
		bindings.forEach(binding -> result.add(binding.mapping()));
		return Collections.unmodifiableList(result);
	}

	/** Dispatches pending presses. Call once per client tick. */
	public void tick() {
		for (Binding binding : bindings) {
			while (binding.mapping().consumeClick()) {
				try {
					binding.onPress().run();
				} catch (RuntimeException e) {
					LOGGER.error("Shortcut {} failed", binding.mapping().getName(), e);
				}
			}
		}
	}

	/** MINEKIT shortcuts that share their key with another shortcut, based on the player's current bindings. */
	public List<KeyConflicts.Conflict> conflicts(Options options) {
		Set<String> own = new HashSet<>();
		bindings.forEach(binding -> own.add(binding.mapping().getName()));
		List<KeyConflicts.Binding> all = new ArrayList<>(options.keyMappings.length);
		for (KeyMapping mapping : options.keyMappings) {
			all.add(new KeyConflicts.Binding(mapping.getName(), mapping.saveString()));
		}
		return KeyConflicts.find(own, all);
	}
}
