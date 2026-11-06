package io.github.nordavid.minekit.core.screen.widget;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** List entry with an item icon and a name, e.g. a search result. The selected entry gets an accent bar. */
public class ItemButton extends Button {
	private final ItemStack icon;
	private final BooleanSupplier selected;
	private @Nullable Component detail;

	public ItemButton(int x, int y, int width, int height, ItemStack icon, Component name, BooleanSupplier selected, Runnable onPress) {
		super(x, y, width, height, name, button -> onPress.run(), DEFAULT_NARRATION);
		this.icon = Objects.requireNonNull(icon, "icon");
		this.selected = Objects.requireNonNull(selected, "selected");
	}

	/** Short muted text on the right, e.g. a count. */
	public ItemButton withDetail(@Nullable Component text) {
		this.detail = text;
		return this;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (selected.getAsBoolean()) {
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_SELECTED);
			graphics.fill(getX(), getY(), getX() + 2, getBottom(), MinekitTheme.ACCENT);
		} else if (isHoveredOrFocused()) {
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_HOVER);
		}
		graphics.item(icon, getX() + 4, getY() + (getHeight() - 16) / 2);
		Font font = Minecraft.getInstance().font;
		int textY = getY() + (getHeight() - 8) / 2;
		int detailWidth = 0;
		if (detail != null) {
			detailWidth = Math.min(font.width(detail), (getWidth() - 28) / 2);
			UiText.drawRightAligned(graphics, font, detail, getRight() - 4, textY, detailWidth, MinekitTheme.TEXT_MUTED);
			detailWidth += 6;
		}
		UiText.draw(graphics, font, getMessage(), getX() + 24, textY, getWidth() - 28 - detailWidth, MinekitTheme.TEXT);
	}
}
