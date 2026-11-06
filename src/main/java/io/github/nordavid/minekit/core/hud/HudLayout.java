package io.github.nordavid.minekit.core.hud;

import io.github.nordavid.minekit.core.config.HudAnchor;

/** Places HUD elements on screen. Pure logic, independent of Minecraft. */
public final class HudLayout {
	public record Position(int x, int y) {
	}

	private HudLayout() {
	}

	/**
	 * Computes the top-left corner of an element of the given size. Offsets are measured from the anchor corner
	 * towards the screen centre. The element is kept fully on screen where it fits.
	 */
	public static Position place(HudAnchor anchor, int offsetX, int offsetY, int width, int height, int screenWidth, int screenHeight) {
		int x = anchor.isRight() ? screenWidth - width - offsetX : offsetX;
		int y = anchor.isBottom() ? screenHeight - height - offsetY : offsetY;
		return new Position(Math.clamp(x, 0, Math.max(0, screenWidth - width)), Math.clamp(y, 0, Math.max(0, screenHeight - height)));
	}
}
