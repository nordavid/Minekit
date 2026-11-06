package io.github.nordavid.minekit.feature.loadout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * JSON format of {@code config/minekit/loadouts.json}.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "nextId": 3,
 *   "loadouts": [
 *     { "id": "loadout-1", "name": "Mining", "exact": false,
 *       "items": { "0": { "item": "minecraft:diamond_pickaxe", "variant": "", "count": 1 } },
 *       "locked": [8] }
 *   ]
 * }
 * }</pre>
 *
 * Positions are vanilla inventory indices ({@link LoadoutSlots}). An invalid loadout or entry is skipped with a
 * warning; the rest loads.
 */
public final class LoadoutsCodec implements JsonCodec<Loadouts> {
	public static final LoadoutsCodec INSTANCE = new LoadoutsCodec();
	public static final int SCHEMA_VERSION = 1;
	private static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");
	private static final int MAX_VARIANT_LENGTH = 4096;

	private LoadoutsCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public Loadouts defaultValue() {
		return Loadouts.EMPTY;
	}

	@Override
	public Decoded<Loadouts> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		Integer nextId = JsonFields.integer(object, "nextId");
		List<Loadout> loadouts = new ArrayList<>();
		JsonElement list = object.get("loadouts");
		if (list != null && !list.isJsonArray()) {
			warnings.add("loadouts: expected a list, ignored");
		} else if (list != null) {
			JsonArray array = list.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) {
				Loadout loadout = readLoadout(array.get(i), "loadouts[" + i + "]", warnings);
				if (loadout != null) {
					loadouts.add(loadout);
				}
			}
		}
		int highest = loadouts.stream().mapToInt(loadout -> number(loadout.id())).max().orElse(0);
		return new Decoded<>(new Loadouts(loadouts, Math.max(nextId == null ? 1 : nextId, highest + 1)), schemaVersion, warnings);
	}

	private static Loadout readLoadout(JsonElement element, String where, List<String> warnings) {
		if (!element.isJsonObject()) {
			warnings.add(where + ": not an object, ignored");
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		String id = JsonFields.string(object.get("id"));
		String name = JsonFields.string(object.get("name"));
		if (id == null || !ID.matcher(id).matches() || name == null) {
			warnings.add(where + ": missing or invalid id or name, ignored");
			return null;
		}
		Boolean exact = JsonFields.bool(object, "exact");
		TreeMap<Integer, LoadoutItem> items = new TreeMap<>();
		for (Map.Entry<String, JsonElement> entry : JsonFields.section(object, "items", warnings)) {
			Integer index = position(entry.getKey());
			LoadoutItem item = readItem(entry.getValue());
			if (index == null || item == null) {
				warnings.add(where + ".items." + entry.getKey() + ": invalid entry ignored");
			} else {
				items.put(index, item);
			}
		}
		TreeSet<Integer> locked = new TreeSet<>();
		JsonElement lockedJson = object.get("locked");
		if (lockedJson != null && lockedJson.isJsonArray()) {
			for (JsonElement entry : lockedJson.getAsJsonArray()) {
				Integer index = entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isNumber() ? position(entry.getAsString()) : null;
				if (index == null) {
					warnings.add(where + ".locked: invalid position ignored");
				} else {
					locked.add(index);
				}
			}
		} else if (lockedJson != null) {
			warnings.add(where + ".locked: expected a list, ignored");
		}
		return new Loadout(id, name, exact != null && exact, items, locked);
	}

	private static LoadoutItem readItem(JsonElement element) {
		if (!element.isJsonObject()) {
			return null;
		}
		JsonObject object = element.getAsJsonObject();
		String item = JsonFields.string(object.get("item"));
		String variant = JsonFields.string(object.get("variant"));
		Integer count = JsonFields.integer(object, "count");
		if (item == null || !JsonFields.isResourceId(item) || count == null || count < 1 || count > 99) {
			return null;
		}
		if (variant == null || variant.length() > MAX_VARIANT_LENGTH) {
			variant = "";
		}
		return new LoadoutItem(item, variant, count);
	}

	private static Integer position(String text) {
		try {
			int index = Integer.parseInt(text);
			return LoadoutSlots.isValid(index) ? index : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static int number(String id) {
		if (id.startsWith("loadout-")) {
			try {
				return Integer.parseInt(id.substring("loadout-".length()));
			} catch (NumberFormatException ignored) {
				return 0;
			}
		}
		return 0;
	}

	@Override
	public JsonObject encode(Loadouts value) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("nextId", value.nextId());
		JsonArray loadouts = new JsonArray();
		for (Loadout loadout : value.loadouts()) {
			JsonObject object = new JsonObject();
			object.addProperty("id", loadout.id());
			object.addProperty("name", loadout.name());
			object.addProperty("exact", loadout.exact());
			JsonObject items = new JsonObject();
			loadout.items().forEach((index, item) -> {
				JsonObject entry = new JsonObject();
				entry.addProperty("item", item.itemId());
				entry.addProperty("variant", item.variant());
				entry.addProperty("count", item.count());
				items.add(String.valueOf(index), entry);
			});
			object.add("items", items);
			JsonArray locked = new JsonArray();
			loadout.locked().forEach(locked::add);
			object.add("locked", locked);
			loadouts.add(object);
		}
		root.add("loadouts", loadouts);
		return root;
	}
}
