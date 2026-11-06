package io.github.nordavid.minekit.feature.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * One way to make an item, in a form the planner can calculate with.
 *
 * @param station     where the recipe is made
 * @param output      item id of the result
 * @param outputCount items produced per craft
 * @param inputs      ingredients per craft, identical ingredients combined, in a stable order
 * @param source      whether the server sent this recipe or it is the vanilla default from the game files
 */
public record RecipeOption(Station station, String output, int outputCount, List<Input> inputs, Source source) {
	/** Where a recipe is made. Furnace-type stations also need fuel, which the plan does not count. */
	public enum Station {
		CRAFTING, STONECUTTER, FURNACE, SMITHING, BLAST_FURNACE, SMOKER, CAMPFIRE, OTHER;

		public boolean needsFuel() {
			return this == FURNACE || this == BLAST_FURNACE || this == SMOKER;
		}
	}

	/**
	 * {@code SERVER}: the server unlocked this recipe for the player (recipe book), so it certainly exists there.
	 * {@code VANILLA}: read from the game's built-in data; a server may have changed or removed it.
	 */
	public enum Source {
		SERVER, VANILLA
	}

	/** {@code count} items matching {@code ingredient} per craft. */
	public record Input(Ingredient ingredient, int count) {
		public Input {
			Objects.requireNonNull(ingredient, "ingredient");
			if (count < 1) {
				throw new IllegalArgumentException("count must be positive");
			}
		}
	}

	public RecipeOption {
		Objects.requireNonNull(station, "station");
		Objects.requireNonNull(output, "output");
		Objects.requireNonNull(source, "source");
		if (outputCount < 1) {
			throw new IllegalArgumentException("outputCount must be positive");
		}
		List<Input> sorted = new ArrayList<>(inputs);
		sorted.sort((a, b) -> a.ingredient().compareTo(b.ingredient()));
		inputs = List.copyOf(sorted);
	}

	/**
	 * Content-based identity, independent of where the recipe came from: station, result and inputs. Saved plans
	 * remember recipe choices by this signature, so they survive restarts and match server and vanilla copies alike.
	 */
	public String signature() {
		return station + ":" + output + "x" + outputCount + "<"
				+ inputs.stream().map(input -> input.count() + "x" + input.ingredient().key()).collect(Collectors.joining(","));
	}

	public RecipeOption withSource(Source newSource) {
		return new RecipeOption(station, output, outputCount, inputs, newSource);
	}

	/** Total number of input items per output item, used to prefer cheaper recipes. */
	public double inputItemsPerOutput() {
		return inputs.stream().mapToInt(Input::count).sum() / (double) outputCount;
	}

	/** Combines equal ingredients, e.g. 3 separate "planks" slots into one input with count 3. */
	public static List<Input> combine(List<Ingredient> slots) {
		java.util.Map<Ingredient, Integer> counts = new java.util.TreeMap<>();
		slots.forEach(slot -> counts.merge(slot, 1, Integer::sum));
		List<Input> inputs = new ArrayList<>();
		counts.forEach((ingredient, count) -> inputs.add(new Input(ingredient, count)));
		return inputs;
	}
}
