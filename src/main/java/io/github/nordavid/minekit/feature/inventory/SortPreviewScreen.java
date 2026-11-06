package io.github.nordavid.minekit.feature.inventory;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.MenuReader;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Shows the inventory now and after sorting, side by side, and starts the sort only when the player confirms.
 * Clicking an item toggles it as a favorite (favorites are sorted first), which updates the preview immediately. The
 * boxes under the hotbar of the result choose what each hotbar slot holds ({@link HotbarLayoutEditor}). Nothing in
 * the inventory changes while this screen is open.
 */
public final class SortPreviewScreen extends Screen {
	private static final int SLOT = 18;
	private static final int GRID_WIDTH = 9 * SLOT;
	private static final int HOTBAR_GAP = 4;
	private static final int GRID_HEIGHT = 4 * SLOT + HOTBAR_GAP;
	/** The hotbar rule boxes under the result's hotbar. */
	private static final int RULE_ROW = SLOT + 2;
	private static final int TEXT_LINES = 3;
	private static final int[][] ROWS = {
			{9, 10, 11, 12, 13, 14, 15, 16, 17},
			{18, 19, 20, 21, 22, 23, 24, 25, 26},
			{27, 28, 29, 30, 31, 32, 33, 34, 35},
			{36, 37, 38, 39, 40, 41, 42, 43, 44}};

	private final SmartInventoryModule module;
	private final HotbarLayoutEditor hotbarEditor;
	private ItemKeyIndex keys;
	private ContainerState shown;
	private SortPlanner.Result plan;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int beforeX;
	private int afterX;
	private int beforeY;
	private int afterY;
	private @Nullable FlatButton sortButton;
	private @Nullable FlatButton profileButton;

	public SortPreviewScreen(SmartInventoryModule module) {
		super(Component.translatable("minekit.sort.title"));
		this.module = Objects.requireNonNull(module, "module");
		this.hotbarEditor = new HotbarLayoutEditor(() -> module.settings().current().hotbar(), this::setHotbarRule, slot -> {
			SlotStack stack = shown == null ? null : shown.slot(InventorySlots.HOTBAR_START + slot);
			return stack == null ? null : keys.displayStack(stack.key());
		});
	}

	/** Center of the rule box under hotbar slot {@code slot} (0-8) in GUI pixels. For tests. */
	public int[] hotbarBoxCenter(int slot) {
		return new int[] {afterX + slot * SLOT + (SLOT - 1) / 2, afterY + GRID_HEIGHT + 2 + (SLOT - 1) / 2};
	}

	/** Hotbar slot (0-8) whose rule picker is open, or -1. For tests. */
	public int hotbarPickerSlot() {
		return hotbarEditor.pickerSlot();
	}

	/** Center of the picker entry for {@code rule} in GUI pixels, or {@code null} if the picker does not offer it. For tests. */
	public int @Nullable [] hotbarChoiceCenter(HotbarRule rule) {
		return hotbarEditor.entryCenter(rule);
	}

	/** The plan currently shown. Exposed for tests. */
	public SortPlanner.Result plan() {
		return plan;
	}

	/** Number of slots the shown plan changes, or -1 if sorting is refused. Exposed for tests. */
	public int changedSlots() {
		return plan instanceof SortPlanner.Planned planned ? planned.changedSlots(shown) : -1;
	}

	@Override
	protected void init() {
		refreshPlan();
		boolean sideBySide = width >= 2 * GRID_WIDTH + 48;
		panelWidth = sideBySide ? 2 * GRID_WIDTH + 36 : GRID_WIDTH + 24;
		int gridsHeight = sideBySide ? GRID_HEIGHT : 2 * GRID_HEIGHT + 16;
		panelHeight = 46 + gridsHeight + RULE_ROW + 6 + TEXT_LINES * MinekitTheme.LINE_HEIGHT + 4 + MinekitTheme.ROW_HEIGHT + 8;
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(4, (height - panelHeight) / 2);
		beforeX = panelX + 12;
		beforeY = panelY + 46;
		afterX = sideBySide ? beforeX + GRID_WIDTH + 12 : beforeX;
		afterY = sideBySide ? beforeY : beforeY + GRID_HEIGHT + 16;
		for (var box : hotbarEditor.createBoxes(afterX, afterY + GRID_HEIGHT + 2, SLOT - 1, SLOT)) {
			addRenderableWidget(box);
		}

		profileButton = addRenderableWidget(new FlatButton(panelX + panelWidth - 132, panelY + 8, 120, MinekitTheme.ROW_HEIGHT,
				profileLabel(), button -> cycleProfile()));
		int buttonY = panelY + panelHeight - MinekitTheme.ROW_HEIGHT - 8;
		addRenderableWidget(new FlatButton(panelX + panelWidth - 12 - 90 - 4 - 90, buttonY, 90, MinekitTheme.ROW_HEIGHT,
				CommonComponents.GUI_CANCEL, button -> onClose()));
		sortButton = addRenderableWidget(new FlatButton(panelX + panelWidth - 12 - 90, buttonY, 90, MinekitTheme.ROW_HEIGHT,
				Component.translatable("minekit.sort.confirm"), button -> confirm()));
		updateButtons();
	}

