package io.github.nordavid.minekit.feature.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nordavid.minekit.feature.crafting.RecipeOption.Input;
import io.github.nordavid.minekit.feature.crafting.RecipeOption.Source;
import io.github.nordavid.minekit.feature.crafting.RecipeOption.Station;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Planner tests with a small, realistic recipe set modelled on vanilla. */
class CraftingPlannerTest {
	private static final Ingredient PLANKS = Ingredient.of("oak_planks", "birch_planks");
	private static final Ingredient STONE_MATERIALS = Ingredient.of("cobblestone", "cobbled_deepslate");

	private static RecipeOption recipe(Station station, String output, int count, Input... inputs) {
		return new RecipeOption(station, output, count, List.of(inputs), Source.SERVER);
	}

	private static Input in(String item, int count) {
		return new Input(Ingredient.of(item), count);
	}

	private static Input in(Ingredient ingredient, int count) {
		return new Input(ingredient, count);
	}

	private static final List<RecipeOption> RECIPES = List.of(
			recipe(Station.CRAFTING, "oak_planks", 4, in("oak_log", 1)),
			recipe(Station.CRAFTING, "birch_planks", 4, in("birch_log", 1)),
			recipe(Station.CRAFTING, "stick", 4, in(PLANKS, 2)),
			recipe(Station.CRAFTING, "torch", 4, in("stick", 1), in("coal", 1)),
			recipe(Station.CRAFTING, "chest", 1, in(PLANKS, 8)),
			recipe(Station.CRAFTING, "crafting_table", 1, in(PLANKS, 4)),
			recipe(Station.CRAFTING, "piston", 1, in(PLANKS, 3), in(STONE_MATERIALS, 4), in("iron_ingot", 1), in("redstone", 1)),
			recipe(Station.FURNACE, "iron_ingot", 1, in("raw_iron", 1)),
			recipe(Station.CRAFTING, "iron_ingot", 9, in("iron_block", 1)),
			recipe(Station.CRAFTING, "iron_ingot", 1, in("iron_nugget", 9)),
			recipe(Station.CRAFTING, "iron_nugget", 9, in("iron_ingot", 1)),
			recipe(Station.CRAFTING, "iron_block", 1, in("iron_ingot", 9)));

	private static final CraftingPlanner PLANNER = new CraftingPlanner(new RecipeIndex(RECIPES));

	private static CraftingPlan plan(PlanRequest request, Map<String, Integer> inventory) {
		return PLANNER.plan(request, inventory);
	}

	private static Map<String, Integer> missing(CraftingPlan plan) {
		return plan.materials().stream().collect(Collectors.toMap(CraftingPlan.Material::chosen, CraftingPlan.Material::missing));
	}

	private static Map<String, Integer> needed(CraftingPlan plan) {
		return plan.materials().stream().collect(Collectors.toMap(CraftingPlan.Material::chosen, CraftingPlan.Material::needed));
	}

	private static Map<String, Integer> crafts(CraftingPlan plan) {
		return plan.steps().stream().collect(Collectors.toMap(CraftingPlan.Step::item, CraftingPlan.Step::crafts));
	}

	@Test
	void sixtyFourPistonsFromScratch() {
		CraftingPlan plan = plan(PlanRequest.of("piston", 64), Map.of());

		// 64 pistons: 192 planks -> 48 logs, 256 stone materials, 64 iron (smelted from raw iron), 64 redstone.
		// Without inventory hints the primary alternatives (listed first, like the game's tags) are used.
		assertEquals(Map.of("oak_log", 48, "cobblestone", 256, "raw_iron", 64, "redstone", 64), needed(plan));
		assertEquals(Map.of("piston", 64, "oak_planks", 48, "iron_ingot", 64), crafts(plan));
		assertEquals("piston", plan.steps().getLast().item(), "the target is crafted last");
	}

	@Test
	void defaultRecipeAvoidsUnpackingBlocksAndNuggets() {
		RecipeOption ingot = PLANNER.plan(PlanRequest.of("iron_ingot", 1), Map.of()).steps().getFirst().recipe();
		assertEquals(Station.FURNACE, ingot.station());
		assertEquals("raw_iron", ingot.inputs().getFirst().ingredient().first());
	}

