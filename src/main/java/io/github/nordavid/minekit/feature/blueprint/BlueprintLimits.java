package io.github.nordavid.minekit.feature.blueprint;

/**
 * Upper bounds for importing a blueprint file, which is untrusted input. Checked before and while reading, so a
 * malformed or hostile file fails with a message instead of exhausting memory.
 *
 * @param maxFileBytes   compressed file size
 * @param maxNbtBytes    memory the decompressed NBT data may take (also limits nesting through Minecraft's accounter)
 * @param maxVolume      positions of the whole structure (each takes two bytes in memory)
 * @param maxPaletteSize distinct block states
 * @param maxRegions     regions of a Litematica file
 */
public record BlueprintLimits(long maxFileBytes, long maxNbtBytes, long maxVolume, int maxPaletteSize, int maxRegions) {
	public static final BlueprintLimits DEFAULT = new BlueprintLimits(32L << 20, 256L << 20, 16_000_000L, 8_192, 256);

	public BlueprintLimits {
		if (maxFileBytes <= 0 || maxNbtBytes <= 0 || maxVolume <= 0 || maxPaletteSize < 2 || maxPaletteSize > Short.MAX_VALUE || maxRegions <= 0) {
			throw new IllegalArgumentException("invalid blueprint limits");
		}
	}
}
