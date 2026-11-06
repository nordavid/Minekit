package io.github.nordavid.minekit.core.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nordavid.minekit.core.config.HudAnchor;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
	private static final int SCREEN_WIDTH = 480;
	private static final int SCREEN_HEIGHT = 270;

	private static HudLayout.Position place(HudAnchor anchor, int offsetX, int offsetY, int width, int height) {
		return HudLayout.place(anchor, offsetX, offsetY, width, height, SCREEN_WIDTH, SCREEN_HEIGHT);
	}

	@Test
	void offsetsAreMeasuredFromTheAnchorCorner() {
		assertEquals(new HudLayout.Position(6, 6), place(HudAnchor.TOP_LEFT, 6, 6, 100, 20));
		assertEquals(new HudLayout.Position(374, 6), place(HudAnchor.TOP_RIGHT, 6, 6, 100, 20));
		assertEquals(new HudLayout.Position(6, 244), place(HudAnchor.BOTTOM_LEFT, 6, 6, 100, 20));
		assertEquals(new HudLayout.Position(374, 244), place(HudAnchor.BOTTOM_RIGHT, 6, 6, 100, 20));
	}

	@Test
	void elementIsKeptOnScreen() {
		assertEquals(new HudLayout.Position(380, 250), place(HudAnchor.TOP_LEFT, 1_000, 1_000, 100, 20));
		assertEquals(new HudLayout.Position(0, 0), place(HudAnchor.BOTTOM_RIGHT, 1_000, 1_000, 100, 20));
	}

	@Test
	void elementLargerThanScreenStartsAtOrigin() {
		assertEquals(new HudLayout.Position(0, 0), place(HudAnchor.BOTTOM_RIGHT, 0, 0, 600, 400));
	}

	@Test
	void anchorsCycleThroughAllCorners() {
		assertEquals(HudAnchor.TOP_RIGHT, HudAnchor.TOP_LEFT.next());
		assertEquals(HudAnchor.TOP_LEFT, HudAnchor.BOTTOM_RIGHT.next());
	}
}
