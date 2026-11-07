package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Checks that only run in a compatibility run (`-Pminekit.extraMods=...`): with Mod Menu loaded, Minekit is listed in
 * its mod list. Does nothing when Mod Menu is not loaded.
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
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(TitleScreen.class);
	}
}
