package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.feature.blueprint.Blueprint;
import io.github.nordavid.minekit.feature.blueprint.BlueprintModule;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import io.github.nordavid.minekit.feature.blueprint.BlueprintScreen;
import io.github.nordavid.minekit.feature.blueprint.Placement;
import io.github.nordavid.minekit.feature.blueprint.ResolvedBlueprint;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Blueprints in the real client: real .litematic, .schem and .schematic files (one broken, one with a block name from an
 * older version, one uncompressed MCEdit file with numeric ids), the guide compared with blocks placed by commands,
 * turning, the screen's tabs with the plan and the 3D view, importing by drag and drop, and the measuring tool.
 */
public final class BlueprintGameTest implements FabricClientGameTest {
	/** 3 x 2 x 3: a stone floor with old-style "grass" in the middle, a stair facing north and a plank above. */
	private static final String[] PALETTE = {"minecraft:air", "minecraft:stone", "minecraft:grass", "minecraft:oak_stairs", "minecraft:oak_planks"};

	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		BlueprintModule module = (BlueprintModule) runtime.modules().module(BlueprintModule.METADATA.id()).orElseThrow();
		try {
			Files.createDirectories(module.directory());
			NbtIo.writeCompressed(litematic(), module.directory().resolve("tower.litematic"));
			NbtIo.writeCompressed(spongeV3(), module.directory().resolve("slab.schem"));
			Files.write(module.directory().resolve("broken.schem"), new byte[] {0x1f, (byte) 0x8b, 1, 2, 3});
			// Uncompressed, like the files of some download sites.
			try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(module.directory().resolve("house.schematic")))) {
				NbtIo.write(mcEdit(), out);
			}
		} catch (IOException e) {
			throw new AssertionError("could not write test blueprints", e);
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			Consumer<String> command = world.getServer()::runCommand;
			command.accept("gamemode creative @a");
			command.accept("time set noon");
			connection.waitForClientboundPackets();

			brokenFileFailsWithAReason(context, runtime, module);
			legacySchematicLoads(context, module);
			BlockPos origin = context.computeOnClient(client -> client.player.blockPosition().offset(3, 0, 1));
			guideFollowsTheWorld(context, module, command, connection, origin);
			screenTabsAndImport(context, runtime, module);
			measuring(context, runtime, module, command, connection);
		}
	}

	private static void brokenFileFailsWithAReason(ClientGameTestContext context, MinekitRuntime runtime, BlueprintModule module) {
		check(context.computeOnClient(client -> module.files()).equals(List.of("broken.schem", "house.schematic", "slab.schem", "tower.litematic")),
				"the four files are listed, got " + context.computeOnClient(client -> module.files()));
		context.runOnClient(client -> module.select("broken.schem"));
		context.waitFor(client -> module.failedFile().isPresent(), 200);
		check(module.active().isEmpty(), "a broken file is not shown");
		check(context.computeOnClient(client -> module.failureReason().map(reason -> reason.getContents() instanceof TranslatableContents contents
				&& contents.getKey().equals("minekit.blueprint.error.damaged")).orElse(false)), "the reason is kept: the compressed data is damaged");
		// The screen says why in full, not only the notification.
		context.runOnClient(client -> action(runtime, "open_blueprints").run().run());
		context.waitForScreen(BlueprintScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("minekit-p6-00-failed");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void legacySchematicLoads(ClientGameTestContext context, BlueprintModule module) {
		context.runOnClient(client -> module.select("house.schematic"));
		context.waitFor(client -> module.active().isPresent() || module.failedFile().isPresent(), 200);
		check(module.active().isPresent(), "the MCEdit file loads, got " + context.computeOnClient(client -> module.failureReason().map(Component::getString)));
		ResolvedBlueprint resolved = context.computeOnClient(client -> module.active().orElseThrow());
		Blueprint blueprint = resolved.blueprint();
		check(blueprint.format() == Blueprint.Format.MCEDIT && blueprint.blockCount() == 4, "four blocks of an MCEdit schematic, got "
				+ blueprint.format() + " with " + blueprint.blockCount());
		check(context.computeOnClient(client -> resolved.state(blueprint.paletteIndex(1, 0, 0)).is(Blocks.GRANITE)
				&& net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(resolved.state(blueprint.paletteIndex(0, 1, 0)).getBlock()).toString()
						.equals("minecraft:red_wool") && resolved.state(blueprint.paletteIndex(1, 1, 0)).is(Blocks.GLASS)),
				"numeric ids and data values become granite, red wool and glass");
		check(resolved.unknownBlocks().isEmpty(), "every block is known, got " + resolved.unknownBlocks());
	}

	private static void guideFollowsTheWorld(ClientGameTestContext context, BlueprintModule module, Consumer<String> command,
			TestServerConnection connection, BlockPos origin) {
		context.runOnClient(client -> module.select("tower.litematic"));
		context.waitFor(client -> module.active().isPresent(), 200);
		ResolvedBlueprint resolved = context.computeOnClient(client -> module.active().orElseThrow());
		Blueprint blueprint = resolved.blueprint();
		check(blueprint.sizeX() == 3 && blueprint.sizeY() == 2 && blueprint.sizeZ() == 3 && blueprint.blockCount() == 11,
				"size and block count, got " + blueprint.sizeX() + "x" + blueprint.sizeY() + "x" + blueprint.sizeZ() + " " + blueprint.blockCount());
		int grass = blueprint.palette().indexOf(io.github.nordavid.minekit.feature.blueprint.BlockSpec.parse("minecraft:grass"));
		String grassState = context.computeOnClient(client -> String.valueOf(resolved.state(grass)));
		check(context.computeOnClient(client -> resolved.state(grass) != null && resolved.state(grass).is(Blocks.SHORT_GRASS)),
				"\"grass\" from a file of an older version is updated to short grass by the data fixer, got " + grassState + " at palette index "
						+ grass + ", unknown " + resolved.unknownBlocks() + ", palette " + blueprint.palette());

		context.runOnClient(client -> module.setPlacement(new Placement(origin.getX(), origin.getY(), origin.getZ(), 0, false)));
		context.waitTicks(3);
		BlueprintProgress empty = context.computeOnClient(client -> module.progress().orElseThrow());
		check(empty.count(Status.MISSING) == 11 && empty.count(Status.UNCHECKED) == 0, "nothing built yet: 11 missing, got missing "
				+ empty.count(Status.MISSING) + ", unchecked " + empty.count(Status.UNCHECKED));

		command.accept("setblock " + xyz(origin) + " minecraft:stone");
		command.accept("setblock " + xyz(origin.offset(1, 0, 0)) + " minecraft:dirt");
		command.accept("setblock " + xyz(origin.offset(1, 0, 1)) + " minecraft:short_grass");
		command.accept("setblock " + xyz(origin.offset(0, 1, 0)) + " minecraft:oak_stairs[facing=south]");
		connection.waitForClientboundPackets();
		context.waitTicks(3);
		BlueprintProgress built = context.computeOnClient(client -> module.progress().orElseThrow());
		check(built.count(Status.CORRECT) == 2 && built.count(Status.WRONG) == 2 && built.count(Status.MISSING) == 7,
				"stone and grass correct, dirt and the stair facing south wrong, 7 missing; got correct " + built.count(Status.CORRECT) + ", wrong "
						+ built.count(Status.WRONG) + ", missing " + built.count(Status.MISSING));
		context.waitFor(client -> module.nextBlock().isPresent(), 40);
		check(context.computeOnClient(client -> module.nextBlock().map(pos -> pos.getY() == origin.getY()).orElse(false)),
				"the next block is in the lowest unfinished layer");

		command.accept("setblock " + xyz(origin.offset(0, 1, 0)) + " minecraft:oak_stairs[facing=north]");
		connection.waitForClientboundPackets();
		context.waitTicks(3);
		check(context.computeOnClient(client -> module.progress().orElseThrow().count(Status.CORRECT)) == 3, "the turned stair is correct now");

		// The guide in the world: look at the structure with no screen open.
		context.getInput().lookAt(origin.offset(1, 0, 1));
		context.waitTicks(5);
		context.takeScreenshot("minekit-p6-01-guide");

		// A quarter turn turns the stair as well: facing north becomes facing east.
		context.runOnClient(client -> module.setPlacement(new Placement(origin.getX(), origin.getY(), origin.getZ(), 1, false)));
		int stairs = blueprint.palette().indexOf(io.github.nordavid.minekit.feature.blueprint.BlockSpec.parse("minecraft:oak_stairs[facing=north,half=bottom]"));
		Direction turned = context.computeOnClient(client -> resolved.expected(stairs, module.placement().orElseThrow())
				.getValue(BlockStateProperties.HORIZONTAL_FACING));
		check(turned == Direction.EAST, "a clockwise quarter turn makes the north-facing stair face east, got " + turned);
		context.runOnClient(client -> module.setPlacement(new Placement(origin.getX(), origin.getY(), origin.getZ(), 0, false)));
		context.waitTicks(3);
	}

	private static void screenTabsAndImport(ClientGameTestContext context, MinekitRuntime runtime, BlueprintModule module) {
		context.runOnClient(client -> action(runtime, "open_blueprints").run().run());
		context.waitForScreen(BlueprintScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("minekit-p6-02-overview");
		context.clickScreenButton("minekit.blueprint.tab.layers");
		// The 3D view is painted on a background thread.
		context.waitFor(client -> client.gui.screen() instanceof BlueprintScreen screen && screen.isStructureDrawn(), 200);
		context.waitTicks(2);
		context.takeScreenshot("minekit-p6-03-layers-3d");
		context.clickScreenButton("minekit.blueprint.view.turn_button");
		context.clickScreenButton("minekit.blueprint.view.turn_button");
		context.waitFor(client -> client.gui.screen() instanceof BlueprintScreen screen && screen.isStructureDrawn(), 200);
		context.waitTicks(5);
		context.takeScreenshot("minekit-p6-03b-layers-3d-turned");
		context.clickScreenButton("minekit.blueprint.view.turn_button");
		context.clickScreenButton("minekit.blueprint.view.turn_button");
		context.clickScreenButton("minekit.blueprint.view.plan");
		context.waitTicks(2);
		context.takeScreenshot("minekit-p6-03c-layers-plan");
		context.clickScreenButton("minekit.blueprint.view.whole");
		context.clickScreenButton("minekit.blueprint.tab.materials");
		context.waitTicks(2);
		context.takeScreenshot("minekit-p6-04-materials");
		context.clickScreenButton("minekit.blueprint.tab.overview");

		Path outside = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("dropped-slab.schem"));
		try {
			NbtIo.writeCompressed(spongeV3(), outside);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		long sizeBefore;
		try {
			sizeBefore = Files.size(outside);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		context.runOnClient(client -> client.gui.screen().onFilesDrop(List.of(outside)));
		context.waitFor(client -> module.active().map(active -> active.file().equals("dropped-slab.schem")).orElse(false), 200);
		check(Files.exists(outside), "the dropped original stays where it was");
		try {
			check(Files.size(outside) == sizeBefore, "and unchanged");
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		Blueprint slab = context.computeOnClient(client -> module.active().orElseThrow().blueprint());
		check(slab.format() == Blueprint.Format.SPONGE_V3 && slab.blockCount() == 4, "the Sponge v3 copy is the active blueprint, got " + slab.format()
				+ " with " + slab.blockCount() + " blocks");
		context.takeScreenshot("minekit-p6-05-imported");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.runOnClient(client -> module.select("tower.litematic"));
		context.waitFor(client -> module.active().isPresent(), 200);
	}

	private static void measuring(ClientGameTestContext context, MinekitRuntime runtime, BlueprintModule module, Consumer<String> command,
			TestServerConnection connection) {
		QuickAction measure = action(runtime, "measure_point");
		// Two marker blocks away from the structure, seen at clear angles: one at feet height, one at head height.
		BlockPos feet = context.computeOnClient(client -> client.player.blockPosition());
		BlockPos cornerA = feet.offset(2, 0, -2);
		BlockPos cornerB = feet.offset(-1, 1, -4);
		command.accept("setblock " + xyz(cornerA) + " minecraft:gold_block");
		command.accept("setblock " + xyz(cornerB) + " minecraft:diamond_block");
		connection.waitForClientboundPackets();
		context.getInput().lookAt(cornerA);
		context.waitTicks(2);
		context.runOnClient(client -> measure.run().run());
		context.getInput().lookAt(cornerB);
		context.waitTicks(2);
		context.runOnClient(client -> measure.run().run());
		check(context.computeOnClient(client -> module.measureA().isPresent() && module.measureB().isPresent()), "two corners are set");
		BlockPos a = context.computeOnClient(client -> module.measureA().orElseThrow());
		BlockPos b = context.computeOnClient(client -> module.measureB().orElseThrow());
		String text = measureText(a, b);
		check(a.equals(cornerA) && b.equals(cornerB), "the looked-at blocks are the corners, got " + a + " and " + b);
		check(text.equals("4 × 2 × 3"), "the box from corner to corner is 4 x 2 x 3, got " + text);
		// Look between the corners so the box and its label are in view, then let a tick draw them.
		context.getInput().lookAt(feet.offset(0, 0, -3));
		context.waitTicks(3);
		context.takeScreenshot("minekit-p6-06-measure");
		context.runOnClient(client -> action(runtime, "measure_clear").run().run());
	}

	private static String measureText(BlockPos a, BlockPos b) {
		return (Math.abs(a.getX() - b.getX()) + 1) + " × " + (Math.abs(a.getY() - b.getY()) + 1) + " × " + (Math.abs(a.getZ() - b.getZ()) + 1);
	}

	// --------------------------------------------------------------------------------------------------------- files

	private static CompoundTag litematic() {
		int[] blocks = new int[3 * 2 * 3];
		for (int i = 0; i < 9; i++) {
			blocks[i] = 1;
		}
		blocks[1 + 1 * 3] = 2;
		blocks[9] = 3;
		blocks[9 + 2 + 2 * 3] = 4;
		ListTag palette = new ListTag();
		for (String name : PALETTE) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Name", name);
			if (name.equals("minecraft:oak_stairs")) {
				CompoundTag properties = new CompoundTag();
				properties.putString("facing", "north");
				properties.putString("half", "bottom");
				entry.put("Properties", properties);
			}
			palette.add(entry);
		}
		CompoundTag region = new CompoundTag();
		region.put("Position", vector(0, 0, 0));
		region.put("Size", vector(3, 2, 3));
		region.put("BlockStatePalette", palette);
		region.putLongArray("BlockStates", pack(blocks, 3));
		CompoundTag regions = new CompoundTag();
		regions.put("Main", region);
		CompoundTag metadata = new CompoundTag();
		metadata.putString("Name", "Test tower");
		metadata.putString("Author", "Minekit gametest");
		CompoundTag root = new CompoundTag();
		root.putInt("Version", 6);
		// 1.20.1: "grass" was renamed to "short_grass" later.
		root.putInt("MinecraftDataVersion", 3465);
		root.put("Metadata", metadata);
		root.put("Regions", regions);
		return root;
	}

	/** MCEdit, 2 x 2 x 1, nested like the files of some sites: stone, granite (1:1); red wool (35:14), glass (20). */
	private static CompoundTag mcEdit() {
		CompoundTag body = new CompoundTag();
		body.putShort("Width", (short) 2);
		body.putShort("Height", (short) 2);
		body.putShort("Length", (short) 1);
		body.putString("Materials", "Alpha");
		body.putByteArray("Blocks", new byte[] {1, 1, 35, 20});
		body.putByteArray("Data", new byte[] {0, 1, 14, 0});
		CompoundTag root = new CompoundTag();
		root.put("Schematic", body);
		return root;
	}

	private static CompoundTag spongeV3() {
		CompoundTag palette = new CompoundTag();
		palette.putInt("minecraft:air", 0);
		palette.putInt("minecraft:smooth_stone_slab[type=bottom]", 1);
		ByteArrayOutputStream data = new ByteArrayOutputStream();
		for (int i = 0; i < 4; i++) {
			data.write(1);
		}
		CompoundTag blocks = new CompoundTag();
		blocks.put("Palette", palette);
		blocks.putByteArray("Data", data.toByteArray());
		CompoundTag body = new CompoundTag();
		body.putInt("Version", 3);
		body.putInt("DataVersion", 4500);
		body.putShort("Width", (short) 2);
		body.putShort("Height", (short) 1);
		body.putShort("Length", (short) 2);
		body.put("Blocks", blocks);
		CompoundTag root = new CompoundTag();
		root.put("Schematic", body);
		return root;
	}

	private static CompoundTag vector(int x, int y, int z) {
		CompoundTag tag = new CompoundTag();
		tag.putInt("x", x);
		tag.putInt("y", y);
		tag.putInt("z", z);
		return tag;
	}

	private static long[] pack(int[] values, int bits) {
		long[] array = new long[(int) (((long) values.length * bits + 63) / 64)];
		for (int i = 0; i < values.length; i++) {
			long start = (long) i * bits;
			int startLong = (int) (start >>> 6);
			int offset = (int) (start & 63);
			array[startLong] |= (long) values[i] << offset;
			if (((start + bits - 1) >>> 6) != startLong) {
				array[startLong + 1] |= (long) values[i] >>> (64 - offset);
			}
		}
		return array;
	}

	private static QuickAction action(MinekitRuntime runtime, String id) {
		return runtime.quickActions().actions().stream().filter(action -> action.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no quick action " + id));
	}

	private static String xyz(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("Minekit blueprint gametest failed: " + description);
		}
	}
}
