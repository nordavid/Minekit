package io.github.nordavid.minekit.core.persistence;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.LoadResult.Status;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads, holds and saves one versioned JSON file.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>A missing, corrupt or unreadable file never prevents the game from starting; defaults are used instead.</li>
 *   <li>A corrupt file is moved aside before defaults are written, a file from a newer MINEKIT is copied aside
 *       before it is first overwritten, and an unreadable file is never overwritten.</li>
 *   <li>Writes go to a temporary file that is then moved over the original, so a crash mid-write cannot leave a
 *       half-written file behind.</li>
 * </ul>
 *
 * <p>Not thread-safe. MINEKIT uses it from the client thread only.
 *
 * @param <T> the immutable model type
 */
public class JsonFileStore<T> {
	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");
	private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final Path file;
	private final Clock clock;
	private final JsonCodec<T> codec;
	private T current;
	private boolean savingAllowed = true;
	private boolean backupBeforeNextSave;
	private boolean lastSaveFailed;

	public JsonFileStore(Path file, Clock clock, JsonCodec<T> codec) {
		this.file = Objects.requireNonNull(file, "file");
		this.clock = Objects.requireNonNull(clock, "clock");
		this.codec = Objects.requireNonNull(codec, "codec");
		this.current = codec.defaultValue();
	}

	public Path file() {
		return file;
	}

	public T current() {
		return current;
	}

	/** Whether changes are written to disk. False after {@link Status#READ_FAILED}. */
	public boolean isSavingAllowed() {
		return savingAllowed;
	}

	/** Whether the most recent save attempt failed, so the UI can tell the player their change is not persisted. */
	public boolean lastSaveFailed() {
		return lastSaveFailed;
	}

	public LoadResult load() {
		String text;
		try {
			text = Files.readString(file, StandardCharsets.UTF_8);
		} catch (NoSuchFileException missing) {
			current = codec.defaultValue();
			savingAllowed = true;
			save();
			return new LoadResult(Status.CREATED, List.of(), Optional.empty());
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Could not read {}; using defaults and leaving the file untouched", file, e);
			current = codec.defaultValue();
			savingAllowed = false;
			return new LoadResult(Status.READ_FAILED, List.of(e.toString()), Optional.empty());
		}

		savingAllowed = true;
		JsonCodec.Decoded<T> decoded;
		try {
			decoded = codec.decode(JsonParser.parseString(text));
		} catch (JsonParseException | JsonCodec.FormatException e) {
			return recoverFromCorruptFile(e);
		}

		current = decoded.value();
		decoded.warnings().forEach(warning -> LOGGER.warn("{}: {}", file.getFileName(), warning));
		if (decoded.schemaVersion() > codec.schemaVersion()) {
			LOGGER.warn("{} was written by a newer MINEKIT (schema {} > {}); it will be backed up before it is changed",
					file, decoded.schemaVersion(), codec.schemaVersion());
			backupBeforeNextSave = true;
			return new LoadResult(Status.NEWER_SCHEMA, decoded.warnings(), Optional.empty());
		}
		return new LoadResult(Status.LOADED, decoded.warnings(), Optional.empty());
	}

	/**
	 * Applies a change and saves it if anything changed.
	 *
	 * @return {@code false} if the new value could not be written; it is still kept in memory for this session
	 */
	public boolean update(UnaryOperator<T> change) {
		T next = Objects.requireNonNull(change.apply(current), "change returned null");
		if (next.equals(current)) {
			return true;
		}
		current = next;
		return save();
	}

	/**
	 * Applies a change in memory only, for values that change often (e.g. counters). Call {@link #save()} later.
	 *
	 * @return whether anything changed
	 */
	public boolean updateInMemory(UnaryOperator<T> change) {
		T next = Objects.requireNonNull(change.apply(current), "change returned null");
		if (next.equals(current)) {
			return false;
		}
		current = next;
		return true;
	}

	/** Writes the current value. Returns {@code false} and logs the reason if that is not possible. */
	public boolean save() {
		lastSaveFailed = true;
		if (!savingAllowed) {
			LOGGER.warn("Not saving {}: it could not be read at startup and is left untouched", file);
			return false;
		}
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			if (backupBeforeNextSave && Files.exists(file)) {
				Path backup = siblingWithSuffix(".newer-schema-" + stamp() + ".bak");
				Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.info("Backed up {} from a newer MINEKIT to {}", file.getFileName(), backup);
			}
			backupBeforeNextSave = false;
			writeAtomically(GSON.toJson(codec.encode(current)) + System.lineSeparator());
			lastSaveFailed = false;
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not save {}", file, e);
			return false;
		}
	}

	private LoadResult recoverFromCorruptFile(Exception cause) {
		current = codec.defaultValue();
		Path backup = siblingWithSuffix(".corrupt-" + stamp());
		try {
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException moveFailed) {
			LOGGER.error("{} is corrupt and could not be moved aside; leaving it untouched", file, moveFailed);
			savingAllowed = false;
			return new LoadResult(Status.READ_FAILED, List.of(cause.toString()), Optional.empty());
		}
		LOGGER.warn("{} is corrupt ({}); moved it to {} and restored defaults", file, cause.getMessage(), backup);
		save();
		return new LoadResult(Status.RECOVERED_CORRUPT, List.of(cause.toString()), Optional.of(backup));
	}

	private void writeAtomically(String content) throws IOException {
		Path temp = siblingWithSuffix(".tmp");
		Files.writeString(temp, content, StandardCharsets.UTF_8);
		try {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private Path siblingWithSuffix(String suffix) {
		return file.resolveSibling(file.getFileName() + suffix);
	}

	private String stamp() {
		return BACKUP_STAMP.format(clock.instant());
	}
}
