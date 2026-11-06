package io.github.nordavid.minekit.core.screen.widget;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Labelled on/off switch.
 *
 * <p>The change handler receives the requested value and returns the value that actually applies, so the switch
 * never shows a state the backing service refused (for example a module that failed to enable).
 */
public class ToggleButton extends Button {
	private static final int TRACK_WIDTH = 20;
	private static final int TRACK_HEIGHT = 10;

	private final Predicate<Boolean> onChange;
	private boolean value;

	public ToggleButton(int x, int y, int width, int height, Component label, boolean initialValue, Predicate<Boolean> onChange) {
		super(x, y, width, height, label, button -> {
		}, DEFAULT_NARRATION);
		this.value = initialValue;
		this.onChange = Objects.requireNonNull(onChange, "onChange");
	}

	public boolean value() {
		return value;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		value = onChange.test(!value);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (active && isHoveredOrFocused()) {
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_HOVER);
		}

		int trackRight = getRight() - 4;
		int trackLeft = trackRight - TRACK_WIDTH;
		int trackTop = getY() + (getHeight() - TRACK_HEIGHT) / 2;
		int trackColor = !active ? MinekitTheme.SURFACE_DISABLED : value ? MinekitTheme.ACCENT : MinekitTheme.BORDER;
		graphics.fill(trackLeft, trackTop, trackRight, trackTop + TRACK_HEIGHT, trackColor);
		int knobLeft = value ? trackRight - TRACK_HEIGHT + 1 : trackLeft + 1;
		int knobColor = active ? MinekitTheme.TEXT : MinekitTheme.TEXT_DISABLED;
		graphics.fill(knobLeft, trackTop + 1, knobLeft + TRACK_HEIGHT - 2, trackTop + TRACK_HEIGHT - 1, knobColor);

		int textColor = active ? MinekitTheme.TEXT : MinekitTheme.TEXT_DISABLED;
		UiText.draw(graphics, Minecraft.getInstance().font, getMessage(), getX() + 4, getY() + (getHeight() - 8) / 2,
				trackLeft - getX() - 10, textColor);
	}

	@Override
	protected MutableComponent createNarrationMessage() {
		return CommonComponents.optionStatus(getMessage(), value);
	}
}
