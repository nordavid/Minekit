package io.github.nordavid.minekit.core.item;

import io.github.nordavid.minekit.core.container.SlotStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Maps Minecraft item stacks to {@link ItemKey}s and provides {@link ItemFacts} for them.
 *
 * <p>Two stacks get the same key exactly when {@link ItemStack#isSameItemSameComponents} says they are the same,
 * which is the condition under which Minecraft merges them. The key's variant is a deterministic description of the
 * stack's component changes (sorted by component type), so keys and therefore sort orders are stable across sessions.
 * One index is used for the whole duration of an operation so keys stay consistent between snapshots.
 */
public final class ItemKeyIndex implements ItemFacts {
	private record Entry(ItemKey key, ItemStack representative, int maxStackSize, ItemCategory category) {
	}

	/** Components that change with use; an exact loadout match ignores them. */
	private static final Set<DataComponentType<?>> WEAR = Set.of(DataComponents.DAMAGE, DataComponents.REPAIR_COST);

	private final ItemCategorizer categorizer;
	private final Map<Item, List<Entry>> byItem = new HashMap<>();
	private final Map<ItemKey, Entry> byKey = new HashMap<>();

	public ItemKeyIndex(ItemCategorizer categorizer) {
		this.categorizer = Objects.requireNonNull(categorizer, "categorizer");
	}

	/** The stack as pure model, or {@code null} for an empty stack. */
	public @Nullable SlotStack toSlotStack(ItemStack stack) {
		return stack.isEmpty() ? null : new SlotStack(keyOf(stack), stack.getCount());
	}

	public ItemKey keyOf(ItemStack stack) {
		if (stack.isEmpty()) {
			throw new IllegalArgumentException("empty stack has no key");
		}
		List<Entry> entries = byItem.computeIfAbsent(stack.getItem(), item -> new ArrayList<>());
		for (Entry entry : entries) {
			if (ItemStack.isSameItemSameComponents(entry.representative(), stack)) {
				return entry.key();
			}
		}
		String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		String variant = describe(stack.getComponentsPatch(), Set.of());
		ItemKey key = new ItemKey(itemId, variant);
		for (int collision = 2; byKey.containsKey(key); collision++) {
			// Different components that print the same way stay distinct.
			key = new ItemKey(itemId, variant + "#" + collision);
		}
		Entry entry = new Entry(key, stack.copyWithCount(1), stack.getMaxStackSize(), categorizer.categorize(stack));
		entries.add(entry);
		byKey.put(key, entry);
		return key;
	}

	/**
	 * The key's components without damage and repair cost, described like {@link ItemKey#variant()}: what a
	 * loadout's exact match compares, so wear and anvil use do not break it.
	 */
	public String exactVariant(ItemKey key) {
		return describe(require(key).representative().getComponentsPatch(), WEAR);
	}

	/** A one-item copy of a stack with this key, for drawing. */
	public ItemStack displayStack(ItemKey key) {
		return require(key).representative().copy();
	}

	@Override
	public int maxStackSize(ItemKey key) {
		return require(key).maxStackSize();
	}

	@Override
	public ItemCategory category(ItemKey key) {
		return require(key).category();
	}

	private Entry require(ItemKey key) {
		Entry entry = byKey.get(key);
		if (entry == null) {
			throw new IllegalArgumentException("unknown key " + key);
		}
		return entry;
	}

	private static String describe(DataComponentPatch patch, Set<DataComponentType<?>> ignored) {
		if (patch.isEmpty()) {
			return "";
		}
		DataComponentPatch.SplitResult split = patch.split();
		List<String> parts = new ArrayList<>();
		for (TypedDataComponent<?> component : split.added()) {
			if (!ignored.contains(component.type())) {
				parts.add(typeId(component.type()) + "=" + component.value());
			}
		}
		for (DataComponentType<?> removed : split.removed()) {
			if (!ignored.contains(removed)) {
				parts.add("!" + typeId(removed));
			}
		}
		if (parts.isEmpty()) {
			return "";
		}
		parts.sort(Comparator.naturalOrder());
		return "{" + String.join(",", parts) + "}";
	}

	private static String typeId(DataComponentType<?> type) {
		return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
	}
}
