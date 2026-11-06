package io.github.nordavid.minekit.feature.crafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/**
 * Calculates what to gather and craft for a target item. Pure and deterministic.
 *
 * <p>How it works:
 * <ol>
 *   <li><b>Resolve</b> one recipe per item and one item per multi-item ingredient ("any planks"), honouring the
 *       player's choices, avoiding recipes that would loop back into an item that is already being made.</li>
 *   <li><b>Order</b> the resulting graph so that every item comes after all items that consume it.</li>
 *   <li><b>Propagate demand</b> in that order. Because all demand for an item is known before it is processed, each
 *       item is crafted exactly once with the combined amount, so batch leftovers (4 planks per log) are shared by
 *       all consumers instead of being wasted.</li>
 * </ol>
 * The propagation runs twice: once ignoring the inventory (total needs) and, if requested, once taking items from the
 * inventory first (what is still missing). Inventory copies of the target itself are never subtracted.
 */
public final class CraftingPlanner {
	private sealed interface Node permits ItemNode, GroupNode {
	}

	private record ItemNode(String item) implements Node {
	}

	private record GroupNode(Ingredient ingredient) implements Node {
	}

	private record Edge(Node child, int perUnit) {
	}

	private final RecipeIndex index;

	public CraftingPlanner(RecipeIndex index) {
		this.index = Objects.requireNonNull(index, "index");
	}

	/**
	 * @param inventory item id to count of items the player has (used only if the request says so, and for display)
	 */
	public CraftingPlan plan(PlanRequest request, Map<String, Integer> inventory) {
		Resolution resolution = new Resolution(request, inventory);
		ItemNode target = new ItemNode(request.target());
		resolution.resolve(target, new ArrayDeque<>());
		List<Node> order = resolution.topologicalOrder(target);

		Propagation total = resolution.propagate(order, target, request.quantity(), Map.of());
		Propagation actual = request.useInventory()
				? resolution.propagate(order, target, request.quantity(), inventory)
				: total;

		List<CraftingPlan.Material> materials = new ArrayList<>();
		total.leafNeeds.forEach((node, needed) -> {
			Ingredient ingredient = node instanceof GroupNode group ? group.ingredient() : Ingredient.of(((ItemNode) node).item());
			String chosen = node instanceof GroupNode group ? resolution.groupChoice.get(group) : ((ItemNode) node).item();
			int inInventory = ingredient.alternatives().stream().mapToInt(item -> inventory.getOrDefault(item, 0)).sum();
			long missing = actual.leafNeeds.getOrDefault(node, 0L);
			boolean craftable = node instanceof ItemNode item && index.hasRecipe(item.item());
			materials.add(new CraftingPlan.Material(ingredient, chosen, saturate(needed), inInventory, saturate(missing), craftable));
		});
		materials.sort(Comparator.comparing((CraftingPlan.Material material) -> material.chosen()));

		List<CraftingPlan.Step> steps = new ArrayList<>();
		for (int i = order.size() - 1; i >= 0; i--) {
			if (order.get(i) instanceof ItemNode node && actual.crafts.containsKey(node)) {
				RecipeOption recipe = resolution.recipe.get(node);
				long crafts = actual.crafts.get(node);
				steps.add(new CraftingPlan.Step(node.item(), recipe, saturate(crafts), saturate(crafts * recipe.outputCount()),
						index.recipesFor(node.item()).size()));
			}
		}

		List<CraftingPlan.Warning> warnings = new ArrayList<>();
		if (resolution.recipe.get(target) == null) {
			warnings.add(new CraftingPlan.Warning(CraftingPlan.WarningKind.NO_RECIPE_FOR_TARGET, request.target()));
		}
		resolution.cycles.forEach(item -> warnings.add(new CraftingPlan.Warning(CraftingPlan.WarningKind.CYCLE, item)));
		steps.stream().filter(step -> step.recipe().source() == RecipeOption.Source.VANILLA)
				.forEach(step -> warnings.add(new CraftingPlan.Warning(CraftingPlan.WarningKind.VANILLA_RECIPE_USED, step.item())));
		steps.stream().filter(step -> step.recipe().station().needsFuel()).findFirst()
				.ifPresent(step -> warnings.add(new CraftingPlan.Warning(CraftingPlan.WarningKind.NEEDS_FUEL, step.item())));

		Map<String, Integer> leftovers = new TreeMap<>();
		actual.leftovers.forEach((item, amount) -> leftovers.put(item, saturate(amount)));
		Map<String, Integer> fromInventory = new TreeMap<>();
		actual.takenForIntermediates.forEach((item, amount) -> fromInventory.put(item, saturate(amount)));
		return new CraftingPlan(List.copyOf(steps), List.copyOf(materials), leftovers, fromInventory, List.copyOf(warnings));
	}

	private static int saturate(long value) {
		return (int) Math.min(Integer.MAX_VALUE, value);
	}

