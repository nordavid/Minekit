package io.github.nordavid.minekit.core.persistence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * Converts a persisted model to and from JSON. Implementations validate every field explicitly: invalid entries are
 * dropped with a warning instead of failing the whole file.
 *
 * @param <T> the immutable model type
 */
public interface JsonCodec<T> {
	/** Thrown when the file is valid JSON but not this kind of file at all (for example an array). */
	final class FormatException extends Exception {
		public FormatException(String message) {
			super(message);
		}
	}

	/**
	 * @param value         the decoded value; invalid entries are left out
	 * @param schemaVersion the version found in the file
	 * @param warnings      one human-readable line per ignored or repaired entry
	 */
	record Decoded<T>(T value, int schemaVersion, List<String> warnings) {
		public Decoded {
			warnings = List.copyOf(warnings);
		}
	}

	/** Version written by this build. Bump it and add a migration step when the format changes incompatibly. */
	int schemaVersion();

	/** The value used when there is no file or it cannot be used. */
	T defaultValue();

	Decoded<T> decode(JsonElement root) throws FormatException;

	JsonObject encode(T value);
}
