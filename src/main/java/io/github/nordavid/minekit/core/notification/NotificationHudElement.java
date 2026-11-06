package io.github.nordavid.minekit.core.notification;

import io.github.nordavid.minekit.core.config.HudAnchor;
import io.github.nordavid.minekit.core.config.HudElementConfig;
import io.github.nordavid.minekit.core.hud.MinekitHudElement;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * Shows {@link NotificationService} messages as small stacked cards. The newest card is closest to the anchor.
 * Cards take at most a third of the screen width and wrap to three lines.
 */
public final class NotificationHudElement implements MinekitHudElement {
	public static final String ID = "notifications";

	private static final int MAX_WIDTH = 220;
	private static final int MIN_WIDTH = 100;
	private static final int MAX_LINES = 3;
	private static final int LINE_HEIGHT = 10;
	private static final int VERTICAL_PADDING = 4;
	private static final int TEXT_PADDING = 6;
	private static final int BAR_WIDTH = 2;
	private static final int GAP = 3;

	private record Card(NotificationQueue.Visible<Component> notification, List<String> lines, int width, int height) {
	}

	private final NotificationService notifications;
	/** Cards of the current frame, computed in {@link #measure} so measure and extract agree. */
	private List<Card> frame = List.of();

	public NotificationHudElement(NotificationService notifications) {
		this.notifications = Objects.requireNonNull(notifications, "notifications");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("minekit.hud.notifications");
	}

	@Override
	public HudElementConfig defaultConfig() {
		return new HudElementConfig(true, HudAnchor.TOP_RIGHT, 6, 6);
	}

	@Override
	public boolean drawOverScreens() {
		return true;
	}

	@Override
	public Size measure(Font font, int screenWidth, int screenHeight) {
		if (notifications.isEmpty()) {
			frame = List.of();
			return Size.NONE;
		}
		int maxCardWidth = Math.clamp(screenWidth / 3, MIN_WIDTH, MAX_WIDTH);
		int maxTextWidth = maxCardWidth - BAR_WIDTH - 2 * TEXT_PADDING;
		List<Card> cards = new ArrayList<>();
		int width = 0;
		int height = 0;
		for (NotificationQueue.Visible<Component> visible : notifications.visibleNow()) {
			List<String> lines = UiText.wrap(font, visible.message().getString(), maxTextWidth, MAX_LINES);
			int textWidth = lines.stream().mapToInt(font::width).max().orElse(0);
			Card card = new Card(visible, lines, textWidth + BAR_WIDTH + 2 * TEXT_PADDING, lines.size() * LINE_HEIGHT + 2 * VERTICAL_PADDING);
			cards.add(card);
			width = Math.max(width, card.width());
			height += card.height() + (cards.size() > 1 ? GAP : 0);
		}
		frame = cards;
		return cards.isEmpty() ? Size.NONE : new Size(width, height);
	}

	@Override
	public void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, Size size, HudAnchor anchor) {
		int offset = 0;
		for (Card card : frame) {
			int left = anchor.isRight() ? x + size.width() - card.width() : x;
			int top = anchor.isBottom() ? y + size.height() - offset - card.height() : y + offset;
			offset += card.height() + GAP;
			float opacity = card.notification().opacity();

			graphics.fill(left, top, left + card.width(), top + card.height(), ARGB.multiplyAlpha(MinekitTheme.PANEL, opacity));
			graphics.fill(left, top, left + BAR_WIDTH, top + card.height(),
					ARGB.multiplyAlpha(levelColor(card.notification().level()), opacity));
			if (opacity > 0.1F) {
				int textColor = ARGB.multiplyAlpha(MinekitTheme.TEXT, opacity);
				for (int line = 0; line < card.lines().size(); line++) {
					graphics.text(font, card.lines().get(line), left + BAR_WIDTH + TEXT_PADDING,
							top + VERTICAL_PADDING + 1 + line * LINE_HEIGHT, textColor, false);
				}
			}
		}
	}

	private static int levelColor(NotificationLevel level) {
		return switch (level) {
			case INFO -> MinekitTheme.INFO;
			case SUCCESS -> MinekitTheme.SUCCESS;
			case WARNING -> MinekitTheme.WARNING;
			case ERROR -> MinekitTheme.ERROR;
		};
	}
}