	private void refreshPlan() {
		LocalPlayer player = Objects.requireNonNull(minecraft.player);
		keys = module.newKeyIndex();
		shown = MenuReader.read(player.inventoryMenu, keys);
		plan = SortPlanner.plan(shown, module.settings().current().rules(), new MinecraftSortFacts(keys));
	}

	private void updateButtons() {
		if (sortButton != null) {
			sortButton.active = plan instanceof SortPlanner.Planned planned && planned.changedSlots(shown) > 0;
		}
		if (profileButton != null) {
			profileButton.setMessage(profileLabel());
		}
	}

	private Component profileLabel() {
		return Component.translatable("minekit.sort.profile_button",
				Component.translatable(module.settings().current().profile().translationKey()));
	}

	private void setHotbarRule(int slot, HotbarRule rule) {
		module.settings().update(settings -> settings.withHotbarRule(slot, rule));
		if (module.settings().lastSaveFailed()) {
			module.notifications().error(Component.translatable("minekit.notification.save_failed"));
		}
		refreshPlan();
		updateButtons();
	}

	private void cycleProfile() {
		module.settings().update(InventorySettings::withNextProfile);
		refreshPlan();
		updateButtons();
	}

	private void confirm() {
		if (!(plan instanceof SortPlanner.Planned planned)) {
			return;
		}
		LocalPlayer player = Objects.requireNonNull(minecraft.player);
		if (!MenuReader.read(player.inventoryMenu, keys).equals(shown)) {
			module.notifications().warning(Component.translatable("minekit.sort.preview_outdated"));
			refreshPlan();
			updateButtons();
			return;
		}
		Optional<Component> reason = module.unavailableReason();
		if (reason.isPresent()) {
			module.notifications().warning(reason.get());
			return;
		}
		module.startSort(keys, planned);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (hotbarEditor.mouseClicked(event) || super.mouseClicked(event, doubleClick)) {
			return true;
		}
		SlotStack stack = stackAt(event.x(), event.y());
		if (stack == null) {
			return false;
		}
		module.settings().update(settings -> settings.withFavoriteToggled(stack.key().itemId()));
		refreshPlan();
		updateButtons();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return hotbarEditor.keyPressed(event) || super.keyPressed(event);
	}

	private @Nullable SlotStack stackAt(double mouseX, double mouseY) {
		SlotStack before = stackAt(shown, beforeX, beforeY, mouseX, mouseY);
		if (before != null) {
			return before;
		}
		return stackAt(afterState(), afterX, afterY, mouseX, mouseY);
	}

	private static @Nullable SlotStack stackAt(ContainerState state, int gridX, int gridY, double mouseX, double mouseY) {
		for (int row = 0; row < ROWS.length; row++) {
			int top = gridY + row * SLOT + (row == 3 ? HOTBAR_GAP : 0);
			for (int column = 0; column < 9; column++) {
				int left = gridX + column * SLOT;
				if (mouseX >= left && mouseX < left + SLOT && mouseY >= top && mouseY < top + SLOT) {
					return state.slot(ROWS[row][column]);
				}
			}
		}
		return null;
	}

	private ContainerState afterState() {
		return plan instanceof SortPlanner.Planned planned ? planned.expectedResult(shown) : shown;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);
		UiText.draw(graphics, font, title, panelX + 12, panelY + 13, panelWidth - 160, MinekitTheme.TEXT);

