package io.github.nordavid.minekit.core.container;

import java.util.regex.Pattern;

/** Pure helpers for container block ids. */
public final class ContainerTypes {
	private static final Pattern COPPER_CHEST = Pattern.compile("minecraft:(waxed_)?(exposed_|weathered_|oxidized_)?copper_chest");

	private ContainerTypes() {
	}

	/**
	 * The id a container is recorded under. Copper chests change their id as they oxidize or get waxed, and two halves
	 * of a double copper chest may be in different stages; they all count as {@code minecraft:copper_chest} so the
	 * container keeps one record.
	 */
	public static String normalize(String blockId) {
		return COPPER_CHEST.matcher(blockId).matches() ? "minecraft:copper_chest" : blockId;
	}
}
