package io.github.nordavid.minekit.core.world;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Stable ids of worlds and servers, used for per-world data (storage index files, resource sessions). A world or
 * server is identified by a hash of its save folder name or address, so no server address is ever stored in plain
 * text, and the same world always gets the same id.
 */
public final class WorldIds {
	private WorldIds() {
	}

	/** Id of a singleplayer world, from its save folder name. */
	public static String singleplayer(String worldFolder) {
		return "sp-" + hash(worldFolder);
	}

	/** Id of a server, from the address the player connected to (case and surrounding spaces ignored). */
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
