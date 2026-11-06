package io.github.nordavid.minekit.feature.blueprint;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nordavid.minekit.core.item.InventoryCounts;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.ItemButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.NavButton;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import io.github.nordavid.minekit.core.storage.StorageBridge;
import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The blueprint screen: files on the left (drop files onto the window to import them); on the right the active
 * blueprint with three tabs: overview (placement, progress, next block, measuring), layers (one layer from above,
 * colored by build status) and materials (what is still needed, what the player carries, what storage holds).
 */
public final class BlueprintScreen extends Screen {
	private enum Tab {
		OVERVIEW("minekit.blueprint.tab.overview"),
		LAYERS("minekit.blueprint.tab.layers"),
		MATERIALS("minekit.blueprint.tab.materials");

		private final String key;

		Tab(String key) {
			this.key = key;
		}
	}

	private static final int MAX_PANEL_WIDTH = 600;
	private static final int MAX_PANEL_HEIGHT = 330;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;
	private static final int LINE = MinekitTheme.LINE_HEIGHT;
	private static Tab tab = Tab.OVERVIEW;
	private static int layer;

	private final BlueprintModule module;
	private final ContentPane list;
	private final ContentPane pane;
	private final LayerTexture texture = new LayerTexture();
	private @Nullable ResolvedBlueprint shown;
	private boolean shownPlaced;
	private boolean deleteArmed;
	private int ticks;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int leftWidth;
	private int rightX;
	private int rightWidth;
	private int contentTop;
	private int mapX;
	private int mapY;
	private int mapWidth;
	private int mapHeight;
	private double mapScale;

	public BlueprintScreen(BlueprintModule module) {
		super(Component.translatable("minekit.blueprint.title"));
		this.module = Objects.requireNonNull(module, "module");
		this.list = new ContentPane(font);
		this.pane = new ContentPane(font);
	}

	/** The blueprint shown on the right, for tests. */
	public Optional<ResolvedBlueprint> shown() {
		return Optional.ofNullable(shown);
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - 8, MAX_PANEL_WIDTH);
		panelHeight = Math.min(height - 4, MAX_PANEL_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		leftWidth = Math.clamp((int) (panelWidth * 0.3), 110, 170);
		rightX = panelX + 8 + leftWidth + 10;
		rightWidth = panelX + panelWidth - 8 - rightX;
		shown = module.active().orElse(null);
		shownPlaced = module.placement().isPresent();

		addRenderableWidget(new FlatButton(panelX + panelWidth - 68, panelY + 6, 60, ROW, CommonComponents.GUI_DONE, button -> onClose()));
		FlatButton folder = addRenderableWidget(new FlatButton(panelX + 8, panelY + panelHeight - 8 - ROW, leftWidth, ROW,
				Component.translatable("minekit.blueprint.open_folder"), button -> openFolder()));
		folder.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.open_folder_tooltip")));
		buildList();

		if (shown == null) {
			return;
		}
		contentTop = panelY + 50 + ROW + 6;
		int tabWidth = Math.min(90, (rightWidth - 8) / 3);
		for (int i = 0; i < Tab.values().length; i++) {
			Tab candidate = Tab.values()[i];
			addRenderableWidget(new NavButton(rightX + i * (tabWidth + 4), panelY + 50, tabWidth, ROW, Component.translatable(candidate.key),
					() -> tab == candidate, () -> {
						tab = candidate;
						rebuildWidgets();
					}));
		}
		layer = Math.clamp(layer, 0, shown.blueprint().sizeY() - 1);
		pane.reset(rightX, contentTop, rightWidth, panelY + panelHeight - 8 - contentTop);
		switch (tab) {
			case OVERVIEW -> buildOverview(shown);
			case LAYERS -> buildLayers(shown);
			case MATERIALS -> buildMaterials(shown);
		}
		pane.layout();
	}

