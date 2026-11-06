package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import io.github.nordavid.minekit.feature.crafting.CraftingAssistantModule;
import io.github.nordavid.minekit.feature.crafting.CraftingScreen;
import io.github.nordavid.minekit.feature.crafting.PlanRequest;
import io.github.nordavid.minekit.feature.resources.ResourceSession;
import io.github.nordavid.minekit.feature.resources.ResourceTracker;
import io.github.nordavid.minekit.feature.resources.ResourceTrackerModule;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Runs a resource session in a real world: items received with no screen open count as collected, items removed as
 * used, changes while the inventory is open as unclear; goals come from the Crafting Assistant; the session ends into
 * the history and is saved.
 */
public final class ResourceTrackerGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		MinekitRuntime runtime = MinekitRuntime.get();
		ResourceTrackerModule module = (ResourceTrackerModule) runtime.modules().module(ResourceTrackerModule.METADATA.id()).orElseThrow();

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			Consumer<String> command = world.getServer()::runCommand;
			command.accept("gamemode survival @a");
			command.accept("clear @a");
			connection.waitForClientboundPackets();

			QuickAction toggle = runtime.quickActions().actions().stream().filter(action -> action.id().equals("resource_session"))
					.findFirst().orElseThrow();
			context.runOnClient(client -> toggle.run().run());
			context.waitTicks(25);
			check(session(context, module) != null && session(context, module).isRunning(), "session started");

			command.accept("give @a minecraft:cobblestone 32");
			command.accept("give @a minecraft:raw_iron 5");
			connection.waitForClientboundPackets();
			context.waitTicks(25);
			command.accept("clear @a minecraft:cobblestone 2");
			connection.waitForClientboundPackets();
			context.waitTicks(25);

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitForScreen(InventoryScreen.class);
			command.accept("give @a minecraft:oak_log 4");
			connection.waitForClientboundPackets();
			context.waitTicks(25);
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
			context.waitTicks(25);

			ResourceSession counted = session(context, module);
			check(counted.collected().equals(Map.of("minecraft:cobblestone", 32L, "minecraft:raw_iron", 5L)),
					"collected with no screen open, got " + counted.collected());
			check(counted.consumed().equals(Map.of("minecraft:cobblestone", 2L)), "used with no screen open, got " + counted.consumed());
			check(counted.unclearIn().equals(Map.of("minecraft:oak_log", 4L)), "unclear while the inventory was open, got " + counted.unclearIn());

			goalsFromCraftingPlan(context, runtime);
			ResourceSession withGoals = session(context, module);
			check(withGoals.goals().containsKey("minecraft:redstone") && withGoals.goals().get("minecraft:redstone") == 2,
					"missing redstone for 2 pistons became a goal, got " + withGoals.goals());
			check(!withGoals.goals().containsKey("minecraft:raw_iron"), "raw iron in the inventory is not missing, got " + withGoals.goals());
			context.takeScreenshot("minekit-res-01-hud");

			context.getInput().pressKey(runtime.dashboardKey());
			context.waitForScreen(DashboardScreen.class);
			context.clickScreenButton("minekit.dashboard.page.resources");
			context.waitTick();
			context.takeScreenshot("minekit-res-02-dashboard");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);

			context.runOnClient(client -> toggle.run().run());
			context.waitTick();
			ResourceTracker tracker = context.computeOnClient(client -> module.tracker());
			check(tracker.current() == null && tracker.history().size() == 1, "session ended into the history");
			String saved;
			try {
				saved = Files.readString(runtime.dataDirectory().resolve("resource-sessions.json"));
			} catch (java.io.IOException e) {
				throw new AssertionError("resource-sessions.json not readable", e);
			}
			check(saved.contains("\"minecraft:raw_iron\": 5"), "history saved to disk: " + saved);
		}
	}

	/** Plans 2 pistons and hands what is missing to the session with the "Add as goals" button. */
	private static void goalsFromCraftingPlan(ClientGameTestContext context, MinekitRuntime runtime) {
		CraftingAssistantModule crafting = (CraftingAssistantModule) runtime.modules().module(CraftingAssistantModule.METADATA.id()).orElseThrow();
		context.runOnClient(client -> crafting.openPlanner(PlanRequest.of("minecraft:piston", 2)));
		context.waitForScreen(CraftingScreen.class);
		context.waitTicks(2);
		context.clickScreenButton("minekit.crafting.as_goals");
		context.waitTicks(2);
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
	}

	private static ResourceSession session(ClientGameTestContext context, ResourceTrackerModule module) {
		return context.computeOnClient(client -> module.tracker().current());
	}

	private static void check(boolean condition, String description) {
		if (!condition) {
			throw new AssertionError("MINEKIT resource tracker gametest failed: " + description);
		}
	}
}
