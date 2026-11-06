package io.github.nordavid.minekit.feature.crafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * All recipes known to the client, by output item, with a deterministic default choice. Pure logic.
 *
 * <p>Recipes that appear in both sources (server recipe book and vanilla game data) are merged into one entry marked
 * {@link RecipeOption.Source#SERVER}.
 *
 * <p>Default preference among several recipes for the same item:
 * <ol>
 *   <li>recipes that are not {@linkplain #isPenalized penalized};</li>
 *   <li>station order: crafting, stonecutter, furnace, smithing, then the faster furnace variants;</li>
 *   <li>recipes the server confirmed before vanilla defaults;</li>
 *   <li>fewer input items per output item;</li>
 *   <li>the signature, so the result never depends on load order.</li>
 * </ol>
 */
public final class RecipeIndex {
	private final Map<String, List<RecipeOption>> byOutput;
	private final Set<String> discouragedInputs;
	private final Map<String, Set<String>> inputItemsOf;

	public RecipeIndex(Collection<RecipeOption> recipes) {
		this(recipes, Set.of());
	}

	/**
	 * @param discouragedInputs items that are unusual to craft from: ore blocks (normally mined into their drop) and
	 *                          damageable tools or armor (smelting them down is recycling, not production)
	 */
	public RecipeIndex(Collection<RecipeOption> recipes, Set<String> discouragedInputs) {
		this.discouragedInputs = Set.copyOf(discouragedInputs);
		Map<String, Map<String, RecipeOption>> unique = new TreeMap<>();
		for (RecipeOption recipe : recipes) {
			Map<String, RecipeOption> forItem = unique.computeIfAbsent(recipe.output(), item -> new LinkedHashMap<>());
			RecipeOption existing = forItem.get(recipe.signature());
			if (existing == null || recipe.source() == RecipeOption.Source.SERVER) {
				forItem.put(recipe.signature(), existing == null ? recipe : recipe.withSource(RecipeOption.Source.SERVER));
			}
		}
		Map<String, Set<String>> inputs = new TreeMap<>();
		unique.forEach((item, forItem) -> {
			Set<String> items = new HashSet<>();
			forItem.values().forEach(recipe -> recipe.inputs().forEach(input -> items.addAll(input.ingredient().alternatives())));
			inputs.put(item, items);
		});
		this.inputItemsOf = inputs;

		Map<String, List<RecipeOption>> sorted = new TreeMap<>();
		unique.forEach((item, forItem) -> {
			List<RecipeOption> options = new ArrayList<>(forItem.values());
			options.sort(Comparator
					.<RecipeOption>comparingInt(recipe -> isPenalized(recipe) ? 1 : 0)
					.thenComparingInt(recipe -> recipe.station().ordinal())
					.thenComparingInt(recipe -> recipe.source().ordinal())
					.thenComparingDouble(RecipeOption::inputItemsPerOutput)
					.thenComparing(RecipeOption::signature));
			sorted.put(item, List.copyOf(options));
		});
		this.byOutput = Collections.unmodifiableMap(sorted);
	}

	/**
	 * A recipe is penalized if it is an unusual way to obtain its output:
	 * <ul>
	 *   <li>it "unpacks": an input can itself be made from the output (ingot from block or nuggets, cobblestone from
	 *       stone), or</li>
	 *   <li>an input slot accepts only discouraged items (smelting ore blocks, recycling tools).</li>
	 * </ul>
	 * When the best recipe of an intermediate item is penalized, the planner treats the item as gathered by default.
	 */
	public boolean isPenalized(RecipeOption recipe) {
		for (RecipeOption.Input input : recipe.inputs()) {
			if (discouragedInputs.containsAll(input.ingredient().alternatives())) {
				return true;
			}
			for (String inputItem : input.ingredient().alternatives()) {
				if (inputItemsOf.getOrDefault(inputItem, Set.of()).contains(recipe.output())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Recipes for {@code item}, the default first. Empty if the client knows none. */
	public List<RecipeOption> recipesFor(String item) {
		return byOutput.getOrDefault(item, List.of());
	}

	public boolean hasRecipe(String item) {
		return byOutput.containsKey(item);
	}

	public Optional<RecipeOption> bySignature(String item, String signature) {
		return recipesFor(item).stream().filter(recipe -> recipe.signature().equals(signature)).findFirst();
	}

	/** Every item that has at least one recipe, sorted by id. */
	public SortedSet<String> craftableItems() {
		return new TreeSet<>(byOutput.keySet());
	}

	public int size() {
		return byOutput.values().stream().mapToInt(List::size).sum();
	}
}
