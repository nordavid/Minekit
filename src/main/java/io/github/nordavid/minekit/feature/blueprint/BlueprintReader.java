package io.github.nordavid.minekit.feature.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.fixes.BlockStateData;

/**
 * Turns the NBT of a blueprint file into a {@link Blueprint}. Supports Litematica ({@code .litematic}) and Sponge
 * schematics v1 to v3 ({@code .schem}); see ADR 0008 for the format facts. Every size, count and index is checked
 * against {@link BlueprintLimits} before anything large is allocated.
 *
 * <p>Uses only Minecraft's NBT classes, which need no running game, so it is unit tested directly.
 */
public final class BlueprintReader {
	private BlueprintReader() {
	}

	/**
	 * Reads whichever supported format the NBT is. Sponge schematics v3 nest their content in a compound named
	 * {@code Schematic}; some tools do the same for v2 and for MCEdit schematics, so the content is looked for there too.
	 */
	public static Blueprint read(CompoundTag root, String fallbackName, BlueprintLimits limits) throws BlueprintException {
		if (root.contains("Regions")) {
			return readLitematic(root, fallbackName, limits);
		}
		CompoundTag body = root.getCompound("Schematic").filter(nested -> !root.contains("Version") && !root.contains("Width")).orElse(root);
		if (body.getByteArray("Blocks").isPresent() || body.contains("Materials")) {
			return readMcEdit(body, fallbackName, limits);
		}
		if (body.contains("Version") || body.contains("BlockData") || body.contains("Palette") || body.getCompound("Blocks").isPresent()) {
			return readSponge(body, fallbackName, limits);
		}
		throw new BlueprintException("unknown_format");
	}

	// ---------------------------------------------------------------------------------------------------------- Litematica

	/** One region of a Litematica file: its minimum corner in schematic coordinates, its size and its block data. */
	private record Region(String name, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, List<BlockSpec> palette, long[] states,
			int bits) {
	}

