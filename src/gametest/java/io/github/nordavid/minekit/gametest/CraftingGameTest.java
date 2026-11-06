package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.feature.crafting.CraftingPlan;
import io.github.nordavid.minekit.feature.crafting.CraftingPlanner;
import io.github.nordavid.minekit.feature.crafting.CraftingScreen;
import io.github.nordavid.minekit.feature.crafting.PlanRequest;
import io.github.nordavid.minekit.feature.crafting.RecipeCatalog;
import io.github.nordavid.minekit.feature.crafting.RecipeIndex;
import io.github.nordavid.minekit.feature.crafting.RecipeOption;
import java.nio.file.Files;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;

/** Loads the real recipe catalog in a world, checks plans against vanilla 26.3 recipes and uses the planner screen. */
public final class CraftingGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			catalogMatchesVanilla(context);
			plannerScreen(context, runtime, world);
		}
	}

	private static void catalogMatchesVanilla(ClientGameTestContext context) {
		RecipeIndex index = context.computeOnClient(RecipeCatalog::load);
		check(index.craftableItems().size() > 500, "catalog knows hundreds of craftable items, got " + index.craftableItems().size());

		CraftingPlan pistons = new CraftingPlanner(index).plan(PlanRequest.of("minecraft:piston", 64), Map.of());
		Map<String, Integer> needed = pistons.materials().stream()
				.collect(Collectors.toMap(CraftingPlan.Material::chosen, CraftingPlan.Material::needed));
		check(needed.equals(Map.of("minecraft:oak_log", 48, "minecraft:cobblestone", 256, "minecraft:raw_iron", 64,
				"minecraft:redstone", 64)), "64 pistons from vanilla recipes, got " + needed);

		RecipeOption ingot = index.recipesFor("minecraft:iron_ingot").getFirst();
		check(ingot.station() == RecipeOption.Station.FURNACE, "iron ingots are smelted by default, got " + ingot);
	}

	private static void plannerScreen(ClientGameTestContext context, MinekitRuntime runtime, TestSingleplayerContext world) {
		world.getServer().runCommand("gamemode survival @a");
		world.getServer().runCommand("clear @a");
		world.getServer().runCommand("give @a minecraft:oak_log 10");
		world.getServer().runCommand("give @a minecraft:redstone 64");
		world.getConnection().waitForClientboundPackets();
		context.waitTicks(2);

		QuickAction open = runtime.quickActions().actions().stream().filter(action -> action.id().equals("open_crafting"))
				.findFirst().orElseThrow();
		context.runOnClient(client -> open.run().run());
		context.waitForScreen(CraftingScreen.class);
		context.waitTick();
		scrollbarDrags(context);
		context.getInput().typeChars("piston");
		context.waitTick();
		context.takeScreenshot("minekit-craft-01-search");

		context.runOnClient(client -> ((CraftingScreen) client.gui.screen()).select("minecraft:piston"));
		context.waitTicks(2);
		String quantityLabel = I18n.get("minekit.crafting.quantity");
		context.runOnClient(client -> {
			Screen screen = client.gui.screen();
			EditBox quantity = screen.children().stream()
					.filter(child -> child instanceof EditBox box && box.getMessage().getString().equals(quantityLabel))
					.map(EditBox.class::cast).findFirst().orElseThrow(() -> new AssertionError("quantity box not found"));
			screen.setFocused(quantity);
		});
		context.getInput().pressKey(InputConstants.KEY_END);
		for (int i = 0; i < 6; i++) {
			context.getInput().pressKey(InputConstants.KEY_BACKSPACE);
		}
		context.getInput().typeChars("64");
		context.waitTicks(2);

		CraftingPlan plan = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).plan());
		check(plan != null, "a plan is shown");
		Map<String, Integer> missing = plan.materials().stream()
				.collect(Collectors.toMap(CraftingPlan.Material::chosen, CraftingPlan.Material::missing));
		check(missing.get("minecraft:oak_log") == 38, "10 logs in the inventory leave 38 missing, got " + missing);
		check(missing.get("minecraft:redstone") == 0, "64 redstone in the inventory cover the need, got " + missing);
		context.takeScreenshot("minekit-craft-02-plan");

		context.clickScreenButton("minekit.crafting.save");
		context.waitTick();
		String saved;
		try {
			saved = Files.readString(runtime.dataDirectory().resolve("crafting-plans.json"));
		} catch (java.io.IOException e) {
			throw new AssertionError("crafting-plans.json not readable", e);
		}
		check(saved.contains("\"target\": \"minecraft:piston\"") && saved.contains("\"quantity\": 64"), "plan saved to disk: " + saved);

		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.getInput().pressKey(runtime.dashboardKey());
		context.waitForScreen(DashboardScreen.class);
		context.clickScreenButton("minekit.dashboard.page.crafting");
		context.waitTick();
		context.takeScreenshot("minekit-craft-03-dashboard-plans");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	/** The list scrollbar follows the real mouse: dragging the thumb, and clicking the track. */
	private static void scrollbarDrags(ClientGameTestContext context) {
		int[] start = context.computeOnClient(client -> {
			ContentPane pane = ((CraftingScreen) client.gui.screen()).resultsPane();
			int[] thumb = pane.thumbCenter();
			return new int[] {thumb[0], thumb[1], pane.scrollOffset(), pane.maxScrollOffset()};
		});
		check(start[3] > 0 && start[2] == 0, "the full item list scrolls and starts at the top, got " + java.util.Arrays.toString(start));

		GameTestMouse.drag(context, start[0], start[1], start[0], start[1] + 60);
		int dragged = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).resultsPane().scrollOffset());
		check(dragged > 0, "dragging the thumb down scrolls, got " + dragged);
		check(!context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).resultsPane().isDraggingScrollbar()),
				"releasing the mouse ends the drag");

		int[] bottom = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).resultsPane().trackBottom());
		GameTestMouse.clickAt(context, bottom[0], bottom[1]);
		int end = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).resultsPane().scrollOffset());
		check(end == start[3], "a click at the end of the track scrolls to the end, got " + end + " of " + start[3]);
		context.takeScreenshot("minekit-craft-00-scrolled");

		int[] thumb = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).resultsPane().thumbCenter());
		GameTestMouse.drag(context, thumb[0], thumb[1], thumb[0], thumb[1] - 1000);
		int top = context.computeOnClient(client -> ((CraftingScreen) client.gui.screen()).resultsPane().scrollOffset());
		check(top == 0, "dragging far up returns to the top, got " + top);
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("MINEKIT crafting gametest failed: " + description);
		}
	}
}
