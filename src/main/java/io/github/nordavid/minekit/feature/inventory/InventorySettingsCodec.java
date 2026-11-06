package io.github.nordavid.minekit.feature.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * JSON format of {@link InventorySettings}, stored in {@code config/minekit/inventory.json}.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 2,
 *   "activeProfile": "general",
 *   "hotbar": ["melee", "pickaxe", "keep", "keep", "keep", "blocks", "item:minecraft:torch", "any", "food"],
 *   "categoryOrders": { "mining": ["TOOLS", "RESOURCES", ...] },
 *   "favorites": ["minecraft:diamond_pickaxe"]
 * }
 * }</pre>
 *
 * <p>Schema 1 had {@code "protectHotbar": true|false} instead of {@code hotbar}; it is read as "keep every slot" or
 * "any item in every slot".
 */
public final class InventorySettingsCodec implements JsonCodec<InventorySettings> {
	public static final InventorySettingsCodec INSTANCE = new InventorySettingsCodec();
	public static final int SCHEMA_VERSION = 2;

	private InventorySettingsCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public InventorySettings defaultValue() {
		return InventorySettings.DEFAULT;
	}

	@Override
	public Decoded<InventorySettings> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		InventorySettings defaults = InventorySettings.DEFAULT;

		String activeProfile = JsonFields.string(object.get("activeProfile"));
		if (activeProfile == null || SortProfile.byId(activeProfile).isEmpty()) {
			if (object.has("activeProfile")) {
				warnings.add("activeProfile: unknown profile, using " + defaults.activeProfile());
			}
			activeProfile = defaults.activeProfile();
		}

		HotbarLayout hotbar = readHotbar(object, defaults.hotbar(), warnings);

		TreeMap<String, List<ItemCategory>> orders = new TreeMap<>();
		for (Map.Entry<String, JsonElement> entry : JsonFields.section(object, "categoryOrders", warnings)) {
			List<ItemCategory> order = readOrder(entry.getValue());
			if (SortProfile.byId(entry.getKey()).isEmpty() || order == null) {
				warnings.add("categoryOrders." + entry.getKey() + ": unknown profile or not every category exactly once, ignored");
				continue;
			}
			orders.put(entry.getKey(), order);
		}

		TreeSet<String> favorites = new TreeSet<>();
		JsonElement favoritesJson = object.get("favorites");
		if (favoritesJson != null && !favoritesJson.isJsonArray()) {
			warnings.add("favorites: expected a list of item ids, ignored");
		} else if (favoritesJson != null) {
			for (JsonElement element : favoritesJson.getAsJsonArray()) {
				String id = JsonFields.string(element);
				if (id != null && JsonFields.isResourceId(id)) {
					favorites.add(id);
				} else {
					warnings.add("favorites: invalid item id " + element + " ignored");
				}
			}
		}

		return new Decoded<>(new InventorySettings(activeProfile, orders, hotbar, favorites), schemaVersion, warnings);
	}

	@Override
	public JsonObject encode(InventorySettings settings) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("activeProfile", settings.activeProfile());
		JsonArray hotbar = new JsonArray();
		settings.hotbar().slots().forEach(rule -> hotbar.add(rule.id()));
		root.add("hotbar", hotbar);
		JsonObject orders = new JsonObject();
		settings.customOrders().forEach((profile, order) -> {
			JsonArray array = new JsonArray();
			order.forEach(category -> array.add(category.name()));
			orders.add(profile, array);
		});
		root.add("categoryOrders", orders);
		JsonArray favorites = new JsonArray();
		settings.favorites().forEach(favorites::add);
		root.add("favorites", favorites);
		return root;
	}

	private static HotbarLayout readHotbar(JsonObject object, HotbarLayout fallback, List<String> warnings) {
		JsonElement element = object.get("hotbar");
		if (element == null) {
			// Schema 1.
			Boolean protect = JsonFields.bool(object, "protectHotbar");
			if (protect == null && object.has("protectHotbar")) {
				warnings.add("protectHotbar: expected true or false, the hotbar stays as it is");
			}
			return protect == null || protect ? HotbarLayout.KEEP_ALL : HotbarLayout.ANY_ALL;
		}
		if (!element.isJsonArray() || element.getAsJsonArray().size() != HotbarLayout.SIZE) {
			warnings.add("hotbar: expected a list of " + HotbarLayout.SIZE + " slot rules, the hotbar stays as it is");
			return fallback;
		}
		List<HotbarRule> rules = new ArrayList<>();
		JsonArray array = element.getAsJsonArray();
		for (int i = 0; i < array.size(); i++) {
			String id = JsonFields.string(array.get(i));
			HotbarRule rule = id == null ? null : HotbarRule.parse(id).orElse(null);
			if (rule == null) {
				warnings.add("hotbar[" + i + "]: unknown slot rule " + array.get(i) + ", slot stays as it is");
				rule = HotbarRule.KEEP;
			}
			rules.add(rule);
		}
		return new HotbarLayout(rules);
	}

	private static List<ItemCategory> readOrder(JsonElement element) {
		if (!element.isJsonArray()) {
			return null;
		}
		List<ItemCategory> order = new ArrayList<>();
		for (JsonElement entry : element.getAsJsonArray()) {
			ItemCategory category = JsonFields.enumValue(entry, ItemCategory.class);
			if (category == null) {
				return null;
			}
			order.add(category);
		}
		return SortProfile.isCompleteOrder(order) ? order : null;
	}
}
