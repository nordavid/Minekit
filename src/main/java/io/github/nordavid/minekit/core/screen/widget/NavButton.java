package io.github.nordavid.minekit.core.screen.widget;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Sidebar navigation entry. The selected entry is marked with an accent bar. */
public class NavButton extends Button {
	private final BooleanSupplier selected;

	public NavButton(int x, int y, int width, int height, Component message, BooleanSupplier selected, Runnable onSelect) {
		super(x, y, width, height, message, button -> onSelect.run(), DEFAULT_NARRATION);
		this.selected = Objects.requireNonNull(selected, "selected");
		Objects.requireNonNull(onSelect, "onSelect");
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		boolean isSelected = selected.getAsBoolean();
		if (isSelected) {
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_SELECTED);
			graphics.fill(getX(), getY(), getX() + 2, getBottom(), MinekitTheme.ACCENT);
		} else if (isHoveredOrFocused()) {
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_HOVER);
		}
		int color = isSelected ? MinekitTheme.TEXT : MinekitTheme.TEXT_MUTED;
		UiText.draw(graphics, Minecraft.getInstance().font, getMessage(), getX() + 8, getY() + (getHeight() - 8) / 2,
				getWidth() - 12, color);
	}

	@Override
	public void updateWidgetNarration(NarrationElementOutput output) {
		super.updateWidgetNarration(output);
		if (selected.getAsBoolean()) {
			output.add(NarratedElementType.HINT, Component.translatable("minekit.narration.selected"));
		}
	}
}