	private void openFolder() {
		try {
			java.nio.file.Files.createDirectories(module.directory());
		} catch (java.io.IOException ignored) {
			// Opening shows the error itself.
		}
		Blaze3D.openPath(module.directory());
	}

	private void buildList() {
		int top = panelY + 28;
		list.reset(panelX + 8, top, leftWidth, panelY + panelHeight - 8 - ROW - 4 - top - 2 * LINE);
		List<String> files = module.files();
		if (files.isEmpty()) {
			list.text(Component.translatable("minekit.blueprint.no_files"), MinekitTheme.TEXT_MUTED);
		}
		String active = module.settings().current().active();
		for (String file : files) {
			ItemButton row = addRenderableWidget(new ItemButton(0, 0, list.width(), ROW, new ItemStack(Items.FILLED_MAP),
					Component.literal(BlueprintFiles.baseName(Path.of(file))), () -> file.equals(active), () -> {
						module.select(file);
						rebuildWidgets();
					}));
			row.setTooltip(Tooltip.create(Component.literal(file)));
			list.widget(row);
		}
		list.layout();
	}

	// ---------------------------------------------------------------------------------------------------------- overview

	private void buildOverview(ResolvedBlueprint resolved) {
		Blueprint blueprint = resolved.blueprint();
		Optional<Placement> placement = module.placement();
		pane.heading(Component.translatable("minekit.blueprint.placement"));
		if (placement.isEmpty()) {
			pane.text(Component.translatable("minekit.blueprint.not_placed"), MinekitTheme.TEXT_MUTED);
			addWidget(new FlatButton(0, 0, pane.width(), ROW, Component.translatable("minekit.blueprint.place_here"), button -> {
				module.placeInFront();
				rebuildWidgets();
			}));
		} else {
			Placement current = placement.get();
			pane.text(Component.translatable("minekit.blueprint.placed_at", current.x(), current.y(), current.z(), current.rotation() * 90,
					Component.translatable(current.mirrored() ? "minekit.blueprint.mirrored" : "minekit.blueprint.not_mirrored")), MinekitTheme.TEXT);
			pane.labeled(Component.translatable("minekit.blueprint.move"), register(move("-X", -1, 0, 0)), register(move("+X", 1, 0, 0)),
					register(move("-Y", 0, -1, 0)), register(move("+Y", 0, 1, 0)), register(move("-Z", 0, 0, -1)), register(move("+Z", 0, 0, 1)));
			int quarter = (pane.width() - 6) / 4;
			FlatButton rotate = register(new FlatButton(0, 0, quarter, ROW, Component.translatable("minekit.blueprint.rotate"),
					button -> change(current.rotatedClockwise())));
			FlatButton mirror = register(new FlatButton(0, 0, quarter, ROW, Component.translatable("minekit.blueprint.mirror"),
					button -> change(current.mirroredToggled())));
			FlatButton here = register(new FlatButton(0, 0, quarter, ROW, Component.translatable("minekit.blueprint.here"), button -> {
				module.placeInFront();
				rebuildWidgets();
			}));
			FlatButton remove = register(new FlatButton(0, 0, quarter, ROW, Component.translatable("minekit.blueprint.remove"),
					button -> {
						module.setPlacement(null);
						rebuildWidgets();
					}));
			here.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.here_tooltip")));
			remove.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.remove_tooltip")));
			pane.line(null, null, null, 0, rotate, mirror, here, remove);
			addWidget(new ToggleButton(0, 0, pane.width(), ROW, Component.translatable("minekit.blueprint.show"), module.settings().current().show(),
					requested -> {
						module.setShow(requested);
						return module.settings().current().show();
					}));
		}

		pane.heading(Component.translatable("minekit.blueprint.progress"));
		Optional<BlueprintProgress> progress = module.progress();
		BlueprintSettings.LayerRange range = module.settings().current().range(resolved.file(), blueprint.sizeY());
		pane.text(Component.translatable("minekit.blueprint.range", range.from() + 1, range.to() + 1, blueprint.sizeY()), MinekitTheme.TEXT_MUTED);
		if (progress.isEmpty()) {
			pane.text(Component.translatable("minekit.blueprint.progress_after_placing"), MinekitTheme.TEXT_MUTED);
		} else {
			BlueprintProgress p = progress.get();
			pane.status(Component.translatable("minekit.blueprint.built"),
					Component.translatable("minekit.blueprint.built_value", Math.round(p.completion() * 100), p.count(Status.CORRECT), p.total()),
					p.completion() >= 1 ? MinekitTheme.SUCCESS : MinekitTheme.TEXT);
			pane.status(Component.translatable("minekit.blueprint.status.missing"), Component.literal(String.valueOf(p.count(Status.MISSING))),
					MinekitTheme.TEXT_MUTED);
			pane.status(Component.translatable("minekit.blueprint.status.wrong"), Component.literal(String.valueOf(p.count(Status.WRONG))),
					p.count(Status.WRONG) > 0 ? MinekitTheme.ERROR : MinekitTheme.TEXT_MUTED);
			pane.status(Component.translatable("minekit.blueprint.status.unchecked"), Component.literal(String.valueOf(p.count(Status.UNCHECKED))),
					MinekitTheme.TEXT_MUTED);
			if (p.count(Status.UNKNOWN) > 0) {
				pane.status(Component.translatable("minekit.blueprint.status.unknown"), Component.literal(String.valueOf(p.count(Status.UNKNOWN))),
						MinekitTheme.WARNING);
			}
			Optional<BlockPos> next = module.nextBlock();
			if (next.isPresent()) {
				BlockPos pos = next.get();
				pane.text(Component.translatable("minekit.blueprint.next", blockName(resolved, pos), pos.getX(), pos.getY(), pos.getZ()),
						MinekitTheme.WARNING);
			} else if (p.count(Status.UNCHECKED) > 0) {
				pane.text(Component.translatable("minekit.blueprint.next_none_loaded"), MinekitTheme.TEXT_MUTED);
			} else {
				pane.text(Component.translatable("minekit.blueprint.done"), MinekitTheme.SUCCESS);
			}
			pane.text(Component.translatable("minekit.blueprint.honest"), MinekitTheme.TEXT_DISABLED);
		}
		if (!resolved.unknownBlocks().isEmpty()) {
			pane.text(Component.translatable("minekit.blueprint.unknown_list", String.join(", ", resolved.unknownBlocks().stream().limit(4).toList())),
					MinekitTheme.WARNING);
		}

		pane.heading(Component.translatable("minekit.blueprint.measure"));
		Optional<BlockPos> a = module.measureA();
		Optional<BlockPos> b = module.measureB();
		if (a.isPresent() && b.isPresent()) {
			pane.text(BlueprintModule.measureText(a.get(), b.get()), MinekitTheme.TEXT);
		}
		pane.text(Component.translatable("minekit.measure.how_to"), MinekitTheme.TEXT_MUTED);

		pane.heading(Component.translatable("minekit.blueprint.file"));
		FlatButton delete = register(new FlatButton(0, 0, 80, ROW, Component.translatable(deleteArmed ? "minekit.blueprint.delete_confirm"
				: "minekit.blueprint.delete"), button -> {
					if (deleteArmed) {
						module.delete(resolved.file());
						deleteArmed = false;
					} else {
						deleteArmed = true;
					}
					rebuildWidgets();
				}));
		pane.line(null, Component.literal(resolved.file()), null, 0, delete);
	}

