package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.container.ContainerPanels;
import io.github.nordavid.minekit.core.container.ContainerState;
import io.github.nordavid.minekit.core.container.MenuReader;
import io.github.nordavid.minekit.core.container.OpenedContainer;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * Smart Deposit inside the open container screen: a small button next to the container, and on request a preview
 * panel that lists what would move, highlights the affected slots, and starts or stops the deposit.
 *
 * <p>The preview has to live in the container screen itself: opening another screen would close the container. The
 * panel swallows mouse presses and releases over it, because vanilla drops the cursor item when a click outside the
 * container is released. The panel background is the first widget (so it is drawn below the controls), but it never
 * claims a point where a control is: vanilla gives a click to the first widget under the mouse.
 */
public final class DepositOverlay implements ContainerPanels.Panel {
	private static final int PANEL_WIDTH = 168;
	private static final int MIN_PANEL_WIDTH = 110;
	/** Enough for the controls and a useful item list; taller containers make it taller. */
	private static final int MIN_PANEL_HEIGHT = 222;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;
	private static final int LINE = MinekitTheme.LINE_HEIGHT;
	private static final int PROFILE_Y = 22;
	private static final int DESCRIPTION_Y = PROFILE_Y + ROW + 3;
	private static final int DESCRIPTION_LINES = 2;
	private static final int ACTIONS_Y = DESCRIPTION_Y + DESCRIPTION_LINES * LINE + 2;
	private static final int HELP_BUTTON = 16;
	private static final String[] HELP = {"minekit.deposit.help.what", "minekit.deposit.help.profile", "minekit.deposit.help.protected",
			"minekit.deposit.help.colors", "minekit.deposit.help.confirm"};
	private static final int TARGET_HIGHLIGHT = 0x7044CC88;
	private static final int SOURCE_HIGHLIGHT = 0x70E8B75A;

	private final SmartDepositModule module;
	private final AbstractContainerScreen<?> screen;
	private final OpenedContainer container;
	private final List<AbstractWidget> widgets = new ArrayList<>();
	private boolean open;
	private boolean includeFavorites;
	private boolean help;
	private @Nullable ItemKeyIndex keys;
	private @Nullable MinecraftDepositRules rules;
	private @Nullable ContainerState planned;
	private DepositPlanner.@Nullable Plan plan;
	private int ticksUntilCheck;

	private int guiLeft;
	private int guiTop;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	DepositOverlay(SmartDepositModule module, AbstractContainerScreen<?> screen, OpenedContainer container) {
		this.module = Objects.requireNonNull(module, "module");
		this.screen = Objects.requireNonNull(screen, "screen");
		this.container = Objects.requireNonNull(container, "container");
	}

	@Override
	public AbstractContainerScreen<?> screen() {
		return screen;
	}

	@Override
	public void refresh() {
		rebuildLater();
	}

