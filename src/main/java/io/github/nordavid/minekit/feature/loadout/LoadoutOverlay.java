package io.github.nordavid.minekit.feature.loadout;

import io.github.nordavid.minekit.core.container.ContainerPanels;
import io.github.nordavid.minekit.core.container.MenuReader;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.container.SlotStack;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * "Loadout from this container": a button next to a confirmed container and, on request, a panel that shows what a
 * loadout would take out of the container and where it would go. Taking only: nothing is ever put into the container.
 * Shares the space left of the container with Smart Deposit ({@link ContainerPanels}); the same rules for mouse
 * input apply (the panel background never claims a point where a control is, and swallows presses and releases).
 */
public final class LoadoutOverlay implements ContainerPanels.Panel {
	private static final int PANEL_WIDTH = 168;
	private static final int MIN_PANEL_WIDTH = 110;
	private static final int MIN_PANEL_HEIGHT = 222;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;
	private static final int LINE = MinekitTheme.LINE_HEIGHT;
	private static final int TARGET_HIGHLIGHT = 0x7044CC88;
	private static final int SOURCE_HIGHLIGHT = 0x70E8B75A;

	private final LoadoutModule module;
	private final AbstractContainerScreen<?> screen;
	private final OpenedContainer container;
	private final List<AbstractWidget> widgets = new ArrayList<>();
	private boolean open;
	private @Nullable String loadoutId;
	private LoadoutModule.@Nullable Planned planned;
	private int ticksUntilCheck;

	private int guiLeft;
	private int guiTop;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	LoadoutOverlay(LoadoutModule module, AbstractContainerScreen<?> screen, OpenedContainer container) {
		this.module = Objects.requireNonNull(module, "module");
		this.screen = Objects.requireNonNull(screen, "screen");
		this.container = Objects.requireNonNull(container, "container");
	}

	@Override
	public AbstractContainerScreen<?> screen() {
		return screen;
	}

