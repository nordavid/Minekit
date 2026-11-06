package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.action.ContainerActionExecutor;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import io.github.nordavid.minekit.feature.inventory.HotbarLayout;
import io.github.nordavid.minekit.feature.inventory.HotbarRule;
import io.github.nordavid.minekit.feature.inventory.InventorySettings;
import io.github.nordavid.minekit.feature.inventory.SmartInventoryModule;
import io.github.nordavid.minekit.feature.inventory.SortPlanner;
import io.github.nordavid.minekit.feature.inventory.SortPreviewScreen;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsModule;
import io.github.nordavid.minekit.feature.quickactions.QuickActionsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Sorts a real inventory in a real client: single player, a dedicated server (real network round trips), creative
 * mode refusal, and an item arriving mid-sort. Checks the exact final layout, that the hotbar is untouched and that
 * no item is lost or duplicated.
 */
public final class SmartInventoryGameTest implements FabricClientGameTest {
	/** Main inventory after sorting the {@link #messyInventory} with the default "General" profile. */
	private static final List<String> SORTED_MAIN = List.of(
			"minecraft:iron_pickaxe x1", // tools
			"minecraft:bread x5", // food
			"minecraft:diamond x3", "minecraft:stick x10", // resources, by id
			"minecraft:cobblestone x64", "minecraft:cobblestone x16", "minecraft:dirt x64"); // blocks: 30 + 50 merged

	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		// The core test may leave Quick Actions off on purpose; this test needs it.
		context.runOnClient(client -> runtime.modules().setEnabled(QuickActionsModule.METADATA.id(), true));

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			Consumer<String> command = world.getServer()::runCommand;

