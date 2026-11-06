package io.github.nordavid.minekit.feature.deposit;

import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.item.ItemKey;
import io.github.nordavid.minekit.core.item.ItemKeyIndex;
import java.util.Objects;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** {@link DepositPlanner.Rules} from the client's item data and the open container's slots. */
final class MinecraftDepositRules implements DepositPlanner.Rules {
	private final ItemKeyIndex keys;
	private final @Nullable Slot containerSlot;

	/**
	 * @param containerSlot any slot of the container (all slots of a supported container accept the same items), or
	 *                      {@code null} if it has none
	 */
	MinecraftDepositRules(ItemKeyIndex keys, @Nullable Slot containerSlot) {
		this.keys = Objects.requireNonNull(keys, "keys");
		this.containerSlot = containerSlot;
	}

	@Override
	public int maxStackSize(ItemKey key) {
		int itemMax = keys.maxStackSize(key);
		return containerSlot == null ? itemMax : Math.min(itemMax, containerSlot.getMaxStackSize(keys.displayStack(key)));
	}

	@Override
	public ItemCategory category(ItemKey key) {
		return keys.category(key);
	}

	@Override
	public boolean isFarming(ItemKey key) {
		ItemStack stack = keys.displayStack(key);
		return stack.is(ConventionalItemTags.SEEDS) || stack.is(ConventionalItemTags.CROPS) || stack.is(ItemTags.SAPLINGS)
				|| stack.is(ConventionalItemTags.FERTILIZERS) || stack.is(Items.BONE_MEAL);
	}

	@Override
	public boolean containerAccepts(ItemKey key) {
		return containerSlot != null && containerSlot.mayPlace(keys.displayStack(key));
	}
}
