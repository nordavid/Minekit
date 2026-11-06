package io.github.nordavid.minekit.core.screen.widget;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Dark, flat push button in the MINEKIT style.
 *
 * <p>MINEKIT widgets extend vanilla {@link Button} (not just {@code AbstractButton}) so that tools which look for
 * buttons, such as the Fabric client gametest API, treat them like vanilla buttons.
 */
public class FlatButton extends Button {
	public FlatButton(int x, int y, int width, int height, Component message, Consumer<FlatButton> onPress) {
		super(x, y, width, height, message, button -> onPress.accept((FlatButton) button), DEFAULT_NARRATION);
		Objects.requireNonNull(onPress, "onPress");
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		boolean highlighted = active && isHoveredOrFocused();
		int background = !active ? MinekitTheme.SURFACE_DISABLED : highlighted ? MinekitTheme.SURFACE_HOVER : MinekitTheme.SURFACE;
		graphics.fill(getX(), getY(), getRight(), getBottom(), background);
		graphics.outline(getX(), getY(), getWidth(), getHeight(), highlighted ? MinekitTheme.ACCENT : MinekitTheme.BORDER);

		Font font = Minecraft.getInstance().font;
		int textColor = active ? MinekitTheme.TEXT : MinekitTheme.TEXT_DISABLED;
		UiText.drawCentered(graphics, font, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2,
				getWidth() - 8, textColor);
	}
}