			sortWithPreview(context, runtime, command, connection, "singleplayer");
			sortedInventoryNeedsNothing(context);
			creativeModeIsRefused(context, runtime, command, connection);
			itemArrivingMidSortStopsSafely(context, runtime, command, connection);
			dashboardInventoryPage(context, runtime);
			hotbarLayoutChosenWithTheMouse(context, runtime, command, connection);
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestDedicatedServerConnection connection = server.connect()) {
			connection.waitForChunksRender();
			sortWithPreview(context, runtime, server::runCommand, connection, "server");
		}
	}

	private static void messyInventory(Consumer<String> command, TestServerConnection connection) {
		command.accept("gamemode survival @a");
		command.accept("clear @a");
		command.accept("item replace entity @a inventory.0 with minecraft:cobblestone 30");
		command.accept("item replace entity @a inventory.3 with minecraft:bread 5");
		command.accept("item replace entity @a inventory.5 with minecraft:stick 10");
		command.accept("item replace entity @a inventory.8 with minecraft:cobblestone 50");
		command.accept("item replace entity @a inventory.12 with minecraft:iron_pickaxe 1");
		command.accept("item replace entity @a inventory.20 with minecraft:dirt 64");
		command.accept("item replace entity @a inventory.26 with minecraft:diamond 3");
		command.accept("item replace entity @a hotbar.0 with minecraft:torch 16");
		command.accept("item replace entity @a hotbar.4 with minecraft:iron_sword 1");
		connection.waitForClientboundPackets();
	}

	private static void sortWithPreview(ClientGameTestContext context, MinekitRuntime runtime, Consumer<String> command,
			TestServerConnection connection, String where) {
		messyInventory(command, connection);
		context.waitTicks(2);
		List<String> hotbarBefore = inventory(context, 0, 9);
		Map<String, Integer> totalsBefore = totals(context);

		context.getInput().pressKey(InputConstants.KEY_R);
		context.waitForScreen(SortPreviewScreen.class);
		SortPlanner.Result plan = context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).plan());
		check(plan instanceof SortPlanner.Planned, where + ": preview has a plan, got " + plan);
		context.takeScreenshot("minekit-inv-01-preview-" + where);

		context.clickScreenButton("minekit.sort.confirm");
		context.waitForScreen(InventoryScreen.class);
		context.waitFor(client -> !runtime.executor().isRunning(), 600);
		context.takeScreenshot("minekit-inv-02-sorted-" + where);

		ContainerActionExecutor.Result result = runtime.executor().lastResult().orElseThrow();
		check(result.outcome() == ContainerActionExecutor.Outcome.COMPLETED, where + ": sort completed, got " + result);
		check(result.itemTotalsUnchanged(), where + ": executor reports unchanged totals");
		connection.waitForClientboundPackets();
		context.waitTicks(2);

		List<String> main = inventory(context, 9, 36);
		check(main.subList(0, SORTED_MAIN.size()).equals(SORTED_MAIN), where + ": sorted order, got " + main);
		check(main.subList(SORTED_MAIN.size(), main.size()).stream().allMatch("empty"::equals), where + ": rest empty, got " + main);
		check(inventory(context, 0, 9).equals(hotbarBefore), where + ": hotbar unchanged");
		check(totals(context).equals(totalsBefore), where + ": no item lost or duplicated");
		check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), where + ": cursor empty");

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void sortedInventoryNeedsNothing(ClientGameTestContext context) {
		context.getInput().pressKey(InputConstants.KEY_R);
		context.waitForScreen(SortPreviewScreen.class);
		int changed = context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).changedSlots());
		check(changed == 0, "sorting a sorted inventory changes nothing, got " + changed);
		context.takeScreenshot("minekit-inv-03-already-sorted");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void creativeModeIsRefused(ClientGameTestContext context, MinekitRuntime runtime, Consumer<String> command,
			TestServerConnection connection) {
		command.accept("gamemode creative @a");
		connection.waitForClientboundPackets();
		context.waitTicks(2);

		QuickAction sort = runtime.quickActions().actions().stream().filter(action -> action.id().equals("sort_inventory"))
				.findFirst().orElseThrow();
		Optional<String> reason = context.computeOnClient(client -> sort.unavailableReason().get().map(component -> component.getString()));
		check(reason.isPresent(), "sorting is unavailable in creative mode");

		context.getInput().pressKey(InputConstants.KEY_R);
		context.waitTicks(3);
		check(context.computeOnClient(client -> client.gui.screen()) == null, "the sort key opens nothing in creative mode");

		context.getInput().pressKey(InputConstants.KEY_J);
		context.waitForScreen(QuickActionsScreen.class);
		context.getInput().typeChars("sort");
		context.waitTick();
		context.takeScreenshot("minekit-inv-04-creative-unavailable");
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.waitTick();
		check(context.computeOnClient(client -> client.gui.screen()) instanceof QuickActionsScreen, "an unavailable action does not run");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		command.accept("gamemode survival @a");
		connection.waitForClientboundPackets();
	}

	private static void itemArrivingMidSortStopsSafely(ClientGameTestContext context, MinekitRuntime runtime, Consumer<String> command,
			TestServerConnection connection) {
		command.accept("clear @a");
		String[] items = {"minecraft:dirt", "minecraft:stick", "minecraft:cobblestone", "minecraft:bread", "minecraft:diamond"};
		for (int slot = 0; slot < 24; slot++) {
			command.accept("item replace entity @a inventory." + slot + " with " + items[slot % items.length] + " " + (slot + 1));
		}
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		Map<String, Integer> totalsBefore = totals(context);

		context.getInput().pressKey(InputConstants.KEY_R);
		context.waitForScreen(SortPreviewScreen.class);
		context.clickScreenButton("minekit.sort.confirm");
		context.waitForScreen(InventoryScreen.class);
		context.waitTicks(8);
		check(runtime.executor().isRunning(), "sort still running when the extra item arrives");
		command.accept("item replace entity @a inventory.26 with minecraft:emerald 1");
		context.waitFor(client -> !runtime.executor().isRunning(), 600);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.takeScreenshot("minekit-inv-05-stopped");

		ContainerActionExecutor.Result result = runtime.executor().lastResult().orElseThrow();
		check(result.outcome() == ContainerActionExecutor.Outcome.STOPPED, "an unexpected item stops the sort, got " + result);
		check(context.computeOnClient(client -> client.player.containerMenu.getCarried().isEmpty()), "nothing left on the cursor");
		Map<String, Integer> expected = new TreeMap<>(totalsBefore);
		expected.merge("minecraft:emerald", 1, Integer::sum);
		check(totals(context).equals(expected), "only the new emerald was added, nothing lost or duplicated: " + totals(context));

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static void dashboardInventoryPage(ClientGameTestContext context, MinekitRuntime runtime) {
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		context.clickScreenButton("minekit.dashboard.page.inventory");
		context.waitTick();
		check(context.computeOnClient(client -> ((DashboardScreen) client.gui.screen()).pageId()).equals("inventory"), "inventory page opens");
		context.takeScreenshot("minekit-inv-06-dashboard-page");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/**
	 * The player picks what goes in two hotbar slots with the real mouse (box, then picker entry), sorts, and the
	 * hotbar follows the rules while the other slots stay as they are.
	 */
	private static void hotbarLayoutChosenWithTheMouse(ClientGameTestContext context, MinekitRuntime runtime, Consumer<String> command,
			TestServerConnection connection) {
		SmartInventoryModule inventory = (SmartInventoryModule) runtime.modules().module(SmartInventoryModule.METADATA.id()).orElseThrow();
		check(inventory.settings().current().hotbar().keepsAll(), "by default the hotbar stays as it is");
		messyInventory(command, connection);
		context.waitTicks(2);
		Map<String, Integer> totalsBefore = totals(context);

		context.getInput().pressKey(InputConstants.KEY_R);
		context.waitForScreen(SortPreviewScreen.class);
		chooseHotbarRule(context, 0, HotbarRule.of(HotbarRule.Kind.PICKAXE));
		context.takeScreenshot("minekit-inv-07-hotbar-picker");
		clickHotbarChoice(context, HotbarRule.of(HotbarRule.Kind.PICKAXE));
		chooseHotbarRule(context, 8, HotbarRule.of(HotbarRule.Kind.FOOD));
		clickHotbarChoice(context, HotbarRule.of(HotbarRule.Kind.FOOD));
		HotbarLayout layout = inventory.settings().current().hotbar();
		check(layout.rule(0).kind() == HotbarRule.Kind.PICKAXE && layout.rule(8).kind() == HotbarRule.Kind.FOOD
				&& layout.rule(4).kind() == HotbarRule.Kind.KEEP, "two slots got rules, the rest stays as it is, got " + layout);

		// Escape closes only the picker, not the preview.
		chooseHotbarRule(context, 2, HotbarRule.EMPTY);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTick();
		check(context.computeOnClient(client -> client.gui.screen() instanceof SortPreviewScreen preview && preview.hotbarPickerSlot() < 0),
				"Escape closes the picker and keeps the preview open");
		context.takeScreenshot("minekit-inv-08-hotbar-preview");

		GameTestMouse.clickWidget(context, "minekit.sort.confirm");
		context.waitForScreen(InventoryScreen.class);
		context.waitFor(client -> !runtime.executor().isRunning(), 600);
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		context.takeScreenshot("minekit-inv-09-hotbar-sorted");

		ContainerActionExecutor.Result result = runtime.executor().lastResult().orElseThrow();
		check(result.outcome() == ContainerActionExecutor.Outcome.COMPLETED, "hotbar sort completed, got " + result);
		List<String> hotbar = inventory(context, 0, 9);
		check(hotbar.get(0).equals("minecraft:iron_pickaxe x1"), "slot 1 holds the pickaxe, got " + hotbar);
		check(hotbar.get(8).equals("minecraft:bread x5"), "slot 9 holds the food, got " + hotbar);
		check(hotbar.get(4).equals("minecraft:iron_sword x1"), "the slot to keep is untouched, got " + hotbar);
		check(inventory(context, 9, 36).contains("minecraft:torch x16"), "the torch from slot 1 moved to the main inventory");
		check(totals(context).equals(totalsBefore), "no item lost or duplicated");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		context.clickScreenButton("minekit.dashboard.page.inventory");
		context.waitTick();
		context.takeScreenshot("minekit-inv-10-dashboard-hotbar");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.runOnClient(client -> inventory.settings().update(settings -> settings.withHotbar(HotbarLayout.KEEP_ALL)));
	}

	/** Clicks the box under a hotbar slot and checks that the picker opens and offers {@code expected}. */
	private static void chooseHotbarRule(ClientGameTestContext context, int slot, HotbarRule expected) {
		int[] box = context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).hotbarBoxCenter(slot));
		GameTestMouse.clickAt(context, box[0], box[1]);
		int open = context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).hotbarPickerSlot());
		check(open == slot, "a click on the box opens the picker for slot " + (slot + 1) + ", got " + open);
		check(context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).hotbarChoiceCenter(expected)) != null,
				"the picker offers " + expected);
	}

	private static void clickHotbarChoice(ClientGameTestContext context, HotbarRule rule) {
		int[] entry = context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).hotbarChoiceCenter(rule));
		GameTestMouse.clickAt(context, entry[0], entry[1]);
		check(context.computeOnClient(client -> ((SortPreviewScreen) client.gui.screen()).hotbarPickerSlot()) < 0, "choosing closes the picker");
	}

	/** "id xCount" or "empty" for player inventory indices [from, to): 0-8 hotbar, 9-35 main inventory. */
	private static List<String> inventory(ClientGameTestContext context, int from, int to) {
		return context.computeOnClient(client -> {
			Inventory inventory = client.player.getInventory();
			List<String> result = new ArrayList<>();
			for (int i = from; i < to; i++) {
				ItemStack stack = inventory.getItem(i);
				result.add(stack.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + " x" + stack.getCount());
			}
			return result;
		});
	}

	/** Item counts over the whole player inventory menu, including the cursor. */
	private static Map<String, Integer> totals(ClientGameTestContext context) {
		return context.computeOnClient(client -> totals(client));
	}

	private static Map<String, Integer> totals(Minecraft client) {
		Map<String, Integer> totals = new TreeMap<>();
		client.player.inventoryMenu.slots.forEach(slot -> add(totals, slot.getItem()));
		add(totals, client.player.inventoryMenu.getCarried());
		return totals;
	}

	private static void add(Map<String, Integer> totals, ItemStack stack) {
		if (!stack.isEmpty()) {
			totals.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
		}
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("MINEKIT inventory gametest failed: " + description);
		}
	}
}
