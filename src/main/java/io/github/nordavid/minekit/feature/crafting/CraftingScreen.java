package io.github.nordavid.minekit.feature.crafting;

import io.github.nordavid.minekit.core.item.CreativeGroups;
import io.github.nordavid.minekit.core.item.InventoryCounts;
import io.github.nordavid.minekit.core.item.ItemIds;
import io.github.nordavid.minekit.core.screen.widget.ContentPane;
import io.github.nordavid.minekit.core.screen.widget.FlatButton;
import io.github.nordavid.minekit.core.screen.widget.ItemButton;
import io.github.nordavid.minekit.core.screen.widget.MinekitTheme;
import io.github.nordavid.minekit.core.screen.widget.ToggleButton;
import io.github.nordavid.minekit.core.screen.widget.UiText;
import io.github.nordavid.minekit.core.search.TextSearch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The crafting planner: search an item on the left, see what it takes on the right.
 *
 * <p>The plan shows raw materials (needed, and what is still missing when the inventory is used), the crafting steps
 * in a followable order, leftovers and warnings. Recipes and "any of" alternatives can be switched, intermediate items
 * can be marked as gathered instead of crafted. The plan updates when the inventory changes while the screen is open.
 */
public final class CraftingScreen extends Screen {
	private static final int MAX_PANEL_WIDTH = 580;
	private static final int MAX_PANEL_HEIGHT = 320;
	/** Category chosen last, so the planner reopens where the player was browsing. */
	private static @Nullable String lastGroupId;
	private static final int ROW = MinekitTheme.ROW_HEIGHT;

	private final CraftingAssistantModule module;
	private final ContentPane results;
	private final ContentPane planPane;
	private final List<AbstractWidget> resultWidgets = new ArrayList<>();
	private final List<AbstractWidget> planWidgets = new ArrayList<>();
	private RecipeIndex index;
	private CraftingPlanner planner;
	private @Nullable PlanRequest request;
	private @Nullable CraftingPlan plan;
	private Map<String, Integer> inventory = Map.of();
	private String query = "";
	private List<CreativeGroups.Group> groups = List.of();
	/** Index into {@link #groups}, or -1 for all categories. */
	private int groupIndex = -1;
	private @Nullable FlatButton groupButton;
	private int ticksUntilInventoryCheck;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;
	private int leftWidth;
	private int rightX;
	private int rightWidth;
	private @Nullable EditBox quantityBox;
	private @Nullable FlatButton minusButton;
	private @Nullable FlatButton plusButton;
	private @Nullable ToggleButton inventoryToggle;
	private @Nullable FlatButton saveButton;
	private @Nullable FlatButton goalsButton;

	public CraftingScreen(CraftingAssistantModule module, @Nullable PlanRequest request) {
		super(Component.translatable("minekit.crafting.title"));
		this.module = Objects.requireNonNull(module, "module");
		this.request = request;
		this.results = new ContentPane(font);
		this.planPane = new ContentPane(font);
	}

	/** The plan shown, for tests. */
	public @Nullable CraftingPlan plan() {
		return plan;
	}

	public @Nullable PlanRequest request() {
		return request;
	}

	/** The list of craftable items on the left, for tests. */
	public ContentPane resultsPane() {
		return results;
	}

