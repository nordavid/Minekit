package io.github.nordavid.minekit.core.config;

import java.util.Objects;

/**
 * Persisted placement of one HUD element.
 *
 * @param visible whether the element is drawn at all
 * @param anchor  screen corner the offsets are measured from
 * @param offsetX horizontal distance from the anchor corner, in GUI pixels
 * @param offsetY vertical distance from the anchor corner, in GUI pixels
 */
public record HudElementConfig(boolean visible, HudAnchor anchor, int offsetX, int offsetY) {
	/** Upper bound for offsets. Larger values are clamped; the renderer additionally keeps elements on screen. */
	public static final int MAX_OFFSET = 2000;

	public HudElementConfig {
		Objects.requireNonNull(anchor, "anchor");
		offsetX = clampOffset(offsetX);
		offsetY = clampOffset(offsetY);
	}

	public HudElementConfig withVisible(boolean newVisible) {
		return new HudElementConfig(newVisible, anchor, offsetX, offsetY);
	}

	public HudElementConfig withAnchor(HudAnchor newAnchor) {
		return new HudElementConfig(visible, newAnchor, offsetX, offsetY);
	}

	public static int clampOffset(int offset) {
		return Math.clamp(offset, 0, MAX_OFFSET);
	}
}
