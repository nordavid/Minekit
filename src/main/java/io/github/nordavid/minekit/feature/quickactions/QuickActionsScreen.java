package io.github.nordavid.minekit.feature.quickactions;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.action.QuickAction;
import io.github.nordavid.minekit.core.action.QuickActionRegistry;
import io.github.nordavid.minekit.core.notification.NotificationService;
import io.github.nordavid.minekit.core.persistence.JsonFileStore;
import io.github.nordavid.minekit.core.search.TextSearch;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compact, searchable popup listing Quick Actions. Typing filters, Up/Down select, Enter runs, Esc closes.
 * Unavailable actions stay visible but disabled, with the reason next to them.
 *
 * <p>Without a search the list starts with the pinned actions (numbered: the keys 1 to 9 run them), then the recently
 * used ones. The star on each row, or Ctrl+P, pins and unpins an action.
 */
public final class QuickActionsScreen extends Screen {
	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	private static final int MAX_PANEL_WIDTH = 260;
	private static final int ROW_HEIGHT = 20;
	/** Unavailable actions show their reason on a second line. */
	private static final int UNAVAILABLE_ROW_HEIGHT = 28;
	private static final int MAX_VISIBLE_ROWS = 8;
	private static final int HEADER_HEIGHT = 32;
	private static final int FOOTER_HEIGHT = 18;

	private final QuickActionRegistry registry;
	private final JsonFileStore<QuickActionSettings> settings;
	private final NotificationService notifications;
	private final List<ActionRow> rows = new ArrayList<>();
	private List<QuickAction> matches = List.of();
	private int pinnedShown;
	/**
	 * A pinned action chosen with a digit key. It runs on the next tick: the character event of that key arrives right
	 * after the key event and must be swallowed here, not reach the search box of the screen the action opens.
	 */
	private int pendingRun = -1;
	private String query = "";
	private int selected;
	private int firstVisible;
	private @Nullable EditBox search;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int listHeight = ROW_HEIGHT;

	public QuickActionsScreen(QuickActionRegistry registry, JsonFileStore<QuickActionSettings> settings, NotificationService notifications) {
		super(Component.translatable("minekit.quick_actions.title"));
		this.registry = Objects.requireNonNull(registry, "registry");
		this.settings = Objects.requireNonNull(settings, "settings");
		this.notifications = Objects.requireNonNull(notifications, "notifications");
	}

	@Override
	protected void init() {
		panelWidth = Math.min(MAX_PANEL_WIDTH, width - 16);
		panelX = (width - panelWidth) / 2;
		panelY = Math.max(8, height / 4);

		search = new EditBox(font, panelX + 13, panelY + 12, panelWidth - 26, 10, Component.translatable("minekit.quick_actions.search"));
		search.setBordered(false);
		search.setMaxLength(64);
		search.setTextColor(MinekitTheme.TEXT);
		search.setHint(Component.translatable("minekit.quick_actions.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			selected = 0;
			firstVisible = 0;
			refreshRows();
		});
		addRenderableWidget(search);
		setInitialFocus(search);
		refreshRows();
	}

	/** Position and size of the row showing {@code actionId} (x, y, width, height), or {@code null}. For tests. */
	public int @Nullable [] rowBounds(String actionId) {
		for (int i = 0; i < rows.size(); i++) {
			if (matches.get(firstVisible + i).id().equals(actionId)) {
				ActionRow row = rows.get(i);
				return new int[] {row.getX(), row.getY(), row.getWidth(), row.getHeight()};
			}
		}
		return null;
	}

	/** Actions currently listed, in display order. Exposed for tests. */
	public List<QuickAction> matches() {
		return matches;
	}

