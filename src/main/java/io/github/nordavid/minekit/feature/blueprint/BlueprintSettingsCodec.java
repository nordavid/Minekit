package io.github.nordavid.minekit.feature.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * JSON format of {@code config/minekit/blueprints.json}.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "active": "tower.litematic",
 *   "show": true,
 *   "wholeStructure": true,
 *   "ghostOpacity": 50,
 *   "placements": { "tower.litematic": { "sp-0123456789abcdef": { "x": 10, "y": 64, "z": -3, "rotation": 1, "mirrored": false } } },
 *   "ranges": { "tower.litematic": [0, 4] },
 *   "maxFileMegabytes": 32,
 *   "maxPositions": 16000000
 * }
 * }</pre>
 */
public final class BlueprintSettingsCodec implements JsonCodec<BlueprintSettings> {
	public static final BlueprintSettingsCodec INSTANCE = new BlueprintSettingsCodec();
	public static final int SCHEMA_VERSION = 1;
	private static final Pattern WORLD = Pattern.compile("(sp|mp)-[0-9a-f]{16}");

	private BlueprintSettingsCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public BlueprintSettings defaultValue() {
		return BlueprintSettings.DEFAULT;
	}

	/** Blueprint file names: one path segment, a supported extension, no path tricks. */
	static boolean isFileName(String name) {
		return !name.isBlank() && name.length() <= 128 && !name.contains("/") && !name.contains("\\") && !name.startsWith(".")
				&& BlueprintFiles.isSupported(java.nio.file.Path.of(name));
	}

	@Override
	public Decoded<BlueprintSettings> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		String active = JsonFields.string(object.get("active"));
		if (active != null && !isFileName(active)) {
			warnings.add("active: invalid file name ignored");
			active = null;
		}
		Boolean show = JsonFields.bool(object, "show");
		// Added in 0.8.0 without a new schema version: older files simply do not have it.
		Boolean whole = JsonFields.bool(object, "wholeStructure");
		Integer ghostOpacity = JsonFields.integer(object, "ghostOpacity");

		Map<String, Map<String, Placement>> placements = new TreeMap<>();
		for (Map.Entry<String, JsonElement> file : JsonFields.section(object, "placements", warnings)) {
			if (!isFileName(file.getKey()) || !file.getValue().isJsonObject()) {
				warnings.add("placements." + file.getKey() + ": ignored");
				continue;
			}
			Map<String, Placement> byWorld = new TreeMap<>();
			for (Map.Entry<String, JsonElement> world : file.getValue().getAsJsonObject().entrySet()) {
				Placement placement = world.getValue().isJsonObject() ? readPlacement(world.getValue().getAsJsonObject()) : null;
				if (!WORLD.matcher(world.getKey()).matches() || placement == null) {
					warnings.add("placements." + file.getKey() + "." + world.getKey() + ": ignored");
				} else {
					byWorld.put(world.getKey(), placement);
				}
			}
			if (!byWorld.isEmpty()) {
				placements.put(file.getKey(), byWorld);
			}
		}

		Map<String, BlueprintSettings.LayerRange> ranges = new TreeMap<>();
		for (Map.Entry<String, JsonElement> entry : JsonFields.section(object, "ranges", warnings)) {
			BlueprintSettings.LayerRange range = null;
			if (isFileName(entry.getKey()) && entry.getValue().isJsonArray() && entry.getValue().getAsJsonArray().size() == 2) {
				JsonArray pair = entry.getValue().getAsJsonArray();
				Integer from = integer(pair.get(0));
				Integer to = integer(pair.get(1));
				if (from != null && to != null && from >= 0 && to >= from) {
					range = new BlueprintSettings.LayerRange(from, to);
				}
			}
			if (range == null) {
				warnings.add("ranges." + entry.getKey() + ": ignored");
			} else {
				ranges.put(entry.getKey(), range);
			}
		}
		Integer maxFile = JsonFields.integer(object, "maxFileMegabytes");
		Long maxPositions = object.get("maxPositions") instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : null;
		BlueprintSettings value = new BlueprintSettings(active, show == null || show, whole == null || whole,
				ghostOpacity == null ? BlueprintSettings.DEFAULT_GHOST_OPACITY : ghostOpacity, placements, ranges,
				maxFile == null ? BlueprintSettings.DEFAULT_MAX_FILE_MEGABYTES : maxFile,
				maxPositions == null ? BlueprintSettings.DEFAULT_MAX_POSITIONS : maxPositions);
		return new Decoded<>(value, schemaVersion, warnings);
	}

	private static Placement readPlacement(JsonObject json) {
		Integer x = JsonFields.integer(json, "x");
		Integer y = JsonFields.integer(json, "y");
		Integer z = JsonFields.integer(json, "z");
		Integer rotation = JsonFields.integer(json, "rotation");
		Boolean mirrored = JsonFields.bool(json, "mirrored");
		if (x == null || y == null || z == null || rotation == null || mirrored == null || rotation < 0 || rotation > 3
				|| Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000 || Math.abs(y) > 4096) {
			return null;
		}
		return new Placement(x, y, z, rotation, mirrored);
	}

	private static Integer integer(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : null;
	}

	@Override
	public JsonObject encode(BlueprintSettings settings) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		if (settings.active() != null) {
			root.addProperty("active", settings.active());
		}
		root.addProperty("show", settings.show());
		root.addProperty("wholeStructure", settings.whole());
		root.addProperty("ghostOpacity", settings.ghostOpacity());
		JsonObject placements = new JsonObject();
		settings.placements().forEach((file, byWorld) -> {
			JsonObject worlds = new JsonObject();
			byWorld.forEach((world, placement) -> {
				JsonObject json = new JsonObject();
				json.addProperty("x", placement.x());
				json.addProperty("y", placement.y());
				json.addProperty("z", placement.z());
				json.addProperty("rotation", placement.rotation());
				json.addProperty("mirrored", placement.mirrored());
				worlds.add(world, json);
			});
			placements.add(file, worlds);
		});
		root.add("placements", placements);
		JsonObject ranges = new JsonObject();
		settings.ranges().forEach((file, range) -> {
			JsonArray pair = new JsonArray();
			pair.add(range.from());
			pair.add(range.to());
			ranges.add(file, pair);
		});
		root.add("ranges", ranges);
		root.addProperty("maxFileMegabytes", settings.maxFileMegabytes());
		root.addProperty("maxPositions", settings.maxPositions());
		return root;
	}
}