	private record Propagation(Map<ItemNode, Long> crafts, Map<Node, Long> leafNeeds, Map<String, Long> leftovers,
			Map<String, Long> takenForIntermediates) {
	}

	/** Recipe and alternative choices for one plan, plus the propagation over the resulting graph. */
	private final class Resolution {
		private final PlanRequest request;
		private final Map<String, Integer> inventory;
		private final Map<ItemNode, @Nullable RecipeOption> recipe = new HashMap<>();
		private final Map<GroupNode, String> groupChoice = new HashMap<>();
		private final Map<Node, List<Edge>> edges = new HashMap<>();
		private final Set<Node> resolved = new HashSet<>();
		private final Set<String> cycles = new LinkedHashSet<>();

		Resolution(PlanRequest request, Map<String, Integer> inventory) {
			this.request = request;
			this.inventory = inventory;
		}

		void resolve(Node node, Deque<Node> stack) {
			if (!resolved.add(node)) {
				return;
			}
			stack.push(node);
			List<Edge> children = new ArrayList<>();
			switch (node) {
				case ItemNode item -> {
					RecipeOption chosen = chooseRecipe(item.item(), stack);
					recipe.put(item, chosen);
					if (chosen != null) {
						for (RecipeOption.Input input : chosen.inputs()) {
							children.add(new Edge(nodeFor(input.ingredient()), input.count()));
						}
					}
				}
				case GroupNode group -> {
					String chosen = chooseAlternative(group.ingredient(), stack);
					groupChoice.put(group, chosen);
					ItemNode child = new ItemNode(chosen);
					// A group whose item is gathered anyway stays a group, so "any of these" is shown and counted.
					if (!isGathered(chosen) && !onStack(child, stack)) {
						children.add(new Edge(child, 1));
					}
				}
			}
			edges.put(node, children);
			for (Edge edge : children) {
				resolve(edge.child(), stack);
			}
			stack.pop();
		}

		private Node nodeFor(Ingredient ingredient) {
			return ingredient.isSingle() ? new ItemNode(ingredient.first()) : new GroupNode(ingredient);
		}

		/** Whether the plan gathers {@code item} instead of crafting it, ignoring loops (used to keep "any of" groups). */
		private boolean isGathered(String item) {
			String choice = request.recipeChoices().get(item);
			if (PlanRequest.GATHER.equals(choice) || !index.hasRecipe(item)) {
				return true;
			}
			return choice == null && !item.equals(request.target()) && naturalDefault(item, new ArrayDeque<>()) == null;
		}

		private boolean onStack(Node node, Deque<Node> stack) {
			return stack.contains(node);
		}

		private @Nullable RecipeOption chooseRecipe(String item, Deque<Node> stack) {
			String choice = request.recipeChoices().get(item);
			if (PlanRequest.GATHER.equals(choice)) {
				return null;
			}
			List<RecipeOption> candidates = new ArrayList<>(index.recipesFor(item));
			if (candidates.isEmpty()) {
				return null;
			}
			if (choice != null) {
				index.bySignature(item, choice).ifPresent(preferred -> {
					candidates.remove(preferred);
					candidates.addFirst(preferred);
				});
			}
			List<RecipeOption> usable = candidates.stream().filter(candidate -> !loopsBack(candidate, stack)).toList();
			if (usable.isEmpty()) {
				cycles.add(item);
				return null;
			}
			boolean explicit = choice != null && usable.getFirst().signature().equals(choice);
			if (explicit || item.equals(request.target())) {
				return usable.getFirst();
			}
			return naturalDefault(item, stack);
		}

		/**
		 * Default recipe for an intermediate item: the best one unless it is penalized (unpacking, ore smelting,
		 * recycling). Then the item counts as a raw material to gather, unless the inventory already holds the
		 * inputs of one of its recipes, e.g. iron blocks to unpack into ingots.
		 */
		private @Nullable RecipeOption naturalDefault(String item, Deque<Node> stack) {
			List<RecipeOption> usable = index.recipesFor(item).stream().filter(candidate -> !loopsBack(candidate, stack)).toList();
			if (usable.isEmpty()) {
				return null;
			}
			if (!index.isPenalized(usable.getFirst())) {
				return usable.getFirst();
			}
			if (request.useInventory()) {
				for (RecipeOption candidate : usable) {
					if (inputsInInventory(candidate)) {
						return candidate;
					}
				}
			}
			return null;
		}

		private boolean inputsInInventory(RecipeOption recipe) {
			return recipe.inputs().stream().allMatch(input ->
					input.ingredient().alternatives().stream().anyMatch(item -> inventory.getOrDefault(item, 0) > 0));
		}

		/** Whether any input of the recipe is, or can only be, an item that is already being made further up. */
		private boolean loopsBack(RecipeOption candidate, Deque<Node> stack) {
			for (RecipeOption.Input input : candidate.inputs()) {
				boolean allOnStack = input.ingredient().alternatives().stream().allMatch(item -> onStack(new ItemNode(item), stack));
				if (allOnStack || stack.contains(nodeFor(input.ingredient()))) {
					return true;
				}
			}
			return false;
		}

