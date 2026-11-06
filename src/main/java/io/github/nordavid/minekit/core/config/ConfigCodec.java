package io.github.nordavid.minekit.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * JSON format of {@link MinekitConfig}.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "modules": { "quick_actions": { "enabled": true } },
 *   "hud": { "notifications": { "visible": true, "anchor": "TOP_RIGHT", "offsetX": 6, "offsetY": 6 } }
 * }
 * }</pre>
 */
public final class ConfigCodec implements JsonCodec<MinekitConfig> {
	public static final ConfigCodec INSTANCE = new ConfigCodec();
	public static final int SCHEMA_VERSION = 1;

	private ConfigCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public MinekitConfig defaultValue() {
		return MinekitConfig.DEFAULT;
	}

	@Override
	public Decoded<MinekitConfig> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();

		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		TreeMap<String, Boolean> modules = new TreeMap<>();
		TreeMap<String, HudElementConfig> hud = new TreeMap<>();

		for (Map.Entry<String, JsonElement> entry : JsonFields.section(object, "modules", warnings)) {
			String path = "modules." + entry.getKey();
			if (!JsonFields.isId(entry.getKey())) {
				warnings.add(path + ": invalid id, entry ignored");
				continue;
			}
			Boolean enabled = entry.getValue().isJsonObject() ? JsonFields.bool(entry.getValue().getAsJsonObject(), "enabled") : null;
			if (enabled == null) {
				warnings.add(path + ": expected {\"enabled\": true|false}, entry ignored");
				continue;
			}
			modules.put(entry.getKey(), enabled);
		}

		for (Map.Entry<String, JsonElement> entry : JsonFields.section(object, "hud", warnings)) {
			String path = "hud." + entry.getKey();
			if (!JsonFields.isId(entry.getKey())) {
				warnings.add(path + ": invalid id, entry ignored");
				continue;
			}
			HudElementConfig element = entry.getValue().isJsonObject() ? readHudElement(entry.getValue().getAsJsonObject()) : null;
			if (element == null) {
				warnings.add(path + ": expected visible, anchor, offsetX and offsetY, entry ignored");
				continue;
			}
			hud.put(entry.getKey(), element);
		}

		return new Decoded<>(new MinekitConfig(modules, hud), schemaVersion, warnings);
	}

	@Override
	public JsonObject encode(MinekitConfig config) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);

		JsonObject modules = new JsonObject();
		config.moduleEnabled().forEach((id, enabled) -> {
			JsonObject module = new JsonObject();
			module.addProperty("enabled", enabled);
			modules.add(id, module);
		});
		root.add("modules", modules);

		JsonObject hud = new JsonObject();
		config.hudElements().forEach((id, element) -> {
			JsonObject json = new JsonObject();
			json.addProperty("visible", element.visible());
			json.addProperty("anchor", element.anchor().name());
			json.addProperty("offsetX", element.offsetX());
			json.addProperty("offsetY", element.offsetY());
			hud.add(id, json);
		});
		root.add("hud", hud);
		return root;
	}

	private static HudElementConfig readHudElement(JsonObject json) {
		Boolean visible = JsonFields.bool(json, "visible");
		HudAnchor anchor = JsonFields.enumValue(json.get("anchor"), HudAnchor.class);
		Integer offsetX = JsonFields.integer(json, "offsetX");
		Integer offsetY = JsonFields.integer(json, "offsetY");
		if (visible == null || anchor == null || offsetX == null || offsetY == null) {
			return null;
		}
		return new HudElementConfig(visible, anchor, offsetX, offsetY);
	}
}
