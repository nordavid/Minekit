package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlueprintReaderTest {
	private static final BlockSpec STONE = BlockSpec.parse("minecraft:stone");
	private static final BlockSpec STAIRS = BlockSpec.parse("minecraft:oak_stairs[half=bottom,facing=east]");

	// ------------------------------------------------------------------------------------------------- file builders

	/** Packs values tightly across long boundaries, least significant bit first: the Litematica layout. */
	static long[] pack(int[] values, int bits) {
		long[] array = new long[(int) (((long) values.length * bits + 63) / 64)];
		for (int i = 0; i < values.length; i++) {
			long start = (long) i * bits;
			int startLong = (int) (start >>> 6);
			int offset = (int) (start & 63);
			array[startLong] |= (long) values[i] << offset;
			int endLong = (int) ((start + bits - 1) >>> 6);
			if (endLong != startLong) {
				array[endLong] |= (long) values[i] >>> (64 - offset);
			}
		}
		return array;
	}

	static CompoundTag vector(int x, int y, int z) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", x);
		tag.putInt("y", y);
		tag.putInt("z", z);
		return tag;
	}

	static CompoundTag paletteEntry(BlockSpec spec) {
		CompoundTag entry = new CompoundTag();
		entry.putString("Name", spec.id());
		if (!spec.properties().isEmpty()) {
			CompoundTag properties = new CompoundTag();
			spec.properties().forEach(properties::putString);
			entry.put("Properties", properties);
		}
		return entry;
	}

	/** A region whose block at local (x, y, z) is {@code palette[blocks[index]]}; sizes may be negative. */
	static CompoundTag region(int[] position, int[] size, List<BlockSpec> palette, int[] blocks) {
		CompoundTag region = new CompoundTag();
		region.put("Position", vector(position[0], position[1], position[2]));
		region.put("Size", vector(size[0], size[1], size[2]));
		ListTag paletteTag = new ListTag();
		palette.forEach(spec -> paletteTag.add(paletteEntry(spec)));
		region.put("BlockStatePalette", paletteTag);
		int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.size() - 1));
		region.putLongArray("BlockStates", pack(blocks, bits));
		return region;
	}

	static CompoundTag litematic(Map<String, CompoundTag> regions) {
		CompoundTag root = new CompoundTag();
		root.putInt("Version", 7);
		root.putInt("MinecraftDataVersion", 4500);
		CompoundTag metadata = new CompoundTag();
		metadata.putString("Name", "Tower");
		metadata.putString("Author", "nordavid");
		root.put("Metadata", metadata);
		CompoundTag regionsTag = new CompoundTag();
		regions.forEach(regionsTag::put);
		root.put("Regions", regionsTag);
		return root;
	}

	static byte[] varints(int[] values) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (int value : values) {
			while ((value & ~0x7F) != 0) {
				out.write((value & 0x7F) | 0x80);
				value >>>= 7;
			}
			out.write(value);
		}
		return out.toByteArray();
	}

	static CompoundTag sponge(int version, int width, int height, int length, List<BlockSpec> palette, int[] blocks) {
		CompoundTag body = new CompoundTag();
		body.putInt("Version", version);
		body.putInt("DataVersion", 4500);
		body.putShort("Width", (short) width);
		body.putShort("Height", (short) height);
		body.putShort("Length", (short) length);
		CompoundTag paletteTag = new CompoundTag();
		for (int i = 0; i < palette.size(); i++) {
			paletteTag.putInt(palette.get(i).toString(), i);
		}
		if (version == 3) {
			CompoundTag blocksTag = new CompoundTag();
			blocksTag.put("Palette", paletteTag);
			blocksTag.putByteArray("Data", varints(blocks));
			body.put("Blocks", blocksTag);
			CompoundTag root = new CompoundTag();
			root.put("Schematic", body);
			return root;
		}
		body.put("Palette", paletteTag);
		body.putByteArray("BlockData", varints(blocks));
		return body;
	}

	static Blueprint roundTrip(CompoundTag root, BlueprintLimits limits) throws Exception {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(root, bytes);
		return BlueprintFiles.read(new ByteArrayInputStream(bytes.toByteArray()), "fallback", limits);
	}

	// ------------------------------------------------------------------------------------------------------- tests

	@Test
	void blockSpecsParseAndPrintCanonically() {
		BlockSpec spec = BlockSpec.parse(" oak_stairs[half=bottom, facing=east] ");
		assertEquals("minecraft:oak_stairs", spec.id());
		assertEquals("minecraft:oak_stairs[facing=east,half=bottom]", spec.toString());
		assertEquals(spec, BlockSpec.parse(spec.toString()));
		assertTrue(BlockSpec.parse("minecraft:cave_air").isAir());
		assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("Not Valid"));
		assertThrows(IllegalArgumentException.class, () -> BlockSpec.parse("minecraft:stone[facing"));
	}

	@Test
	void litematicRegionsMergeIntoOneBoxIncludingNegativeSizes() throws Exception {
		// Region a: 2x1x1 at (0,0,0): stone, stairs. Region b: size (-2,2,1) at (5,0,0) covers x 4..5, y 0..1.
		CompoundTag a = region(new int[] {0, 0, 0}, new int[] {2, 1, 1}, List.of(BlockSpec.AIR, STONE, STAIRS), new int[] {1, 2});
		CompoundTag b = region(new int[] {5, 0, 0}, new int[] {-2, 2, 1}, List.of(BlockSpec.AIR, STONE), new int[] {1, 0, 0, 1});
		Blueprint blueprint = roundTrip(litematic(Map.of("a", a, "b", b)), BlueprintLimits.DEFAULT);

		assertEquals("Tower", blueprint.name());
		assertEquals("nordavid", blueprint.author());
		assertEquals(Blueprint.Format.LITEMATIC, blueprint.format());
		assertEquals(4500, blueprint.dataVersion());
		assertEquals(6, blueprint.sizeX());
		assertEquals(2, blueprint.sizeY());
		assertEquals(1, blueprint.sizeZ());
		assertEquals(STONE, blueprint.at(0, 0, 0));
		assertEquals(STAIRS, blueprint.at(1, 0, 0));
		assertEquals(STONE, blueprint.at(4, 0, 0));
		assertEquals(BlockSpec.AIR, blueprint.at(5, 0, 0));
		assertEquals(STONE, blueprint.at(5, 1, 0));
		assertEquals(4, blueprint.blockCount());
	}

	@Test
	void paletteEntriesInTheFieldNamesOfMinecraft26AreRead() throws Exception {
		// Since data version 5006, stored block states use "id" and "properties".
		CompoundTag region = region(new int[] {0, 0, 0}, new int[] {1, 1, 1}, List.of(BlockSpec.AIR, STAIRS), new int[] {1});
		CompoundTag entry = region.getList("BlockStatePalette").orElseThrow().getCompound(1).orElseThrow();
		entry.putString("id", entry.getStringOr("Name", ""));
		entry.remove("Name");
		entry.put("properties", entry.getCompound("Properties").orElseThrow());
		entry.remove("Properties");
		assertEquals(STAIRS, roundTrip(litematic(Map.of("r", region)), BlueprintLimits.DEFAULT).at(0, 0, 0));
	}

	@Test
	void litematicValuesThatSpanTwoLongsAreRead() throws Exception {
		List<BlockSpec> palette = new ArrayList<>(List.of(BlockSpec.AIR));
		for (int i = 0; i < 20; i++) {
			palette.add(BlockSpec.parse("minecraft:block_" + i));
		}
		// 21 entries: 5 bits per value, so values cross long boundaries regularly.
		int[] blocks = new int[7 * 5 * 3];
		Random random = new Random(7);
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = random.nextInt(palette.size());
		}
		Blueprint blueprint = roundTrip(litematic(Map.of("r", region(new int[] {0, 0, 0}, new int[] {7, 5, 3}, palette, blocks))),
				BlueprintLimits.DEFAULT);
		int index = 0;
		for (int y = 0; y < 5; y++) {
			for (int z = 0; z < 3; z++) {
				for (int x = 0; x < 7; x++, index++) {
					assertEquals(palette.get(blocks[index]), blueprint.at(x, y, z), "position " + x + "," + y + "," + z);
				}
			}
		}
	}

	@Test
	void spongeVersionsTwoAndThreeWithMultiByteVarints() throws Exception {
		List<BlockSpec> palette = new ArrayList<>(List.of(BlockSpec.AIR));
		for (int i = 0; i < 300; i++) {
			palette.add(BlockSpec.parse("minecraft:wool_" + i));
		}
		int[] blocks = new int[4 * 3 * 5];
		for (int i = 0; i < blocks.length; i++) {
			blocks[i] = (i * 37) % palette.size();
		}
		for (int version : new int[] {2, 3}) {
			Blueprint blueprint = roundTrip(sponge(version, 4, 3, 5, palette, blocks), BlueprintLimits.DEFAULT);
			assertEquals(version == 2 ? Blueprint.Format.SPONGE_V2 : Blueprint.Format.SPONGE_V3, blueprint.format());
			int index = 0;
			for (int y = 0; y < 3; y++) {
				for (int z = 0; z < 5; z++) {
					for (int x = 0; x < 4; x++, index++) {
						assertEquals(palette.get(blocks[index]), blueprint.at(x, y, z));
					}
				}
			}
		}
	}

	@Test
	void spongeVersionThreeAcceptsBlockPaletteAndUnsignedSizes() throws Exception {
		CompoundTag root = sponge(3, 1, 1, 1, List.of(BlockSpec.AIR, STONE), new int[] {1});
		CompoundTag blocks = root.getCompound("Schematic").orElseThrow().getCompound("Blocks").orElseThrow();
		blocks.put("BlockPalette", blocks.getCompound("Palette").orElseThrow());
		blocks.remove("Palette");
		assertEquals(STONE, roundTrip(root, BlueprintLimits.DEFAULT).at(0, 0, 0));

		// Width 40,000 does not fit a signed short; the format stores it unsigned. The volume limit rejects it here.
		CompoundTag wide = sponge(2, 40_000, 1, 1, List.of(BlockSpec.AIR), new int[0]);
		BlueprintException tooBig = assertThrows(BlueprintException.class,
				() -> roundTrip(wide, new BlueprintLimits(1 << 20, 1 << 24, 1000, 64, 4)));
		assertEquals("too_many_positions", tooBig.reason());
		assertEquals(40_000L, tooBig.arguments()[0]);
	}

	@Test
	void malformedFilesFailWithAReason() {
		BlueprintLimits limits = BlueprintLimits.DEFAULT;
		assertReason("invalid_palette_index", litematic(Map.of("r",
				region(new int[] {0, 0, 0}, new int[] {2, 1, 1}, List.of(BlockSpec.AIR, STONE), new int[] {1, 3}))), limits);
		assertReason("invalid_palette_index", sponge(2, 2, 1, 1, List.of(BlockSpec.AIR, STONE), new int[] {1, 2}), limits);

		CompoundTag truncated = sponge(2, 3, 1, 1, List.of(BlockSpec.AIR, STONE), new int[] {1, 1, 1});
		truncated.putByteArray("BlockData", new byte[] {1, 1});
		assertReason("data_too_short", truncated, limits);

		CompoundTag trailing = sponge(2, 1, 1, 1, List.of(BlockSpec.AIR, STONE), new int[] {1});
		trailing.putByteArray("BlockData", new byte[] {1, 0});
		assertReason("invalid_data", trailing, limits);

		CompoundTag shortStates = litematic(Map.of("r", region(new int[] {0, 0, 0}, new int[] {40, 1, 1}, List.of(BlockSpec.AIR, STONE),
				new int[40])));
		shortStates.getCompound("Regions").orElseThrow().getCompound("r").orElseThrow().putLongArray("BlockStates", new long[1]);
		assertReason("data_too_short", shortStates, limits);

		CompoundTag badBlock = sponge(2, 1, 1, 1, List.of(BlockSpec.AIR, STONE), new int[] {0});
		badBlock.getCompound("Palette").orElseThrow().putInt("Bad Name!", 1);
		badBlock.getCompound("Palette").orElseThrow().remove("minecraft:stone");
		assertReason("invalid_block", badBlock, limits);

		CompoundTag legacy = new CompoundTag();
		legacy.putString("Materials", "Alpha");
		assertReason("legacy_schematic", legacy, limits);
		assertReason("unknown_format", new CompoundTag(), limits);

		CompoundTag future = sponge(2, 1, 1, 1, List.of(BlockSpec.AIR), new int[] {0});
		future.putInt("Version", 9);
		assertReason("unsupported_version", future, limits);

		BlueprintLimits tiny = new BlueprintLimits(1 << 20, 64, 1000, 64, 4);
		assertReason("too_complex", sponge(2, 10, 10, 10, List.of(BlockSpec.AIR), new int[1000]), tiny);
	}

	@Test
	void notCompressedAndTooLargeFilesAreRejected(@TempDir Path directory) throws IOException {
		BlueprintException notCompressed = assertThrows(BlueprintException.class,
				() -> BlueprintFiles.read(new ByteArrayInputStream(new byte[] {1, 2, 3, 4}), "x", BlueprintLimits.DEFAULT));
		assertEquals("not_compressed", notCompressed.reason());

		Path big = directory.resolve("big.schem");
		Files.write(big, new byte[4096]);
		BlueprintException tooLarge = assertThrows(BlueprintException.class,
				() -> BlueprintFiles.read(big, new BlueprintLimits(1024, 1 << 20, 1000, 64, 4)));
		assertEquals("file_too_large", tooLarge.reason());

		Path text = directory.resolve("notes.txt");
		Files.writeString(text, "hi");
		assertEquals("unsupported_file", assertThrows(BlueprintException.class, () -> BlueprintFiles.read(text, BlueprintLimits.DEFAULT)).reason());
	}

	private static void assertReason(String reason, CompoundTag root, BlueprintLimits limits) {
		BlueprintException error = assertThrows(BlueprintException.class, () -> roundTrip(root, limits));
		assertEquals(reason, error.reason(), error.getMessage());
	}
}
