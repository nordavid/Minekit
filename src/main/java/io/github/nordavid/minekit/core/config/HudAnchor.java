package io.github.nordavid.minekit.core.config;

/** Screen corner a HUD element is positioned relative to. */
public enum HudAnchor {
	TOP_LEFT,
	TOP_RIGHT,
	BOTTOM_LEFT,
	BOTTOM_RIGHT;

	/** The next anchor in declaration order, wrapping around. Used to cycle through anchors in the settings UI. */
	public HudAnchor next() {
		HudAnchor[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	public boolean isRight() {
		return this == TOP_RIGHT || this == BOTTOM_RIGHT;
	}

	public boolean isBottom() {
		return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
	}
}
