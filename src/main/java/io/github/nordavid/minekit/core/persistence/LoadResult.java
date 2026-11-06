package io.github.nordavid.minekit.core.persistence;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Outcome of {@link JsonFileStore#load()}.
 *
 * @param status   what happened
 * @param warnings entries that were ignored or repaired while reading
 * @param backup   copy of the original file, if one was made
 */
public record LoadResult(Status status, List<String> warnings, Optional<Path> backup) {
	public enum Status {
		/** No file existed; defaults were written. */
		CREATED,
		/** The file was read; individual entries may still have been ignored (see warnings). */
		LOADED,
		/** The file was written by a newer Minekit. It is backed up before it is first changed. */
		NEWER_SCHEMA,
		/** The file could not be parsed. It was moved to a backup and defaults are used. */
		RECOVERED_CORRUPT,
		/** The file exists but could not be read. Defaults are used and nothing is written, to protect the file. */
		READ_FAILED
	}

	public LoadResult {
		warnings = List.copyOf(warnings);
	}

	/** Whether the user should be told that some of their data could not be applied. */
	public boolean needsAttention() {
		return status != Status.CREATED && (status != Status.LOADED || !warnings.isEmpty());
	}
}