	@Override
	protected void init() {
		index = module.recipeIndex();
		planner = new CraftingPlanner(index);
		inventory = currentInventory();

		panelWidth = Math.min(width - 12, MAX_PANEL_WIDTH);
		panelHeight = Math.min(height - 12, MAX_PANEL_HEIGHT);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		leftWidth = Math.clamp((int) (panelWidth * 0.36), 120, 200);
		rightX = panelX + 8 + leftWidth + 10;
		rightWidth = panelX + panelWidth - 8 - rightX;

		addRenderableWidget(new FlatButton(panelX + panelWidth - 68, panelY + 6, 60, ROW, CommonComponents.GUI_DONE, button -> onClose()));

		EditBox search = new EditBox(font, panelX + 13, panelY + 33, leftWidth - 10, 10, Component.translatable("minekit.crafting.search"));
		search.setBordered(false);
		search.setMaxLength(64);
		search.setTextColor(MinekitTheme.TEXT);
		search.setHint(Component.translatable("minekit.crafting.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setValue(query);
		search.setResponder(value -> {
			query = value;
			results.scrollToTop();
			rebuildResults();
		});
		addRenderableWidget(search);
		setInitialFocus(search);

		Set<String> craftable = index.craftableItems();
		groups = CreativeGroups.current().stream().filter(group -> group.items().stream().anyMatch(craftable::contains)).toList();
		groupIndex = -1;
		for (int i = 0; i < groups.size(); i++) {
			if (groups.get(i).id().equals(lastGroupId)) {
				groupIndex = i;
			}
		}
		int arrow = 18;
		FlatButton previous = addRenderableWidget(new FlatButton(panelX + 8, panelY + 50, arrow, ROW, Component.literal("\u25c0"),
				button -> stepGroup(-1)));
		previous.setTooltip(Tooltip.create(Component.translatable("minekit.crafting.group_previous")));
		groupButton = addRenderableWidget(new FlatButton(panelX + 8 + arrow + 2, panelY + 50, leftWidth - 2 * arrow - 4, ROW, groupLabel(),
				button -> stepGroup(1)));
		groupButton.setTooltip(Tooltip.create(Component.translatable("minekit.crafting.group_tooltip")));
		FlatButton next = addRenderableWidget(new FlatButton(panelX + 8 + leftWidth - arrow, panelY + 50, arrow, ROW, Component.literal("\u25b6"),
				button -> stepGroup(1)));
		next.setTooltip(Tooltip.create(Component.translatable("minekit.crafting.group_next")));

		int headerY = panelY + 28;
		minusButton = addRenderableWidget(new FlatButton(rightX + rightWidth - 92, headerY, 20, ROW, Component.literal("−"),
				button -> changeQuantity(-1)));
		quantityBox = new EditBox(font, rightX + rightWidth - 68, headerY + 1, 44, ROW - 2, Component.translatable("minekit.crafting.quantity"));
		quantityBox.setMaxLength(6);
		quantityBox.setValue(request == null ? "1" : String.valueOf(request.quantity()));
		quantityBox.setResponder(this::onQuantityTyped);
		addRenderableWidget(quantityBox);
		plusButton = addRenderableWidget(new FlatButton(rightX + rightWidth - 20, headerY, 20, ROW, Component.literal("+"),
				button -> changeQuantity(+1)));
		inventoryToggle = addRenderableWidget(new ToggleButton(rightX, headerY + ROW + 4, rightWidth, ROW,
				Component.translatable("minekit.crafting.use_inventory"), request == null || request.useInventory(), requested -> {
					if (request != null) {
						request = request.withUseInventory(requested);
						rebuildPlan();
					}
					return requested;
				}));
		saveButton = addRenderableWidget(new FlatButton(rightX + rightWidth - 110, panelY + panelHeight - ROW - 6, 110, ROW,
				Component.translatable("minekit.crafting.save"), button -> {
					if (request != null) {
						module.savePlan(request);
					}
				}));
		goalsButton = addRenderableWidget(new FlatButton(rightX, panelY + panelHeight - ROW - 6, 110, ROW,
				Component.translatable("minekit.crafting.as_goals"), button -> addMissingAsGoals()));
		goalsButton.setTooltip(Tooltip.create(Component.translatable("minekit.crafting.as_goals_tooltip")));

		rebuildResults();
		rebuildPlan();
	}

	private Map<String, Integer> currentInventory() {
		return minecraft.player == null ? Map.of() : InventoryCounts.of(minecraft.player);
	}

	/** Selects a target; a saved plan for it is restored. Public for tests. */
	public void select(String item) {
		request = module.savedPlans().current().forTarget(item).map(SavedPlans.SavedPlan::request).orElse(PlanRequest.of(item, 1));
		// Rebuild everything after the current click: quantity box and inventory switch follow the selected plan.
		minecraft.execute(() -> {
			if (minecraft.gui.screen() == this) {
				planPane.scrollToTop();
				rebuildWidgets();
			}
		});
	}

	private void changeQuantity(int delta) {
		if (request == null || quantityBox == null) {
			return;
		}
		int step = minecraft.hasShiftDown() ? 64 : 1;
		int next = Math.clamp((long) request.quantity() + (long) delta * step, 1, PlanRequest.MAX_QUANTITY);
		quantityBox.setValue(String.valueOf(next));
	}

	private void onQuantityTyped(String text) {
		// The box accepts any text; only whole numbers change the plan.
		if (request == null || !text.matches("\\d{1,6}")) {
			return;
		}
		int quantity = Integer.parseInt(text);
		if (quantity >= 1 && quantity != request.quantity()) {
			request = request.withQuantity(quantity);
			rebuildPlan();
		}
	}

	/**
	 * The craftable items on the left. Without a search they are listed like the creative inventory: by category, in
	 * its order, so players can browse; with a search, ranked by how well they match. Always all of them.
	 */
	private void rebuildResults() {
		resultWidgets.forEach(this::removeWidget);
		resultWidgets.clear();
		int top = panelY + 50 + ROW + 4;
		results.reset(panelX + 8, top, leftWidth, panelY + panelHeight - 8 - top);
		Set<String> craftable = index.craftableItems();
		if (craftable.isEmpty()) {
			results.text(Component.translatable("minekit.crafting.no_recipes"), MinekitTheme.TEXT_MUTED);
			results.layout();
			return;
		}
		if (query.isBlank() && groupIndex < 0) {
			Set<String> shown = new HashSet<>();
			for (CreativeGroups.Group group : groups) {
				List<String> items = group.items().stream().filter(craftable::contains).filter(item -> !shown.contains(item)).toList();
				if (!items.isEmpty()) {
					results.heading(Component.translatable("minekit.crafting.group", group.name(), items.size()));
					items.forEach(this::addResult);
					shown.addAll(items);
				}
			}
			List<String> others = craftable.stream().filter(item -> !shown.contains(item)).toList();
			if (!others.isEmpty()) {
				results.heading(Component.translatable("minekit.crafting.group", Component.translatable("minekit.crafting.group_other"),
						others.size()));
				others.forEach(this::addResult);
			}
		} else {
			List<String> candidates = groupIndex < 0 ? new ArrayList<>(craftable)
					: groups.get(groupIndex).items().stream().filter(craftable::contains).toList();
			List<String> matches = TextSearch.filter(candidates, item -> ItemIds.name(item).getString() + " " + item, query);
			matches.forEach(this::addResult);
			if (matches.isEmpty()) {
				results.text(Component.translatable("minekit.crafting.no_match"), MinekitTheme.TEXT_MUTED);
			}
		}
		results.layout();
	}

	private void addResult(String item) {
		ItemButton button = addRenderableWidget(new ItemButton(0, 0, results.width(), ROW, ItemIds.icon(item), ItemIds.name(item),
				() -> request != null && request.target().equals(item), () -> select(item)));
		resultWidgets.add(results.widget(button));
	}

	/** Steps through "all categories" and the creative categories that have craftable items. */
	private void stepGroup(int delta) {
		int count = groups.size() + 1;
		int position = Math.floorMod(groupIndex + 1 + delta, count);
		groupIndex = position - 1;
		lastGroupId = groupIndex < 0 ? null : groups.get(groupIndex).id();
		if (groupButton != null) {
			groupButton.setMessage(groupLabel());
		}
		results.scrollToTop();
		rebuildResults();
	}

	private Component groupLabel() {
		if (groupIndex < 0) {
			return Component.translatable("minekit.crafting.group_all");
		}
		return groups.get(groupIndex).name();
	}

	/** Number of items listed on the left, for tests. */
	public int resultCount() {
		return resultWidgets.size();
	}

	/** Number of craftable items, for tests. */
	public int craftableCount() {
		return index.craftableItems().size();
	}

	/** Shown categories, for tests: "all" first. */
	public List<String> groupIds() {
		List<String> ids = new ArrayList<>();
		ids.add("all");
		groups.forEach(group -> ids.add(group.id()));
		return ids;
	}

	private void rebuildPlan() {
		planWidgets.forEach(this::removeWidget);
		planWidgets.clear();
		int top = panelY + 28 + 2 * (ROW + 4);
		planPane.reset(rightX, top, rightWidth, panelY + panelHeight - ROW - 12 - top);
		boolean selected = request != null;
		for (AbstractWidget widget : new AbstractWidget[] {minusButton, quantityBox, plusButton, inventoryToggle, saveButton, goalsButton}) {
			if (widget != null) {
				widget.visible = selected;
			}
		}
		if (!selected) {
			plan = null;
			planPane.layout();
			return;
		}
		PlanRequest current = Objects.requireNonNull(request);
		plan = planner.plan(current, inventory);
		if (goalsButton != null) {
			goalsButton.active = module.goals().isAvailable() && plan.materials().stream().anyMatch(material -> goalAmount(current, material) > 0);
		}
		buildTargetRecipe(current, plan);
		buildMaterials(current, plan);
		buildSteps(current, plan);
		buildNotes(plan);
		planPane.layout();
	}

	private void buildTargetRecipe(PlanRequest current, CraftingPlan result) {
		CraftingPlan.Step target = result.steps().stream().filter(step -> step.item().equals(current.target())).findFirst().orElse(null);
		if (target == null) {
			planPane.text(Component.translatable("minekit.crafting.no_recipe_for_target"), MinekitTheme.WARNING);
			return;
		}
		RecipeOption recipe = target.recipe();
		MutableComponent label = Component.translatable("minekit.crafting.recipe", stationName(recipe.station()));
		if (recipe.source() == RecipeOption.Source.VANILLA) {
			label.append(Component.literal(" · ")).append(Component.translatable("minekit.crafting.source.vanilla"));
		}
		line(null, label, null, 0, recipeSwitch(current.target(), recipe, target.alternatives()));
		planPane.text(Component.translatable("minekit.crafting.per_craft", inputsText(recipe, 1)), MinekitTheme.TEXT_MUTED);
	}

	private void buildMaterials(PlanRequest current, CraftingPlan result) {
		planPane.heading(Component.translatable("minekit.crafting.materials"));
		for (CraftingPlan.Material material : result.materials()) {
			MutableComponent label = Component.literal(material.needed() + "× ").append(ItemIds.name(material.chosen()));
			if (!material.ingredient().isSingle()) {
				label.append(Component.translatable("minekit.crafting.or_similar"));
			}
			MutableComponent status = null;
			int statusColor = MinekitTheme.TEXT_MUTED;
			if (current.useInventory()) {
				status = material.missing() == 0
						? Component.translatable("minekit.crafting.have_all")
						: Component.translatable("minekit.crafting.missing", material.missing());
				statusColor = material.missing() == 0 ? MinekitTheme.SUCCESS : MinekitTheme.WARNING;
			}
			if (!current.useInventory() || material.missing() > 0) {
				Component stored = storedText(material.chosen());
				if (stored != null) {
					status = status == null ? stored.copy() : status.append(Component.literal(" · ")).append(stored);
				}
			}
			List<AbstractWidget> buttons = new ArrayList<>();
			if (!material.ingredient().isSingle()) {
				buttons.add(iconButton("⇄", "minekit.crafting.alternative_tooltip", () -> nextAlternative(material)));
			}
			if (material.craftable()) {
				buttons.add(iconButton("⚒", "minekit.crafting.craft_it_tooltip",
						() -> update(request.withRecipeChoice(material.chosen(), index.recipesFor(material.chosen()).getFirst().signature()))));
			}
			line(ItemIds.icon(material.chosen()), label, status, statusColor, buttons.toArray(AbstractWidget[]::new));
		}
	}

	private void buildSteps(PlanRequest current, CraftingPlan result) {
		if (result.steps().isEmpty()) {
			return;
		}
		planPane.heading(Component.translatable("minekit.crafting.steps"));
		int number = 1;
		for (CraftingPlan.Step step : result.steps()) {
			MutableComponent label = Component.literal(number++ + ". " + step.produced() + "× ").append(ItemIds.name(step.item()));
			Component status = Component.translatable("minekit.crafting.step_status", step.crafts(), stationName(step.recipe().station()));
			List<AbstractWidget> buttons = new ArrayList<>();
			FlatButton recipeSwitch = recipeSwitch(step.item(), step.recipe(), step.alternatives());
			if (recipeSwitch != null) {
				buttons.add(recipeSwitch);
			}
			if (!step.item().equals(current.target())) {
				buttons.add(iconButton("⛏", "minekit.crafting.gather_tooltip", () -> update(request.withRecipeChoice(step.item(), PlanRequest.GATHER))));
			}
			line(ItemIds.icon(step.item()), label, status, MinekitTheme.TEXT_MUTED, buttons.toArray(AbstractWidget[]::new));
			MutableComponent from = Component.translatable("minekit.crafting.from", inputsText(step.recipe(), step.crafts()));
			if (step.recipe().source() == RecipeOption.Source.VANILLA) {
				from.append(Component.literal(" · ")).append(Component.translatable("minekit.crafting.source.vanilla"));
			}
			Component stored = storedText(step.item());
			if (stored != null && !step.item().equals(current.target())) {
				// Intermediate products in storage, e.g. iron ingots for a pickaxe, may save the whole step.
				from.append(Component.literal(" · ")).append(stored);
			}
			planPane.text(from, MinekitTheme.TEXT_MUTED);
		}
	}

	private void buildNotes(CraftingPlan result) {
		List<Component> notes = new ArrayList<>();
		if (!result.leftovers().isEmpty()) {
			notes.add(Component.translatable("minekit.crafting.leftovers", amounts(result.leftovers())));
		}
		if (!result.fromInventory().isEmpty()) {
			notes.add(Component.translatable("minekit.crafting.from_inventory", amounts(result.fromInventory())));
		}
		long vanillaSteps = result.warnings().stream().filter(w -> w.kind() == CraftingPlan.WarningKind.VANILLA_RECIPE_USED).count();
		if (vanillaSteps > 0) {
			notes.add(Component.translatable("minekit.crafting.warning.vanilla", vanillaSteps));
		}
		for (CraftingPlan.Warning warning : result.warnings()) {
			switch (warning.kind()) {
				case CYCLE -> notes.add(Component.translatable("minekit.crafting.warning.cycle", ItemIds.name(warning.item())));
				case NEEDS_FUEL -> notes.add(Component.translatable("minekit.crafting.warning.fuel"));
				case NO_RECIPE_FOR_TARGET, VANILLA_RECIPE_USED -> {
				}
			}
		}
		if (!notes.isEmpty()) {
			planPane.heading(Component.translatable("minekit.crafting.notes"));
			notes.forEach(note -> planPane.text(note, MinekitTheme.TEXT_MUTED));
		}
	}

	/** What the storage index knows about an item, e.g. "64 in storage"; marked when the observation is old. */
	private @Nullable Component storedText(String item) {
		return module.storage().known(item).map(known -> {
			boolean old = System.currentTimeMillis() - known.oldestObservedAt() >= 24 * 3_600_000L;
			return (Component) Component.translatable(old ? "minekit.crafting.in_storage_old" : "minekit.crafting.in_storage", known.total());
		}).orElse(null);
	}

	private @Nullable FlatButton recipeSwitch(String item, RecipeOption current, int alternatives) {
		if (alternatives <= 1) {
			return null;
		}
		List<RecipeOption> recipes = index.recipesFor(item);
		int position = Math.max(0, recipes.indexOf(current));
		RecipeOption next = recipes.get((position + 1) % recipes.size());
		FlatButton button = new FlatButton(0, 0, 30, ROW, Component.literal((position + 1) + "/" + recipes.size()),
				pressed -> update(request.withRecipeChoice(item, next.signature())));
		button.setTooltip(Tooltip.create(Component.translatable("minekit.crafting.recipe_tooltip", recipes.size())));
		return button;
	}

	private FlatButton iconButton(String icon, String tooltipKey, Runnable action) {
		FlatButton button = new FlatButton(0, 0, 20, ROW, Component.literal(icon), pressed -> action.run());
		button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
		return button;
	}

	/** What a material contributes as a collection goal: what is missing, or everything if the inventory is ignored. */
	private static int goalAmount(PlanRequest current, CraftingPlan.Material material) {
		return current.useInventory() ? material.missing() : material.needed();
	}

	private void addMissingAsGoals() {
		if (request == null || plan == null) {
			return;
		}
		PlanRequest current = request;
		Map<String, Integer> goals = new java.util.TreeMap<>();
		plan.materials().stream().filter(material -> goalAmount(current, material) > 0)
				.forEach(material -> goals.merge(material.chosen(), goalAmount(current, material), Integer::sum));
		String sessionName = net.minecraft.client.resources.language.I18n.get("minekit.crafting.session_name", current.quantity(),
				ItemIds.name(current.target()).getString());
		module.goals().addGoals(goals, sessionName);
	}

	private void nextAlternative(CraftingPlan.Material material) {
		List<String> options = new ArrayList<>(material.ingredient().alternatives());
		String next = options.get((options.indexOf(material.chosen()) + 1) % options.size());
		update(request.withAlternativeChoice(material.ingredient(), next));
	}

	private void update(PlanRequest next) {
		request = next;
		// Run after the click that triggered this, because rebuilding removes the clicked button.
		minecraft.execute(() -> {
			if (minecraft.gui.screen() == this) {
				rebuildPlan();
			}
		});
	}

	private void line(@Nullable ItemStack icon, Component label, @Nullable Component status, int statusColor,
			@Nullable AbstractWidget... widgets) {
		List<AbstractWidget> present = new ArrayList<>();
		for (AbstractWidget widget : widgets) {
			if (widget != null) {
				present.add(addRenderableWidget(widget));
			}
		}
		planWidgets.addAll(present);
		planPane.line(icon, label, status, statusColor, present.toArray(AbstractWidget[]::new));
	}

	private static Component stationName(RecipeOption.Station station) {
		return Component.translatable("minekit.crafting.station." + station.name().toLowerCase(Locale.ROOT));
	}

	private static Component inputsText(RecipeOption recipe, int crafts) {
		MutableComponent text = Component.empty();
		boolean first = true;
		for (RecipeOption.Input input : recipe.inputs()) {
			if (!first) {
				text.append(Component.literal(", "));
			}
			first = false;
			text.append(Component.literal((long) input.count() * crafts + "× ")).append(ItemIds.name(input.ingredient().primary()));
			if (!input.ingredient().isSingle()) {
				text.append(Component.translatable("minekit.crafting.or_similar"));
			}
		}
		return text;
	}

	private static Component amounts(Map<String, Integer> amounts) {
		return Component.literal(amounts.entrySet().stream()
				.map(entry -> entry.getValue() + "× " + ItemIds.name(entry.getKey()).getString())
				.collect(Collectors.joining(", ")));
	}

	@Override
	public void tick() {
		if (--ticksUntilInventoryCheck <= 0) {
			ticksUntilInventoryCheck = 20;
			Map<String, Integer> now = currentInventory();
			if (!now.equals(inventory)) {
				inventory = now;
				if (request != null) {
					rebuildPlan();
				}
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, MinekitTheme.PANEL);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, MinekitTheme.PANEL_BORDER);
		UiText.draw(graphics, font, title, panelX + 10, panelY + 11, panelWidth - 90, MinekitTheme.ACCENT);
		graphics.verticalLine(rightX - 6, panelY + 26, panelY + panelHeight - 6, MinekitTheme.PANEL_BORDER);

		graphics.fill(panelX + 8, panelY + 28, panelX + 8 + leftWidth, panelY + 46, MinekitTheme.SURFACE);
		graphics.outline(panelX + 8, panelY + 28, leftWidth, 18, MinekitTheme.BORDER);
		results.extract(graphics, mouseX, mouseY);

		if (request != null) {
			graphics.item(ItemIds.icon(request.target()), rightX, panelY + 29);
			UiText.draw(graphics, font, ItemIds.name(request.target()), rightX + 20, panelY + 33, rightWidth - 120, MinekitTheme.TEXT);
		} else {
			extractEmptyState(graphics);
		}
		planPane.extract(graphics, mouseX, mouseY);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Nothing chosen yet: a short invitation, centered in the right half. */
	private void extractEmptyState(GuiGraphicsExtractor graphics) {
		int width = Math.min(rightWidth - 24, 260);
		List<String> lines = new ArrayList<>(UiText.wrap(font, Component.translatable("minekit.crafting.choose_item").getString(), width, 3));
		List<String> hint = UiText.wrap(font, Component.translatable("minekit.crafting.browse_hint").getString(), width, 3);
		int lineHeight = MinekitTheme.LINE_HEIGHT;
		int height = (lines.size() + hint.size()) * lineHeight + 6;
		int top = panelY + 28 + (panelHeight - 36 - height) / 2;
		int center = rightX + rightWidth / 2;
		for (String line : lines) {
			graphics.text(font, line, center - font.width(line) / 2, top, MinekitTheme.TEXT, false);
			top += lineHeight;
		}
		top += 6;
		for (String line : hint) {
			graphics.text(font, line, center - font.width(line) / 2, top, MinekitTheme.TEXT_MUTED, false);
			top += lineHeight;
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return results.mouseClicked(event) || planPane.mouseClicked(event) || super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		return results.mouseDragged(event) || planPane.mouseDragged(event) || super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		// Always let the screen see the release too, so its own drag state ends.
		boolean ended = results.mouseReleased(event) | planPane.mouseReleased(event);
		return super.mouseReleased(event) || ended;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		return results.mouseScrolled(mouseX, mouseY, scrollY) || planPane.mouseScrolled(mouseX, mouseY, scrollY)
				|| super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
