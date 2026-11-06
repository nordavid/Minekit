package io.github.nordavid.minekit.core.screen.widget;

/**
 * Shared visual tokens for all MINEKIT screens and HUD elements. Colors are ARGB.
 *
 * <p>Restrained dark palette with a single accent, as described in PROJECT_PLAN.md §5.3. Change values here rather
 * than in individual screens so the UI stays consistent.
 */
public final class MinekitTheme {
	public static final int PANEL = 0xF2171A1F;
	public static final int PANEL_BORDER = 0xFF2B3038;
	public static final int SIDEBAR = 0xFF121418;
	public static final int SURFACE = 0xFF20242B;
	public static final int SURFACE_HOVER = 0xFF2A2F38;
	public static final int SURFACE_SELECTED = 0xFF1F332E;
	public static final int SURFACE_DISABLED = 0xFF1A1D22;
	public static final int BORDER = 0xFF343A44;
	public static final int ACCENT = 0xFF4CC9A6;

	public static final int TEXT = 0xFFE8EAED;
	public static final int TEXT_MUTED = 0xFF9BA3AE;
	public static final int TEXT_DISABLED = 0xFF5D646E;

	public static final int INFO = 0xFF6FA8DC;
	public static final int SUCCESS = 0xFF6BCB8B;
	public static final int WARNING = 0xFFE8B75A;
	public static final int ERROR = 0xFFE5716F;

	/** Inner padding of panels, in GUI pixels. */
	public static final int PADDING = 8;
	/** Height of interactive rows (buttons, toggles). */
	public static final int ROW_HEIGHT = 18;
	/** Height of one line of body text, including spacing. */
	public static final int LINE_HEIGHT = 11;

	private MinekitTheme() {
	}
}