	@Test
	void outputCountsAndLeftoversAreShared() {
		// 10 torches -> 3 crafts (12 torches) -> 3 sticks -> 1 stick craft (4) -> 2 planks -> 1 log (4 planks).
		CraftingPlan plan = plan(PlanRequest.of("torch", 10).withAlternativeChoice(PLANKS, "oak_planks"), Map.of());

		assertEquals(Map.of("torch", 3, "stick", 1, "oak_planks", 1), crafts(plan));
		assertEquals(Map.of("oak_log", 1, "coal", 3), needed(plan));
		assertEquals(Map.of("torch", 2, "stick", 1, "oak_planks", 2), plan.leftovers());
	}

	@Test
	void demandFromSeveralConsumersIsBatched() {
		// A chest (8 planks) and a crafting table (4 planks) need 12 planks = 3 logs, not 2 + 1 rounded separately.
		List<RecipeOption> recipes = new java.util.ArrayList<>(RECIPES);
		recipes.add(recipe(Station.CRAFTING, "starter_kit", 1, in("chest", 1), in("crafting_table", 1)));
		CraftingPlanner planner = new CraftingPlanner(new RecipeIndex(recipes));

		CraftingPlan plan = planner.plan(PlanRequest.of("starter_kit", 1).withAlternativeChoice(PLANKS, "oak_planks"), Map.of());

		assertEquals(Map.of("oak_log", 3), needed(plan));
		assertEquals(3, crafts(plan).get("oak_planks"));
	}

	@Test
	void inventoryReducesWhatIsMissingButNotTheTotal() {
		Map<String, Integer> inventory = Map.of("iron_ingot", 10, "oak_planks", 6, "cobblestone", 100, "redstone", 64);

		CraftingPlan plan = plan(PlanRequest.of("piston", 64), inventory);

		Map<String, Integer> missing = missing(plan);
		assertEquals(54, missing.get("raw_iron"), "10 ingots in the inventory");
		assertEquals(47, missing.get("oak_log"), "192 planks - 6 = 186 -> 47 logs");
		assertEquals(156, plan.materials().stream().filter(m -> m.ingredient().equals(STONE_MATERIALS)).findFirst().orElseThrow().missing());
		assertEquals(0, missing.get("redstone"));
		assertEquals(64, needed(plan).get("redstone"), "the total ignores the inventory");
		assertEquals(Map.of("iron_ingot", 10, "oak_planks", 6), plan.fromInventory());
	}

	@Test
	void alternativeTheInventoryCanMakeIsPreferred() {
		CraftingPlan plan = plan(PlanRequest.of("stick", 4), Map.of("birch_log", 3));
		assertEquals(Map.of("birch_log", 1), needed(plan), "birch logs in the inventory make birch planks the natural choice");
	}

	@Test
	void anyOfTheAlternativesCountsFromTheInventory() {
		Map<String, Integer> inventory = Map.of("cobblestone", 2, "cobbled_deepslate", 2);

		CraftingPlan.Material stone = plan(PlanRequest.of("piston", 1), inventory).materials().stream()
				.filter(material -> material.ingredient().equals(STONE_MATERIALS)).findFirst().orElseThrow();

		assertEquals(4, stone.inInventory());
		assertEquals(0, stone.missing());
	}

	@Test
	void inventoryCopiesOfTheTargetAreNotSubtracted() {
		CraftingPlan plan = plan(PlanRequest.of("crafting_table", 2).withAlternativeChoice(PLANKS, "oak_planks"), Map.of("crafting_table", 5));
		assertEquals(2, crafts(plan).get("crafting_table"));
	}

	@Test
	void playerChoicesAreHonoured() {
		String fromBlock = new RecipeIndex(RECIPES).recipesFor("iron_ingot").stream()
				.filter(r -> r.inputs().getFirst().ingredient().first().equals("iron_block")).findFirst().orElseThrow().signature();

		CraftingPlan viaBlock = plan(PlanRequest.of("iron_ingot", 18).withRecipeChoice("iron_ingot", fromBlock), Map.of());
		assertEquals(Map.of("iron_block", 2), needed(viaBlock));

		CraftingPlan gathered = plan(PlanRequest.of("piston", 2).withRecipeChoice("iron_ingot", PlanRequest.GATHER), Map.of());
		assertEquals(2, needed(gathered).get("iron_ingot"));
		assertTrue(gathered.materials().stream().filter(m -> m.chosen().equals("iron_ingot")).findFirst().orElseThrow().craftable());
	}