	OpenedContainer container() {
		return container;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	/** Whether the panel shows the explanation instead of the preview. For tests. */
	public boolean isShowingHelp() {
		return open && help;
	}

	/** The plan the preview shows, for tests. */
	public Optional<DepositPlanner.Plan> plan() {
		return Optional.ofNullable(plan);
	}

	/** Position and size of the preview panel in GUI pixels: x, y, width, height. For tests. */
	public int[] panelBounds() {
		return new int[] {panelX, panelY, panelWidth, panelHeight};
	}

	/** Called after every (re-)initialization of the screen: Fabric has then dropped its widgets and events. */
	void install() {
		widgets.clear();
		module.panels().register(this);
		layout();
		ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, tickDelta) -> extractHighlights(graphics));
		ScreenEvents.afterTick(screen).register(current -> tick());
		ScreenMouseEvents.allowMouseRelease(screen).register((current, event) -> {
			if (!covers(event.x(), event.y())) {
				return true;
			}
			// Vanilla would drop the cursor item. End the screen's drag state, which the skipped release would have done.
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
		// Left of the container (item list mods usually take the right side), as wide as fits. Only on very small
		// screens does the panel cover the container.
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
		if (module.isDepositing()) {
			module.cancelDeposit();
			return;
		}
		open = !open;
		if (open) {
			includeFavorites = false;
			help = false;
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
		help = false;
		plan = null;
		rebuildLater();
		if (wasOpen) {
			module.panels().closed(this);
		}
	}

	private void toggleHelp() {
		help = !help;
		rebuildLater();
	}

	/** Starts the deposit shown in the preview, if it is still what the preview shows. */
	void confirm() {
		if (!open || help || module.isDepositing()) {
			return;
		}
		DepositPlanner.Plan shown = plan;
		replan();
		if (plan == null || plan.refusal().isPresent() || keys == null || rules == null
				|| shown == null || !shown.moved().equals(plan.moved())) {
			// Something changed since the preview was drawn: show the new preview and let the player confirm again.
			rebuildLater();
			return;
		}
		module.startDeposit(this, keys, plan, rules);
		rebuildLater();
	}

	/** Called by the module when the deposit has ended. */
	void onDepositFinished() {
		open = false;
		plan = null;
		rebuildLater();
		module.panels().closed(this);
	}

	private void replan() {
		ItemKeyIndex index = module.newKeyIndex();
		int[] containerSlots = container.containerSlots();
		Slot sample = containerSlots.length == 0 ? null : container.menu().slots.get(containerSlots[0]);
		MinecraftDepositRules newRules = new MinecraftDepositRules(index, sample);
		ContainerState state = MenuReader.read(container.menu(), index);
		DepositPlanner.Layout layout = new DepositPlanner.Layout(containerSlots, container.mainInventorySlots(), container.hotbarSlots());
		DepositPlanner.Request request = new DepositPlanner.Request(module.settings().current(), module.favorites(), includeFavorites);
		keys = index;
		rules = newRules;
		planned = state;
		plan = DepositPlanner.plan(state, layout, request, newRules);
	}

	private void tick() {
		if (!open || module.isDepositing() || --ticksUntilCheck > 0) {
			return;
		}
		ticksUntilCheck = 10;
		// The player may move items by hand while the preview is open: keep it truthful.
		if (keys != null && planned != null && !MenuReader.read(container.menu(), keys).equals(planned)) {
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
		boolean running = module.isDepositing();
		if (!open && !running) {
			if (module.panels().otherOpen(this)) {
				return;
			}
			FlatButton opener = new FlatButton(Math.max(2, guiLeft - 24), guiTop + 4, 22, ROW, Component.literal("⇩"), button -> toggle());
			opener.setTooltip(Tooltip.create(Component.translatable("minekit.deposit.open_tooltip", module.depositKey().getTranslatedKeyMessage())));
			add(opener);
			return;
		}
		add(new Panel());
		int inner = panelWidth - 16;
		int x = panelX + 8;
		// Controls at the top: a notification card in the bottom corner may briefly cover the end of the list, never them.
		if (running) {
			add(new FlatButton(x, panelY + PROFILE_Y, inner, ROW, Component.translatable("minekit.deposit.stop"), button -> module.cancelDeposit()));
			return;
		}
		FlatButton helpButton = add(new FlatButton(panelX + panelWidth - 6 - HELP_BUTTON, panelY + 4, HELP_BUTTON, 14,
				Component.literal(help ? "✕" : "?"), button -> toggleHelp()));
		helpButton.setTooltip(Tooltip.create(Component.translatable(help ? "minekit.deposit.help.close" : "minekit.deposit.help.open")));
		if (help) {
			return;
		}
		add(new FlatButton(x, panelY + PROFILE_Y, inner, ROW, Component.translatable("minekit.deposit.profile_button",
				Component.translatable(module.settings().current().profile().translationKey())), button -> {
					module.cycleProfile();
					replan();
					rebuildLater();
				}));
		int half = (inner - 4) / 2;
		FlatButton confirm = add(new FlatButton(x, panelY + ACTIONS_Y, half, ROW, Component.translatable("minekit.deposit.confirm"),
				button -> confirm()));
		confirm.active = plan != null && plan.refusal().isEmpty();
		add(new FlatButton(x + half + 4, panelY + ACTIONS_Y, inner - half - 4, ROW, Component.translatable("minekit.deposit.cancel"),
				button -> close()));
		if (plan != null && hasProtectedFavorites(plan)) {
			ToggleButton favoritesToggle = add(new ToggleButton(x, panelY + ACTIONS_Y + ROW + 4, inner, ROW,
					Component.translatable("minekit.deposit.include_favorites"), includeFavorites, requested -> {
						includeFavorites = requested;
						replan();
						rebuildLater();
						return requested;
					}));
			favoritesToggle.setTooltip(Tooltip.create(Component.translatable("minekit.deposit.include_favorites_tooltip")));
		}
	}

	/** Where the text below the controls starts. */
	private int contentTop(DepositPlanner.Plan current) {
		int rows = hasProtectedFavorites(current) ? 2 : 1;
		return panelY + ACTIONS_Y + rows * (ROW + 4) + 4;
	}

	private boolean hasProtectedFavorites(DepositPlanner.Plan current) {
		Set<String> favorites = module.favorites();
		return includeFavorites || current.kept().keySet().stream().anyMatch(key -> favorites.contains(key.itemId()));
	}

	private <T extends AbstractWidget> T add(T widget) {
		widgets.add(widget);
		Screens.getWidgets(screen).add(widget);
		return widget;
	}

	private void extractHighlights(GuiGraphicsExtractor graphics) {
		DepositPlanner.Plan current = plan;
		ContainerState before = planned;
		if (current == null || before == null || help || !(open || module.isDepositing())) {
			return;
		}
		for (int slot : current.changedContainerSlots(before)) {
			fillSlot(graphics, slot, TARGET_HIGHLIGHT);
		}
		for (int slot : current.sources()) {
			if (!Objects.equals(before.slot(slot), current.expected().slot(slot))) {
				fillSlot(graphics, slot, SOURCE_HIGHLIGHT);
			}
		}
	}

	private void fillSlot(GuiGraphicsExtractor graphics, int index, int color) {
		Slot slot = container.menu().slots.get(index);
		int x = guiLeft + slot.x;
		int y = guiTop + slot.y;
		graphics.fill(x, y, x + 16, y + 16, color);
	}

	/** Background and text of the preview. Swallows clicks so they never reach the container screen. */
	private final class Panel extends AbstractWidget {
		Panel() {
			super(panelX, panelY, panelWidth, panelHeight, Component.translatable("minekit.deposit.title"));
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			Font font = Minecraft.getInstance().font;
			graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.PANEL);
			graphics.outline(getX(), getY(), getWidth(), getHeight(), MinekitTheme.PANEL_BORDER);
			int x = getX() + 8;
			int inner = getWidth() - 16;
			UiText.draw(graphics, font, getMessage(), x, getY() + 8, inner - HELP_BUTTON - 2, MinekitTheme.ACCENT);
			if (module.isDepositing()) {
				int clicks = module.runningClicks();
				int top = getY() + PROFILE_Y + ROW + 8;
				UiText.draw(graphics, font, Component.translatable("minekit.deposit.running"), x, top, inner, MinekitTheme.TEXT);
				UiText.draw(graphics, font, Component.translatable("minekit.deposit.running_clicks", clicks), x, top + MinekitTheme.LINE_HEIGHT,
						inner, MinekitTheme.TEXT_MUTED);
				return;
			}
			if (help) {
				extractHelp(graphics, font, x, inner);
				return;
			}
			DepositPlanner.Plan current = plan;
			if (current == null) {
				return;
			}
			Component description = Component.translatable(module.settings().current().profile().translationKey() + ".description");
			lines(graphics, font, UiText.wrap(font, description.getString(), inner, DESCRIPTION_LINES), x, getY() + DESCRIPTION_Y,
					MinekitTheme.TEXT_MUTED);
			// Notes at the bottom; the item list gets the space between them and the controls.
			List<String> remainingNote = List.of();
			int remaining = current.remaining().values().stream().mapToInt(Integer::intValue).sum();
			if (remaining > 0) {
				remainingNote = UiText.wrap(font, Component.translatable("minekit.deposit.remaining", remaining).getString(), inner, 2);
			}
			List<String> keptNote = List.of();
			int kept = current.kept().values().stream().mapToInt(Integer::intValue).sum();
			if (kept > 0) {
				keptNote = UiText.wrap(font, Component.translatable("minekit.deposit.kept", kept).getString(), inner, 2);
			}
			int notesTop = getBottom() - 6 - (remainingNote.size() + keptNote.size()) * MinekitTheme.LINE_HEIGHT;
			int y = contentTop(current);
			if (current.refusal().isPresent()) {
				Component reason = Component.translatable("minekit.deposit.refused." + current.refusal().get().name().toLowerCase(java.util.Locale.ROOT));
				y = lines(graphics, font, UiText.wrap(font, reason.getString(), inner, 4), x, y, MinekitTheme.WARNING);
			} else {
				Component summary = Component.translatable("minekit.deposit.summary", current.movedTotal(), current.moved().size());
				y = lines(graphics, font, UiText.wrap(font, summary.getString(), inner, 2), x, y, MinekitTheme.TEXT) + 2;
				int shown = 0;
				int total = current.moved().size();
				for (Map.Entry<ItemKey, Integer> entry : current.moved().entrySet()) {
					boolean last = shown == total - 1;
					// The last row needs only its own height; any other also needs room for the "and n more" line.
					if (y + ROW + (last ? 0 : MinekitTheme.LINE_HEIGHT) > notesTop - 2) {
						UiText.draw(graphics, font, Component.translatable("minekit.deposit.more", total - shown), x, y + 2, inner,
								MinekitTheme.TEXT_MUTED);
						break;
					}
					graphics.item(keys == null ? net.minecraft.world.item.ItemStack.EMPTY : keys.displayStack(entry.getKey()), x, y);
					Component count = Component.literal(entry.getValue() + "×");
					int countWidth = font.width(count);
					UiText.draw(graphics, font, keys == null ? Component.literal(entry.getKey().itemId()) : keys.displayStack(entry.getKey()).getHoverName(),
							x + 20, y + 4, inner - 26 - countWidth, MinekitTheme.TEXT);
					UiText.drawRightAligned(graphics, font, count, x + inner, y + 4, countWidth, MinekitTheme.TEXT_MUTED);
					y += ROW;
					shown++;
				}
			}
			int notes = lines(graphics, font, remainingNote, x, notesTop, MinekitTheme.WARNING);
			lines(graphics, font, keptNote, x, notes, MinekitTheme.TEXT_MUTED);
		}

		/** The explanation: paragraphs from below the title down to the bottom of the panel, as far as they fit. */
		private void extractHelp(GuiGraphicsExtractor graphics, Font font, int x, int inner) {
			int y = getY() + PROFILE_Y;
			int bottom = getBottom() - 6;
			for (String key : HELP) {
				int room = (bottom - y) / LINE;
				if (room <= 0) {
					return;
				}
				int color = key.equals(HELP[0]) ? MinekitTheme.TEXT : MinekitTheme.TEXT_MUTED;
				y = lines(graphics, font, UiText.wrap(font, Component.translatable(key).getString(), inner, room), x, y, color) + 4;
			}
		}

		/** Draws pre-wrapped lines and returns the y below them. */
		private int lines(GuiGraphicsExtractor graphics, Font font, List<String> lines, int x, int y, int color) {
			for (String line : lines) {
				graphics.text(font, line, x, y, color, false);
				y += MinekitTheme.LINE_HEIGHT;
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
