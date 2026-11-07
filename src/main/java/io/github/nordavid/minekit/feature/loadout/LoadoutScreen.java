package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.ItemButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import io.github.nordavid.minekit.feature.loadout.LoadoutPlanner.SlotReport;
import io.github.nordavid.minekit.feature.loadout.LoadoutPlanner.SlotStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The Loadout Manager: saved loadouts on the left; on the right the selected one laid out like the inventory, each
 * position colored by how it compares with the inventory now, and the buttons to change and apply it. Nothing in the
 * inventory changes until the player presses Apply.
 */
public final class LoadoutScreen extends Screen {
	private static final int SLOT = 18;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;
	private static final int LINE = MinekitTheme.LINE_HEIGHT;
	private static final int LIST_WIDTH = 112;
	private static final int GRID_WIDTH = 11 * SLOT + 12;
	private static final int GRID_HEIGHT = 4 * SLOT + 4;
	private static final int MAX_PANEL_HEIGHT = 240;
	private static final int FADE = 0x7020242B;

	private final LoadoutModule module;
	private final ContentPane list;
	private final List<AbstractWidget> listWidgets = new ArrayList<>();
	private @Nullable String selectedId;
	private LoadoutModule.@Nullable Planned planned;
	private @Nullable ContainerState now;
	private boolean deleteArmed;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int rightX;
	private int rightWidth;
	private int gridX;
	private int gridY;

	public LoadoutScreen(LoadoutModule module, @Nullable String selectedId) {
		super(Component.translatable("minekit.loadout.title"));
		this.module = Objects.requireNonNull(module, "module");
		this.selectedId = selectedId;
		this.list = new ContentPane(font);
	}

	/** The selected loadout, for tests. */
	public Optional<Loadout> selected() {
		return selectedId == null ? Optional.empty() : module.store().current().byId(selectedId);
	}

	/** The comparison and plan shown, for tests. */
	public Optional<LoadoutPlanner.Plan> plan() {
		return Optional.ofNullable(planned).map(LoadoutModule.Planned::plan);
	}

	/** Center of an inventory position in the grid, in GUI pixels. For tests. */
	public int[] positionCenter(int index) {
		int[] position = position(index);
		return new int[] {position[0] + SLOT / 2, position[1] + SLOT / 2};
	}

