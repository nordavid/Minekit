package io.github.nordavid.minekit.feature.blueprint;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;

/**
 * Reads blueprint files from disk. The file is untrusted: its size is checked before reading, the decompressed NBT is
 * read with Minecraft's accounter (a memory quota and a nesting limit, against zip bombs and deeply nested data), and
 * every failure becomes a {@link BlueprintException} with a message for the player, never a crash.
 */
public final class BlueprintFiles {
	public static final String LITEMATIC = ".litematic";
	public static final String SCHEM = ".schem";

	private BlueprintFiles() {
	}

	/** Whether Minekit reads files with this name. */
	public static boolean isSupported(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		return name.endsWith(LITEMATIC) || name.endsWith(SCHEM);
	}

	/** The file name without its extension, the blueprint's name if the file has none. */
	public static String baseName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	public static Blueprint read(Path file, BlueprintLimits limits) throws BlueprintException {
		if (!isSupported(file)) {
			throw new BlueprintException("unsupported_file", file.getFileName().toString());
		}
		long size;
		try {
			size = Files.size(file);
		} catch (IOException e) {
			throw new BlueprintException("read_failed", file.getFileName().toString());
		}
		if (size > limits.maxFileBytes()) {
			throw new BlueprintException("file_too_large", megabytes(size), megabytes(limits.maxFileBytes()));
		}
		try (InputStream input = Files.newInputStream(file)) {
			return read(input, baseName(file), limits);
		} catch (IOException e) {
			throw new BlueprintException("read_failed", file.getFileName().toString());
		}
	}

	/** Reads gzip-compressed NBT from a stream; the caller has checked the size. */
	public static Blueprint read(InputStream input, String fallbackName, BlueprintLimits limits) throws BlueprintException {
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(input, NbtAccounter.create(limits.maxNbtBytes()));
		} catch (NbtAccounterException e) {
			throw new BlueprintException("too_complex", megabytes(limits.maxNbtBytes()));
		} catch (ZipException e) {
			throw new BlueprintException("not_compressed");
		} catch (IOException | RuntimeException e) {
			// Truncated or garbled data, or a tag type that cannot occur in valid NBT.
			throw new BlueprintException("not_nbt");
		}
		try {
			return BlueprintReader.read(root, fallbackName, limits);
		} catch (RuntimeException e) {
			// Defensive: a structure the reader did not foresee must still not crash the game.
			throw new BlueprintException("invalid_data", e.getClass().getSimpleName());
		}
	}

	private static String megabytes(long bytes) {
		return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0);
	}
}
