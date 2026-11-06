package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import io.github.nordavid.minekit.feature.crafting.CraftingScreen;
import io.github.nordavid.minekit.feature.deposit.SmartDepositModule;
import io.github.nordavid.minekit.feature.loadout.Loadout;
import io.github.nordavid.minekit.feature.loadout.LoadoutModule;
import io.github.nordavid.minekit.feature.loadout.LoadoutOverlay;
import io.github.nordavid.minekit.feature.loadout.LoadoutPlanner;
import io.github.nordavid.minekit.feature.loadout.LoadoutScreen;
import io.github.nordavid.minekit.feature.loadout.LoadoutSlots;
import io.github.nordavid.minekit.feature.quickactions.QuickActionSettings;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsModule;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsScreen;
import io.github.nordavid.minekit.feature.resources.ResourceTrackerModule;
import io.github.nordavid.minekit.feature.storage.StorageFinderScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Phase 5 in the real client: Quick Actions pinned with Ctrl+P and the star, run with a number key; actions for the
 * held item; a loadout saved, compared and applied in the inventory; a loadout taken from a chest (single player and
 * a dedicated server); the side panels sharing their space; and the dashboard pages. Clicks on MINEKIT controls use
 * the real mouse.
 */
public final class LoadoutQuickActionGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		LoadoutModule loadouts = (LoadoutModule) runtime.modules().module(LoadoutModule.METADATA.id()).orElseThrow();
		QuickActionsModule quickActions = (QuickActionsModule) runtime.modules().module(QuickActionsModule.METADATA.id()).orElseThrow();
		context.runOnClient(client -> runtime.modules().setEnabled(QuickActionsModule.METADATA.id(), true));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			TestServerContext server = world.getServer();
			Consumer<String> command = server::runCommand;
			command.accept("gamemode survival @a");

			pinAndRunWithNumberKeys(context, quickActions);
			heldItemActions(context, runtime, command, connection);
			loadoutInTheInventory(context, runtime, loadouts, command, connection);
			BlockPos chest = context.computeOnClient(client -> client.player.blockPosition()).offset(2, 0, 0);
			loadoutFromAChest(context, runtime, loadouts, server, connection, chest, "singleplayer");
			panelsShareTheirSpace(context, runtime, loadouts, connection, chest);
			dashboardPages(context, runtime);
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			server.runCommand("gamemode survival @a");
			BlockPos chest = context.computeOnClient(client -> client.player.blockPosition()).offset(2, 0, 0);
			loadoutFromAChest(context, runtime, loadouts, server, connection, chest, "server");
		}
		context.runOnClient(client -> quickActions.settings().update(settings -> QuickActionSettings.DEFAULT));
	}

	private static void pinAndRunWithNumberKeys(ClientGameTestContext context, QuickActionsModule quickActions) {
		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitForScreen(QuickActionsScreen.class);
		context.getInput().typeChars("sort");
		context.waitTick();
		context.getInput().holdControl();
		context.getInput().pressKey(InputConstants.KEY_P);
		context.getInput().releaseControl();
		context.waitTick();
		check(quickActions.settings().current().pinned().equals(List.of("sort_inventory")), "Ctrl+P pins the selected action, got "
				+ quickActions.settings().current().pinned());
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitForScreen(QuickActionsScreen.class);
		List<String> listed = context.computeOnClient(client -> ids(((QuickActionsScreen) client.gui.screen()).matches()));
		check(listed.getFirst().equals("sort_inventory"), "pinned actions come first, got " + listed);
		int[] row = context.computeOnClient(client -> ((QuickActionsScreen) client.gui.screen()).rowBounds("open_crafting"));
		check(row != null, "the crafting action is listed");
		GameTestMouse.clickAt(context, row[0] + row[2] - 8, row[1] + row[3] / 2.0);
		check(quickActions.settings().current().pinned().equals(List.of("sort_inventory", "open_crafting")),
				"a click on the star pins the action, got " + quickActions.settings().current().pinned());
		check(context.computeOnClient(client -> client.gui.screen() instanceof QuickActionsScreen), "the star click does not run the action");
		context.takeScreenshot("minekit-p5-01-pinned");

		context.getInput().pressKey(InputConstants.KEY_2);
		context.waitForScreen(CraftingScreen.class);
		check(quickActions.settings().current().recent().getFirst().equals("open_crafting"), "the run is remembered as recent");
		String search = context.computeOnClient(client -> client.gui.screen().children().stream()
				.filter(child -> child instanceof net.minecraft.client.gui.components.EditBox).map(child ->
						((net.minecraft.client.gui.components.EditBox) child).getValue()).findFirst().orElse(""));
		check(search.isEmpty(), "the number key does not leak into the next screen, got '" + search + "'");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void heldItemActions(ClientGameTestContext context, MinekitRuntime runtime, Consumer<String> command,
			TestServerConnection connection) {
		command.accept("clear @a");
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		QuickAction find = action(runtime, "find_held_item");
		check(context.computeOnClient(client -> find.unavailableReason().get().isPresent()), "without an item in hand the action is unavailable");

		command.accept("item replace entity @a hotbar.0 with minecraft:iron_pickaxe");
		command.accept("item replace entity @a hotbar.1 with minecraft:diamond 3");
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.runOnClient(client -> action(runtime, "plan_held_item").run().run());
		context.waitForScreen(CraftingScreen.class);
		String target = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).request().target());
		check(target.equals("minecraft:iron_pickaxe"), "the planner opens with the held item, got " + target);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		context.runOnClient(client -> find.run().run());
		context.waitForScreen(StorageFinderScreen.class);
		String selected = context.computeOnClient(client -> ((StorageFinderScreen) client.gui.screen()).selected());
		check("minecraft:iron_pickaxe".equals(selected), "the finder opens with the held item selected, got " + selected);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		ResourceTrackerModule resources = (ResourceTrackerModule) runtime.modules().module(ResourceTrackerModule.METADATA.id()).orElseThrow();
		context.runOnClient(client -> action(runtime, "goal_held_item").run().run());
		int goal = context.computeOnClient(client -> resources.tracker().current().goals().getOrDefault("minecraft:iron_pickaxe", 0));
		check(goal == 64, "the held item becomes a goal of a new session, got " + goal);
		context.runOnClient(client -> resources.stop());
	}

	private static void loadoutInTheInventory(ClientGameTestContext context, MinekitRuntime runtime, LoadoutModule loadouts, Consumer<String> command,
			TestServerConnection connection) {
		command.accept("clear @a");
		command.accept("item replace entity @a hotbar.0 with minecraft:iron_pickaxe");
		command.accept("item replace entity @a hotbar.1 with minecraft:iron_sword");
		command.accept("item replace entity @a hotbar.8 with minecraft:torch 32");
		command.accept("item replace entity @a inventory.0 with minecraft:bread 10");
		command.accept("item replace entity @a armor.head with minecraft:iron_helmet");
		connection.waitForClientboundPackets();
		context.waitTicks(2);

		context.runOnClient(client -> action(runtime, "save_loadout").run().run());
		context.waitForScreen(LoadoutScreen.class);
		Loadout saved = context.computeOnClient(client -> ((LoadoutScreen) client.gui.screen()).selected().orElseThrow());
		check(saved.items().keySet().equals(java.util.Set.of(0, 1, 8, 9, LoadoutSlots.HEAD)), "the loadout holds the five items, got " + saved);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// Everything moved around, and dirt where the pickaxe belongs.
		command.accept("clear @a");
		command.accept("item replace entity @a hotbar.0 with minecraft:dirt 64");
		command.accept("item replace entity @a inventory.5 with minecraft:iron_pickaxe");
		command.accept("item replace entity @a inventory.6 with minecraft:iron_sword");
		command.accept("item replace entity @a inventory.7 with minecraft:torch 32");
		command.accept("item replace entity @a inventory.0 with minecraft:bread 10");
		command.accept("item replace entity @a inventory.8 with minecraft:iron_helmet");
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		Map<String, Integer> totalsBefore = context.computeOnClient(LoadoutQuickActionGameTest::playerItems);

		context.runOnClient(client -> action(runtime, LoadoutModule.ACTION_PREFIX + saved.id()).run().run());
		context.waitForScreen(LoadoutScreen.class);
		LoadoutPlanner.Plan plan = context.computeOnClient(client -> ((LoadoutScreen) client.gui.screen()).plan().orElseThrow());
		check(plan.count(LoadoutPlanner.SlotStatus.FILLED) == 4 && plan.count(LoadoutPlanner.SlotStatus.PRESENT) == 1,
				"four positions will be filled, the bread is present, got " + plan.slots());
		context.takeScreenshot("minekit-p5-02-loadout-compare");

		GameTestMouse.clickWidget(context, "minekit.loadout.apply");
		context.waitForScreen(InventoryScreen.class);
		context.waitFor(client -> !runtime.executor().isRunning(), 600);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.takeScreenshot("minekit-p5-03-loadout-applied");
		ContainerActionExecutor.Result result = runtime.executor().lastResult().orElseThrow();
		check(result.outcome() == ContainerActionExecutor.Outcome.COMPLETED && result.itemTotalsUnchanged(), "applied completely, got " + result);
		List<String> positions = context.computeOnClient(client -> positions(client.player.getInventory(), 0, 1, 8, 9, LoadoutSlots.HEAD));
		check(positions.equals(List.of("minecraft:iron_pickaxe x1", "minecraft:iron_sword x1", "minecraft:torch x32", "minecraft:bread x10",
				"minecraft:iron_helmet x1")), "every item is in its position, got " + positions);
		check(context.computeOnClient(LoadoutQuickActionGameTest::playerItems).equals(totalsBefore), "nothing lost or created");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// The grid: a click frees a position, a shift-click takes what is in the inventory there.
		context.runOnClient(client -> loadouts.openManager(saved.id()));
		context.waitForScreen(LoadoutScreen.class);
		LoadoutPlanner.Plan again = context.computeOnClient(client -> ((LoadoutScreen) client.gui.screen()).plan().orElseThrow());
		check(again.refusal().orElse(null) == LoadoutPlanner.Refusal.NOTHING_TO_DO, "applied loadout matches, got " + again.refusal());
		int[] torch = context.computeOnClient(client -> ((LoadoutScreen) client.gui.screen()).positionCenter(8));
		GameTestMouse.clickAt(context, torch[0], torch[1]);
		check(!loadouts.store().current().byId(saved.id()).orElseThrow().items().containsKey(8), "a click removes the torch position");
		context.getInput().holdShift();
		GameTestMouse.clickAt(context, torch[0], torch[1]);
		context.getInput().releaseShift();
		check(loadouts.store().current().byId(saved.id()).orElseThrow().items().get(8).count() == 32, "a shift-click takes the torches again");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void loadoutFromAChest(ClientGameTestContext context, MinekitRuntime runtime, LoadoutModule loadouts, TestServerContext server,
			TestServerConnection connection, BlockPos chest, String where) {
		if (loadouts.store().current().loadouts().isEmpty()) {
			// A fresh client run on the dedicated server: save the loadout first.
			server.runCommand("clear @a");
			server.runCommand("item replace entity @a hotbar.0 with minecraft:iron_pickaxe");
			server.runCommand("item replace entity @a hotbar.1 with minecraft:iron_sword");
			server.runCommand("item replace entity @a hotbar.8 with minecraft:torch 32");
			server.runCommand("item replace entity @a inventory.0 with minecraft:bread 10");
			connection.waitForClientboundPackets();
			context.waitTicks(2);
			context.runOnClient(client -> loadouts.store().update(current -> current.withAdded("Mining",
					loadoutsCapture(client))));
		}
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with minecraft:dirt 64");
		server.runCommand("setblock " + xyz(chest) + " minecraft:chest");
		server.runCommand("item replace block " + xyz(chest) + " container.0 with minecraft:iron_pickaxe");
		server.runCommand("item replace block " + xyz(chest) + " container.1 with minecraft:iron_sword");
		server.runCommand("item replace block " + xyz(chest) + " container.2 with minecraft:torch 16");
		server.runCommand("item replace block " + xyz(chest) + " container.3 with minecraft:torch 40");
		server.runCommand("item replace block " + xyz(chest) + " container.4 with minecraft:bread 10");
		server.runCommand("item replace block " + xyz(chest) + " container.5 with minecraft:iron_helmet");
		server.runCommand("item replace block " + xyz(chest) + " container.6 with minecraft:cobblestone 30");
		connection.waitForClientboundPackets();
		context.waitTicks(2);

		openByRightClick(context, connection, chest);
		int[] opener = context.computeOnClient(client -> loadouts.overlay().orElseThrow().openerCenter());
		GameTestMouse.clickAt(context, opener[0], opener[1]);
		LoadoutPlanner.Plan plan = context.computeOnClient(client -> loadouts.overlay().flatMap(LoadoutOverlay::plan).orElse(null));
		check(plan != null && plan.refusal().isEmpty(), where + ": the ⚒ button opens the panel with a plan, got " + plan);
		Map<String, Integer> taken = new TreeMap<>();
		plan.taken().forEach((key, count) -> taken.merge(key.itemId(), count, Integer::sum));
		check(taken.equals(Map.of("minecraft:iron_pickaxe", 1, "minecraft:iron_sword", 1, "minecraft:torch", 40, "minecraft:bread", 10)),
				where + ": takes the larger torch stack, not the helmet (armor is not in this screen), got " + taken);
		context.takeScreenshot("minekit-p5-04-chest-panel-" + where);

		GameTestMouse.clickWidget(context, "minekit.loadout.take");
		context.waitFor(client -> !runtime.executor().isRunning(), 600);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.takeScreenshot("minekit-p5-05-chest-taken-" + where);
		ContainerActionExecutor.Result result = runtime.executor().lastResult().orElseThrow();
		check(result.outcome() == ContainerActionExecutor.Outcome.COMPLETED && result.itemTotalsUnchanged(), where + ": completed, got " + result);
		Map<String, Integer> chestNow = server.computeOnServer(minecraft -> contents(minecraft, chest));
		check(chestNow.equals(Map.of("minecraft:torch", 16, "minecraft:iron_helmet", 1, "minecraft:cobblestone", 30)),
				where + ": the server's chest only gave, got " + chestNow);
		List<String> positions = context.computeOnClient(client -> positions(client.player.getInventory(), 0, 1, 8, 9));
		check(positions.equals(List.of("minecraft:iron_pickaxe x1", "minecraft:iron_sword x1", "minecraft:torch x40", "minecraft:bread x10")),
				where + ": the items are where the loadout wants them, got " + positions);
		check(context.computeOnClient(client -> playerItems(client).getOrDefault("minecraft:dirt", 0)) == 64, where + ": the dirt moved, not lost");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** Smart Deposit and the loadout panel share the space left of the container: opening one closes the other. */
	private static void panelsShareTheirSpace(ClientGameTestContext context, MinekitRuntime runtime, LoadoutModule loadouts,
			TestServerConnection connection, BlockPos chest) {
		SmartDepositModule deposit = (SmartDepositModule) runtime.modules().module(SmartDepositModule.METADATA.id()).orElseThrow();
		openByRightClick(context, connection, chest);
		int[] opener = context.computeOnClient(client -> loadouts.overlay().orElseThrow().openerCenter());
		GameTestMouse.clickAt(context, opener[0], opener[1]);
		check(context.computeOnClient(client -> loadouts.overlay().orElseThrow().isOpen()), "the loadout panel opens");
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(2);
		check(context.computeOnClient(client -> deposit.overlay().orElseThrow().isOpen()), "G opens Smart Deposit");
		check(!context.computeOnClient(client -> loadouts.overlay().orElseThrow().isOpen()), "which closes the loadout panel");
		context.takeScreenshot("minekit-p5-06-panels");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void dashboardPages(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		context.clickScreenButton("minekit.dashboard.page.loadouts");
		context.waitTick();
		check(context.computeOnClient(client -> ((DashboardScreen) client.gui.screen()).pageId()).equals("loadouts"), "loadouts page opens");
		context.takeScreenshot("minekit-p5-07-dashboard-loadouts");
		context.clickScreenButton("minekit.dashboard.page.quick_actions");
		context.waitTick();
		check(context.computeOnClient(client -> ((DashboardScreen) client.gui.screen()).pageId()).equals("quick_actions"), "quick actions page opens");
		context.takeScreenshot("minekit-p5-08-dashboard-quick-actions");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static Map<Integer, io.github.nordavid.minekit.feature.loadout.LoadoutItem> loadoutsCapture(Minecraft client) {
		Map<Integer, io.github.nordavid.minekit.feature.loadout.LoadoutItem> items = new TreeMap<>();
		Inventory inventory = client.player.getInventory();
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			ItemStack stack = inventory.getItem(index);
			if (!stack.isEmpty()) {
				items.put(index, new io.github.nordavid.minekit.feature.loadout.LoadoutItem(
						BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "", stack.getCount()));
			}
		}
		return items;
	}

	private static QuickAction action(MinekitRuntime runtime, String id) {
		return runtime.quickActions().actions().stream().filter(action -> action.id().equals(id)).findFirst()
				.orElseThrow(() -> new AssertionError("no quick action " + id));
	}

	private static List<String> ids(List<QuickAction> actions) {
		return actions.stream().map(QuickAction::id).toList();
	}

	private static List<String> positions(Inventory inventory, int... indices) {
		List<String> result = new ArrayList<>();
		for (int index : indices) {
			ItemStack stack = inventory.getItem(index);
			result.add(stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
		}
		return result;
	}

	private static Map<String, Integer> playerItems(Minecraft client) {
		Map<String, Integer> items = new TreeMap<>();
		Inventory inventory = client.player.getInventory();
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			ItemStack stack = inventory.getItem(index);
			if (!stack.isEmpty()) {
				items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		return items;
	}

	private static Map<String, Integer> contents(MinecraftServer server, BlockPos pos) {
		Container container = (Container) server.overworld().getBlockEntity(pos);
		Map<String, Integer> items = new TreeMap<>();
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (!stack.isEmpty()) {
				items.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		return items;
	}

	private static void openByRightClick(ClientGameTestContext context, TestServerConnection connection, BlockPos pos) {
		context.getInput().lookAt(pos);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitForScreen(ContainerScreen.class);
		connection.waitForClientboundPackets();
		context.waitFor(client -> client.player.containerMenu.getStateId() != 0, 100);
		context.waitFor(client -> MinekitRuntime.get().containers().current().isPresent(), 40);
		context.waitTicks(2);
	}

	private static String xyz(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("MINEKIT phase 5 gametest failed: " + description);
		}
	}
}
