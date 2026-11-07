package io.github.nordavid.minekit.feature.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The boxes under the sort preview's hotbar, which show and change what each hotbar slot holds after sorting, and the
 * picker that opens from them.
 *
 * <p>The screen adds the boxes as widgets, gives mouse and key input to the editor first (an open picker takes all of
 * it), and draws the picker after its widgets, so the picker is on top.
 */
final class HotbarLayoutEditor {
	private static final int ENTRY_WIDTH = 112;
	private static final int ENTRY_HEIGHT = 18;
	private static final int COLUMNS = 2;
	private static final int GAP = 4;
	private static final int PADDING = 6;
	private static final int DESCRIPTION_LINES = 2;
	/** Dims an item icon to show "goes here" rather than "is here". */
	private static final int FADE = 0x9020242B;

	private final Supplier<HotbarLayout> layout;
	private final BiConsumer<Integer, HotbarRule> onChange;
	private final IntFunction<@Nullable ItemStack> itemNow;
	private final List<RuleBox> boxes = new ArrayList<>();

	private int pickerSlot = -1;
	private List<HotbarRule> choices = List.of();
	private int selected;
	private int pickerX;
	private int pickerY;
	private int pickerWidth;
	private int pickerHeight;

	/**
	 * @param layout   the current layout
	 * @param onChange called with a hotbar slot (0-8) and its new rule
	 * @param itemNow  the item in a hotbar slot (0-8) right now, or {@code null}; offered as "this item"
	 */
	HotbarLayoutEditor(Supplier<HotbarLayout> layout, BiConsumer<Integer, HotbarRule> onChange, IntFunction<@Nullable ItemStack> itemNow) {
		this.layout = Objects.requireNonNull(layout, "layout");
		this.onChange = Objects.requireNonNull(onChange, "onChange");
		this.itemNow = Objects.requireNonNull(itemNow, "itemNow");
	}

	/** Creates the nine boxes in a row starting at {@code x}, {@code spacing} pixels apart. The screen adds them. */
	List<? extends Button> createBoxes(int x, int y, int size, int spacing) {
		closePicker();
		boxes.clear();
		for (int slot = 0; slot < HotbarLayout.SIZE; slot++) {
			boxes.add(new RuleBox(slot, x + slot * spacing, y, size));
		}
		return List.copyOf(boxes);
	}

	boolean isPickerOpen() {
		return pickerSlot >= 0;
	}

	/** Hotbar slot (0-8) the picker is open for, or -1. For tests. */
	int pickerSlot() {
		return pickerSlot;
	}

	void openPicker(int slot) {
		Minecraft minecraft = Minecraft.getInstance();
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int screenHeight = minecraft.getWindow().getGuiScaledHeight();
		pickerSlot = slot;
		choices = choicesFor(slot);
		selected = Math.max(0, choices.indexOf(layout.get().rule(slot)));
		int rows = (choices.size() + COLUMNS - 1) / COLUMNS;
		pickerWidth = 2 * PADDING + COLUMNS * ENTRY_WIDTH + (COLUMNS - 1) * GAP;
		pickerHeight = 2 * PADDING + MinekitTheme.LINE_HEIGHT + 2 + rows * ENTRY_HEIGHT + 4 + DESCRIPTION_LINES * MinekitTheme.LINE_HEIGHT;
		RuleBox box = boxes.get(slot);
		pickerX = Math.clamp(box.getX() + box.getWidth() / 2 - pickerWidth / 2, 4, Math.max(4, screenWidth - pickerWidth - 4));
		int above = box.getY() - pickerHeight - 4;
		int below = box.getBottom() + 4;
		pickerY = above >= 4 ? above : Math.clamp(below, 4, Math.max(4, screenHeight - pickerHeight - 4));
	}

	void closePicker() {
		pickerSlot = -1;
	}

