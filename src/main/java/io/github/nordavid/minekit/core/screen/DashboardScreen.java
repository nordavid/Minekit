package io.github.nordavid.minekit.core.screen;

import io.github.nordavid.minekit.MinekitRuntime;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.NavButton;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Main MINEKIT screen: a sidebar with the visible {@link DashboardPage}s and a scrolling content area.
 */
public final class DashboardScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 460;
	private static final int MAX_PANEL_HEIGHT = 290;
	private static final int TITLE_HEIGHT = 18;

	/** Page shown when the dashboard is opened without an explicit page; remembered for the session. */
	private static String lastPageId = OverviewPage.ID;

	private final MinekitRuntime runtime;
	private final @Nullable Screen parent;
	private final ContentPane content;
	private String pageId;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int sidebarWidth;
	private int contentX;
	private int contentY;
	private int contentWidth;
	private int contentHeight;

	public DashboardScreen(MinekitRuntime runtime, @Nullable Screen parent, @Nullable String pageId) {
		super(Component.translatable("minekit.dashboard.title"));
		this.runtime = Objects.requireNonNull(runtime, "runtime");
		this.parent = parent;
		this.pageId = pageId != null ? pageId : lastPageId;
		this.content = new ContentPane(font);
	}

	/** Opens the dashboard on top of the game. {@code pageId} may be null to reopen the last page. */
	public static void open(MinekitRuntime runtime, @Nullable String pageId) {
		Minecraft.getInstance().gui.setScreen(new DashboardScreen(runtime, null, pageId));
	}

	/** Id of the page currently shown. */
	public String pageId() {
		return pageId;
	}

	@Override
	protected void init() {
		int margin = width < 360 || height < 240 ? 6 : 16;
		panelWidth = Math.min(width - 2 * margin, MAX_PANEL_WIDTH);
		panelHeight = Math.min(height - 2 * margin, MAX_PANEL_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		sidebarWidth = Math.clamp(panelWidth / 4, 80, 112);
		contentX = panelX + sidebarWidth + 12;
		contentY = panelY + 10;
		contentWidth = panelX + panelWidth - 10 - contentX;
		contentHeight = panelY + panelHeight - 10 - contentY;

		List<DashboardPage> pages = runtime.dashboardPages().visible();
		DashboardPage page = pages.stream().filter(candidate -> candidate.id().equals(pageId)).findFirst()
				.orElse(pages.getFirst());
		pageId = page.id();

		// The sidebar entries share the height above the Done button; on small windows they get closer together.
		int navY = panelY + 36;
		int navSpace = panelY + panelHeight - 6 - MinekitTheme.ROW_HEIGHT - 4 - navY;
		int step = Math.clamp(navSpace / Math.max(1, pages.size()), 13, MinekitTheme.ROW_HEIGHT + 2);
		int navHeight = Math.min(MinekitTheme.ROW_HEIGHT, step - 1);
		for (DashboardPage candidate : pages) {
			addRenderableWidget(new NavButton(panelX + 4, navY, sidebarWidth - 8, navHeight, candidate.title(),
					() -> pageId.equals(candidate.id()), () -> selectPage(candidate.id())));
			navY += step;
		}
		addRenderableWidget(new FlatButton(panelX + 6, panelY + panelHeight - 6 - MinekitTheme.ROW_HEIGHT, sidebarWidth - 12,
				MinekitTheme.ROW_HEIGHT, CommonComponents.GUI_DONE, button -> onClose()));

		content.reset(contentX, contentY + TITLE_HEIGHT, contentWidth, contentHeight - TITLE_HEIGHT);
		page.build(new DashboardPage.Builder() {
			@Override
			public ContentPane content() {
				return content;
			}

			@Override
			public <T extends AbstractWidget> T widget(T widget) {
				return content.widget(addRenderableWidget(widget));
			}

			@Override
			public void labeled(Component label, AbstractWidget... widgets) {
				for (AbstractWidget widget : widgets) {
					addRenderableWidget(widget);
				}
				content.labeled(label, widgets);
			}

			@Override
			public void line(@Nullable ItemStack icon, Component label, @Nullable Component status, int statusColor,
					AbstractWidget... widgets) {
				for (AbstractWidget widget : widgets) {
					addRenderableWidget(widget);
				}
				content.line(icon, label, status, statusColor, widgets);
			}

			@Override
			public void rebuildLater() {
				minecraft.execute(() -> {
					if (minecraft.gui.screen() == DashboardScreen.this) {
						rebuildWidgets();
					}
				});
			}

			@Override
			public Screen screen() {
				return DashboardScreen.this;
			}
		});
		content.layout();
	}

	private void selectPage(String target) {
		if (target.equals(pageId)) {
			return;
		}
		pageId = target;
		lastPageId = target;
		content.scrollToTop();
		rebuildWidgets();
	}

	private Component currentTitle() {
		return runtime.dashboardPages().byId(pageId).map(DashboardPage::title).orElse(Component.empty());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);
		graphics.fill(panelX + 1, panelY + 1, panelX + sidebarWidth, panelY + panelHeight - 1, MinekitTheme.SIDEBAR);
		graphics.verticalLine(panelX + sidebarWidth, panelY + 1, panelY + panelHeight - 2, MinekitTheme.PANEL_BORDER);

		graphics.text(font, "MINEKIT", panelX + 10, panelY + 10, MinekitTheme.ACCENT, false);
		UiText.draw(graphics, font, Component.literal(runtime.versions().mod()), panelX + 10, panelY + 21, sidebarWidth - 14,
				MinekitTheme.TEXT_MUTED);

		UiText.draw(graphics, font, currentTitle(), contentX, contentY + 2, contentWidth, MinekitTheme.TEXT);
		content.extract(graphics, mouseX, mouseY);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return content.mouseClicked(event) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return content.mouseDragged(event) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		// Always let the screen see the release too, so its own drag state ends.
		boolean ended = content.mouseReleased(event);
		return super.mouseReleased(event) || ended;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return content.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (runtime.dashboardKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}
}