		private String chooseAlternative(Ingredient ingredient, Deque<Node> stack) {
			String choice = request.alternativeChoices().get(ingredient.key());
			if (choice != null && ingredient.alternatives().contains(choice) && !onStack(new ItemNode(choice), stack)) {
				return choice;
			}
			List<String> options = new ArrayList<>(ingredient.alternatives());
			options.removeIf(item -> onStack(new ItemNode(item), stack));
			if (options.isEmpty()) {
				return ingredient.first();
			}
			options.sort(Comparator.<String>comparingInt(item -> -inventory.getOrDefault(item, 0))
					.thenComparingInt(item -> -inputsInInventory(item))
					.thenComparingInt(item -> item.equals(ingredient.primary()) ? 0 : 1)
					.thenComparing(Comparator.naturalOrder()));
			return options.getFirst();
		}

		/** How many items matching the default recipe's inputs the player holds; prefers alternatives they can make now. */
		private int inputsInInventory(String item) {
			List<RecipeOption> recipes = index.recipesFor(item);
			if (recipes.isEmpty()) {
				return 0;
			}
			int count = 0;
			for (RecipeOption.Input input : recipes.getFirst().inputs()) {
				for (String alternative : input.ingredient().alternatives()) {
					count += inventory.getOrDefault(alternative, 0);
				}
			}
			return count;
		}

		/** Kahn's algorithm: every node after all of its consumers. */
		List<Node> topologicalOrder(Node root) {
			Map<Node, Integer> consumers = new LinkedHashMap<>();
			consumers.put(root, 0);
			edges.forEach((parent, children) -> children.forEach(edge -> consumers.merge(edge.child(), 1, Integer::sum)));
			Deque<Node> ready = new ArrayDeque<>(List.of(root));
			List<Node> order = new ArrayList<>();
			while (!ready.isEmpty()) {
				Node node = ready.poll();
				order.add(node);
				for (Edge edge : edges.getOrDefault(node, List.of())) {
					if (consumers.merge(edge.child(), -1, Integer::sum) == 0) {
						ready.add(edge.child());
					}
				}
			}
			return order;
		}

		Propagation propagate(List<Node> order, ItemNode target, int quantity, Map<String, Integer> stock) {
			Map<String, Long> available = new HashMap<>();
			stock.forEach((item, count) -> available.put(item, (long) count));
			Map<Node, Long> demand = new HashMap<>();
			demand.put(target, (long) quantity);
			Map<ItemNode, Long> crafts = new HashMap<>();
			Map<Node, Long> leafNeeds = new LinkedHashMap<>();
			Map<String, Long> leftovers = new TreeMap<>();
			Map<String, Long> taken = new TreeMap<>();

			for (Node node : order) {
				long need = demand.getOrDefault(node, 0L);
				if (need <= 0) {
					continue;
				}
				List<Edge> children = edges.getOrDefault(node, List.of());
				boolean leaf = children.isEmpty();
				if (!node.equals(target)) {
					long fromStock = takeFromStock(node, need, available);
					need -= fromStock;
					if (fromStock > 0 && !leaf) {
						String item = node instanceof ItemNode itemNode ? itemNode.item() : groupChoice.get((GroupNode) node);
						taken.merge(item, fromStock, Long::sum);
					}
				}
				if (leaf) {
					leafNeeds.merge(node, need, Long::sum);
					continue;
				}
				if (need == 0) {
					continue;
				}
				if (node instanceof ItemNode item) {
					RecipeOption recipe = Objects.requireNonNull(this.recipe.get(item));
					long count = Math.ceilDiv(need, recipe.outputCount());
					crafts.put(item, count);
					long extra = count * recipe.outputCount() - need;
					if (extra > 0) {
						leftovers.merge(item.item(), extra, Long::sum);
					}
					for (Edge edge : children) {
						demand.merge(edge.child(), count * edge.perUnit(), Long::sum);
					}
				} else {
					for (Edge edge : children) {
						demand.merge(edge.child(), need * edge.perUnit(), Long::sum);
					}
				}
			}
			return new Propagation(crafts, leafNeeds, leftovers, taken);
		}

		private long takeFromStock(Node node, long need, Map<String, Long> available) {
			List<String> items = new ArrayList<>(switch (node) {
				case ItemNode item -> List.of(item.item());
				case GroupNode group -> group.ingredient().alternatives();
			});
			items.sort(Comparator.<String>comparingLong(item -> -available.getOrDefault(item, 0L)).thenComparing(Comparator.naturalOrder()));
			long taken = 0;
			for (String item : items) {
				long take = Math.min(need - taken, available.getOrDefault(item, 0L));
				if (take > 0) {
					available.merge(item, -take, Long::sum);
					taken += take;
				}
			}
			return taken;
		}
	}
}