		InventorySettings settings = module.settings().current();
		UiText.draw(graphics, font, Component.translatable("minekit.sort.now"), beforeX, beforeY - 11, GRID_WIDTH, MinekitTheme.ACCENT);
		UiText.draw(graphics, font, Component.translatable("minekit.sort.after"), afterX, afterY - 11, GRID_WIDTH, MinekitTheme.ACCENT);
		extractGrid(graphics, shown, beforeX, beforeY, settings);
		extractGrid(graphics, afterState(), afterX, afterY, settings);

		int textY = Math.max(beforeY, afterY) + GRID_HEIGHT + RULE_ROW + 6;
		int line = MinekitTheme.LINE_HEIGHT;
		UiText.draw(graphics, font, summary(settings), panelX + 12, textY, panelWidth - 24, summaryColor());
		Component hotbarHint = Component.translatable(settings.hotbar().keepsAll() ? "minekit.sort.hotbar_hint_kept" : "minekit.sort.hotbar_hint");
		UiText.draw(graphics, font, hotbarHint, panelX + 12, textY + line, panelWidth - 24, MinekitTheme.TEXT_MUTED);
		UiText.draw(graphics, font, Component.translatable("minekit.sort.favorite_hint"), panelX + 12, textY + 2 * line, panelWidth - 24,
				MinekitTheme.TEXT_MUTED);

		// With the picker open, nothing below it reacts to the mouse.
		boolean picker = hotbarEditor.isPickerOpen();
		super.extractRenderState(graphics, picker ? -1 : mouseX, picker ? -1 : mouseY, partialTick);
		hotbarEditor.extract(graphics, font, mouseX, mouseY);

		SlotStack hovered = picker ? null : stackAt(mouseX, mouseY);
		if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(font, tooltip(hovered, settings), mouseX, mouseY);
		}
	}

	private void extractGrid(GuiGraphicsExtractor graphics, ContainerState state, int gridX, int gridY, InventorySettings settings) {
		for (int row = 0; row < ROWS.length; row++) {
			boolean hotbar = row == 3;
			int top = gridY + row * SLOT + (hotbar ? HOTBAR_GAP : 0);
			for (int column = 0; column < 9; column++) {
				boolean locked = hotbar && settings.hotbar().rule(column).kind() == HotbarRule.Kind.KEEP;
				int left = gridX + column * SLOT;
				SlotStack stack = state.slot(ROWS[row][column]);
				boolean favorite = stack != null && settings.favorites().contains(stack.key().itemId());
				graphics.fill(left, top, left + SLOT - 1, top + SLOT - 1, locked ? MinekitTheme.SURFACE_DISABLED : MinekitTheme.SURFACE);
				graphics.outline(left, top, SLOT - 1, SLOT - 1, favorite ? MinekitTheme.ACCENT : MinekitTheme.PANEL_BORDER);
				if (stack != null) {
					ItemStack display = keys.displayStack(stack.key()).copyWithCount(stack.count());
					graphics.item(display, left, top);
					graphics.itemDecorations(font, display, left, top);
				}
			}
		}
	}

	private Component summary(InventorySettings settings) {
		return switch (plan) {
			case SortPlanner.Refused refused -> Component.translatable("minekit.sort.refused." + refused.reason().name().toLowerCase(java.util.Locale.ROOT));
			case SortPlanner.Planned planned -> {
				int changed = planned.changedSlots(shown);
				yield changed == 0
						? Component.translatable("minekit.sort.already_sorted")
						: Component.translatable(settings.hotbar().keepsAll() ? "minekit.sort.summary_hotbar_kept" : "minekit.sort.summary", changed);
			}
		};
	}

	private int summaryColor() {
		return plan instanceof SortPlanner.Refused ? MinekitTheme.WARNING : MinekitTheme.TEXT;
	}

	private List<Component> tooltip(SlotStack stack, InventorySettings settings) {
		List<Component> lines = new ArrayList<>();
		lines.add(keys.displayStack(stack.key()).getHoverName());
		lines.add(Component.translatable(keys.category(stack.key()).translationKey()).withStyle(ChatFormatting.GRAY));
		boolean favorite = settings.favorites().contains(stack.key().itemId());
		lines.add(Component.translatable(favorite ? "minekit.sort.tooltip_unfavorite" : "minekit.sort.tooltip_favorite")
				.withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
