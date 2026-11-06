package io.github.nordavid.minekit.feature.crafting;

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

/**
 * JSON format of {@link SavedPlans}.
 *
 * <pre>{@code
 * {
 *   "schemaVersion": 1,
 *   "plans": [
 *     { "target": "minecraft:piston", "quantity": 64, "useInventory": true, "savedAt": 1793980000000,
 *       "recipes": { "minecraft:iron_ingot": "FURNACE:minecraft:iron_ingotx1<1xminecraft:raw_iron" },
 *       "alternatives": { "minecraft:birch_planks|minecraft:oak_planks": "minecraft:oak_planks" } }
 *   ]
 * }
 * }</pre>
 * Recipe choices are stored by recipe signature, so they survive restarts and apply on any server that has the recipe.
 */
public final class SavedPlansCodec implements JsonCodec<SavedPlans> {
	public static final SavedPlansCodec INSTANCE = new SavedPlansCodec();
	public static final int SCHEMA_VERSION = 1;

	private SavedPlansCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public SavedPlans defaultValue() {
		return SavedPlans.EMPTY;
	}

	@Override
	public Decoded<SavedPlans> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		List<SavedPlans.SavedPlan> plans = new ArrayList<>();
		JsonElement list = object.get("plans");
		if (list != null && !list.isJsonArray()) {
			warnings.add("plans: expected a list, ignored");
		} else if (list != null) {
			int index = 0;
			for (JsonElement element : list.getAsJsonArray()) {
				SavedPlans.SavedPlan plan = element.isJsonObject() ? readPlan(element.getAsJsonObject()) : null;
				if (plan == null) {
					warnings.add("plans[" + index + "]: invalid plan ignored");
				} else if (plans.size() < SavedPlans.MAX_PLANS) {
					plans.add(plan);
				}
				index++;
			}
		}
		return new Decoded<>(new SavedPlans(plans), schemaVersion, warnings);
	}

	private static SavedPlans.SavedPlan readPlan(JsonObject json) {
		String target = JsonFields.string(json.get("target"));
		Integer quantity = JsonFields.integer(json, "quantity");
		Boolean useInventory = JsonFields.bool(json, "useInventory");
		if (target == null || !JsonFields.isResourceId(target) || quantity == null || quantity < 1 || useInventory == null) {
			return null;
		}
		long savedAt = json.get("savedAt") instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : 0L;
		return new SavedPlans.SavedPlan(new PlanRequest(target, quantity, readStrings(json.get("recipes")),
				readStrings(json.get("alternatives")), useInventory), savedAt);
	}

	private static Map<String, String> readStrings(JsonElement element) {
		Map<String, String> values = new TreeMap<>();
		if (element != null && element.isJsonObject()) {
			element.getAsJsonObject().entrySet().forEach(entry -> {
				String value = JsonFields.string(entry.getValue());
				if (value != null) {
					values.put(entry.getKey(), value);
				}
			});
		}
		return values;
	}

	@Override
	public JsonObject encode(SavedPlans value) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		JsonArray plans = new JsonArray();
		for (SavedPlans.SavedPlan plan : value.plans()) {
			JsonObject json = new JsonObject();
			PlanRequest request = plan.request();
			json.addProperty("target", request.target());
			json.addProperty("quantity", request.quantity());
			json.addProperty("useInventory", request.useInventory());
			json.addProperty("savedAt", plan.savedAt());
			json.add("recipes", strings(request.recipeChoices()));
			json.add("alternatives", strings(request.alternativeChoices()));
			plans.add(json);
		}
		root.add("plans", plans);
		return root;
	}

	private static JsonObject strings(Map<String, String> values) {
		JsonObject json = new JsonObject();
		new TreeMap<>(values).forEach(json::addProperty);
		return json;
	}
}
