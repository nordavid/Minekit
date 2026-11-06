package io.github.nordavid.minekit.feature.storage;

import io.github.nordavid.minekit.core.container.ContainerTracker;
import io.github.nordavid.minekit.core.container.ContainerTypes;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.ItemButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import io.github.nordavid.minekit.core.search.TextSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * The Storage Finder: items known from opened containers on the left, the containers holding the selected item on the
 * right with coordinates, distance and the age of the observation.
 *
 * <p>Every number is "as last seen". Records are colored by age, and a container whose block is visibly gone (its
 * chunk is loaded and another block stands there) is marked so, but never removed without the player's say.
 */
public final class StorageFinderScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 580;
	private static final int MAX_PANEL_HEIGHT = 320;
	private static final int MAX_RESULTS = 300;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;
	private static final int NOTE_LINES = 3;

	private final StorageFinderModule module;
	private final ContentPane results;
	private final ContentPane details;
	private final List<AbstractWidget> resultWidgets = new ArrayList<>();
	private final List<AbstractWidget> detailWidgets = new ArrayList<>();
	private @Nullable String selected;
	private String query = "";
	private StorageView.Filter filter = new StorageView.Filter(StorageView.TypeFilter.ALL, false);

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int leftWidth;
	private int rightX;
	private int rightWidth;

	public StorageFinderScreen(StorageFinderModule module, @Nullable String selected) {
		super(Component.translatable("minekit.storage.title"));
		this.module = Objects.requireNonNull(module, "module");
		this.selected = selected;
		this.results = new ContentPane(font);
		this.details = new ContentPane(font);
	}

	/** The selected item, or {@code null} for "all containers". For tests. */
	public @Nullable String selected() {
		return selected;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - 12, MAX_PANEL_WIDTH);
		panelHeight = Math.min(height - 12, MAX_PANEL_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		leftWidth = Math.clamp((int) (panelWidth * 0.38), 120, 210);
		rightX = panelX + 8 + leftWidth + 10;
		rightWidth = panelX + panelWidth - 8 - rightX;

		addRenderableWidget(new FlatButton(panelX + panelWidth - 68, panelY + 6, 60, ROW, CommonComponents.GUI_DONE, button -> onClose()));

		EditBox search = new EditBox(font, panelX + 13, panelY + 33, leftWidth - 10, 10, Component.translatable("minekit.storage.search"));
		search.setBordered(false);
		search.setMaxLength(64);
		search.setTextColor(MinekitTheme.TEXT);
		search.setHint(Component.translatable("minekit.storage.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			results.scrollToTop();
			rebuildResults();
		});
		addRenderableWidget(search);
		setInitialFocus(search);

		int filterWidth = (rightWidth - 4) / 2;
		addRenderableWidget(new FlatButton(rightX, panelY + 28, filterWidth, ROW,
				Component.translatable("minekit.storage.filter", Component.translatable(filter.type().translationKey())), button -> {
					filter = new StorageView.Filter(filter.type().next(), filter.thisDimension());
					refreshLater();
				}));
		addRenderableWidget(new ToggleButton(rightX + filterWidth + 4, panelY + 28, rightWidth - filterWidth - 4, ROW,
				Component.translatable("minekit.storage.this_dimension"), filter.thisDimension(), requested -> {
					filter = new StorageView.Filter(filter.type(), requested);
					refreshLater();
					return requested;
				}));

		rebuildResults();
		rebuildDetails();
	}

	private StorageIndex index() {
		return module.index().orElse(StorageIndex.empty(""));
	}

	private StorageView.Viewer viewer() {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return new StorageView.Viewer("", 0, 0, 0);
		}
		return new StorageView.Viewer(player.level().dimension().identifier().toString(), player.getX(), player.getY(), player.getZ());
	}

	private void rebuildResults() {
		resultWidgets.forEach(this::removeWidget);
		resultWidgets.clear();
		int top = panelY + 50;
		results.reset(panelX + 8, top, leftWidth, panelY + panelHeight - 8 - top);
		StorageIndex index = index();
		SortedMap<String, Long> totals = StorageView.itemTotals(index, filter, viewer());

		ItemButton all = new ItemButton(0, 0, results.width(), ROW, new ItemStack(Items.CHEST), Component.translatable("minekit.storage.all_containers"),
				() -> selected == null, () -> select(null));
		all.withDetail(Component.literal(String.valueOf(StorageView.containers(index, null, filter, viewer()).size())));
		resultWidgets.add(results.widget(addRenderableWidget(all)));

		List<String> matches = TextSearch.filter(new ArrayList<>(totals.keySet()), item -> ItemIds.name(item).getString() + " " + item, query);
		for (String item : matches.subList(0, Math.min(matches.size(), MAX_RESULTS))) {
			ItemButton button = new ItemButton(0, 0, results.width(), ROW, ItemIds.icon(item), ItemIds.name(item),
					() -> item.equals(selected), () -> select(item));
			button.withDetail(Component.literal(String.valueOf(totals.get(item))));
			resultWidgets.add(results.widget(addRenderableWidget(button)));
		}
		if (index.containers().isEmpty()) {
			results.text(Component.translatable("minekit.storage.empty"), MinekitTheme.TEXT_MUTED);
		} else if (matches.isEmpty() && !query.isBlank()) {
			results.text(Component.translatable("minekit.storage.no_match"), MinekitTheme.TEXT_MUTED);
		} else if (matches.size() > MAX_RESULTS) {
			results.text(Component.translatable("minekit.storage.more_results", matches.size() - MAX_RESULTS), MinekitTheme.TEXT_MUTED);
		}
		results.layout();
	}

	private void rebuildDetails() {
		detailWidgets.forEach(this::removeWidget);
		detailWidgets.clear();
		int top = panelY + 28 + ROW + 6;
		details.reset(rightX, top, rightWidth, panelY + panelHeight - 8 - top - NOTE_LINES * MinekitTheme.LINE_HEIGHT);
		StorageIndex index = index();
		StorageView.Viewer viewer = viewer();
		List<ContainerRecord> containers = StorageView.containers(index, selected, filter, viewer);
		long now = System.currentTimeMillis();

		if (selected != null) {
			long total = containers.stream().mapToLong(record -> record.count(selected)).sum();
			details.heading(Component.translatable("minekit.storage.item_heading", ItemIds.name(selected), total, containers.size()));
		} else {
			details.heading(Component.translatable("minekit.storage.containers_heading", containers.size()));
		}
		if (containers.isEmpty()) {
			details.text(Component.translatable(index.containers().isEmpty() ? "minekit.storage.how_to" : "minekit.storage.none_for_filter"),
					MinekitTheme.TEXT_MUTED);
		}
		for (ContainerRecord record : containers) {
			boolean gone = isGone(record);
			MutableComponent status = Component.empty();
			if (selected != null) {
				status.append(Component.literal(record.count(selected) + "× · "));
			} else {
				// How full it was: used slots of all slots.
				status.append(Component.literal(record.usedSlots() + "/" + record.slots() + " · "));
			}
			if (gone) {
				status.append(Component.translatable("minekit.storage.gone"));
			} else {
				StorageView.Age age = StorageView.Age.of(now - record.observedAt());
				status.append(Component.translatable(age.translationKey(), age.amount()));
			}
			int color = gone ? MinekitTheme.ERROR : switch (record.freshness(now)) {
				case RECENT -> MinekitTheme.SUCCESS;
				case OLDER -> MinekitTheme.TEXT_MUTED;
				case STALE -> MinekitTheme.WARNING;
			};
			FlatButton forget = new FlatButton(0, 0, 20, ROW, Component.literal("✕"), button -> {
				module.forget(record.key());
				refreshLater();
			});
			forget.setTooltip(Tooltip.create(Component.translatable("minekit.storage.forget_tooltip")));
			detailWidgets.add(addRenderableWidget(forget));
			details.line(containerIcon(record), describe(record, viewer), status, color, forget);
		}
		details.layout();
	}

	/**
	 * Coordinates and distance (or dimension): what the player needs to walk there. The icon shows the container type,
	 * so the name is left out to keep the row short on narrow screens.
	 */
	private Component describe(ContainerRecord record, StorageView.Viewer viewer) {
		if (record.isEnderChest()) {
			return containerName(record).copy().append(Component.literal(" · ")).append(Component.translatable("minekit.storage.ender_everywhere"));
		}
		MutableComponent text = Component.literal(record.positions().getFirst().toString());
		double distance = viewer.distanceTo(record);
		text.append(Component.literal(" · ")).append(distance >= 0
				? Component.translatable("minekit.storage.distance", Math.round(distance))
				: dimensionName(record.dimension()));
		if (record.positions().size() == 2) {
			text.append(Component.translatable("minekit.storage.double"));
		}
		return text;
	}

	private static Component dimensionName(String dimension) {
		String key = "minekit.storage.dimension." + dimension.replace(':', '.');
		return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : Component.literal(dimension);
	}

	private static Component containerName(ContainerRecord record) {
		Block block = block(record.type());
		return block == Blocks.AIR ? Component.literal(record.type()) : block.getName();
	}

	private static ItemStack containerIcon(ContainerRecord record) {
		Block block = block(record.type());
		return block == Blocks.AIR || block.asItem() == Items.AIR ? new ItemStack(Items.CHEST) : new ItemStack(block.asItem());
	}

	private static Block block(String id) {
		Identifier identifier = Identifier.tryParse(id);
		return identifier != null && BuiltInRegistries.BLOCK.containsKey(identifier) ? BuiltInRegistries.BLOCK.getValue(identifier) : Blocks.AIR;
	}

	/** True only if the client can see that the container's block is no longer there. */
	private boolean isGone(ContainerRecord record) {
		ClientLevel level = minecraft.level;
		if (level == null || record.isEnderChest() || !record.dimension().equals(level.dimension().identifier().toString())) {
			return false;
		}
		for (ContainerRecord.Pos pos : record.positions()) {
			BlockPos blockPos = new BlockPos(pos.x(), pos.y(), pos.z());
			if (!level.isLoaded(blockPos)) {
				return false;
			}
			Block block = level.getBlockState(blockPos).getBlock();
			if (ContainerTracker.kindOf(block) == null
					|| !ContainerTypes.normalize(BuiltInRegistries.BLOCK.getKey(block).toString()).equals(record.type())) {
				return true;
			}
		}
		return false;
	}

	public void select(@Nullable String item) {
		selected = item;
		refreshLater();
	}

	/** Rebuilds after the current click, because rebuilding removes the clicked button. */
	private void refreshLater() {
		minecraft.execute(() -> {
			if (minecraft.gui.screen() == this) {
				details.scrollToTop();
				rebuildWidgets();
			}
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);
		MutableComponent heading = title.copy();
		String label = index().label();
		if (!label.isBlank()) {
			heading.append(Component.literal(" · " + label).withStyle(ChatFormatting.GRAY));
		}
		UiText.draw(graphics, font, heading, panelX + 10, panelY + 11, panelWidth - 90, MinekitTheme.ACCENT);
		graphics.verticalLine(rightX - 6, panelY + 26, panelY + panelHeight - 6, MinekitTheme.PANEL_BORDER);

		graphics.fill(panelX + 8, panelY + 28, panelX + 8 + leftWidth, panelY + 46, MinekitTheme.SURFACE);
		graphics.outline(panelX + 8, panelY + 28, leftWidth, 18, MinekitTheme.BORDER);
		results.extract(graphics, mouseX, mouseY);
		details.extract(graphics, mouseX, mouseY);

		List<String> note = UiText.wrap(font, Component.translatable("minekit.storage.limitation").getString(), rightWidth, NOTE_LINES);
		int noteY = panelY + panelHeight - 6 - note.size() * MinekitTheme.LINE_HEIGHT;
		for (int i = 0; i < note.size(); i++) {
			graphics.text(font, note.get(i), rightX, noteY + i * MinekitTheme.LINE_HEIGHT, MinekitTheme.TEXT_MUTED, false);
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return results.mouseClicked(event) || details.mouseClicked(event) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return results.mouseDragged(event) || details.mouseDragged(event) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		// Always let the screen see the release too, so its own drag state ends.
		boolean ended = results.mouseReleased(event) | details.mouseReleased(event);
		return super.mouseReleased(event) || ended;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return results.mouseScrolled(mouseX, mouseY, scrollY) || details.mouseScrolled(mouseX, mouseY, scrollY)
				|| super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** Totals per item for the current filter, for tests. */
	public Map<String, Long> totals() {
		return StorageView.itemTotals(index(), filter, viewer());
	}
}
