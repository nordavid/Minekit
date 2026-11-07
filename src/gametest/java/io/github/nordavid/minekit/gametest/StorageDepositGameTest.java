package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import io.github.nordavid.minekit.feature.crafting.CraftingScreen;
import io.github.nordavid.minekit.feature.deposit.DepositPlanner;
import io.github.nordavid.minekit.feature.deposit.DepositProfile;
import io.github.nordavid.minekit.feature.deposit.SmartDepositModule;
import io.github.nordavid.minekit.feature.inventory.SmartInventoryModule;
import io.github.nordavid.minekit.feature.storage.ContainerRecord;
import io.github.nordavid.minekit.feature.storage.StorageFinderModule;
import io.github.nordavid.minekit.feature.storage.StorageFinderScreen;
import io.github.nordavid.minekit.feature.storage.StorageIndex;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Storage Finder and Smart Deposit in a real client: containers opened with a real right-click are indexed when closed,
 * a deposit runs with ordinary clicks and the server confirms the result, protected items stay, a click on the panel
 * with an item on the cursor drops nothing, a menu the server opens on its own is never treated as a container block,
 * and the index survives leaving the world. Single player and a dedicated server.
 */
public final class StorageDepositGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		SmartDepositModule deposit = (SmartDepositModule) runtime.modules().module(SmartDepositModule.METADATA.id()).orElseThrow();
		StorageFinderModule finder = (StorageFinderModule) runtime.modules().module(StorageFinderModule.METADATA.id()).orElseThrow();
		SmartInventoryModule inventory = (SmartInventoryModule) runtime.modules().module(SmartInventoryModule.METADATA.id()).orElseThrow();
		context.runOnClient(client -> inventory.settings().update(settings -> settings.favorites().contains("minecraft:diamond")
				? settings : settings.withFavoriteToggled("minecraft:diamond")));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			TestServerContext server = world.getServer();
			BlockPos player = context.computeOnClient(client -> client.player.blockPosition());

			BlockPos chest = player.offset(2, 0, 0);
			depositIntoChest(context, runtime, deposit, server, connection, chest, "singleplayer");
			panelClickWithCursorItemDropsNothing(context, deposit, server, connection, chest);
			doubleChestIsOneRecord(context, finder, server, connection, player);
			shulkerBoxRejectsShulkerBoxes(context, runtime, deposit, server, connection, player);
			enderChestIsKnownEverywhere(context, finder, server, connection, player);
			serverMenuIsNotAContainerBlock(context, deposit, finder, server, connection);
			storageFinderScreen(context, runtime, finder);
			craftingShowsStoredMaterials(context, runtime);
			dashboardPages(context, runtime);
		}
		indexSurvivesLeavingTheWorld(context, runtime, "sp-", "\"minecraft:cobblestone\": 100");

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			BlockPos player = context.computeOnClient(client -> client.player.blockPosition());
			depositIntoChest(context, runtime, deposit, server, connection, player.offset(2, 0, 0), "server");
		}
		indexSurvivesLeavingTheWorld(context, runtime, "mp-", "\"minecraft:iron_ingot\": 13");

		context.runOnClient(client -> inventory.settings().update(settings -> settings.withFavoriteToggled("minecraft:diamond")));
	}

	/** Matching deposit: tops up partial stacks, fills empty slots, keeps hotbar and favorites, ignores other items. */
	private static void depositIntoChest(ClientGameTestContext context, MinekitRuntime runtime, SmartDepositModule deposit, TestServerContext server,
			TestServerConnection connection, BlockPos chest, String where) {
		Consumer<String> command = server::runCommand;
		command.accept("gamemode survival @a");
		command.accept("clear @a");
		command.accept("setblock " + xyz(chest) + " minecraft:chest");
		command.accept("item replace block " + xyz(chest) + " container.0 with minecraft:cobblestone 60");
		command.accept("item replace block " + xyz(chest) + " container.1 with minecraft:iron_ingot 3");
		command.accept("item replace block " + xyz(chest) + " container.2 with minecraft:diamond 1");
		command.accept("item replace entity @a inventory.0 with minecraft:cobblestone 40");
		command.accept("item replace entity @a inventory.1 with minecraft:iron_ingot 10");
		command.accept("item replace entity @a inventory.2 with minecraft:bread 5");
		command.accept("item replace entity @a inventory.3 with minecraft:diamond 2");
		command.accept("item replace entity @a hotbar.0 with minecraft:cobblestone 16");
		connection.waitForClientboundPackets();
		context.waitTicks(2);

		openByRightClick(context, connection, chest, ContainerScreen.class);
		OpenedContainer opened = context.computeOnClient(client -> runtime.containers().current().orElse(null));
		check(opened != null && opened.kind() == OpenedContainer.Kind.CHEST && opened.positions().equals(List.of(chest)),
				where + ": the chest is identified, got " + opened);

		check(deposit.settings().current().profile() == DepositProfile.MATCHING, where + ": default profile is Matching");
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(2);
		DepositPlanner.Plan plan = context.computeOnClient(client -> deposit.overlay().flatMap(overlay -> overlay.plan()).orElse(null));
		check(plan != null, where + ": G opens the preview");
		check(ids(plan.moved()).equals(Map.of("minecraft:cobblestone", 40, "minecraft:iron_ingot", 10)), where + ": preview moves, got " + plan.moved());
		check(ids(plan.kept()).equals(Map.of("minecraft:cobblestone", 16, "minecraft:diamond", 2)), where + ": preview keeps, got " + plan.kept());
		context.takeScreenshot("minekit-storage-01-preview-" + where);

		// Everything on the panel is clicked with the real mouse: the panel background must not take the clicks.
		int[] panel = context.computeOnClient(client -> deposit.overlay().orElseThrow().panelBounds());
		GameTestMouse.clickAt(context, panel[0] + panel[2] - 14, panel[1] + 11);
		check(context.computeOnClient(client -> deposit.overlay().orElseThrow().isShowingHelp()), where + ": the ? button shows the explanation");
		context.takeScreenshot("minekit-storage-01b-help-" + where);
		GameTestMouse.clickAt(context, panel[0] + panel[2] - 14, panel[1] + 11);
		check(!context.computeOnClient(client -> deposit.overlay().orElseThrow().isShowingHelp()), where + ": clicking it again shows the preview");
		clickProfileButton(context);
		check(deposit.settings().current().profile() == DepositProfile.ALL, where + ": a click on the profile button switches the profile, got "
				+ deposit.settings().current().profile());
		for (int i = 1; i < DepositProfile.ORDER.size(); i++) {
			clickProfileButton(context);
		}
		check(deposit.settings().current().profile() == DepositProfile.MATCHING, where + ": the profiles cycle back to Matching");
		DepositPlanner.Plan again = context.computeOnClient(client -> deposit.overlay().flatMap(overlay -> overlay.plan()).orElse(null));
		check(again != null && again.moved().equals(plan.moved()), where + ": the preview is the same again, got " + again);

		GameTestMouse.clickWidget(context, "minekit.deposit.confirm");
		context.waitTicks(2);
		check(deposit.isDepositing() || runtime.executor().lastResult().isPresent(), where + ": the deposit starts");
		context.waitFor(client -> !runtime.executor().isRunning(), 600);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.takeScreenshot("minekit-storage-02-deposited-" + where);

		ContainerActionExecutor.Result result = runtime.executor().lastResult().orElseThrow();
		check(result.outcome() == ContainerActionExecutor.Outcome.COMPLETED && result.itemTotalsUnchanged(), where + ": completed, got " + result);
		Map<String, Integer> chestContents = server.computeOnServer(minecraft -> containerContents(minecraft, chest));
		check(chestContents.equals(Map.of("minecraft:cobblestone", 100, "minecraft:iron_ingot", 13, "minecraft:diamond", 1)),
				where + ": the server has the items in the chest, got " + chestContents);
		Map<String, Integer> playerItems = context.computeOnClient(StorageDepositGameTest::playerInventory);
		check(playerItems.equals(Map.of("minecraft:cobblestone", 16, "minecraft:bread", 5, "minecraft:diamond", 2)),
				where + ": hotbar, favorites and other items stay, got " + playerItems);

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(2);
		ContainerRecord record = recordAt(runtime, chest);
		check(record != null && record.items().equals(chestContents), where + ": the closed chest is indexed, got " + record);
	}

	/** Vanilla drops the cursor item when a click outside the container is released; the panel must swallow it. */
	private static void panelClickWithCursorItemDropsNothing(ClientGameTestContext context, SmartDepositModule deposit, TestServerContext server,
			TestServerConnection connection, BlockPos chest) {
		openByRightClick(context, connection, chest, ContainerScreen.class);
		Map<String, Integer> before = context.computeOnClient(StorageDepositGameTest::menuTotals);
		context.runOnClient(client -> {
			// Pick up the bread like a player would: an ordinary left click on its slot.
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.gui.screen();
			Slot bread = screen.getMenu().slots.stream().filter(slot -> slot.getItem().is(net.minecraft.world.item.Items.BREAD)).findFirst().orElseThrow();
			client.gameMode.handleContainerInput(screen.getMenu().containerId, bread.index, 0, ContainerInput.PICKUP, client.player);
		});
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(2);
		DepositPlanner.Plan plan = context.computeOnClient(client -> deposit.overlay().flatMap(overlay -> overlay.plan()).orElse(null));
		check(plan != null && plan.refusal().equals(Optional.of(DepositPlanner.Refusal.CURSOR_NOT_EMPTY)), "an item on the cursor refuses the deposit");
		context.takeScreenshot("minekit-storage-03-cursor-refused");

		// Click into an empty part of the panel, below the text and above the buttons.
		int[] panel = context.computeOnClient(client -> deposit.overlay().orElseThrow().panelBounds());
		GameTestMouse.clickAt(context, panel[0] + panel[2] / 2.0, panel[1] + 140);
		context.waitTick();
		connection.waitForClientboundPackets();
		check(context.computeOnClient(client -> client.player.containerMenu.getCarried().is(net.minecraft.world.item.Items.BREAD)),
				"the bread is still on the cursor");
		check(context.computeOnClient(StorageDepositGameTest::menuTotals).equals(before), "nothing was dropped");
		int dropped = server.computeOnServer(minecraft -> {
			ServerPlayer serverPlayer = minecraft.getPlayerList().getPlayers().getFirst();
			return serverPlayer.level().getEntitiesOfClass(ItemEntity.class, new AABB(serverPlayer.blockPosition()).inflate(8)).size();
		});
		check(dropped == 0, "no item entity in the world, got " + dropped);

		context.runOnClient(client -> {
			AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) client.gui.screen();
			Slot empty = screen.getMenu().slots.stream().filter(slot -> slot.container instanceof net.minecraft.world.entity.player.Inventory
					&& slot.getContainerSlot() == 11 && !slot.hasItem()).findFirst().orElseThrow();
			client.gameMode.handleContainerInput(screen.getMenu().containerId, empty.index, 0, ContainerInput.PICKUP, client.player);
		});
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void doubleChestIsOneRecord(ClientGameTestContext context, StorageFinderModule finder, TestServerContext server,
			TestServerConnection connection, BlockPos player) {
		BlockPos left = player.offset(0, 0, 2);
		BlockPos right = left.east();
		server.runCommand("setblock " + xyz(left) + " minecraft:chest[facing=north,type=left]");
		server.runCommand("setblock " + xyz(right) + " minecraft:chest[facing=north,type=right]");
		server.runCommand("item replace block " + xyz(left) + " container.5 with minecraft:dirt 64");
		connection.waitForClientboundPackets();
		int before = finder.index().orElseThrow().containers().size();

		openByRightClick(context, connection, right, ContainerScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		openByRightClick(context, connection, left, ContainerScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(2);

		StorageIndex index = finder.index().orElseThrow();
		check(index.containers().size() == before + 1, "both halves give one record, got " + (index.containers().size() - before));
		ContainerRecord record = index.containersWith("minecraft:dirt").getFirst();
		check(record.positions().size() == 2 && record.slots() == 54 && record.count("minecraft:dirt") == 64, "double chest record, got " + record);
	}

	private static void shulkerBoxRejectsShulkerBoxes(ClientGameTestContext context, MinekitRuntime runtime, SmartDepositModule deposit,
			TestServerContext server, TestServerConnection connection, BlockPos player) {
		BlockPos box = player.offset(-2, 0, 0);
		server.runCommand("setblock " + xyz(box) + " minecraft:shulker_box");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a inventory.4 with minecraft:red_shulker_box 1");
		server.runCommand("item replace entity @a inventory.5 with minecraft:oak_log 12");
		connection.waitForClientboundPackets();
		context.runOnClient(client -> deposit.settings().update(settings -> settings.withProfile(DepositProfile.ALL)));

		openByRightClick(context, connection, box, ShulkerBoxScreen.class);
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(2);
		DepositPlanner.Plan plan = context.computeOnClient(client -> deposit.overlay().flatMap(overlay -> overlay.plan()).orElse(null));
		check(plan != null && ids(plan.moved()).equals(Map.of("minecraft:oak_log", 12)), "only the logs go into the shulker box, got "
				+ (plan == null ? null : plan.moved()));
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTicks(2);
		context.waitFor(client -> !runtime.executor().isRunning(), 300);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		check(context.computeOnClient(StorageDepositGameTest::playerInventory).equals(Map.of("minecraft:red_shulker_box", 1)),
				"the shulker box item stays with the player");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.runOnClient(client -> deposit.settings().update(settings -> settings.withProfile(DepositProfile.MATCHING)));
	}

	private static void enderChestIsKnownEverywhere(ClientGameTestContext context, StorageFinderModule finder, TestServerContext server,
			TestServerConnection connection, BlockPos player) {
		BlockPos ender = player.offset(0, 0, -2);
		server.runCommand("setblock " + xyz(ender) + " minecraft:ender_chest");
		server.runCommand("item replace entity @a enderchest.0 with minecraft:emerald 7");
		connection.waitForClientboundPackets();
		openByRightClick(context, connection, ender, ContainerScreen.class);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(2);
		ContainerRecord record = finder.index().orElseThrow().containersWith("minecraft:emerald").stream().findFirst().orElse(null);
		check(record != null && record.isEnderChest() && record.count("minecraft:emerald") == 7, "ender chest record, got " + record);
	}

	/** A chest menu the server opens without a block (as plugins do) gets neither an index entry nor Smart Deposit. */
	private static void serverMenuIsNotAContainerBlock(ClientGameTestContext context, SmartDepositModule deposit, StorageFinderModule finder,
			TestServerContext server, TestServerConnection connection) {
		int before = finder.index().orElseThrow().containers().size();
		server.runOnServer(minecraft -> {
			ServerPlayer serverPlayer = minecraft.getPlayerList().getPlayers().getFirst();
			serverPlayer.openMenu(new SimpleMenuProvider((id, playerInventory, ignored) -> ChestMenu.threeRows(id, playerInventory),
					Component.literal("Virtual shop")));
		});
		context.waitForScreen(ContainerScreen.class);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(2);
		check(context.computeOnClient(client -> deposit.overlay().isEmpty()), "no Smart Deposit in a server menu");
		context.takeScreenshot("minekit-storage-04-server-menu");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(2);
		check(finder.index().orElseThrow().containers().size() == before, "a server menu is not indexed");

		// What a plugin does: the player right-clicks a real chest, the plugin cancels the opening and shows its own
		// chest menu instead. Here vanilla refuses to open the chest (a block on top) and the server opens a menu.
		BlockPos player = context.computeOnClient(client -> client.player.blockPosition());
		BlockPos chest = player.offset(-2, 0, -2);
		server.runCommand("setblock " + xyz(chest) + " minecraft:chest");
		server.runCommand("setblock " + xyz(chest.above()) + " minecraft:stone");
		connection.waitForClientboundPackets();
		context.getInput().lookAt(chest);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		connection.waitForClientboundPackets();
		check(context.computeOnClient(client -> client.gui.screen()) == null, "vanilla does not open a blocked chest");
		server.runOnServer(minecraft -> minecraft.getPlayerList().getPlayers().getFirst().openMenu(
				new SimpleMenuProvider((id, playerInventory, ignored) -> ChestMenu.threeRows(id, playerInventory), Component.literal("Crate preview"))));
		context.waitForScreen(ContainerScreen.class);
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		check(context.computeOnClient(client -> MinekitRuntime.get().containers().current().isEmpty()),
				"a substitute menu after a right-click on a chest is not confirmed, because the chest lid never opened");
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(2);
		check(context.computeOnClient(client -> deposit.overlay().isEmpty()), "no Smart Deposit in the substitute menu");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(2);
		check(finder.index().orElseThrow().containers().size() == before, "the substitute menu is not indexed");
		server.runCommand("setblock " + xyz(chest.above()) + " minecraft:air");
		server.runCommand("setblock " + xyz(chest) + " minecraft:air");
		connection.waitForClientboundPackets();
	}

	private static void storageFinderScreen(ClientGameTestContext context, MinekitRuntime runtime, StorageFinderModule finder) {
		QuickAction open = runtime.quickActions().actions().stream().filter(action -> action.id().equals("open_storage_finder")).findFirst()
				.orElseThrow();
		context.runOnClient(client -> open.run().run());
		context.waitForScreen(StorageFinderScreen.class);
		context.takeScreenshot("minekit-storage-05-finder");
		context.clickScreenButton("block.minecraft.cobblestone");
		context.waitTicks(2);
		String selected = context.computeOnClient(client -> ((StorageFinderScreen) client.gui.screen()).selected());
		check("minecraft:cobblestone".equals(selected), "clicking an item selects it, got " + selected);
		long cobblestone = context.computeOnClient(client -> ((StorageFinderScreen) client.gui.screen()).totals().get("minecraft:cobblestone"));
		check(cobblestone == 100, "known cobblestone, got " + cobblestone);
		context.takeScreenshot("minekit-storage-06-finder-item");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void craftingShowsStoredMaterials(ClientGameTestContext context, MinekitRuntime runtime) {
		Optional<StorageBridge.Known> iron = runtime.storage().known("minecraft:iron_ingot");
		check(iron.isPresent() && iron.get().total() == 13, "the bridge knows the stored iron, got " + iron);
		QuickAction open = runtime.quickActions().actions().stream().filter(action -> action.id().equals("open_crafting")).findFirst().orElseThrow();
		context.runOnClient(client -> open.run().run());
		context.waitForScreen(CraftingScreen.class);
		context.runOnClient(client -> ((CraftingScreen) client.gui.screen()).select("minecraft:iron_pickaxe"));
		context.waitTicks(3);
		context.takeScreenshot("minekit-storage-07-crafting-in-storage");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void dashboardPages(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(io.github.nordavid.minekit.core.screen.DashboardScreen.class);
		context.clickScreenButton("minekit.dashboard.page.storage");
		context.waitTick();
		context.takeScreenshot("minekit-storage-08-dashboard-storage");
		context.clickScreenButton("minekit.dashboard.page.deposit");
		context.waitTick();
		context.takeScreenshot("minekit-storage-09-dashboard-deposit");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void indexSurvivesLeavingTheWorld(ClientGameTestContext context, MinekitRuntime runtime, String prefix, String expected) {
		context.waitTicks(3);
		Path directory = runtime.dataDirectory().resolve("storage-index");
		try (Stream<Path> files = Files.list(directory)) {
			List<Path> matching = files.filter(file -> file.getFileName().toString().startsWith(prefix)).toList();
			check(matching.size() == 1, "one " + prefix + " index file, got " + matching);
			String json = Files.readString(matching.getFirst());
			check(json.contains(expected), "the saved index contains " + expected);
		} catch (IOException e) {
			throw new AssertionError("cannot read " + directory, e);
		}
	}

	private static void openByRightClick(ClientGameTestContext context, TestServerConnection connection, BlockPos pos,
			Class<? extends AbstractContainerScreen<?>> screen) {
		context.getInput().lookAt(pos);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitForScreen(screen);
		connection.waitForClientboundPackets();
		context.waitFor(client -> client.player.containerMenu.getStateId() != 0, 100);
		context.waitFor(client -> MinekitRuntime.get().containers().current().isPresent(), 40);
		context.waitTicks(2);
	}

	private static ContainerRecord recordAt(MinekitRuntime runtime, BlockPos pos) {
		StorageFinderModule finder = (StorageFinderModule) runtime.modules().module(StorageFinderModule.METADATA.id()).orElseThrow();
		ContainerRecord.Pos wanted = new ContainerRecord.Pos(pos.getX(), pos.getY(), pos.getZ());
		return finder.index().orElseThrow().containers().values().stream().filter(record -> record.positions().contains(wanted)).findFirst()
				.orElse(null);
	}

	private static Map<String, Integer> containerContents(MinecraftServer server, BlockPos pos) {
		Container container = (Container) server.overworld().getBlockEntity(pos);
		Map<String, Integer> items = new TreeMap<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			add(items, container.getItem(slot));
		}
		return items;
	}

	/** Player inventory (main, hotbar, offhand) by item id. */
	private static Map<String, Integer> playerInventory(Minecraft client) {
		Map<String, Integer> items = new TreeMap<>();
		client.player.inventoryMenu.slots.stream().filter(slot -> slot.index >= 9).forEach(slot -> add(items, slot.getItem()));
		return items;
	}

	/** Everything in the open menu plus the cursor. */
	private static Map<String, Integer> menuTotals(Minecraft client) {
		Map<String, Integer> items = new TreeMap<>();
		client.player.containerMenu.slots.forEach(slot -> add(items, slot.getItem()));
		add(items, client.player.containerMenu.getCarried());
		return items;
	}

	private static void add(Map<String, Integer> items, ItemStack stack) {
		if (!stack.isEmpty()) {
			items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
		}
	}

	private static Map<String, Integer> ids(Map<ItemKey, Integer> byKey) {
		return byKey.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().itemId(), Map.Entry::getValue, Integer::sum, TreeMap::new));
	}

	private static String xyz(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	/** The profile button says "Profile: Matching", or only "Matching" on a narrow panel. */
	private static void clickProfileButton(ClientGameTestContext context) {
		GameTestMouse.clickWidget(context, "the profile button", key -> key.equals("minekit.deposit.profile_button")
				|| key.startsWith("minekit.deposit.profile.") && !key.endsWith(".description"));
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("Minekit storage gametest failed: " + description);
		}
	}
}
