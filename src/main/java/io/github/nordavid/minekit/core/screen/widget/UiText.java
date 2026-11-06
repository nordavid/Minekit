package io.github.nordavid.minekit.core.screen.widget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** Text helpers shared by Minekit widgets. Minekit draws flat text without drop shadow. */
public final class UiText {
	private static final String ELLIPSIS = "…";

	private UiText() {
	}

	/** Shortens {@code text} with an ellipsis so it fits into {@code maxWidth}. */
	public static String fit(Font font, String text, int maxWidth) {
		if (maxWidth <= 0) {
			return "";
		}
		if (font.width(text) <= maxWidth) {
			return text;
		}
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(ELLIPSIS))) + ELLIPSIS;
	}

	/**
	 * Wraps {@code text} into at most {@code maxLines} lines of {@code maxWidth}; if it needs more, the last line ends
	 * with an ellipsis.
	 */
	public static List<String> wrap(Font font, String text, int maxWidth, int maxLines) {
		List<FormattedText> lines = font.getSplitter().splitLines(text, Math.max(1, maxWidth), Style.EMPTY);
		List<String> result = new ArrayList<>(Math.min(lines.size(), maxLines));
		for (int i = 0; i < lines.size() && i < maxLines; i++) {
			result.add(lines.get(i).getString());
		}
		if (lines.size() > maxLines && maxLines > 0) {
			StringBuilder rest = new StringBuilder();
			for (int i = maxLines - 1; i < lines.size(); i++) {
				rest.append(lines.get(i).getString()).append(' ');
			}
			result.set(maxLines - 1, fit(font, rest.toString().strip(), maxWidth));
		}
		return result;
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxWidth, int color) {
		graphics.text(font, fit(font, text.getString(), maxWidth), x, y, color, false);
	}

	public static void drawCentered(GuiGraphicsExtractor graphics, Font font, Component text, int centerX, int y, int maxWidth, int color) {
		String fitted = fit(font, text.getString(), maxWidth);
		graphics.text(font, fitted, centerX - font.width(fitted) / 2, y, color, false);
	}

	public static void drawRightAligned(GuiGraphicsExtractor graphics, Font font, Component text, int right, int y, int maxWidth, int color) {
		String fitted = fit(font, text.getString(), maxWidth);
		graphics.text(font, fitted, right - font.width(fitted), y, color, false);
	}
}
