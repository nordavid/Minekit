package io.github.nordavid.minekit.feature.quickactions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * JSON format of {@code config/minekit/quick-actions.json}.
 *
 * <pre>{@code
 * { "schemaVersion": 1, "pinned": ["sort_inventory"], "hidden": ["open_keybinds"], "recent": ["open_crafting"] }
 * }</pre>
 */
public final class QuickActionSettingsCodec implements JsonCodec<QuickActionSettings> {
	public static final QuickActionSettingsCodec INSTANCE = new QuickActionSettingsCodec();
	public static final int SCHEMA_VERSION = 1;

	private QuickActionSettingsCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public QuickActionSettings defaultValue() {
		return QuickActionSettings.DEFAULT;
	}

	@Override
	public Decoded<QuickActionSettings> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		List<String> pinned = ids(object, "pinned", warnings);
		Set<String> hidden = new TreeSet<>(ids(object, "hidden", warnings));
		List<String> recent = ids(object, "recent", warnings);
		return new Decoded<>(new QuickActionSettings(pinned, hidden, recent), schemaVersion, warnings);
	}

	@Override
	public JsonObject encode(QuickActionSettings settings) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.add("pinned", array(settings.pinned()));
		root.add("hidden", array(settings.hidden()));
		root.add("recent", array(settings.recent()));
		return root;
	}

	private static List<String> ids(JsonObject object, String key, List<String> warnings) {
		List<String> ids = new ArrayList<>();
		JsonElement element = object.get(key);
		if (element == null) {
			return ids;
		}
		if (!element.isJsonArray()) {
			warnings.add(key + ": expected a list of action ids, ignored");
			return ids;
		}
		for (JsonElement entry : element.getAsJsonArray()) {
			String id = JsonFields.string(entry);
			if (id != null && QuickActionSettings.isId(id)) {
				ids.add(id);
			} else {
				warnings.add(key + ": invalid action id " + entry + " ignored");
			}
		}
		return ids;
	}

	private static JsonArray array(Iterable<String> ids) {
		JsonArray array = new JsonArray();
		ids.forEach(array::add);
		return array;
	}
}