	OpenedContainer container() {
		return container;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	/** The plan the panel shows, for tests. */
	public Optional<LoadoutPlanner.Plan> plan() {
		return Optional.ofNullable(planned).map(LoadoutModule.Planned::plan);
	}

	/** Position and size of the panel in GUI pixels: x, y, width, height. For tests. */
	public int[] panelBounds() {
		return new int[] {panelX, panelY, panelWidth, panelHeight};
	}

	/** Center of the opener button in GUI pixels. For tests. */
	public int[] openerCenter() {
		return new int[] {Math.max(2, guiLeft - 24) + 11, guiTop + 4 + ROW + 4 + ROW / 2};
	}

	@Override
	public void refresh() {
		rebuildLater();
	}

	void install() {
		widgets.clear();
		module.panels().register(this);
		layout();
		ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, tickDelta) -> module.context().guard("draw", () -> extractHighlights(graphics)));
		ScreenEvents.afterTick(screen).register(current -> module.context().guard("tick", this::tick));
		ScreenMouseEvents.allowMouseRelease(screen).register((current, event) -> {
			if (!covers(event.x(), event.y())) {
				return true;
			}
			current.setDragging(false);
			return false;
		});
		rebuild();
	}

	private void layout() {
		int imageHeight = switch (container.kind()) {
			case CHEST, BARREL, ENDER_CHEST -> 114 + (container.menu() instanceof ChestMenu chest ? chest.getRowCount() : 3) * 18;
			case SHULKER_BOX -> 167;
			case HOPPER -> 133;
			case DISPENSER -> 166;
		};
		guiLeft = (screen.width - 176) / 2;
		guiTop = (screen.height - imageHeight) / 2;
		panelHeight = Math.min(Math.max(imageHeight, MIN_PANEL_HEIGHT), screen.height - 8);
		panelY = Math.clamp(guiTop, 4, Math.max(4, screen.height - panelHeight - 4));
		int space = guiLeft - 10;
		if (space >= MIN_PANEL_WIDTH) {
			panelWidth = Math.min(PANEL_WIDTH, space);
			panelX = guiLeft - 6 - panelWidth;
		} else {
			panelWidth = Math.min(PANEL_WIDTH, screen.width - 8);
			panelX = 4;
		}
	}

	private boolean covers(double x, double y) {
		for (AbstractWidget widget : widgets) {
			if (widget.visible && x >= widget.getX() && x < widget.getRight() && y >= widget.getY() && y < widget.getBottom()) {
				return true;
			}
		}
		return false;
	}

	void toggle() {
		if (module.isApplying()) {
			module.cancelApplying();
			return;
		}
		open = !open;
		if (open) {
			List<Loadout> loadouts = module.store().current().loadouts();
			String preferred = module.lastUsed();
			loadoutId = loadouts.stream().map(Loadout::id).filter(id -> id.equals(preferred)).findFirst()
					.orElse(loadouts.isEmpty() ? null : loadouts.getFirst().id());
			replan();
			module.panels().opened(this);
		} else {
			module.panels().closed(this);
		}
		rebuildLater();
	}

	@Override
	public void close() {
		boolean wasOpen = open;
		open = false;
		planned = null;
		rebuildLater();
		if (wasOpen) {
			module.panels().closed(this);
		}
	}

	void onApplied() {
		open = false;
		planned = null;
		rebuildLater();
		module.panels().closed(this);
	}

	private Optional<Loadout> loadout() {
		return loadoutId == null ? Optional.empty() : module.store().current().byId(loadoutId);
	}

	private void cycleLoadout() {
		List<Loadout> loadouts = module.store().current().loadouts();
		if (loadouts.isEmpty()) {
			return;
		}
		int index = loadouts.stream().map(Loadout::id).toList().indexOf(loadoutId);
		loadoutId = loadouts.get((index + 1) % loadouts.size()).id();
		replan();
		rebuildLater();
	}

	/** Takes what the panel shows, if the container and inventory are still as shown. */
	void confirm() {
		if (!open || module.isApplying() || planned == null) {
			return;
		}
		LoadoutModule.Planned shown = planned;
		replan();
		Loadout loadout = loadout().orElse(null);
		if (planned == null || loadout == null || planned.plan().refusal().isPresent() || !LoadoutPlanner.sameClicks(shown.plan(), planned.plan())) {
			rebuildLater();
			return;
		}
		module.applyFromContainer(this, loadout, planned);
		rebuildLater();
	}

	private void replan() {
		planned = loadout().map(loadout -> module.planInContainer(loadout, container)).orElse(null);
	}

	private void tick() {
		if (!open || module.isApplying() || --ticksUntilCheck > 0) {
			return;
		}
		ticksUntilCheck = 10;
		if (planned != null && !MenuReader.read(container.menu(), planned.keys()).equals(planned.plan().start())) {
			replan();
			rebuildLater();
		}
	}

	private void rebuildLater() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.execute(() -> {
			if (minecraft.gui.screen() == screen) {
				rebuild();
			}
		});
	}

	private void rebuild() {
		List<AbstractWidget> buttons = Screens.getWidgets(screen);
		buttons.removeAll(widgets);
		widgets.clear();
		if (!module.isEnabled()) {
			return;
		}
		boolean running = module.isApplying();
		if (!open && !running) {
			if (module.panels().otherOpen(this)) {
				return;
			}
			FlatButton opener = new FlatButton(Math.max(2, guiLeft - 24), guiTop + 4 + ROW + 4, 22, ROW, Component.literal("⚒"),
					button -> toggle());
			opener.setTooltip(Tooltip.create(Component.translatable("minekit.loadout.open_tooltip")));
			add(opener);
			return;
		}
		add(new Panel());
		int inner = panelWidth - 16;
		int x = panelX + 8;
		if (running) {
			add(new FlatButton(x, panelY + 22, inner, ROW, Component.translatable("minekit.deposit.stop"), button -> module.cancelApplying()));
			return;
		}
		Loadout loadout = loadout().orElse(null);
		FlatButton choose = add(new FlatButton(x, panelY + 22, inner, ROW, loadout == null
				? Component.translatable("minekit.loadout.none_short") : Component.literal(loadout.name()), button -> cycleLoadout()));
		choose.active = module.store().current().loadouts().size() > 1;
		if (choose.active) {
			choose.setTooltip(Tooltip.create(Component.translatable("minekit.loadout.choose_tooltip")));
		}
		int half = (inner - 4) / 2;
		FlatButton take = add(new FlatButton(x, panelY + 22 + ROW + 4, half, ROW, Component.translatable("minekit.loadout.take"), button -> confirm()));
		take.active = planned != null && planned.plan().refusal().isEmpty();
		add(new FlatButton(x + half + 4, panelY + 22 + ROW + 4, inner - half - 4, ROW, Component.translatable("minekit.deposit.cancel"),
				button -> close()));
	}

	private <T extends AbstractWidget> T add(T widget) {
		widgets.add(widget);
		Screens.getWidgets(screen).add(widget);
		return widget;
	}

	private void extractHighlights(GuiGraphicsExtractor graphics) {
		LoadoutModule.Planned current = planned;
		if (current == null || !(open || module.isApplying())) {
			return;
		}
		for (int slot : current.plan().changedSlots()) {
			boolean fromContainer = false;
			for (int containerSlot : container.containerSlots()) {
				fromContainer |= containerSlot == slot;
			}
			fillSlot(graphics, slot, fromContainer ? SOURCE_HIGHLIGHT : TARGET_HIGHLIGHT);
		}
	}

	private void fillSlot(GuiGraphicsExtractor graphics, int index, int color) {
		Slot slot = container.menu().slots.get(index);
		int x = guiLeft + slot.x;
		int y = guiTop + slot.y;
		graphics.fill(x, y, x + 16, y + 16, color);
	}

	/** Background and text of the panel. Swallows clicks so they never reach the container screen. */
	private final class Panel extends AbstractWidget {
		Panel() {
			super(panelX, panelY, panelWidth, panelHeight, Component.translatable("minekit.loadout.panel_title"));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			Font font = Minecraft.getInstance().font;
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.PANEL);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), MinekitTheme.PANEL_BORDER);
			int x = getX() + 8;
			int inner = getWidth() - 16;
			UiText.draw(graphics, font, getMessage(), x, getY() + 8, inner, MinekitTheme.ACCENT);
			int y = getY() + 22 + 2 * (ROW + 4) + 2;
			if (module.isApplying()) {
				UiText.draw(graphics, font, Component.translatable("minekit.loadout.running", module.runningClicks()), x, getY() + 22 + ROW + 8, inner,
						MinekitTheme.TEXT);
				return;
			}
			if (planned == null) {
				lines(graphics, font, UiText.wrap(font, Component.translatable("minekit.loadout.overlay_none").getString(), inner, 8), x, y,
						MinekitTheme.TEXT_MUTED);
				return;
			}
			LoadoutPlanner.Plan plan = planned.plan();
			y = lines(graphics, font, UiText.wrap(font, Component.translatable("minekit.loadout.overlay_explained").getString(), inner, 3), x, y,
					MinekitTheme.TEXT_MUTED) + 4;
			List<String> notes = new ArrayList<>();
			if (!plan.missing().isEmpty()) {
				notes.addAll(UiText.wrap(font, Component.translatable("minekit.loadout.missing", LoadoutModule.missingText(plan.missing()))
						.getString(), inner, 3));
			}
			if (plan.count(LoadoutPlanner.SlotStatus.NO_ROOM) > 0) {
				notes.addAll(UiText.wrap(font, Component.translatable("minekit.loadout.no_room", plan.count(LoadoutPlanner.SlotStatus.NO_ROOM))
						.getString(), inner, 2));
			}
			int notesTop = getBottom() - 6 - notes.size() * LINE;
			if (plan.refusal().isPresent()) {
				Component reason = plan.refusal().get() == LoadoutPlanner.Refusal.CURSOR_NOT_EMPTY
						? Component.translatable("minekit.deposit.refused.cursor_not_empty")
						: Component.translatable(plan.missing().isEmpty() ? "minekit.loadout.nothing_to_do" : "minekit.loadout.nothing_here");
				lines(graphics, font, UiText.wrap(font, reason.getString(), inner, 4), x, y, MinekitTheme.WARNING);
			} else {
				int total = plan.taken().values().stream().mapToInt(Integer::intValue).sum();
				Component summary = total == 0 ? Component.translatable("minekit.loadout.overlay_rearrange")
						: Component.translatable("minekit.loadout.takes", total, plan.taken().size());
				y = lines(graphics, font, UiText.wrap(font, summary.getString(), inner, 2), x, y, MinekitTheme.TEXT) + 2;
				int shown = 0;
				for (Map.Entry<ItemKey, Integer> entry : plan.taken().entrySet()) {
					boolean last = shown == plan.taken().size() - 1;
					if (y + ROW + (last ? 0 : LINE) > notesTop - 2) {
						UiText.draw(graphics, font, Component.translatable("minekit.deposit.more", plan.taken().size() - shown), x, y + 2, inner,
								MinekitTheme.TEXT_MUTED);
						break;
					}
					var stack = planned.keys().displayStack(entry.getKey());
					graphics.item(stack, x, y);
					Component count = Component.literal(entry.getValue() + "×");
					int countWidth = font.width(count);
					UiText.draw(graphics, font, stack.getHoverName(), x + 20, y + 4, inner - 26 - countWidth, MinekitTheme.TEXT);
					UiText.drawRightAligned(graphics, font, count, x + inner, y + 4, countWidth, MinekitTheme.TEXT_MUTED);
					y += ROW;
					shown++;
				}
			}
			lines(graphics, font, notes, x, notesTop, MinekitTheme.WARNING);
		}

		private int lines(GuiGraphicsExtractor graphics, Font font, List<String> lines, int x, int y, int color) {
			for (String line : lines) {
				graphics.text(font, line, x, y, color, false);
				y += LINE;
			}
			return y;
		}

		/** Over the panel, but not over one of its controls: those must get the click themselves. */
		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			if (!super.isMouseOver(mouseX, mouseY)) {
				return false;
			}
			for (AbstractWidget widget : widgets) {
				if (widget != this && widget.isMouseOver(mouseX, mouseY)) {
					return false;
				}
			}
			return true;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			// Swallow the click; vanilla would treat it as a click outside the container.
		}

		@Override
		public void playDownSound(SoundManager soundManager) {
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