	static Blueprint readLitematic(CompoundTag root, String fallbackName, BlueprintLimits limits) throws BlueprintException {
		int version = root.getIntOr("Version", -1);
		if (version < 2) {
			throw new BlueprintException("unsupported_version", "Litematica " + version);
		}
		CompoundTag metadata = root.getCompound("Metadata").orElse(new CompoundTag());
		String name = metadata.getStringOr("Name", fallbackName);
		String author = metadata.getStringOr("Author", "");
		int dataVersion = root.getIntOr("MinecraftDataVersion", 0);
		CompoundTag regions = root.getCompound("Regions").orElseThrow(() -> new BlueprintException("missing_field", "Regions"));
		if (regions.keySet().isEmpty()) {
			throw new BlueprintException("empty");
		}
		if (regions.keySet().size() > limits.maxRegions()) {
			throw new BlueprintException("too_many_regions", regions.keySet().size(), limits.maxRegions());
		}

		List<Region> parsed = new ArrayList<>();
		for (String regionName : new TreeMap<>(asMap(regions)).keySet()) {
			CompoundTag region = regions.getCompound(regionName).orElseThrow(() -> new BlueprintException("invalid_region", regionName));
			int[] position = vector(region, "Position", regionName);
			int[] size = vector(region, "Size", regionName);
			if (size[0] == 0 || size[1] == 0 || size[2] == 0) {
				throw new BlueprintException("invalid_region", regionName);
			}
			int sizeX = Math.abs(size[0]);
			int sizeY = Math.abs(size[1]);
			int sizeZ = Math.abs(size[2]);
			checkVolume((long) sizeX * sizeY * sizeZ, limits);
			// A negative size means the region extends from its position towards smaller coordinates.
			int minX = position[0] + (size[0] < 0 ? size[0] + 1 : 0);
			int minY = position[1] + (size[1] < 0 ? size[1] + 1 : 0);
			int minZ = position[2] + (size[2] < 0 ? size[2] + 1 : 0);

			ListTag paletteTag = region.getList("BlockStatePalette").orElseThrow(() -> new BlueprintException("missing_field", "BlockStatePalette"));
			if (paletteTag.isEmpty() || paletteTag.size() > limits.maxPaletteSize()) {
				throw new BlueprintException("invalid_palette_size", paletteTag.size(), limits.maxPaletteSize());
			}
			List<BlockSpec> palette = new ArrayList<>();
			for (int i = 0; i < paletteTag.size(); i++) {
				CompoundTag entry = paletteTag.getCompound(i).orElseThrow(() -> new BlueprintException("invalid_block", "?"));
				palette.add(blockFromTag(entry));
			}
			long[] states = region.getLongArray("BlockStates").orElseThrow(() -> new BlueprintException("missing_field", "BlockStates"));
			int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
			long volume = (long) sizeX * sizeY * sizeZ;
			long neededLongs = (volume * bits + 63) / 64;
			if (states.length < neededLongs) {
				throw new BlueprintException("data_too_short", regionName);
			}
			parsed.add(new Region(regionName, minX, minY, minZ, sizeX, sizeY, sizeZ, palette, states, bits));
		}

		int minX = parsed.stream().mapToInt(Region::minX).min().orElseThrow();
		int minY = parsed.stream().mapToInt(Region::minY).min().orElseThrow();
		int minZ = parsed.stream().mapToInt(Region::minZ).min().orElseThrow();
		long sizeX = parsed.stream().mapToLong(region -> (long) region.minX() + region.sizeX()).max().orElseThrow() - minX;
		long sizeY = parsed.stream().mapToLong(region -> (long) region.minY() + region.sizeY()).max().orElseThrow() - minY;
		long sizeZ = parsed.stream().mapToLong(region -> (long) region.minZ() + region.sizeZ()).max().orElseThrow() - minZ;
		checkVolume(sizeX * sizeY * sizeZ, limits);

		PaletteBuilder palette = new PaletteBuilder(limits);
		short[] cells = new short[(int) (sizeX * sizeY * sizeZ)];
		for (Region region : parsed) {
			short[] mapping = new short[region.palette().size()];
			for (int i = 0; i < mapping.length; i++) {
				mapping[i] = palette.indexOf(region.palette().get(i));
			}
			long mask = (1L << region.bits()) - 1;
			int index = 0;
			for (int y = 0; y < region.sizeY(); y++) {
				for (int z = 0; z < region.sizeZ(); z++) {
					for (int x = 0; x < region.sizeX(); x++, index++) {
						int value = (int) packedValue(region.states(), index, region.bits(), mask);
						if (value >= mapping.length) {
							throw new BlueprintException("invalid_palette_index", value, region.name());
						}
						short global = mapping[value];
						// Air of a later region never erases blocks of an earlier, overlapping one.
						if (global != 0) {
							int gx = region.minX() - minX + x;
							int gy = region.minY() - minY + y;
							int gz = region.minZ() - minZ + z;
							cells[(int) (gx + gz * sizeX + gy * sizeX * sizeZ)] = global;
						}
					}
				}
			}
		}
		return new Blueprint(name, author, Blueprint.Format.LITEMATIC, dataVersion, (int) sizeX, (int) sizeY, (int) sizeZ, palette.entries(), cells);
	}

	/**
	 * Value number {@code index} of a tightly packed long array: values of {@code bits} bits follow each other from the
	 * least significant bit, and a value may continue in the next long.
	 */
	static long packedValue(long[] array, long index, int bits, long mask) {
		long startBit = index * bits;
		int startLong = (int) (startBit >>> 6);
		int offset = (int) (startBit & 63);
		int endLong = (int) ((startBit + bits - 1) >>> 6);
		if (startLong == endLong) {
			return (array[startLong] >>> offset) & mask;
		}
		return ((array[startLong] >>> offset) | (array[endLong] << (64 - offset))) & mask;
	}

	private static int[] vector(CompoundTag region, String key, String regionName) throws BlueprintException {
		CompoundTag tag = region.getCompound(key).orElseThrow(() -> new BlueprintException("missing_field", regionName + "." + key));
		Optional<Integer> x = tag.getInt("x");
		Optional<Integer> y = tag.getInt("y");
		Optional<Integer> z = tag.getInt("z");
		if (x.isEmpty() || y.isEmpty() || z.isEmpty()) {
			throw new BlueprintException("missing_field", regionName + "." + key);
		}
		return new int[] {x.get(), y.get(), z.get()};
	}

	/**
	 * A palette entry. Minecraft 26.x (data version 5006) renamed the fields of a stored block state from {@code Name}
	 * and {@code Properties} to {@code id} and {@code properties}; files may use either.
	 */
	static BlockSpec blockFromTag(CompoundTag entry) throws BlueprintException {
		String name = entry.getString("Name").or(() -> entry.getString("id")).orElse("");
		Map<String, String> properties = new HashMap<>();
		CompoundTag propertiesTag = entry.getCompound("Properties").or(() -> entry.getCompound("properties")).orElse(new CompoundTag());
		for (String key : propertiesTag.keySet()) {
			properties.put(key, propertiesTag.getStringOr(key, ""));
		}
		try {
			return BlockSpec.of(name, properties);
		} catch (IllegalArgumentException e) {
			throw new BlueprintException("invalid_block", name.isEmpty() ? "?" : name);
		}
	}