	private void refreshRows() {
		rows.forEach(this::removeWidget);
		rows.clear();
		QuickActionSettings arrangement = settings.current();
		if (query.isBlank()) {
			matches = arrangement.arrange(registry.actions(), QuickAction::id);
			pinnedShown = arrangement.pinnedCount(matches, QuickAction::id);
		} else {
			matches = TextSearch.filter(registry.actions(), QuickAction::searchableText, query);
			pinnedShown = 0;
		}
		selected = matches.isEmpty() ? 0 : Math.clamp(selected, 0, matches.size() - 1);
		if (selected < firstVisible) {
			firstVisible = selected;
		} else if (selected >= firstVisible + MAX_VISIBLE_ROWS) {
			firstVisible = selected - MAX_VISIBLE_ROWS + 1;
		}

		int y = panelY + HEADER_HEIGHT;
		int end = Math.min(matches.size(), firstVisible + MAX_VISIBLE_ROWS);
		for (int i = firstVisible; i < end; i++) {
			int index = i;
			QuickAction action = matches.get(i);
			Optional<Component> reason = action.unavailableReason().get();
			int rowHeight = reason.isEmpty() ? ROW_HEIGHT : UNAVAILABLE_ROW_HEIGHT;
			String number = i < Math.min(pinnedShown, QuickActionSettings.NUMBERED) ? String.valueOf(i + 1) : null;
			ActionRow row = new ActionRow(panelX + 6, y, panelWidth - 12, rowHeight, action.name(), reason.orElse(null),
					() -> index == selected, () -> run(index), number, arrangement.isPinned(action.id()), () -> togglePin(action));
			row.active = reason.isEmpty();
			addRenderableWidget(row);
			rows.add(row);
			y += rowHeight;
		}
		listHeight = Math.max(ROW_HEIGHT, y - (panelY + HEADER_HEIGHT));
		if (search != null) {
			setFocused(search);
		}
	}

	private void moveSelection(int delta) {
		if (matches.isEmpty()) {
			return;
		}
		selected = Math.floorMod(selected + delta, matches.size());
		refreshRows();
	}

	private void run(int index) {
		if (index < 0 || index >= matches.size()) {
			return;
		}
		QuickAction action = matches.get(index);
		Optional<Component> reason = action.unavailableReason().get();
		if (reason.isPresent()) {
			notifications.warning(reason.get());
			return;
		}
		settings.update(current -> current.withUsed(action.id()));
		minecraft.gui.setScreen(null);
		try {
			action.run().run();
		} catch (RuntimeException e) {
			LOGGER.error("Quick action '{}' failed", action.id(), e);
			notifications.error(Component.translatable("minekit.notification.action_failed", action.name()));
		}
	}

