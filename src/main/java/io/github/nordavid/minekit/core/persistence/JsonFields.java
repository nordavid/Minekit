package io.github.nordavid.minekit.core.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Small, strict readers shared by {@link JsonCodec} implementations. They return {@code null} for invalid values. */
public final class JsonFields {
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");
	private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");

	private JsonFields() {
	}

	/** Reads {@code schemaVersion}; adds a warning and assumes {@code current} if it is missing or invalid. */
	public static int schemaVersion(JsonObject root, int current, List<String> warnings) {
		if (root.get("schemaVersion") instanceof JsonPrimitive primitive && primitive.isNumber()) {
			Integer value = asInt(primitive);
			if (value != null && value >= 1) {
				return value;
			}
		}
		warnings.add("schemaVersion missing or invalid, assuming " + current);
		return current;
	}

	/** Entries of an object-valued section, or none (with a warning) if the section has the wrong type. */
	public static Iterable<Map.Entry<String, JsonElement>> section(JsonObject root, String key, List<String> warnings) {
		JsonElement element = root.get(key);
		if (element == null) {
			return List.of();
		}
		if (!element.isJsonObject()) {
			warnings.add(key + ": expected an object, section ignored");
			return List.of();
		}
		return element.getAsJsonObject().entrySet();
	}

	/** Lower-case id such as a module or HUD element id. */
	public static boolean isId(String id) {
		return ID.matcher(id).matches();
	}

	/** Namespaced id such as {@code minecraft:diamond_pickaxe}. */
	public static boolean isResourceId(String id) {
		return RESOURCE_ID.matcher(id).matches();
	}

	public static Boolean bool(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isBoolean() ? primitive.getAsBoolean() : null;
	}

	public static Integer integer(JsonObject json, String key) {
		return json.get(key) instanceof JsonPrimitive primitive && primitive.isNumber() ? asInt(primitive) : null;
	}

	public static String string(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isString() ? primitive.getAsString() : null;
	}

	public static <E extends Enum<E>> E enumValue(JsonElement element, Class<E> type) {
		String value = string(element);
		if (value == null) {
			return null;
		}
		try {
			return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ignored) {
			return null;
		}
	}

	private static Integer asInt(JsonPrimitive primitive) {
		double value = primitive.getAsDouble();
		return value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE ? (int) value : null;
	}
}