	// ------------------------------------------------------------------------------------------------------------- Sponge

	static Blueprint readSponge(CompoundTag body, String fallbackName, BlueprintLimits limits) throws BlueprintException {
		int version = body.getIntOr("Version", -1);
		if (version < 1 || version > 3) {
			throw new BlueprintException("unsupported_version", "Sponge " + version);
		}
		int width = unsignedShort(body, "Width");
		int height = unsignedShort(body, "Height");
		int length = unsignedShort(body, "Length");
		if (width == 0 || height == 0 || length == 0) {
			throw new BlueprintException("empty");
		}
		long volume = (long) width * height * length;
		checkVolume(volume, limits);

		CompoundTag paletteTag;
		byte[] data;
		if (version == 3) {
			CompoundTag blocks = body.getCompound("Blocks").orElseThrow(() -> new BlueprintException("missing_field", "Blocks"));
			// The v3 text says "Palette", its change log "BlockPalette": accept both.
			paletteTag = blocks.getCompound("Palette").or(() -> blocks.getCompound("BlockPalette"))
					.orElseThrow(() -> new BlueprintException("missing_field", "Blocks.Palette"));
			data = blocks.getByteArray("Data").orElseThrow(() -> new BlueprintException("missing_field", "Blocks.Data"));
		} else {
			paletteTag = body.getCompound("Palette").orElseThrow(() -> new BlueprintException("missing_field", "Palette"));
			data = body.getByteArray("BlockData").orElseThrow(() -> new BlueprintException("missing_field", "BlockData"));
		}
		if (paletteTag.keySet().isEmpty() || paletteTag.keySet().size() > limits.maxPaletteSize()) {
			throw new BlueprintException("invalid_palette_size", paletteTag.keySet().size(), limits.maxPaletteSize());
		}
		BlockSpec[] localPalette = new BlockSpec[paletteTag.keySet().size()];
		for (String key : paletteTag.keySet()) {
			int index = paletteTag.getIntOr(key, -1);
			if (index < 0 || index >= localPalette.length || localPalette[index] != null) {
				throw new BlueprintException("invalid_palette_index", index, "Palette");
			}
			try {
				localPalette[index] = BlockSpec.parse(key);
			} catch (IllegalArgumentException e) {
				throw new BlueprintException("invalid_block", key);
			}
		}
		PaletteBuilder palette = new PaletteBuilder(limits);
		short[] mapping = new short[localPalette.length];
		for (int i = 0; i < localPalette.length; i++) {
			mapping[i] = palette.indexOf(localPalette[i]);
		}

		short[] cells = new short[(int) volume];
		int position = 0;
		for (int index = 0; index < volume; index++) {
			int value = 0;
			int shift = 0;
			while (true) {
				if (position >= data.length) {
					throw new BlueprintException("data_too_short", "BlockData");
				}
				byte next = data[position++];
				value |= (next & 0x7F) << shift;
				if ((next & 0x80) == 0) {
					break;
				}
				shift += 7;
				if (shift > 28) {
					throw new BlueprintException("invalid_data", "BlockData");
				}
			}
			if (value < 0 || value >= mapping.length) {
				throw new BlueprintException("invalid_palette_index", value, "BlockData");
			}
			cells[index] = mapping[value];
		}
		if (position != data.length) {
			throw new BlueprintException("invalid_data", "BlockData");
		}
		CompoundTag metadata = body.getCompound("Metadata").orElse(new CompoundTag());
		Blueprint.Format format = switch (version) {
			case 1 -> Blueprint.Format.SPONGE_V1;
			case 2 -> Blueprint.Format.SPONGE_V2;
			default -> Blueprint.Format.SPONGE_V3;
		};
		return new Blueprint(metadata.getStringOr("Name", fallbackName), metadata.getStringOr("Author", ""), format,
				body.getIntOr("DataVersion", 0), width, height, length, palette.entries(), cells);
	}

	// ------------------------------------------------------------------------------------------------------------- MCEdit

	/** Data version the 1.13 block states of {@link BlockStateData} are in (after the flattening). */
	static final int FLATTENED_DATA_VERSION = 1452;

