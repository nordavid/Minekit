package io.github.nordavid.minekit.core.hud;

import io.github.nordavid.minekit.core.config.ConfigManager;
import io.github.nordavid.minekit.core.config.HudElementConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Draws all MINEKIT HUD elements according to their visibility and position settings.
 *
 * <p>Hooked into the game twice: as one Fabric HUD element (in-game HUD, hidden with F1) and after every screen is
 * drawn (for elements that {@linkplain MinekitHudElement#drawOverScreens() draw over screens}). An element that throws
 * while drawing is switched off for the rest of the session instead of crashing the game or flooding the log.
 */
public final class HudManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	private final ConfigManager config;
	private final List<MinekitHudElement> elements = new ArrayList<>();
	private final Set<String> brokenElements = new HashSet<>();

	public HudManager(ConfigManager config) {
		this.config = Objects.requireNonNull(config, "config");
	}

	public void register(MinekitHudElement element) {
		Objects.requireNonNull(element, "element");
		if (elements.stream().anyMatch(existing -> existing.id().equals(element.id()))) {
			throw new IllegalArgumentException("Duplicate HUD element id: " + element.id());
		}
		elements.add(element);
	}

	public List<MinekitHudElement> elements() {
		return Collections.unmodifiableList(elements);
	}

	public HudElementConfig settings(MinekitHudElement element) {
		return config.current().hudElement(element.id()).orElse(element.defaultConfig());
	}

	/** Changes and saves an element's settings. Returns {@code false} if saving failed. */
	public boolean updateSettings(MinekitHudElement element, UnaryOperator<HudElementConfig> change) {
		HudElementConfig next = Objects.requireNonNull(change.apply(settings(element)), "change returned null");
		return config.update(current -> current.withHudElement(element.id(), next));
	}

	/** Fabric HUD callback. */
	public void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.hud.isHidden()) {
			return;
		}
		boolean screenOpen = minecraft.gui.screen() != null;
		// Elements that draw over screens are drawn by extractOverScreen while a screen is open, never twice.
		extractAll(graphics, minecraft.font, element -> !(screenOpen && element.drawOverScreens()));
	}

	/** Called after any screen has been drawn. */
	public void extractOverScreen(GuiGraphicsExtractor graphics) {
		graphics.nextStratum();
		extractAll(graphics, Minecraft.getInstance().font, MinekitHudElement::drawOverScreens);
	}

	private void extractAll(GuiGraphicsExtractor graphics, Font font, Predicate<MinekitHudElement> filter) {
		for (MinekitHudElement element : elements) {
			if (brokenElements.contains(element.id()) || !filter.test(element)) {
				continue;
			}
			HudElementConfig settings = settings(element);
			if (!settings.visible()) {
				continue;
			}
			try {
				MinekitHudElement.Size size = element.measure(font, graphics.guiWidth(), graphics.guiHeight());
				if (size.isEmpty()) {
					continue;
				}
				HudLayout.Position position = HudLayout.place(settings.anchor(), settings.offsetX(), settings.offsetY(),
						size.width(), size.height(), graphics.guiWidth(), graphics.guiHeight());
				element.extract(graphics, font, position.x(), position.y(), size, settings.anchor());
			} catch (RuntimeException e) {
				brokenElements.add(element.id());
				LOGGER.error("HUD element '{}' failed and is disabled until the next start", element.id(), e);
			}
		}
	}
}
