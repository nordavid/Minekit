package io.github.nordavid.minekit.feature.blueprint;

import java.util.List;
import java.util.Objects;

/**
 * An imported structure: a box of {@code sizeX * sizeY * sizeZ} positions, each pointing into a palette of block
 * specs. Immutable. Positions are local, from (0, 0, 0) at the box's minimum corner; index order is
 * {@code x + z * sizeX + y * sizeX * sizeZ}, so one layer (one y) is contiguous.
 */
public final class Blueprint {
	/** The file formats Minekit reads. */
	public enum Format {
		LITEMATIC("Litematica"),
		MCEDIT("MCEdit schematic"),
		SPONGE_V1("Sponge schematic v1"),
		SPONGE_V2("Sponge schematic v2"),
		SPONGE_V3("Sponge schematic v3");

		private final String displayName;

		Format(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	private final String name;
	private final String author;
	private final Format format;
	private final int dataVersion;
	private final int sizeX;
	private final int sizeY;
	private final int sizeZ;
	private final List<BlockSpec> palette;
	private final short[] cells;
	private final long[] paletteCounts;
	private final long nonAir;

	/**
	 * @param cells palette index of every position, in index order; the array is taken over, not copied
	 */
	Blueprint(String name, String author, Format format, int dataVersion, int sizeX, int sizeY, int sizeZ, List<BlockSpec> palette,
			short[] cells) {
		this.name = Objects.requireNonNull(name, "name");
		this.author = Objects.requireNonNull(author, "author");
		this.format = Objects.requireNonNull(format, "format");
		this.dataVersion = dataVersion;
		if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || (long) sizeX * sizeY * sizeZ != cells.length) {
			throw new IllegalArgumentException("size " + sizeX + "x" + sizeY + "x" + sizeZ + " does not match " + cells.length + " cells");
		}
		this.sizeX = sizeX;
		this.sizeY = sizeY;
		this.sizeZ = sizeZ;
		this.palette = List.copyOf(palette);
		this.cells = cells;
		this.paletteCounts = new long[palette.size()];
		long solid = 0;
		for (short cell : cells) {
			if (cell < 0 || cell >= palette.size()) {
				throw new IllegalArgumentException("palette index out of range: " + cell);
			}
			paletteCounts[cell]++;
		}
		for (int i = 0; i < palette.size(); i++) {
			if (!palette.get(i).isAir()) {
				solid += paletteCounts[i];
			}
		}
		this.nonAir = solid;
	}

	public String name() {
		return name;
	}

	public String author() {
		return author;
	}

	public Format format() {
		return format;
	}

	/** Minecraft data version the file was saved with, or 0 if unknown. */
	public int dataVersion() {
		return dataVersion;
	}

	public int sizeX() {
		return sizeX;
	}

	public int sizeY() {
		return sizeY;
	}

	public int sizeZ() {
		return sizeZ;
	}

	public long volume() {
		return cells.length;
	}

	/** Positions that hold a block (not air). */
	public long blockCount() {
		return nonAir;
	}

	public List<BlockSpec> palette() {
		return palette;
	}

	/** How many positions use each palette entry, in palette order. */
	public long paletteCount(int paletteIndex) {
		return paletteCounts[paletteIndex];
	}

	public int index(int x, int y, int z) {
		return x + z * sizeX + y * sizeX * sizeZ;
	}

	public int paletteIndex(int x, int y, int z) {
		return cells[index(x, y, z)];
	}

	public BlockSpec at(int x, int y, int z) {
		return palette.get(cells[index(x, y, z)]);
	}

	/** The palette index at a raw index (see {@link #index}). */
	public int paletteIndexAt(int index) {
		return cells[index];
	}
}
