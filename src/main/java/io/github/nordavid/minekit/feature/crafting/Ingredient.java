package io.github.nordavid.minekit.feature.crafting;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * One recipe input slot: any of {@code alternatives} (item ids) is accepted, e.g. any kind of planks.
 * Item components are ignored, as the recipe book does for ordinary recipes.
 *
 * @param alternatives every accepted item
 * @param primary      the item listed first by the game (tags list the typical item first, e.g. oak planks or
 *                     cobblestone); used when nothing else decides which alternative to plan with
 */
public record Ingredient(SortedSet<String> alternatives, String primary) implements Comparable<Ingredient> {
	public Ingredient {
		Objects.requireNonNull(alternatives, "alternatives");
		Objects.requireNonNull(primary, "primary");
		if (alternatives.isEmpty()) {
			throw new IllegalArgumentException("an ingredient needs at least one item");
		}
		if (!alternatives.contains(primary)) {
			throw new IllegalArgumentException("primary must be one of the alternatives");
		}
		alternatives = Collections.unmodifiableSortedSet(new TreeSet<>(alternatives));
	}

	/** An ingredient in the game's order: the first item is the primary one. */
	public static Ingredient of(String... itemIds) {
		return ofOrdered(List.of(itemIds));
	}

	public static Ingredient ofOrdered(List<String> itemIds) {
		return new Ingredient(new TreeSet<>(itemIds), itemIds.getFirst());
	}

	public boolean isSingle() {
		return alternatives.size() == 1;
	}

	public String first() {
		return alternatives.first();
	}

	/** Stable text form of the accepted items, used as the key for the player's choice among alternatives. */
	public String key() {
		return String.join("|", alternatives);
	}

	@Override
	public int compareTo(Ingredient other) {
		int byItems = key().compareTo(other.key());
		return byItems != 0 ? byItems : primary.compareTo(other.primary);
	}
}
