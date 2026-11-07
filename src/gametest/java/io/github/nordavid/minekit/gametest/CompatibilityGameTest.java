package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.screen.DashboardScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Checks that only run in a compatibility run (`-Pminekit.extraMods=...`): with Mod Menu loaded, Minekit is listed in
 * its mod list and its settings button opens the dashboard. Does nothing when Mod Menu is not loaded.
 */
public final class CompatibilityGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
			return;
		}
		context.waitForScreen(TitleScreen.class);
		context.clickScreenButton("modmenu.title");
		context.waitTicks(5);
		// The search field has the focus when the list opens.
		context.getInput().typeChars("Minekit");
		context.waitTicks(5);
		String screen = context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
		if (!screen.contains("modmenu")) {
			throw new AssertionError("Minekit compatibility gametest failed: Mod Menu's list did not open, got " + screen);
		}
		// The first entry is Minekit: select it to show its details and links.
		GameTestMouse.clickAt(context, 60, 85);
		context.waitTicks(5);
		context.takeScreenshot("minekit-compat-modmenu");
		// The settings button at the top right of the details opens the Minekit dashboard; Escape returns to Mod Menu.
		GameTestMouse.clickAt(context, 413, 57);
		context.waitForScreen(DashboardScreen.class);
		context.waitTicks(2);
		context.takeScreenshot("minekit-compat-modmenu-settings");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitTicks(2);
		String back = context.computeOnClient(client -> client.gui.screen() == null ? "none" : client.gui.screen().getClass().getName());
		if (!back.contains("modmenu")) {
			throw new AssertionError("Minekit compatibility gametest failed: closing the dashboard should return to Mod Menu, got " + back);
		}
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
	}
}