	/** The rules the picker offers for a slot: every kind, plus "this item" for what is in the slot now. */
	private List<HotbarRule> choicesFor(int slot) {
		List<HotbarRule> result = new ArrayList<>();
		for (HotbarRule.Kind kind : HotbarRule.Kind.values()) {
			if (kind == HotbarRule.Kind.ITEM) {
				HotbarRule current = layout.get().rule(slot);
				if (current.kind() == HotbarRule.Kind.ITEM) {
					result.add(current);
				}
				ItemStack now = itemNow.apply(slot);
				if (now != null && !now.isEmpty()) {
					HotbarRule thisItem = HotbarRule.item(BuiltInRegistries.ITEM.getKey(now.getItem()).toString());
					if (!result.contains(thisItem)) {
						result.add(thisItem);
					}
				}
			} else {
				result.add(HotbarRule.of(kind));
			}
		}
		return result;
	}

	private void choose(HotbarRule rule) {
		int slot = pickerSlot;
		closePicker();
		onChange.accept(slot, rule);
	}

	/** While the picker is open, takes every click: a choice applies, anything else closes the picker. */
	boolean mouseClicked(MouseButtonEvent event) {
		if (!isPickerOpen()) {
			return false;
		}
		int index = entryAt(event.x(), event.y());
		if (index >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			choose(choices.get(index));
		} else if (!insidePicker(event.x(), event.y())) {
			closePicker();
		}
		return true;
	}

	/** While the picker is open: arrow keys move, Enter or Space chooses, Escape closes. */
	boolean keyPressed(KeyEvent event) {
		if (!isPickerOpen()) {
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_ESCAPE) {
			closePicker();
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
			choose(choices.get(selected));
		} else if (key == InputConstants.KEY_LEFT) {
			selected = Math.max(0, selected - 1);
		} else if (key == InputConstants.KEY_RIGHT) {
			selected = Math.min(choices.size() - 1, selected + 1);
		} else if (key == InputConstants.KEY_UP) {
			selected = Math.max(0, selected - COLUMNS);
		} else if (key == InputConstants.KEY_DOWN) {
			selected = Math.min(choices.size() - 1, selected + COLUMNS);
		}
		// Keys do nothing else while the picker is open.
		return true;
	}

	private boolean insidePicker(double x, double y) {
		return x >= pickerX && x < pickerX + pickerWidth && y >= pickerY && y < pickerY + pickerHeight;
	}

	private int entryX(int index) {
		return pickerX + PADDING + (index % COLUMNS) * (ENTRY_WIDTH + GAP);
	}

	private int entryY(int index) {
		return pickerY + PADDING + MinekitTheme.LINE_HEIGHT + 2 + (index / COLUMNS) * ENTRY_HEIGHT;
	}

	private int entryAt(double x, double y) {
		for (int i = 0; i < choices.size(); i++) {
			int left = entryX(i);
			int top = entryY(i);
			if (x >= left && x < left + ENTRY_WIDTH && y >= top && y < top + ENTRY_HEIGHT - 1) {
				return i;
			}
		}
		return -1;
	}

	/** Center of the picker entry for {@code rule} in GUI pixels, or {@code null} if it is not offered. For tests. */
	int @Nullable [] entryCenter(HotbarRule rule) {
		int index = choices.indexOf(rule);
		return !isPickerOpen() || index < 0 ? null : new int[] {entryX(index) + ENTRY_WIDTH / 2, entryY(index) + ENTRY_HEIGHT / 2};
	}

