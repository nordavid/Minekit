package io.github.nordavid.minekit.gametest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Clicks and drags with the real mouse, like a player.
 *
 * <p>Fabric's {@code clickScreenButton} presses a button directly and skips the screen's own click handling, so it
 * cannot notice a widget that another one covers. Minekit widgets inside other screens (Smart Deposit's panel) are
 * therefore clicked through the mouse here.
 */
final class GameTestMouse {
	private GameTestMouse() {
	}

	/** Window pixel position of a GUI position. */
	static double[] toWindow(Minecraft client, double guiX, double guiY) {
		Window window = client.getWindow();
		return new double[] {guiX * window.getScreenWidth() / window.getGuiScaledWidth(), guiY * window.getScreenHeight() / window.getGuiScaledHeight()};
	}

	/** Moves the cursor to a GUI position. */
	static void moveTo(ClientGameTestContext context, double guiX, double guiY) {
		double[] cursor = context.computeOnClient(client -> toWindow(client, guiX, guiY));
		context.getInput().setCursorPos(cursor[0], cursor[1]);
	}

	/** Left click at a GUI position. */
	static void clickAt(ClientGameTestContext context, double guiX, double guiY) {
		moveTo(context, guiX, guiY);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}

	/** Left click on the center of the visible widget of the current screen whose message has this translation key. */
	static void clickWidget(ClientGameTestContext context, String translationKey) {
		double[] center = context.computeOnClient(client -> {
			for (AbstractWidget widget : Screens.getWidgets(client.gui.screen())) {
				if (widget.visible && widget.getMessage().getContents() instanceof TranslatableContents contents
						&& contents.getKey().equals(translationKey)) {
					return new double[] {widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0};
				}
			}
			throw new AssertionError("no visible widget '" + translationKey + "' on " + client.gui.screen());
		});
		clickAt(context, center[0], center[1]);
	}

	/** Presses the left button at one GUI position, moves to another in steps, and releases there. */
	static void drag(ClientGameTestContext context, double fromX, double fromY, double toX, double toY) {
		moveTo(context, fromX, fromY);
		context.getInput().holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTick();
		int steps = 5;
		for (int step = 1; step <= steps; step++) {
			moveTo(context, fromX + (toX - fromX) * step / steps, fromY + (toY - fromY) * step / steps);
			context.waitTick();
		}
		context.getInput().releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		context.waitTicks(2);
	}
}
