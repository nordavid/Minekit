package io.github.nordavid.minekit.core.hud;

import io.github.nordavid.minekit.core.config.HudAnchor;
import io.github.nordavid.minekit.core.config.HudElementConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A Minekit HUD element. {@link HudManager} decides whether and where it is drawn, based on the player's settings.
 *
 * <p>Implementations must be cheap: {@link #measure} and {@link #extract} run every frame.
 */
public interface MinekitHudElement {
	/** Size of the element for this frame. */
	record Size(int width, int height) {
		public static final Size NONE = new Size(0, 0);

		public boolean isEmpty() {
			return width <= 0 || height <= 0;
		}
	}

	/** Stable id, used as the config key. */
	String id();

	Component displayName();

	HudElementConfig defaultConfig();

	/** Whether the element is also drawn on top of open screens (for example notifications). */
	default boolean drawOverScreens() {
		return false;
	}

	/**
	 * Current size, or {@link Size#NONE} if there is nothing to draw. The screen size lets elements stay
	 * proportionate on small windows and large GUI scales.
	 */
	Size measure(Font font, int screenWidth, int screenHeight);

	/**
	 * Draws the element with its top-left corner at ({@code x}, {@code y}).
	 *
	 * @param anchor where the element is anchored; content can align towards that edge
	 */
	void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, Size size, HudAnchor anchor);
}
