package io.github.nordavid.minekit.feature.crafting;

import java.util.List;
import java.util.Map;

/**
 * Result of {@link CraftingPlanner#plan}.
 *
 * @param steps       crafting steps in an order that can be followed: every step's inputs are made by earlier steps
 * @param materials   things to gather (or take from the inventory), aggregated over the whole tree
 * @param leftovers   items produced beyond what is needed, because recipes make several at once
 * @param fromInventory intermediate items taken from the inventory instead of crafting them
 * @param warnings    problems the player should know about
 */
public record CraftingPlan(
		List<Step> steps,
		List<Material> materials,
		Map<String, Integer> leftovers,
		Map<String, Integer> fromInventory,
		List<Warning> warnings) {

	/**
	 * Make {@code crafts} times {@code recipe}, producing {@code produced} of {@code item}.
	 *
	 * @param alternatives number of recipes the client knows for this item, so the UI can offer a switch
	 */
	public record Step(String item, RecipeOption recipe, int crafts, int produced, int alternatives) {
	}

	/**
	 * Something to gather.
	 *
	 * @param ingredient the accepted items; more than one means "any of these"
	 * @param chosen     the alternative the plan expands or shows
	 * @param needed     total amount for the whole plan, ignoring the inventory
	 * @param inInventory amount of matching items in the inventory
	 * @param missing    amount still to gather after using the inventory (equals {@code needed} if the inventory is
	 *                   not used)
	 * @param craftable  whether a recipe exists, i.e. the player chose to gather it or crafting it would loop
	 */
	public record Material(Ingredient ingredient, String chosen, int needed, int inInventory, int missing, boolean craftable) {
	}

	public enum WarningKind {
		/** The target itself has no known recipe. */
		NO_RECIPE_FOR_TARGET,
		/** All recipes for an item lead back to an item already being made; it is treated as gathered. */
		CYCLE,
		/** A recipe from the vanilla game files is used; the server may have changed or removed it. */
		VANILLA_RECIPE_USED,
		/** A furnace-type station is used; fuel is not part of the plan. */
		NEEDS_FUEL
	}

	public record Warning(WarningKind kind, String item) {
	}

	public boolean isComplete() {
		return materials.stream().allMatch(material -> material.missing() == 0);
	}
}
