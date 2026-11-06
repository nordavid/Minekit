package io.github.nordavid.minekit.feature.resources;

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
import org.jspecify.annotations.Nullable;

/**
 * JSON format of {@link ResourceTracker}, stored in {@code config/minekit/resource-sessions.json}.
 * A session that was running when the game closed is restored as paused at its last saved duration.
 */
public final class ResourceTrackerCodec implements JsonCodec<ResourceTracker> {
	public static final ResourceTrackerCodec INSTANCE = new ResourceTrackerCodec();
	public static final int SCHEMA_VERSION = 1;

	private ResourceTrackerCodec() {
	}

	@Override
	public int schemaVersion() {
		return SCHEMA_VERSION;
	}

	@Override
	public ResourceTracker defaultValue() {
		return ResourceTracker.EMPTY;
	}

	@Override
	public Decoded<ResourceTracker> decode(JsonElement root) throws FormatException {
		if (!root.isJsonObject()) {
			throw new FormatException("root element is not a JSON object");
		}
		JsonObject object = root.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		int schemaVersion = JsonFields.schemaVersion(object, SCHEMA_VERSION, warnings);

		ResourceSession current = null;
		JsonElement currentJson = object.get("current");
		if (currentJson != null && !currentJson.isJsonNull()) {
			current = currentJson.isJsonObject() ? readSession(currentJson.getAsJsonObject()) : null;
			if (current == null) {
				warnings.add("current: invalid session ignored");
			} else if (current.isRunning()) {
				current = current.with(current.activeMillis(), -1, -1);
			}
		}

		List<ResourceSession> history = new ArrayList<>();
		JsonElement historyJson = object.get("history");
		if (historyJson != null && historyJson.isJsonArray()) {
			int index = 0;
			for (JsonElement element : historyJson.getAsJsonArray()) {
				ResourceSession session = element.isJsonObject() ? readSession(element.getAsJsonObject()) : null;
				if (session == null) {
					warnings.add("history[" + index + "]: invalid session ignored");
				} else if (history.size() < ResourceTracker.MAX_HISTORY) {
					history.add(session);
				}
				index++;
			}
		} else if (historyJson != null) {
			warnings.add("history: expected a list, ignored");
		}
		return new Decoded<>(new ResourceTracker(current, history), schemaVersion, warnings);
	}

	private static @Nullable ResourceSession readSession(JsonObject json) {
		String id = JsonFields.string(json.get("id"));
		String name = JsonFields.string(json.get("name"));
		String world = JsonFields.string(json.get("world"));
		Long startedAt = longValue(json.get("startedAt"));
		Long activeMillis = longValue(json.get("activeMillis"));
		Long endedAt = longValue(json.get("endedAt"));
		if (id == null || name == null || world == null || startedAt == null || activeMillis == null || activeMillis < 0 || endedAt == null) {
			return null;
		}
		Map<String, Integer> goals = new TreeMap<>();
		readCounts(json.get("goals")).forEach((item, count) -> goals.put(item, (int) Math.min(Integer.MAX_VALUE, count)));
		return new ResourceSession(id, name, world, startedAt, activeMillis, -1, endedAt, readCounts(json.get("collected")),
				readCounts(json.get("consumed")), readCounts(json.get("unclearIn")), readCounts(json.get("unclearOut")), goals);
	}

	private static Map<String, Long> readCounts(JsonElement element) {
		Map<String, Long> counts = new TreeMap<>();
		if (element != null && element.isJsonObject()) {
			element.getAsJsonObject().entrySet().forEach(entry -> {
				Long value = longValue(entry.getValue());
				if (JsonFields.isResourceId(entry.getKey()) && value != null && value > 0) {
					counts.put(entry.getKey(), value);
				}
			});
		}
		return counts;
	}

	private static @Nullable Long longValue(JsonElement element) {
		return element instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : null;
	}

	@Override
	public JsonObject encode(ResourceTracker tracker) {
		JsonObject root = new JsonObject();
		root.addProperty("schemaVersion", SCHEMA_VERSION);
		long now = System.currentTimeMillis();
		root.add("current", tracker.current() == null ? null : writeSession(tracker.current(), now));
		JsonArray history = new JsonArray();
		tracker.history().forEach(session -> history.add(writeSession(session, now)));
		root.add("history", history);
		return root;
	}

	private static JsonObject writeSession(ResourceSession session, long now) {
		JsonObject json = new JsonObject();
		json.addProperty("id", session.id());
		json.addProperty("name", session.name());
		json.addProperty("world", session.world());
		json.addProperty("startedAt", session.startedAt());
		// A running session is saved with its duration so far; it is restored paused.
		json.addProperty("activeMillis", session.durationMillis(now));
		json.addProperty("endedAt", session.endedAt());
		json.add("collected", counts(session.collected()));
		json.add("consumed", counts(session.consumed()));
		json.add("unclearIn", counts(session.unclearIn()));
		json.add("unclearOut", counts(session.unclearOut()));
		JsonObject goals = new JsonObject();
		session.goals().forEach(goals::addProperty);
		json.add("goals", goals);
		return json;
	}

	private static JsonObject counts(Map<String, Long> counts) {
		JsonObject json = new JsonObject();
		counts.forEach(json::addProperty);
		return json;
	}
}
