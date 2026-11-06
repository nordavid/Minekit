package io.github.nordavid.minekit.feature.storage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * File names of storage indexes. A world or server is identified by a hash of its folder name or address, so the
 * storage folder never contains a server address in plain text, and the same world always maps to the same file.
 */
public final class ContextIds {
	private ContextIds() {
	}

	/** Index id of a singleplayer world, from its save folder name. */
	public static String singleplayer(String worldFolder) {
		return "sp-" + hash(worldFolder);
	}

	/** Index id of a server, from the address the player connected to (case and surrounding spaces ignored). */
	public static String multiplayer(String address) {
		return "mp-" + hash(address.trim().toLowerCase(Locale.ROOT));
	}

	private static String hash(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 8);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is part of every Java runtime", impossible);
		}
	}
}