	@Override
	protected void init() {
		Loadouts loadouts = module.store().current();
		if (selectedId == null || loadouts.byId(selectedId).isEmpty()) {
			selectedId = loadouts.loadouts().isEmpty() ? null : loadouts.loadouts().getFirst().id();
		}
		panelWidth = Math.min(width - 8, 8 + LIST_WIDTH + 10 + GRID_WIDTH + 8);
		panelHeight = Math.min(height - 4, MAX_PANEL_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		rightX = panelX + 8 + LIST_WIDTH + 10;
		rightWidth = panelX + panelWidth - 8 - rightX;
		gridX = rightX;
		gridY = panelY + 52;
		deleteArmed = false;
		refreshPlan();

		addRenderableWidget(new FlatButton(panelX + panelWidth - 68, panelY + 6, 60, ROW, CommonComponents.GUI_DONE, button -> onClose()));
		FlatButton save = addRenderableWidget(new FlatButton(panelX + 8, panelY + panelHeight - 8 - ROW, LIST_WIDTH, ROW,
				Component.translatable("minekit.loadout.save_inventory"), button -> module.saveCurrent().ifPresent(id -> {
					selectedId = id;
					rebuildWidgets();
				})));
		save.active = !loadouts.isFull();
		save.setTooltip(Tooltip.create(Component.translatable("minekit.loadout.save_tooltip")));
		rebuildList();

		Loadout loadout = selected().orElse(null);
		if (loadout == null) {
			return;
		}
		int buttonWidth = 46;
		EditBox name = new EditBox(font, rightX + 4, panelY + 33, rightWidth - 2 * (buttonWidth + 4) - 8, 10,
				Component.translatable("minekit.loadout.name"));
		name.setBordered(false);
		name.setMaxLength(Loadout.MAX_NAME);
		name.setTextColor(MinekitTheme.TEXT);
		name.setValue(loadout.name());
		name.setResponder(value -> {
			if (!value.isBlank()) {
				module.update(current -> current.withReplaced(selectedLoadout().withName(value)));
				rebuildList();
			}
		});
		addRenderableWidget(name);
		FlatButton copy = addRenderableWidget(new FlatButton(rightX + rightWidth - 2 * buttonWidth - 4, panelY + 28, buttonWidth, ROW,
				Component.translatable("minekit.loadout.duplicate"), button -> duplicate()));
		copy.active = !module.store().current().isFull();
		addRenderableWidget(new FlatButton(rightX + rightWidth - buttonWidth, panelY + 28, buttonWidth, ROW,
				Component.translatable("minekit.loadout.delete"), button -> delete()));

		int toggleY = gridY + GRID_HEIGHT + 4 + 3 * LINE + 2;
		ToggleButton exact = addRenderableWidget(new ToggleButton(rightX, toggleY, rightWidth, ROW, Component.translatable("minekit.loadout.exact"),
				loadout.exact(), requested -> {
					module.update(current -> current.withReplaced(selectedLoadout().withExact(requested)));
					refreshPlan();
					return selectedLoadout().exact();
				}));
		exact.setTooltip(Tooltip.create(Component.translatable("minekit.loadout.exact_tooltip")));
		int bottom = panelY + panelHeight - 8 - ROW;
		int half = (rightWidth - 4) / 2;
		FlatButton update = addRenderableWidget(new FlatButton(rightX, bottom, half, ROW, Component.translatable("minekit.loadout.update"),
				button -> {
					module.update(current -> current.withReplaced(selectedLoadout().withItems(module.captureInventory())));
					refreshPlan();
				}));
		update.setTooltip(Tooltip.create(Component.translatable("minekit.loadout.update_tooltip")));
		FlatButton apply = addRenderableWidget(new FlatButton(rightX + half + 4, bottom, rightWidth - half - 4, ROW,
				Component.translatable("minekit.loadout.apply"), button -> apply()));
		LoadoutPlanner.Plan plan = planned == null ? null : planned.plan();
		apply.active = plan != null && plan.refusal().isEmpty() && module.applyUnavailableReason().isEmpty();
		module.applyUnavailableReason().ifPresent(reason -> apply.setTooltip(Tooltip.create(reason)));
	}

	private Loadout selectedLoadout() {
		return selected().orElseThrow();
	}

	private void rebuildList() {
		listWidgets.forEach(this::removeWidget);
		listWidgets.clear();
		int top = panelY + 28;
		list.reset(panelX + 8, top, LIST_WIDTH, panelY + panelHeight - 8 - ROW - 4 - top);
		Loadouts loadouts = module.store().current();
		if (loadouts.loadouts().isEmpty()) {
			list.text(Component.translatable("minekit.loadout.none"), MinekitTheme.TEXT_MUTED);
		}
		for (Loadout loadout : loadouts.loadouts()) {
			ItemButton row = addRenderableWidget(new ItemButton(0, 0, list.width(), ROW, icon(loadout), Component.literal(loadout.name()),
					() -> loadout.id().equals(selectedId), () -> {
						selectedId = loadout.id();
						rebuildWidgets();
					}));
			listWidgets.add(list.widget(row));
		}
		list.layout();
	}

	private static ItemStack icon(Loadout loadout) {
		for (int index : LoadoutSlots.APPLY_ORDER) {
			LoadoutItem item = loadout.items().get(index);
			if (item != null && LoadoutSlots.isStorage(index)) {
				return ItemIds.icon(item.itemId());
			}
		}
		return loadout.items().isEmpty() ? ItemStack.EMPTY : ItemIds.icon(loadout.items().values().iterator().next().itemId());
	}

	private void refreshPlan() {
		Loadout loadout = selected().orElse(null);
		if (loadout == null || minecraft == null || minecraft.player == null) {
			planned = null;
			now = null;
			return;
		}
		planned = module.planInInventory(loadout);
		now = planned.plan().start();
	}

	private void duplicate() {
		Loadout loadout = selectedLoadout();
		String id = module.store().current().peekId();
		module.update(current -> current.withDuplicate(loadout.id(), Component.translatable("minekit.loadout.copy_name", loadout.name()).getString()));
		selectedId = id;
		rebuildWidgets();
	}

	private void delete() {
		if (!deleteArmed) {
			deleteArmed = true;
			module.notifications().info(Component.translatable("minekit.loadout.delete_confirm", selectedLoadout().name()));
			return;
		}
		String id = selectedLoadout().id();
		module.update(current -> current.without(id));
		module.notifications().info(Component.translatable("minekit.loadout.deleted"));
		selectedId = null;
		rebuildWidgets();
	}

	private void apply() {
		if (planned == null) {
			return;
		}
		if (!module.applyInInventory(selectedLoadout(), planned.plan())) {
			module.notifications().warning(Component.translatable("minekit.sort.preview_outdated"));
			refreshPlan();
			rebuildWidgets();
		}
	}

	/** Top-left corner of an inventory position in the grid. */
	private int[] position(int index) {
		int mainX = gridX + SLOT + 6;
		if (LoadoutSlots.isArmor(index)) {
			int row = LoadoutSlots.HEAD - index;
			return new int[] {gridX, gridY + row * SLOT + (row == 3 ? 4 : 0)};
		}
		if (index == LoadoutSlots.OFFHAND) {
			return new int[] {mainX + 9 * SLOT + 6, gridY + 3 * SLOT + 4};
		}
		if (index < LoadoutSlots.MAIN_START) {
			return new int[] {mainX + index * SLOT, gridY + 3 * SLOT + 4};
		}
		int main = index - LoadoutSlots.MAIN_START;
		return new int[] {mainX + (main % 9) * SLOT, gridY + (main / 9) * SLOT};
	}

	private int positionAt(double mouseX, double mouseY) {
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			int[] position = position(index);
			if (mouseX >= position[0] && mouseX < position[0] + SLOT && mouseY >= position[1] && mouseY < position[1] + SLOT) {
				return index;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (list.mouseClicked(event) || super.mouseClicked(event, doubleClick)) {
			return true;
		}
		Loadout loadout = selected().orElse(null);
		int index = positionAt(event.x(), event.y());
		if (loadout == null || index < 0) {
			return false;
		}
		Loadout changed;
		// The held key counts too: synthetic mouse events may carry no modifier flags.
		boolean shift = event.hasShiftDown() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_LSHIFT)
				|| com.mojang.blaze3d.platform.InputConstants.isKeyDown(com.mojang.blaze3d.platform.InputConstants.KEY_RSHIFT);
		if (shift) {
			LoadoutItem here = module.captureInventory().get(index);
			if (here == null) {
				return true;
			}
			changed = loadout.withItem(index, here);
		} else if (loadout.items().containsKey(index)) {
			changed = loadout.withFree(index);
		} else if (loadout.locked().contains(index)) {
			changed = loadout.withFree(index);
		} else {
			changed = loadout.withLocked(index);
		}
		module.update(current -> current.withReplaced(changed));
		refreshPlan();
		rebuildWidgets();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return list.mouseDragged(event) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean ended = list.mouseReleased(event);
		return super.mouseReleased(event) || ended;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return list.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);
		UiText.draw(graphics, font, title, panelX + 8, panelY + 11, panelWidth - 90, MinekitTheme.ACCENT);
		list.extract(graphics, mouseX, mouseY);

		Loadout loadout = selected().orElse(null);
		if (loadout == null) {
			int y = panelY + 34;
			for (String line : UiText.wrap(font, Component.translatable("minekit.loadout.empty_hint").getString(), rightWidth, 8)) {
				graphics.text(font, line, rightX, y, MinekitTheme.TEXT_MUTED, false);
				y += LINE;
			}
			super.extractRenderState(graphics, mouseX, mouseY, partialTick);
			return;
		}
		graphics.fill(rightX, panelY + 28, rightX + rightWidth - 2 * 50, panelY + 28 + ROW, MinekitTheme.SURFACE);
		graphics.outline(rightX, panelY + 28, rightWidth - 2 * 50, ROW, MinekitTheme.BORDER);
		extractGrid(graphics, loadout);
		extractSummary(graphics);
		int hintY = gridY + GRID_HEIGHT + 4 + 3 * LINE + 2 + ROW + 4;
		for (String line : UiText.wrap(font, Component.translatable("minekit.loadout.grid_hint").getString(), rightWidth, 2)) {
			graphics.text(font, line, rightX, hintY, MinekitTheme.TEXT_DISABLED, false);
			hintY += LINE;
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int hovered = positionAt(mouseX, mouseY);
		if (hovered >= 0) {
			graphics.setComponentTooltipForNextFrame(font, tooltip(loadout, hovered), mouseX, mouseY);
		}
	}

	private void extractGrid(GuiGraphicsExtractor graphics, Loadout loadout) {
		LoadoutPlanner.Plan plan = planned == null ? null : planned.plan();
		for (int index = 0; index < LoadoutSlots.SIZE; index++) {
			int[] position = position(index);
			int x = position[0];
			int y = position[1];
			LoadoutItem item = loadout.items().get(index);
			boolean locked = loadout.locked().contains(index);
			graphics.fill(x, y, x + SLOT - 1, y + SLOT - 1, locked ? MinekitTheme.SURFACE_DISABLED : MinekitTheme.SURFACE);
			int border = MinekitTheme.PANEL_BORDER;
			if (item != null && plan != null) {
				border = plan.report(index).map(report -> color(report.status())).orElse(MinekitTheme.BORDER);
			}
			graphics.outline(x, y, SLOT - 1, SLOT - 1, border);
			if (item != null) {
				ItemStack icon = ItemIds.icon(item.itemId()).copyWithCount(item.count());
				graphics.item(icon, x, y);
				graphics.itemDecorations(font, icon, x, y);
			} else {
				ItemStack current = currentStack(index);
				if (!current.isEmpty()) {
					graphics.item(current, x, y);
					graphics.fill(x, y, x + 16, y + 16, FADE);
				}
				if (locked) {
					graphics.text(font, "–", x + 6, y + 4, MinekitTheme.TEXT_MUTED, false);
				}
			}
		}
	}

	private ItemStack currentStack(int index) {
		if (now == null || planned == null) {
			return ItemStack.EMPTY;
		}
		SlotStack stack = now.slot(LoadoutLayout.inventoryMenu().menuSlot(index));
		return stack == null ? ItemStack.EMPTY : planned.keys().displayStack(stack.key()).copyWithCount(stack.count());
	}

	private static int color(SlotStatus status) {
		return switch (status) {
			case PRESENT -> MinekitTheme.SUCCESS;
			case FILLED -> MinekitTheme.ACCENT;
			case PARTIAL -> MinekitTheme.WARNING;
			case MISSING, NO_ROOM -> MinekitTheme.ERROR;
			case NOT_HERE -> MinekitTheme.BORDER;
		};
	}

	private void extractSummary(GuiGraphicsExtractor graphics) {
		if (planned == null) {
			return;
		}
		LoadoutPlanner.Plan plan = planned.plan();
		int x = rightX;
		int y = gridY + GRID_HEIGHT + 4;
		Component counts = Component.translatable("minekit.loadout.counts", plan.count(SlotStatus.PRESENT),
				plan.count(SlotStatus.FILLED) + plan.count(SlotStatus.PARTIAL),
				plan.count(SlotStatus.MISSING) + plan.count(SlotStatus.NO_ROOM) + plan.count(SlotStatus.PARTIAL));
		UiText.draw(graphics, font, counts, x, y, rightWidth, MinekitTheme.TEXT);
		Component missing = plan.missing().isEmpty()
				? Component.translatable("minekit.loadout.nothing_missing")
				: Component.translatable("minekit.loadout.missing", LoadoutModule.missingText(plan.missing()));
		UiText.draw(graphics, font, missing, x, y + LINE, rightWidth, plan.missing().isEmpty() ? MinekitTheme.SUCCESS : MinekitTheme.WARNING);
		Component third;
		int thirdColor = MinekitTheme.TEXT_MUTED;
		if (plan.refusal().orElse(null) == LoadoutPlanner.Refusal.CURSOR_NOT_EMPTY) {
			third = Component.translatable("minekit.deposit.refused.cursor_not_empty");
			thirdColor = MinekitTheme.WARNING;
		} else if (plan.count(SlotStatus.NO_ROOM) > 0) {
			third = Component.translatable("minekit.loadout.no_room", plan.count(SlotStatus.NO_ROOM));
			thirdColor = MinekitTheme.WARNING;
		} else if (plan.refusal().orElse(null) == LoadoutPlanner.Refusal.NOTHING_TO_DO) {
			third = Component.translatable("minekit.loadout.nothing_to_do");
		} else {
			third = Component.translatable("minekit.loadout.extra", plan.extra().size());
		}
		UiText.draw(graphics, font, third, x, y + 2 * LINE, rightWidth, thirdColor);
	}

	private List<Component> tooltip(Loadout loadout, int index) {
		List<Component> lines = new ArrayList<>();
		LoadoutItem item = loadout.items().get(index);
		ItemStack current = currentStack(index);
		Component nowLine = current.isEmpty() ? null
				: Component.translatable("minekit.loadout.tooltip.now", current.getCount() + "× " + current.getHoverName().getString())
						.withStyle(ChatFormatting.GRAY);
		if (item != null) {
			lines.add(Component.literal(item.count() + "× ").append(ItemIds.name(item.itemId())));
			LoadoutPlanner.Plan plan = planned == null ? null : planned.plan();
			SlotReport report = plan == null ? null : plan.report(index).orElse(null);
			if (report != null) {
				lines.add(Component.translatable("minekit.loadout.status." + report.status().name().toLowerCase(java.util.Locale.ROOT), report.after())
						.withStyle(ChatFormatting.GRAY));
			}
			if (nowLine != null && report != null && report.status() != SlotStatus.PRESENT) {
				lines.add(nowLine);
			}
			lines.add(Component.translatable("minekit.loadout.tooltip.click_item").withStyle(ChatFormatting.DARK_GRAY));
		} else if (loadout.locked().contains(index)) {
			lines.add(Component.translatable("minekit.loadout.tooltip.locked"));
			lines.add(Component.translatable("minekit.loadout.tooltip.click_locked").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			lines.add(Component.translatable("minekit.loadout.tooltip.free"));
			if (nowLine != null) {
				lines.add(nowLine);
			}
			lines.add(Component.translatable("minekit.loadout.tooltip.click_free").withStyle(ChatFormatting.DARK_GRAY));
		}
		return lines;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
