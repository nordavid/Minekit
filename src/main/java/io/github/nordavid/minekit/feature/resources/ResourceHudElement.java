package io.github.nordavid.minekit.feature.resources;

import io.github.nordavid.minekit.core.config.HudAnchor;
import io.github.nordavid.minekit.core.config.HudElementConfig;
import io.github.nordavid.minekit.core.hud.MinekitHudElement;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Small HUD panel for the open resource session: duration and either goal progress or the most collected items.
 * Hidden while no session is open; movable and hideable in the dashboard settings like every Minekit HUD element.
 */
final class ResourceHudElement implements MinekitHudElement {
	static final String ID = "resource_session";
	private static final int MAX_LINES = 4;
	private static final int LINE_HEIGHT = 10;
	private static final int PADDING = 4;
	private static final int MAX_WIDTH = 170;

	private record Line(Component text, int color) {
	}

	private final ResourceTrackerModule module;
	private List<Line> frame = List.of();

	ResourceHudElement(ResourceTrackerModule module) {
		this.module = Objects.requireNonNull(module, "module");
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("minekit.hud.resource_session");
	}

	@Override
	public HudElementConfig defaultConfig() {
		return new HudElementConfig(true, HudAnchor.TOP_LEFT, 6, 6);
	}

	@Override
	public Size measure(Font font, int screenWidth, int screenHeight) {
		ResourceSession session = module.isEnabled() ? module.tracker().current() : null;
		if (session == null) {
			frame = List.of();
			return Size.NONE;
		}
		List<Line> lines = new ArrayList<>();
		String duration = Durations.format(session.durationMillis(System.currentTimeMillis()));
		lines.add(new Line(Component.literal((session.isRunning() ? "⏱ " : "⏸ ") + duration + "  ").append(session.name()),
				session.isRunning() ? MinekitTheme.ACCENT : MinekitTheme.TEXT_MUTED));
		if (!session.goals().isEmpty()) {
			for (Map.Entry<String, Integer> goal : session.goals().entrySet()) {
				if (lines.size() > MAX_LINES) {
					break;
				}
				long have = session.collected(goal.getKey());
				lines.add(new Line(Component.literal(Math.min(have, goal.getValue()) + "/" + goal.getValue() + " ").append(ItemIds.name(goal.getKey())),
						have >= goal.getValue() ? MinekitTheme.SUCCESS : MinekitTheme.TEXT));
			}
		} else {
			session.collected().entrySet().stream()
					.sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
					.limit(MAX_LINES - 1)
					.forEach(entry -> lines.add(new Line(Component.literal("+" + entry.getValue() + " ").append(ItemIds.name(entry.getKey())),
							MinekitTheme.TEXT)));
		}
		frame = lines;
		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, font.width(line.text()));
		}
		return new Size(Math.min(MAX_WIDTH, width) + 2 * PADDING, lines.size() * LINE_HEIGHT + 2 * PADDING - 1);
	}

	@Override
	public void extract(GuiGraphicsExtractor graphics, Font font, int x, int y, Size size, HudAnchor anchor) {
		graphics.fill(x, y, x + size.width(), y + size.height(), MinekitTheme.PANEL);
		for (int i = 0; i < frame.size(); i++) {
			UiText.draw(graphics, font, frame.get(i).text(), x + PADDING, y + PADDING + i * LINE_HEIGHT, size.width() - 2 * PADDING,
					frame.get(i).color());
		}
	}
}