	private void togglePin(QuickAction action) {
		settings.update(current -> current.withPinToggled(action.id()));
		if (settings.lastSaveFailed()) {
			notifications.error(Component.translatable("minekit.notification.save_failed"));
		}
		refreshRows();
		int index = matches.indexOf(action);
		if (index >= 0) {
			selected = index;
			refreshRows();
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (query.isBlank() && !isControlDown(event) && !event.hasAltDown() && key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			int index = key - InputConstants.KEY_1;
			if (index < Math.min(pinnedShown, QuickActionSettings.NUMBERED)) {
				pendingRun = index;
				return true;
			}
		}
		if (isControlDown(event) && key == InputConstants.KEY_P) {
			if (selected >= 0 && selected < matches.size()) {
				togglePin(matches.get(selected));
			}
			return true;
		}
		if (event.isDown()) {
			moveSelection(1);
			return true;
		}
		if (event.isUp()) {
			moveSelection(-1);
			return true;
		}
		if (event.isConfirmation()) {
			run(selected);
			return true;
		}
		return super.keyPressed(event);
	}

	/** The event's modifier flag, or the held key itself (synthetic key events may carry no modifier flags). */
	private static boolean isControlDown(KeyEvent event) {
		return event.hasControlDown() || InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return pendingRun >= 0 || super.charTyped(event);
	}

	@Override
	public void tick() {
		super.tick();
		if (pendingRun >= 0) {
			int index = pendingRun;
			pendingRun = -1;
			run(index);
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// Keep the world visible behind the popup instead of blurring it.
		extractTransparentBackground(graphics);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int panelHeight = HEADER_HEIGHT + listHeight + FOOTER_HEIGHT;
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);

		boolean searchFocused = search != null && search.isFocused();
		graphics.fill(panelX + 6, panelY + 6, panelX + panelWidth - 6, panelY + 26, MinekitTheme.SURFACE);
		graphics.outline(panelX + 6, panelY + 6, panelWidth - 12, 20, searchFocused ? MinekitTheme.ACCENT : MinekitTheme.BORDER);

		int listTop = panelY + HEADER_HEIGHT;
		if (matches.isEmpty()) {
			UiText.drawCentered(graphics, font, Component.translatable("minekit.quick_actions.empty"), panelX + panelWidth / 2,
					listTop + 6, panelWidth - 16, MinekitTheme.TEXT_MUTED);
		}
		UiText.drawCentered(graphics, font, Component.translatable("minekit.quick_actions.hint"), panelX + panelWidth / 2,
				listTop + listHeight + 5, panelWidth - 16, MinekitTheme.TEXT_DISABLED);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** One action. The star at the right end pins or unpins it; a click anywhere else runs it. */
	private static final class ActionRow extends Button {
		private static final int STAR_WIDTH = 16;
		private static final int NUMBER_WIDTH = 12;

		private final @Nullable Component unavailableReason;
		private final BooleanSupplier selected;
		private final @Nullable String number;
		private final boolean pinned;
		private final Runnable onPin;

		ActionRow(int x, int y, int width, int height, Component name, @Nullable Component unavailableReason,
				BooleanSupplier selected, Runnable onRun, @Nullable String number, boolean pinned, Runnable onPin) {
			super(x, y, width, height, name, button -> onRun.run(), DEFAULT_NARRATION);
			this.unavailableReason = unavailableReason;
			this.selected = selected;
			this.number = number;
			this.pinned = pinned;
			this.onPin = onPin;
		}

		private boolean inside(double mouseX, double mouseY) {
			return mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getBottom();
		}

		private boolean overStar(double mouseX) {
			return mouseX >= getRight() - STAR_WIDTH;
		}

		/** Unavailable actions cannot run, but can still be pinned. */
		@Override
		public boolean isMouseOver(double mouseX, double mouseY) {
			return visible && (active || overStar(mouseX)) && inside(mouseX, mouseY);
		}

		@Override
		public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
			if (visible && event.button() == InputConstants.MOUSE_BUTTON_LEFT && inside(event.x(), event.y())
					&& overStar(event.x())) {
				playDownSound(Minecraft.getInstance().getSoundManager());
				onPin.run();
				return true;
			}
			return super.mouseClicked(event, doubleClick);
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			if (selected.getAsBoolean()) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_SELECTED);
				graphics.fill(getX(), getY(), getX() + 2, getBottom(), MinekitTheme.ACCENT);
			} else if (isHovered()) {
				graphics.fill(getX(), getY(), getRight(), getBottom(), MinekitTheme.SURFACE_HOVER);
			}
			var font = Minecraft.getInstance().font;
			int color = active ? MinekitTheme.TEXT : MinekitTheme.TEXT_DISABLED;
			int textX = getX() + 8;
			if (number != null) {
				graphics.text(font, number, textX, getY() + (unavailableReason == null ? (getHeight() - 8) / 2 : 4), MinekitTheme.ACCENT, false);
				textX += NUMBER_WIDTH;
			}
			int textWidth = getRight() - STAR_WIDTH - 4 - textX;
			if (unavailableReason == null) {
				UiText.draw(graphics, font, getMessage(), textX, getY() + (getHeight() - 8) / 2, textWidth, color);
			} else {
				UiText.draw(graphics, font, getMessage(), textX, getY() + 4, textWidth, color);
				UiText.draw(graphics, font, unavailableReason, textX, getY() + 15, textWidth, MinekitTheme.WARNING);
			}
			boolean starHovered = isHovered() && overStar(mouseX);
			String star = pinned ? "\u2605" : "\u2606";
			int starColor = pinned ? MinekitTheme.ACCENT : starHovered ? MinekitTheme.TEXT : MinekitTheme.TEXT_DISABLED;
			if (pinned || starHovered || selected.getAsBoolean()) {
				graphics.text(font, star, getRight() - STAR_WIDTH + (STAR_WIDTH - font.width(star)) / 2, getY() + (getHeight() - 8) / 2,
						starColor, false);
			}
		}

		@Override
		public void updateWidgetNarration(NarrationElementOutput output) {
			super.updateWidgetNarration(output);
			if (unavailableReason != null) {
				output.add(NarratedElementType.HINT, unavailableReason);
			}
		}
	}
}
