package io.github.nordavid.minekit.core.screen.widget;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Vertically scrolling column of headings, text, item lines and widgets inside a fixed rectangle.
 *
 * <p>The owning screen registers widgets with itself (so they get input) and passes them to {@link #widget} or
 * {@link #line}; the pane only positions them. Widgets that are not fully inside the visible area are hidden, so
 * nothing outside the clipped area can be clicked. Rebuild the rows in the screen's {@code init()} (or after a
 * change); the scroll position is kept across rebuilds.
 *
 * <p>The scrollbar can be dragged and clicked; the owning screen forwards {@link #mouseClicked}, {@link #mouseDragged}
 * and {@link #mouseReleased} for that, and the mouse wheel via {@link #mouseScrolled}.
 */
public final class ContentPane {
	private static final int HEADING_HEIGHT = 18;
	private static final int STATUS_HEIGHT = 13;
	private static final int ICON_SIZE = 16;
	private static final int WIDGET_GAP = 2;
	private static final int SCROLL_STEP = 14;
	private static final int SCROLLBAR_WIDTH = 4;
	/** Width of the area that grabs the scrollbar: wider than the bar itself, so it is easy to hit. */
	private static final int SCROLLBAR_GRAB_WIDTH = 8;
	private static final int MIN_THUMB_HEIGHT = 12;

	private sealed interface Row permits Heading, TextLine, Line, Spacer {
		int height();
	}

	private record Heading(Component text) implements Row {
		@Override
		public int height() {
			return HEADING_HEIGHT;
		}
	}

	private record TextLine(FormattedCharSequence line, int color) implements Row {
		@Override
		public int height() {
			return MinekitTheme.LINE_HEIGHT;
		}
	}

	/**
	 * A line with an optional item icon and label on the left, an optional status text on the right and optional
	 * widgets right-aligned after it. Without icon, label and status, a single widget spans the full width.
	 */
	private record Line(@Nullable ItemStack icon, @Nullable Component label, @Nullable Component status, int statusColor,
			List<AbstractWidget> widgets) implements Row {
		@Override
		public int height() {
			int content = Math.max(icon != null ? ICON_SIZE + 2 : 0, label != null || status != null ? STATUS_HEIGHT : 0);
			for (AbstractWidget widget : widgets) {
				content = Math.max(content, widget.getHeight());
			}
			return content + WIDGET_GAP;
		}
	}

	private record Spacer(int height) implements Row {
	}

	private final Font font;
	private final List<Row> rows = new ArrayList<>();
	private final List<Integer> rowTops = new ArrayList<>();
	private int x;
	private int y;
	private int width;
	private int height;
	private int scroll;
	private int contentHeight;
	/** While the thumb is dragged: where inside the thumb it was grabbed. Otherwise -1. */
	private int dragOffset = -1;

	public ContentPane(Font font) {
		this.font = Objects.requireNonNull(font, "font");
	}

	/** Clears all rows and sets the visible area. Keeps the scroll position. */
	public void reset(int x, int y, int width, int height) {
		this.x = x;
		this.y = y;
		this.width = Math.max(0, width);
		this.height = Math.max(0, height);
		rows.clear();
	}

	public void scrollToTop() {
		scroll = 0;
	}

	/** Usable width for rows, leaving room for the scrollbar and its grab area. */
	public int width() {
		return Math.max(0, width - SCROLLBAR_GRAB_WIDTH);
	}

	public void heading(Component text) {
		rows.add(new Heading(text));
	}

	/** Adds text, wrapped to the pane width. */
	public void text(Component text, int color) {
		for (FormattedCharSequence line : font.split(text, width())) {
			rows.add(new TextLine(line, color));
		}
	}

	/** Adds a line with a label on the left and a status value on the right. */
	public void status(Component label, Component status, int statusColor) {
		rows.add(new Line(null, label, status, statusColor, List.of()));
	}

	/** Adds a widget already registered with the screen as a full-width row. */
	public <T extends AbstractWidget> T widget(T widget) {
		widget.setWidth(width());
		rows.add(new Line(null, null, null, 0, List.of(widget)));
		return widget;
	}

	/** Adds a label with widgets (already registered with the screen) right-aligned on the same line. */
	public void labeled(Component label, AbstractWidget... widgets) {
		rows.add(new Line(null, label, null, 0, List.of(widgets)));
	}

	/**
	 * Adds a general line: optional item icon, label, status text (right side, before the widgets) and widgets
	 * already registered with the screen.
	 */
	public void line(@Nullable ItemStack icon, @Nullable Component label, @Nullable Component status, int statusColor,
			AbstractWidget... widgets) {
		rows.add(new Line(icon, label, status, statusColor, List.of(widgets)));
	}

	public void spacer(int pixels) {
		rows.add(new Spacer(pixels));
	}

	/** Positions all rows for the current scroll offset. Call after adding rows and after scrolling. */
	public void layout() {
		rowTops.clear();
		int cursor = 0;
		for (Row row : rows) {
			rowTops.add(cursor);
			cursor += row.height();
		}
		contentHeight = cursor;
		scroll = Math.clamp(scroll, 0, maxScroll());

		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i) instanceof Line line && !line.widgets().isEmpty()) {
				int top = y + rowTops.get(i) - scroll;
				boolean fullWidth = line.icon() == null && line.label() == null && line.status() == null;
				int widgetX = fullWidth ? x : x + width() - totalWidth(line.widgets());
				int rowHeight = line.height() - WIDGET_GAP;
				for (AbstractWidget widget : line.widgets()) {
					int widgetTop = top + (rowHeight - widget.getHeight()) / 2;
					widget.setX(widgetX);
					widget.setY(widgetTop);
					widget.visible = widgetTop >= y && widgetTop + widget.getHeight() <= y + height;
					widgetX += widget.getWidth() + WIDGET_GAP;
				}
			}
		}
	}

	/** Handles mouse wheel input inside the pane. */
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!contains(mouseX, mouseY) || maxScroll() == 0) {
			return false;
		}
		scroll = Math.clamp(scroll - Math.round(amount * SCROLL_STEP), 0, maxScroll());
		layout();
		return true;
	}

	/**
	 * Starts dragging the scrollbar when it is pressed. A press on the thumb keeps the grab point; a press elsewhere on
	 * the track first centers the thumb there. Returns whether the pane used the press.
	 */
	public boolean mouseClicked(MouseButtonEvent event) {
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !isOverScrollbar(event.x(), event.y())) {
			return false;
		}
		int thumbY = thumbY();
		int thumbHeight = thumbHeight();
		dragOffset = event.y() >= thumbY && event.y() < thumbY + thumbHeight ? (int) event.y() - thumbY : thumbHeight / 2;
		dragTo(event.y());
		return true;
	}

	/** Moves the thumb with the mouse while it is dragged. */
	public boolean mouseDragged(MouseButtonEvent event) {
		if (dragOffset < 0) {
			return false;
		}
		dragTo(event.y());
		return true;
	}

	/** Ends dragging. Returns whether a drag ended. */
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragOffset < 0) {
			return false;
		}
		dragOffset = -1;
		return true;
	}

	public boolean isDraggingScrollbar() {
		return dragOffset >= 0;
	}

	/** Current scroll offset in pixels, for tests. */
	public int scrollOffset() {
		return scroll;
	}

	/** Largest scroll offset in pixels (0 if everything fits), for tests. */
	public int maxScrollOffset() {
		return maxScroll();
	}

	/** Center of the scrollbar thumb in GUI pixels (x, y), for tests. */
	public int[] thumbCenter() {
		return new int[] {x + width - SCROLLBAR_WIDTH / 2, thumbY() + thumbHeight() / 2};
	}

	/** Bottom end of the scrollbar track in GUI pixels (x, y), for tests. */
	public int[] trackBottom() {
		return new int[] {x + width - SCROLLBAR_WIDTH / 2, y + height - 1};
	}

	private boolean isOverScrollbar(double mouseX, double mouseY) {
		return maxScroll() > 0 && mouseX >= x + width - SCROLLBAR_GRAB_WIDTH && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	private void dragTo(double mouseY) {
		int track = height - thumbHeight();
		if (track <= 0) {
			return;
		}
		double position = Math.clamp((mouseY - dragOffset - y) / track, 0.0, 1.0);
		scroll = (int) Math.round(position * maxScroll());
		layout();
	}

	private int thumbHeight() {
		return contentHeight <= 0 ? height : Math.min(height, Math.max(MIN_THUMB_HEIGHT, height * height / contentHeight));
	}

	private int thumbY() {
		int max = maxScroll();
		return max == 0 ? y : y + (height - thumbHeight()) * scroll / max;
	}

	public boolean contains(double mouseX, double mouseY) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** Draws everything except widgets, which the screen draws itself. */
	public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.enableScissor(x, y, x + width, y + height);
		int rowWidth = width();
		for (int i = 0; i < rows.size(); i++) {
			int top = y + rowTops.get(i) - scroll;
			Row row = rows.get(i);
			if (top + row.height() < y || top > y + height) {
				continue;
			}
			switch (row) {
				case Heading(Component text) -> {
					UiText.draw(graphics, font, text, x, top + 6, rowWidth, MinekitTheme.ACCENT);
					graphics.horizontalLine(x, x + rowWidth - 1, top + HEADING_HEIGHT - 2, MinekitTheme.PANEL_BORDER);
				}
				case TextLine(FormattedCharSequence line, int color) -> graphics.text(font, line, x, top + 1, color, false);
				case Line line -> extractLine(graphics, line, top, rowWidth);
				case Spacer ignored -> {
				}
			}
		}
		graphics.disableScissor();

		if (maxScroll() > 0) {
			int trackX = x + width - SCROLLBAR_WIDTH;
			int thumbY = thumbY();
			int thumbColor = isDraggingScrollbar() ? MinekitTheme.ACCENT
					: isOverScrollbar(mouseX, mouseY) ? MinekitTheme.TEXT_MUTED : MinekitTheme.BORDER;
			graphics.fill(trackX, y, trackX + SCROLLBAR_WIDTH, y + height, MinekitTheme.SURFACE);
			graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight(), thumbColor);
		}
	}

	private void extractLine(GuiGraphicsExtractor graphics, Line line, int top, int rowWidth) {
		int rowHeight = line.height() - WIDGET_GAP;
		int textY = top + (rowHeight - 8) / 2;
		int left = x;
		if (line.icon() != null) {
			graphics.item(line.icon(), x, top + (rowHeight - ICON_SIZE) / 2);
			left += ICON_SIZE + 4;
		} else if (line.label() != null && !line.widgets().isEmpty()) {
			left += 4;
		}
		int right = x + rowWidth - (line.widgets().isEmpty() ? 0 : totalWidth(line.widgets()) + 6);
		int statusWidth = 0;
		if (line.status() != null) {
			statusWidth = Math.min(font.width(line.status()), (right - left) / 2);
			UiText.drawRightAligned(graphics, font, line.status(), right, textY, statusWidth, line.statusColor());
		}
		if (line.label() != null) {
			UiText.draw(graphics, font, line.label(), left, textY, right - left - statusWidth - 6, MinekitTheme.TEXT);
		}
	}

	private static int totalWidth(List<AbstractWidget> widgets) {
		int total = 0;
		for (AbstractWidget widget : widgets) {
			total += widget.getWidth() + WIDGET_GAP;
		}
		return Math.max(0, total - WIDGET_GAP);
	}

	private int maxScroll() {
		return Math.max(0, contentHeight - height);
	}
}
