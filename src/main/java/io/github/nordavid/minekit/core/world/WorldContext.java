package io.github.nordavid.minekit.core.world;

import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The world or server the player is in.
 *
 * @param id    stable id ({@link WorldIds}): {@code sp-...} for a singleplayer world, {@code mp-...} for a server
 * @param label the world's name or the server list entry's name, for display only
 */
public record WorldContext(String id, String label) {
	/** The current world or server, or empty outside a world (or on a server joined without an address). */
	public static Optional<WorldContext> current(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			return Optional.empty();
		}
		IntegratedServer singleplayer = minecraft.getSingleplayerServer();
		if (singleplayer != null) {
			Path folder = singleplayer.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			if (folder == null) {
				return Optional.empty();
			}
			return Optional.of(new WorldContext(WorldIds.singleplayer(folder.toString()), singleplayer.getWorldData().getLevelName()));
		}
		ServerData server = minecraft.getCurrentServer();
		if (server == null || server.ip == null || server.ip.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new WorldContext(WorldIds.multiplayer(server.ip), server.name));
	}
}
