package io.github.nordavid.minekit.feature.crafting;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * What to plan and the player's choices.
 *
 * @param target             item id to make
 * @param quantity           how many to make (the inventory's existing copies of the target are not subtracted)
 * @param recipeChoices      item id to recipe signature, or to {@link #GATHER} to treat the item as gathered
 * @param alternativeChoices ingredient key to the item id to use for it
 * @param useInventory       whether items already in the inventory reduce what must be crafted or gathered
 */
public record PlanRequest(
		String target,
		int quantity,
		Map<String, String> recipeChoices,
		Map<String, String> alternativeChoices,
		boolean useInventory) {
	/** Recipe choice meaning "do not craft this item, the player gathers or already has it". */
	public static final String GATHER = "gather";
	public static final int MAX_QUANTITY = 100_000;

	public PlanRequest {
		Objects.requireNonNull(target, "target");
		quantity = Math.clamp(quantity, 1, MAX_QUANTITY);
		recipeChoices = Map.copyOf(new TreeMap<>(recipeChoices));
		alternativeChoices = Map.copyOf(new TreeMap<>(alternativeChoices));
	}

	public static PlanRequest of(String target, int quantity) {
		return new PlanRequest(target, quantity, Map.of(), Map.of(), true);
	}

	public PlanRequest withQuantity(int newQuantity) {
		return new PlanRequest(target, newQuantity, recipeChoices, alternativeChoices, useInventory);
	}

	public PlanRequest withRecipeChoice(String item, String choice) {
		Map<String, String> choices = new TreeMap<>(recipeChoices);
		choices.put(item, choice);
		return new PlanRequest(target, quantity, choices, alternativeChoices, useInventory);
	}

	public PlanRequest withoutRecipeChoice(String item) {
		Map<String, String> choices = new TreeMap<>(recipeChoices);
		choices.remove(item);
		return new PlanRequest(target, quantity, choices, alternativeChoices, useInventory);
	}

	public PlanRequest withAlternativeChoice(Ingredient ingredient, String item) {
		Map<String, String> choices = new TreeMap<>(alternativeChoices);
		choices.put(ingredient.key(), item);
		return new PlanRequest(target, quantity, recipeChoices, choices, useInventory);
	}

	public PlanRequest withUseInventory(boolean use) {
		return new PlanRequest(target, quantity, recipeChoices, alternativeChoices, use);
	}
}
