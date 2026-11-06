package io.github.nordavid.minekit.feature.blueprint;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A block state as a blueprint file names it: block id and properties, e.g. {@code minecraft:oak_stairs[facing=north]}.
 * Pure text; whether this Minecraft version knows the block is decided later, when it is resolved.
 *
 * @param id         namespaced block id
 * @param properties property name to value, sorted by name
 */
public record BlockSpec(String id, SortedMap<String, String> properties) {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,64}");
	private static final Pattern VALUE = Pattern.compile("[a-z0-9_.-]{1,64}");

	public static final BlockSpec AIR = new BlockSpec("minecraft:air", new TreeMap<>());

	public BlockSpec {
		Objects.requireNonNull(id, "id");
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("invalid block id: " + id);
		}
		TreeMap<String, String> checked = new TreeMap<>();
		for (Map.Entry<String, String> entry : properties.entrySet()) {
			if (!NAME.matcher(entry.getKey()).matches() || !VALUE.matcher(entry.getValue()).matches()) {
				throw new IllegalArgumentException("invalid block property " + entry.getKey() + "=" + entry.getValue());
			}
			checked.put(entry.getKey(), entry.getValue());
		}
		properties = Collections.unmodifiableSortedMap(checked);
	}

	public static BlockSpec of(String id, Map<String, String> properties) {
		return new BlockSpec(withNamespace(id), new TreeMap<>(properties));
	}

	/** Parses {@code namespace:id[name=value,...]}; the namespace defaults to {@code minecraft}. */
	public static BlockSpec parse(String text) {
		String trimmed = text.strip();
		int bracket = trimmed.indexOf('[');
		String id = bracket < 0 ? trimmed : trimmed.substring(0, bracket);
		TreeMap<String, String> properties = new TreeMap<>();
		if (bracket >= 0) {
			if (!trimmed.endsWith("]")) {
				throw new IllegalArgumentException("unclosed properties: " + text);
			}
			String inner = trimmed.substring(bracket + 1, trimmed.length() - 1);
			if (!inner.isBlank()) {
				for (String pair : inner.split(",")) {
					int equals = pair.indexOf('=');
					if (equals <= 0) {
						throw new IllegalArgumentException("invalid property in " + text);
					}
					properties.put(pair.substring(0, equals).strip(), pair.substring(equals + 1).strip());
				}
			}
		}
		return new BlockSpec(withNamespace(id), properties);
	}

	private static String withNamespace(String id) {
		return id.indexOf(':') < 0 ? "minecraft:" + id : id;
	}

	/** Air, cave air and void air: positions a blueprint leaves empty. */
	public boolean isAir() {
		return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
	}

	@Override
	public String toString() {
		if (properties.isEmpty()) {
			return id;
		}
		StringBuilder text = new StringBuilder(id).append('[');
		properties.forEach((name, value) -> text.append(name).append('=').append(value).append(','));
		text.setCharAt(text.length() - 1, ']');
		return text.toString();
	}
}