	private FlatButton move(String label, int dx, int dy, int dz) {
		FlatButton button = new FlatButton(0, 0, 24, ROW, Component.literal(label), pressed -> module.placement().ifPresent(current -> {
			int step = InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT) ? 10 : 1;
			change(current.movedBy(dx * step, dy * step, dz * step));
		}));
		button.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.move_tooltip")));
		return button;
	}

	private void change(Placement placement) {
		module.setPlacement(placement);
		rebuildWidgets();
	}

	private <T extends AbstractWidget> T register(T widget) {
		return addRenderableWidget(widget);
	}

	private void addWidget(AbstractWidget widget) {
		pane.widget(addRenderableWidget(widget));
	}

	/** Name of the block the blueprint wants at a world position. */
	private Component blockName(ResolvedBlueprint resolved, BlockPos pos) {
		Optional<Placement> placement = module.placement();
		int[] local = placement.map(p -> p.toLocal(resolved.blueprint(), pos.getX(), pos.getY(), pos.getZ())).orElse(null);
		if (local == null) {
			return Component.literal("?");
		}
		int palette = resolved.blueprint().paletteIndex(local[0], local[1], local[2]);
		BlockState state = resolved.state(palette);
		return state == null ? Component.literal(resolved.blueprint().palette().get(palette).id()) : state.getBlock().getName();
	}

	// ------------------------------------------------------------------------------------------------------------ layers

	private void buildLayers(ResolvedBlueprint resolved) {
		Blueprint blueprint = resolved.blueprint();
		int y = contentTop;
		addRenderableWidget(new FlatButton(rightX, y, 22, ROW, Component.literal("▼"), button -> stepLayer(-1)))
				.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.layer_down")));
		addRenderableWidget(new FlatButton(rightX + rightWidth - 22, y, 22, ROW, Component.literal("▲"), button -> stepLayer(1)))
				.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.layer_up")));
		BlueprintSettings.LayerRange range = module.settings().current().range(resolved.file(), blueprint.sizeY());
		int third = (rightWidth - 8) / 3;
		int buttonsY = y + ROW + 4;
		addRenderableWidget(new FlatButton(rightX, buttonsY, third, ROW, Component.translatable("minekit.blueprint.range_from"),
				button -> setRange(new BlueprintSettings.LayerRange(layer, Math.max(layer, range.to())))));
		addRenderableWidget(new FlatButton(rightX + third + 4, buttonsY, third, ROW, Component.translatable("minekit.blueprint.range_to"),
				button -> setRange(new BlueprintSettings.LayerRange(Math.min(layer, range.from()), layer))));
		addRenderableWidget(new FlatButton(rightX + 2 * (third + 4), buttonsY, rightWidth - 2 * (third + 4), ROW,
				Component.translatable("minekit.blueprint.range_all"), button -> setRange(null)));
		mapX = rightX;
		mapY = buttonsY + ROW + 6;
		mapWidth = rightWidth;
		mapHeight = panelY + panelHeight - 8 - 2 * LINE - mapY;
		texture.paint(resolved, layer, module.placement().orElse(null), module.progress().orElse(null));
	}

	private void stepLayer(int delta) {
		if (shown != null) {
			layer = Math.clamp(layer + delta, 0, shown.blueprint().sizeY() - 1);
			rebuildWidgets();
		}
	}

	private void setRange(BlueprintSettings.@Nullable LayerRange range) {
		module.setRange(range);
		rebuildWidgets();
	}

	/** Local position (x, z) of the map pixel under the mouse, or {@code null}. */
	private int @Nullable [] mapCell(double mouseX, double mouseY) {
		if (shown == null || tab != Tab.LAYERS || mapScale <= 0) {
			return null;
		}
		int px = (int) ((mouseX - mapX) / mapScale);
		int pz = (int) ((mouseY - mapY) / mapScale);
		if (mouseX < mapX || mouseY < mapY || px >= texture.width() || pz >= texture.height()) {
			return null;
		}
		Optional<Placement> placement = module.placement();
		if (placement.isEmpty()) {
			return new int[] {px, pz};
		}
		int[] local = placement.get().toLocal(shown.blueprint(), placement.get().x() + px, placement.get().y() + layer, placement.get().z() + pz);
		return local == null ? null : new int[] {local[0], local[2]};
	}

	// --------------------------------------------------------------------------------------------------------- materials

	/** One item to place, with everything known about where to get it. */
	private record Material(Item item, long needed, long placed, int carried, Optional<StorageBridge.Known> stored) {
		long toPlace() {
			return Math.max(0, needed - placed);
		}

		long missing() {
			return Math.max(0, toPlace() - carried);
		}
	}

	private void buildMaterials(ResolvedBlueprint resolved) {
		Blueprint blueprint = resolved.blueprint();
		BlueprintSettings.LayerRange range = module.settings().current().range(resolved.file(), blueprint.sizeY());
		BlueprintProgress progress = module.progress().filter(p -> p.blueprint() == blueprint).orElseGet(() -> new BlueprintProgress(blueprint, range));
		Map<String, Integer> carried = minecraft.player == null ? Map.of() : InventoryCounts.of(minecraft.player);
		Map<Item, long[]> byItem = new LinkedHashMap<>();
		long withoutItem = 0;
		for (int i = 0; i < blueprint.palette().size(); i++) {
			long needed = progress.needed(i);
			if (needed == 0) {
				continue;
			}
			Item item = resolved.item(i);
			if (item == Items.AIR) {
				withoutItem += needed;
				continue;
			}
			long[] counts = byItem.computeIfAbsent(item, ignored -> new long[2]);
			counts[0] += needed;
			counts[1] += progress.placed(i);
		}
		List<Material> materials = new ArrayList<>();
		byItem.forEach((item, counts) -> {
			String id = BuiltInRegistries.ITEM.getKey(item).toString();
			materials.add(new Material(item, counts[0], counts[1], carried.getOrDefault(id, 0), module.storage().known(id)));
		});
		materials.sort((first, second) -> Long.compare(second.toPlace(), first.toPlace()));

		pane.text(Component.translatable("minekit.blueprint.materials_for", range.from() + 1, range.to() + 1,
				module.progress().isPresent() ? Component.translatable("minekit.blueprint.materials_placed")
						: Component.translatable("minekit.blueprint.materials_all")), MinekitTheme.TEXT_MUTED);
		Map<String, Integer> missingGoals = new TreeMap<>();
		for (Material material : materials) {
			if (material.missing() > 0) {
				missingGoals.put(BuiltInRegistries.ITEM.getKey(material.item()).toString(), (int) Math.min(Integer.MAX_VALUE, material.missing()));
			}
		}
		FlatButton goals = register(new FlatButton(0, 0, pane.width(), ROW, Component.translatable("minekit.blueprint.goals", missingGoals.size()),
				button -> {
					module.goals().addGoals(missingGoals, resolved.blueprint().name());
					rebuildWidgets();
				}));
		goals.active = !missingGoals.isEmpty() && module.goals().isAvailable();
		goals.setTooltip(Tooltip.create(Component.translatable("minekit.blueprint.goals_tooltip")));
		pane.widget(goals);
		for (Material material : materials) {
			MutableText status = new MutableText();
			status.add(Component.translatable("minekit.blueprint.material_to_place", material.toPlace()));
			status.add(Component.translatable("minekit.blueprint.material_carried", material.carried()));
			material.stored().ifPresent(known -> status.add(Component.translatable("minekit.blueprint.material_stored", known.total())));
			int color = material.toPlace() == 0 ? MinekitTheme.SUCCESS : material.missing() == 0 ? MinekitTheme.TEXT : MinekitTheme.WARNING;
			pane.line(new ItemStack(material.item()), material.item().getName(new ItemStack(material.item())), status.build(), color);
		}
		if (withoutItem > 0) {
			pane.text(Component.translatable("minekit.blueprint.without_item", withoutItem), MinekitTheme.TEXT_MUTED);
		}
	}

	/** Joins parts with " · ". */
	private static final class MutableText {
		private final List<Component> parts = new ArrayList<>();

		void add(Component part) {
			parts.add(part);
		}

		Component build() {
			var result = Component.empty();
			for (int i = 0; i < parts.size(); i++) {
				if (i > 0) {
					result.append(" · ");
				}
				result.append(parts.get(i));
			}
			return result;
		}
	}

	// ------------------------------------------------------------------------------------------------------- lifecycle

	@Override
	public void tick() {
		super.tick();
		ticks++;
		ResolvedBlueprint now = module.active().orElse(null);
		boolean placed = module.placement().isPresent();
		if (now != shown || placed != shownPlaced) {
			rebuildWidgets();
			return;
		}
		if (shown != null && ticks % 20 == 0) {
			if (tab == Tab.LAYERS) {
				texture.paint(shown, layer, module.placement().orElse(null), module.progress().orElse(null));
			} else {
				rebuildWidgets();
			}
		}
	}

	@Override
	public void onFilesDrop(List<Path> files) {
		List<String> imported = module.importFiles(files);
		if (!imported.isEmpty()) {
			module.select(imported.getLast());
		}
		rebuildWidgets();
	}

	@Override
	public void removed() {
		texture.close();
		super.removed();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return list.mouseClicked(event) || pane.mouseClicked(event) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return list.mouseDragged(event) || pane.mouseDragged(event) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean ended = list.mouseReleased(event) | pane.mouseReleased(event);
		return super.mouseReleased(event) || ended;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (tab == Tab.LAYERS && shown != null && mouseX >= mapX && mouseY >= mapY && mouseX < mapX + mapWidth && mouseY < mapY + mapHeight) {
			stepLayer(scrollY > 0 ? 1 : -1);
			return true;
		}
		return list.mouseScrolled(mouseX, mouseY, scrollY) || pane.mouseScrolled(mouseX, mouseY, scrollY)
				|| super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);
		UiText.draw(graphics, font, title, panelX + 10, panelY + 11, panelWidth - 90, MinekitTheme.ACCENT);
		graphics.verticalLine(rightX - 6, panelY + 26, panelY + panelHeight - 6, MinekitTheme.PANEL_BORDER);
		list.extract(graphics, mouseX, mouseY);
		int hintY = panelY + panelHeight - 8 - ROW - 2 - 2 * LINE;
		for (String line : UiText.wrap(font, Component.translatable("minekit.blueprint.drop_hint").getString(), leftWidth, 2)) {
			graphics.text(font, line, panelX + 8, hintY, MinekitTheme.TEXT_DISABLED, false);
			hintY += LINE;
		}

		if (shown == null) {
			extractEmptyState(graphics);
		} else {
			Blueprint blueprint = shown.blueprint();
			UiText.draw(graphics, font, Component.literal(blueprint.name()), rightX, panelY + 28, rightWidth - 70, MinekitTheme.TEXT);
			Component info = Component.translatable("minekit.blueprint.info", blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(),
					blueprint.blockCount(), blueprint.format().displayName());
			UiText.draw(graphics, font, blueprint.author().isEmpty() ? info : info.copy().append(" · " + blueprint.author()), rightX,
					panelY + 39, rightWidth, MinekitTheme.TEXT_MUTED);
			if (tab == Tab.LAYERS) {
				extractLayer(graphics, mouseX, mouseY);
			} else {
				pane.extract(graphics, mouseX, mouseY);
			}
		}
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (tab == Tab.LAYERS && shown != null) {
			int[] cell = mapCell(mouseX, mouseY);
			if (cell != null) {
				graphics.setComponentTooltipForNextFrame(font, cellTooltip(cell[0], cell[1]), mouseX, mouseY);
			}
		}
	}

	private void extractLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Blueprint blueprint = Objects.requireNonNull(shown).blueprint();
		Optional<Placement> placement = module.placement();
		Component label = placement.isPresent()
				? Component.translatable("minekit.blueprint.layer_world", layer + 1, blueprint.sizeY(), placement.get().worldY(layer))
				: Component.translatable("minekit.blueprint.layer", layer + 1, blueprint.sizeY());
		UiText.drawCentered(graphics, font, label, rightX + rightWidth / 2, contentTop + 5, rightWidth - 52, MinekitTheme.TEXT);
		graphics.fill(mapX, mapY, mapX + mapWidth, mapY + mapHeight, MinekitTheme.SURFACE_DISABLED);
		mapScale = texture.draw(graphics, mapX, mapY, mapWidth, mapHeight);
		int legendY = mapY + mapHeight + 3;
		int x = rightX;
		x = legend(graphics, x, legendY, LayerTexture.CORRECT, "minekit.blueprint.status.correct");
		x = legend(graphics, x, legendY, LayerTexture.mix(0xFF8899AA, LayerTexture.EMPTY, 0.45), "minekit.blueprint.status.missing");
		x = legend(graphics, x, legendY, LayerTexture.WRONG, "minekit.blueprint.status.wrong");
		legend(graphics, x, legendY, LayerTexture.mix(0xFF8899AA, LayerTexture.EMPTY, 0.75), "minekit.blueprint.status.unchecked");
		UiText.draw(graphics, font, Component.translatable(placement.isPresent() ? "minekit.blueprint.north_up" : "minekit.blueprint.not_placed_map"),
				rightX, legendY + LINE, rightWidth, MinekitTheme.TEXT_DISABLED);
	}

	private int legend(GuiGraphicsExtractor graphics, int x, int y, int color, String key) {
		graphics.fill(x, y + 1, x + 7, y + 8, color);
		Component text = Component.translatable(key);
		graphics.text(font, text, x + 10, y, MinekitTheme.TEXT_MUTED, false);
		return x + 10 + font.width(text) + 10;
	}

	private List<Component> cellTooltip(int localX, int localZ) {
		ResolvedBlueprint resolved = Objects.requireNonNull(shown);
		Blueprint blueprint = resolved.blueprint();
		int palette = blueprint.paletteIndex(localX, layer, localZ);
		BlockSpec spec = blueprint.palette().get(palette);
		List<Component> lines = new ArrayList<>();
		BlockState state = resolved.state(palette);
		lines.add(spec.isAir() ? Component.translatable("minekit.blueprint.air")
				: state == null ? Component.literal(spec.id()) : state.getBlock().getName());
		Optional<Placement> placement = module.placement();
		if (placement.isPresent()) {
			Placement p = placement.get();
			lines.add(Component.literal(p.worldX(blueprint, localX, localZ) + " " + p.worldY(layer) + " " + p.worldZ(blueprint, localX, localZ))
					.withStyle(ChatFormatting.GRAY));
			Optional<BlueprintProgress> progress = module.progress();
			int index = blueprint.index(localX, layer, localZ);
			if (progress.isPresent() && progress.get().isTracked(index)) {
				lines.add(Component.translatable("minekit.blueprint.status." + progress.get().status(index).name().toLowerCase(java.util.Locale.ROOT))
						.withStyle(ChatFormatting.DARK_GRAY));
			}
		}
		return lines;
	}

	private void extractEmptyState(GuiGraphicsExtractor graphics) {
		Component message = module.isLoading() ? Component.translatable("minekit.blueprint.loading")
				: module.failedFile().isPresent() ? Component.translatable("minekit.blueprint.failed", module.failedFile().get())
						: Component.translatable("minekit.blueprint.choose");
		int width = Math.min(rightWidth - 24, 280);
		List<String> lines = UiText.wrap(font, message.getString(), width, 6);
		int top = panelY + 28 + (panelHeight - 36 - lines.size() * LINE) / 2;
		for (String line : lines) {
			graphics.text(font, line, rightX + rightWidth / 2 - font.width(line) / 2, top, MinekitTheme.TEXT_MUTED, false);
			top += LINE;
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