	/** Draws the open picker, or the tooltip of the hovered box. Call after the screen's widgets. */
	void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
		if (!isPickerOpen()) {
			for (RuleBox box : boxes) {
				if (box.isHovered()) {
					HotbarRule rule = layout.get().rule(box.slot);
					graphics.setComponentTooltipForNextFrame(font, List.of(
							Component.translatable("minekit.hotbar.box_tooltip", box.slot + 1, HotbarRuleIcons.name(rule)),
							HotbarRuleIcons.description(rule).copy().withStyle(ChatFormatting.GRAY),
							Component.translatable("minekit.hotbar.box_hint").withStyle(ChatFormatting.DARK_GRAY)), mouseX, mouseY);
				}
			}
			return;
		}
		graphics.fill(pickerX, pickerY, pickerX + pickerWidth, pickerY + pickerHeight, 0xFF171A1F);
		graphics.outline(pickerX, pickerY, pickerWidth, pickerHeight, MinekitTheme.ACCENT);
		UiText.draw(graphics, font, Component.translatable("minekit.hotbar.picker_title", pickerSlot + 1), pickerX + PADDING, pickerY + PADDING,
				pickerWidth - 2 * PADDING, MinekitTheme.ACCENT);
		HotbarRule current = layout.get().rule(pickerSlot);
		int hovered = entryAt(mouseX, mouseY);
		if (hovered >= 0) {
			selected = hovered;
		}
		for (int i = 0; i < choices.size(); i++) {
			HotbarRule rule = choices.get(i);
			int left = entryX(i);
			int top = entryY(i);
			boolean highlighted = i == selected;
			graphics.fill(left, top, left + ENTRY_WIDTH, top + ENTRY_HEIGHT - 1, highlighted ? MinekitTheme.SURFACE_HOVER : MinekitTheme.SURFACE);
			if (rule.equals(current)) {
				graphics.outline(left, top, ENTRY_WIDTH, ENTRY_HEIGHT - 1, MinekitTheme.ACCENT);
			} else if (highlighted) {
				graphics.outline(left, top, ENTRY_WIDTH, ENTRY_HEIGHT - 1, MinekitTheme.BORDER);
			}
			extractRuleIcon(graphics, font, rule, left, top, false);
			UiText.draw(graphics, font, HotbarRuleIcons.name(rule), left + 20, top + 4, ENTRY_WIDTH - 24, MinekitTheme.TEXT);
		}
		List<String> description = UiText.wrap(font, HotbarRuleIcons.description(choices.get(selected)).getString(), pickerWidth - 2 * PADDING,
				DESCRIPTION_LINES);
		int y = pickerY + pickerHeight - PADDING - DESCRIPTION_LINES * MinekitTheme.LINE_HEIGHT;
		for (String line : description) {
			graphics.text(font, line, pickerX + PADDING, y, MinekitTheme.TEXT_MUTED, false);
			y += MinekitTheme.LINE_HEIGHT;
		}
	}

	/** Draws a rule's icon (16 x 16) or symbol at {@code x, y}; faded icons mean "goes here". */
	static void extractRuleIcon(GuiGraphicsExtractor graphics, Font font, HotbarRule rule, int x, int y, boolean faded) {
		ItemStack icon = HotbarRuleIcons.icon(rule);
		if (icon != null && !icon.isEmpty()) {
			graphics.item(icon, x, y);
			if (faded && rule.kind() != HotbarRule.Kind.ITEM) {
				graphics.fill(x, y, x + 16, y + 16, FADE);
			}
			return;
		}
		String symbol = HotbarRuleIcons.symbol(rule);
		graphics.text(font, symbol, x + 8 - font.width(symbol) / 2, y + 4, MinekitTheme.TEXT_MUTED, false);
	}

	/** One box under a hotbar slot. Its tooltip is drawn by the editor so it never shows above the open picker. */
	private final class RuleBox extends Button {
		private final int slot;

		RuleBox(int slot, int x, int y, int size) {
			super(x, y, size, size, Component.empty(), button -> {
			}, DEFAULT_NARRATION);
			this.slot = slot;
		}

		@Override
		public Component getMessage() {
			return Component.translatable("minekit.hotbar.box_tooltip", slot + 1, HotbarRuleIcons.name(layout.get().rule(slot)));
		}

		@Override
		public void onPress(net.minecraft.client.input.InputWithModifiers input) {
			openPicker(slot);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			HotbarRule rule = layout.get().rule(slot);
			boolean highlighted = active && (isHoveredOrFocused() || pickerSlot == slot);
			int background = rule.kind() == HotbarRule.Kind.KEEP ? MinekitTheme.SURFACE_DISABLED
					: highlighted ? MinekitTheme.SURFACE_HOVER : MinekitTheme.SURFACE;
			graphics.fill(getX(), getY(), getRight(), getBottom(), background);
			int border = highlighted ? MinekitTheme.ACCENT : rule.kind() == HotbarRule.Kind.ITEM ? MinekitTheme.TEXT_MUTED : MinekitTheme.BORDER;
			graphics.outline(getX(), getY(), getWidth(), getHeight(), border);
			extractRuleIcon(graphics, Minecraft.getInstance().font, rule, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2, true);
		}
	}
}
