package io.github.nordavid.minekit.feature.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import io.github.nordavid.minekit.core.persistence.JsonFields;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * JSON format of one world's {@link StorageIndex}, stored in {@code config/minekit/storage-index/<world>.json}.
 * The file name is a hash of the world folder or server address, so no address is stored in plain text.
 */
public final class StorageIndexCodec implements JsonCodec<StorageIndex> {
	public static final int SCHEMA_VERSION = 1;

	private final String defaultLabel;

	public StorageIndexCodec(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public StorageIndex defaultValue() {
		return StorageIndex.empty(defaultLabel);
	}

	@Override
	public Decoded<StorageIndex> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);
		String label = JsonFields.string(object.get("label"));
		Map<String, ContainerRecord> containers = new LinkedHashMap<>();
		JsonElement list = object.get("containers");
		if (list != null && list.isJsonArray()) {
			int index = 0;
			for (JsonElement element : list.getAsJsonArray()) {
				ContainerRecord record = element.isJsonObject() ? read(element.getAsJsonObject()) : null;
				if (record == null) {
					warnings.add("containers[" + index + "]: invalid record ignored");
				} else if (containers.size() < StorageIndex.MAX_CONTAINERS) {
					containers.put(record.key(), record);
				}
				index++;
			}
		} else if (list != null) {
			warnings.add("containers: expected a list, ignored");
		}
		return new Decoded<>(new StorageIndex(label == null ? defaultLabel : label, containers), schemaVersion, warnings);
	}

	private static @Nullable ContainerRecord read(JsonObject json) {
		String dimension = JsonFields.string(json.get("dimension"));
		String type = JsonFields.string(json.get("type"));
		Integer slots = JsonFields.integer(json, "slots");
		Integer usedSlots = JsonFields.integer(json, "usedSlots");
		Long observedAt = json.get("observedAt") instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : null;
		if (dimension == null || !(dimension.isEmpty() || JsonFields.isResourceId(dimension)) || type == null
				|| !JsonFields.isResourceId(type) || slots == null || slots < 0 || usedSlots == null || usedSlots < 0 || observedAt == null
				|| !(json.get("positions") instanceof JsonArray positionsJson) || positionsJson.size() > 2) {
			return null;
		}
		List<ContainerRecord.Pos> positions = new ArrayList<>();
		for (JsonElement element : positionsJson) {
			if (!(element instanceof JsonArray xyz) || xyz.size() != 3 || !isInt(xyz.get(0)) || !isInt(xyz.get(1)) || !isInt(xyz.get(2))) {
				return null;
			}
			positions.add(new ContainerRecord.Pos(xyz.get(0).getAsInt(), xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
		}
		if (positions.isEmpty() != dimension.isEmpty()) {
			return null; // only ender chests have neither a position nor a dimension
		}
		Map<String, Integer> items = new TreeMap<>();
		if (json.get("items") instanceof JsonObject itemsJson) {
			itemsJson.entrySet().forEach(entry -> {
				if (JsonFields.isResourceId(entry.getKey()) && entry.getValue() instanceof JsonPrimitive count && count.isNumber()
						&& count.getAsInt() > 0) {
					items.put(entry.getKey(), count.getAsInt());
				}
			});
		}
		return new ContainerRecord(dimension, type, positions, items, slots, usedSlots, observedAt);
	}

	private static boolean isInt(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isNumber() && primitive.getAsDouble() == Math.rint(primitive.getAsDouble())
				&& Math.abs(primitive.getAsDouble()) <= 30_000_000;
	}

	@Override
	public JsonObject encode(StorageIndex index) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		root.addProperty("label", index.label());
		JsonArray containers = new JsonArray();
		for (ContainerRecord record : index.containers().values()) {
			JsonObject json = new JsonObject();
			json.addProperty("dimension", record.dimension());
			json.addProperty("type", record.type());
			JsonArray positions = new JsonArray();
			record.positions().forEach(pos -> {
				JsonArray xyz = new JsonArray();
				xyz.add(pos.x());
				xyz.add(pos.y());
				xyz.add(pos.z());
				positions.add(xyz);
			});
			json.add("positions", positions);
			json.addProperty("slots", record.slots());
			json.addProperty("usedSlots", record.usedSlots());
			json.addProperty("observedAt", record.observedAt());
			JsonObject items = new JsonObject();
			record.items().forEach(items::addProperty);
			json.add("items", items);
			containers.add(json);
		}
		root.add("containers", containers);
		return root;
	}
}