	@Test
	void cyclesAreBrokenAndReported() {
		// Only "unpacking" recipes: block <-> ingot. Planning ingots must not loop forever.
		CraftingPlanner planner = new CraftingPlanner(new RecipeIndex(List.of(
				recipe(Station.CRAFTING, "iron_ingot", 9, in("iron_block", 1)),
				recipe(Station.CRAFTING, "iron_block", 1, in("iron_ingot", 9)))));

		CraftingPlan plan = planner.plan(PlanRequest.of("iron_block", 1), Map.of());

		assertEquals(Map.of("iron_ingot", 9), needed(plan));
		assertTrue(plan.warnings().contains(new CraftingPlan.Warning(CraftingPlan.WarningKind.CYCLE, "iron_ingot")));
	}

	@Test
	void rawMaterialsAreGatheredByDefaultUnlessTheInventoryHoldsInputs() {
		// Redstone is normally mined. Its recipes either unpack a block or smelt an ore block (discouraged).
		List<RecipeOption> recipes = new java.util.ArrayList<>(RECIPES);
		recipes.add(recipe(Station.CRAFTING, "redstone", 9, in("redstone_block", 1)));
		recipes.add(recipe(Station.CRAFTING, "redstone_block", 1, in("redstone", 9)));
		recipes.add(recipe(Station.FURNACE, "redstone", 1, in("redstone_ore", 1)));
		CraftingPlanner planner = new CraftingPlanner(new RecipeIndex(recipes, java.util.Set.of("redstone_ore")));

		CraftingPlan fresh = planner.plan(PlanRequest.of("piston", 9), Map.of());
		assertEquals(9, needed(fresh).get("redstone"));
		assertTrue(fresh.materials().stream().filter(m -> m.chosen().equals("redstone")).findFirst().orElseThrow().craftable(),
				"the UI can still offer to craft it");

		CraftingPlan withBlock = planner.plan(PlanRequest.of("piston", 9), Map.of("redstone_block", 1));
		assertEquals(1, crafts(withBlock).get("redstone"), "a redstone block in the inventory is unpacked");

		CraftingPlan target = planner.plan(PlanRequest.of("redstone", 9), Map.of());
		assertEquals(1, crafts(target).get("redstone"), "the target itself is always crafted");
	}

	@Test
	void unknownTargetIsReported() {
		CraftingPlan plan = plan(PlanRequest.of("dragon_egg", 1), Map.of());

		assertEquals(Map.of("dragon_egg", 1), needed(plan));
		assertTrue(plan.steps().isEmpty());
		assertEquals(CraftingPlan.WarningKind.NO_RECIPE_FOR_TARGET, plan.warnings().getFirst().kind());
	}

	@Test
	void furnaceAndVanillaRecipesAreFlagged() {
		CraftingPlanner planner = new CraftingPlanner(new RecipeIndex(List.of(
				new RecipeOption(Station.FURNACE, "iron_ingot", 1, List.of(in("raw_iron", 1)), Source.VANILLA))));

		List<CraftingPlan.WarningKind> kinds = planner.plan(PlanRequest.of("iron_ingot", 3), Map.of()).warnings().stream()
				.map(CraftingPlan.Warning::kind).toList();

		assertEquals(List.of(CraftingPlan.WarningKind.VANILLA_RECIPE_USED, CraftingPlan.WarningKind.NEEDS_FUEL), kinds);
	}

	@Test
	void sameRecipeFromBothSourcesIsMergedAsServer() {
		RecipeOption vanilla = new RecipeOption(Station.CRAFTING, "stick", 4, List.of(in(PLANKS, 2)), Source.VANILLA);
		RecipeOption server = vanilla.withSource(Source.SERVER);

		RecipeIndex index = new RecipeIndex(List.of(vanilla, server));

		assertEquals(List.of(server), index.recipesFor("stick"));
	}

	@Test
	void hugeQuantitiesDoNotOverflow() {
		CraftingPlan plan = plan(PlanRequest.of("piston", PlanRequest.MAX_QUANTITY), Map.of());
		assertEquals(PlanRequest.MAX_QUANTITY * 4, plan.materials().stream()
				.filter(m -> m.ingredient().equals(STONE_MATERIALS)).findFirst().orElseThrow().needed());
	}
}
