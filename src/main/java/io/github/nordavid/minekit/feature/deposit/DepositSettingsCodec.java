package io.github.nordavid.minekit.feature.deposit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/** JSON format of {@code config/minekit/deposit.json}. */
public final class DepositSettingsCodec implements JsonCodec<DepositSettings> {
	public static final DepositSettingsCodec INSTANCE = new DepositSettingsCodec();
	public static final int SCHEMA_VERSION = 1;

	private DepositSettingsCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public DepositSettings defaultValue() {
		return DepositSettings.DEFAULT;
	}

	@Override
	public Decoded<DepositSettings> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		DepositSettings defaults = DepositSettings.DEFAULT;

		DepositProfile profile = defaults.profile();
		String profileId = JsonFields.string(object.get("profile"));
		if (profileId != null && DepositProfile.byId(profileId).isPresent()) {
			profile = DepositProfile.byId(profileId).get();
		} else if (object.has("profile")) {
			warnings.add("profile: unknown value, using " + profile.id());
		}

		EnumSet<ItemCategory> categories = EnumSet.copyOf(defaults.customCategories());
		JsonElement list = object.get("customCategories");
		if (list != null && list.isJsonArray()) {
			categories = EnumSet.noneOf(ItemCategory.class);
			for (JsonElement element : list.getAsJsonArray()) {
				ItemCategory category = JsonFields.enumValue(element, ItemCategory.class);
				if (category == null) {
					warnings.add("customCategories: unknown category ignored");
				} else {
					categories.add(category);
				}
			}
		} else if (list != null) {
			warnings.add("customCategories: expected a list, using defaults");
		}

		Boolean includeHotbar = JsonFields.bool(object, "includeHotbar");
		if (includeHotbar == null && object.has("includeHotbar")) {
			warnings.add("includeHotbar: expected true or false");
		}
		return new Decoded<>(new DepositSettings(profile, categories, includeHotbar == null ? defaults.includeHotbar() : includeHotbar),
				schemaVersion, warnings);
	}

	@Override
	public JsonObject encode(DepositSettings settings) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("profile", settings.profile().id());
		JsonArray categories = new JsonArray();
		settings.customCategories().forEach(category -> categories.add(category.name().toLowerCase(Locale.ROOT)));
		root.add("customCategories", categories);
		root.addProperty("includeHotbar", settings.includeHotbar());
		return root;
	}
}