	/**
	 * MCEdit {@code .schematic} files (Minecraft 1.12 and older): numeric block ids and data values, translated to block
	 * states with the table Minecraft itself uses to upgrade 1.12 worlds. {@code AddBlocks} adds four high bits to the
	 * ids for blocks above 255 (modded blocks), which stay unknown.
	 */
	static Blueprint readMcEdit(CompoundTag body, String fallbackName, BlueprintLimits limits) throws BlueprintException {
		String materials = body.getStringOr("Materials", "Alpha");
		if (!materials.equals("Alpha")) {
			throw new BlueprintException("legacy_schematic", materials);
		}
		int width = unsignedShort(body, "Width");
		int height = unsignedShort(body, "Height");
		int length = unsignedShort(body, "Length");
		if (width == 0 || height == 0 || length == 0) {
			throw new BlueprintException("empty");
		}
		long volume = (long) width * height * length;
		checkVolume(volume, limits);
		byte[] blocks = body.getByteArray("Blocks").orElseThrow(() -> new BlueprintException("missing_field", "Blocks"));
		byte[] data = body.getByteArray("Data").orElse(new byte[blocks.length]);
		byte[] add = body.getByteArray("AddBlocks").orElse(new byte[0]);
		if (blocks.length != volume || data.length != volume) {
			throw new BlueprintException("data_too_short", "Blocks");
		}
		PaletteBuilder palette = new PaletteBuilder(limits);
		Map<Integer, Short> known = new HashMap<>();
		short[] cells = new short[(int) volume];
		for (int index = 0; index < volume; index++) {
			int id = blocks[index] & 0xFF;
			int high = index >> 1 < add.length ? ((index & 1) == 0 ? add[index >> 1] & 0x0F : (add[index >> 1] >> 4) & 0x0F) : 0;
			int legacy = ((high << 8) | id) << 4 | (data[index] & 0x0F);
			Short global = known.get(legacy);
			if (global == null) {
				global = palette.indexOf(legacyBlock(legacy));
				known.put(legacy, global);
			}
			cells[index] = global;
		}
		return new Blueprint(fallbackName, "", Blueprint.Format.MCEDIT, FLATTENED_DATA_VERSION, width, height, length, palette.entries(), cells);
	}

	/** The 1.13 block state of a 1.12 block id and data value ({@code id << 4 | data}). */
	private static BlockSpec legacyBlock(int legacy) throws BlueprintException {
		if (legacy >> 4 > 255) {
			return BlockSpec.parse("minecraft:legacy_" + (legacy >> 4) + "_" + (legacy & 15));
		}
		Tag tag = BlockStateData.getTag(legacy).convert(NbtOps.INSTANCE).getValue();
		return tag instanceof CompoundTag compound ? blockFromTag(compound) : BlockSpec.AIR;
	}

	private static int unsignedShort(CompoundTag body, String key) throws BlueprintException {
		return body.getShort(key).orElseThrow(() -> new BlueprintException("missing_field", key)) & 0xFFFF;
	}

	// ------------------------------------------------------------------------------------------------------------- Shared

	private static void checkVolume(long volume, BlueprintLimits limits) throws BlueprintException {
		if (volume > limits.maxVolume()) {
			throw new BlueprintException("too_many_positions", volume, limits.maxVolume());
		}
	}

	private static Map<String, Tag> asMap(CompoundTag tag) {
		Map<String, Tag> map = new HashMap<>();
		for (String key : tag.keySet()) {
			map.put(key, tag.get(key));
		}
		return map;
	}

	/** The blueprint's palette: air first, then every distinct spec once. */
	private static final class PaletteBuilder {
		private final BlueprintLimits limits;
		private final List<BlockSpec> entries = new ArrayList<>(List.of(BlockSpec.AIR));
		private final Map<BlockSpec, Short> indexes = new HashMap<>(Map.of(BlockSpec.AIR, (short) 0));

		PaletteBuilder(BlueprintLimits limits) {
			this.limits = limits;
		}

		short indexOf(BlockSpec spec) throws BlueprintException {
			if (spec.isAir()) {
				return 0;
			}
			Short existing = indexes.get(spec);
			if (existing != null) {
				return existing;
			}
			if (entries.size() >= limits.maxPaletteSize()) {
				throw new BlueprintException("invalid_palette_size", entries.size() + 1, limits.maxPaletteSize());
			}
			short index = (short) entries.size();
			entries.add(spec);
			indexes.put(spec, index);
			return index;
		}

		List<BlockSpec> entries() {
			return entries;
		}
	}
}
