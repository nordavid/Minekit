package io.github.nordavid.minekit.core.container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.nordavid.minekit.core.item.ItemKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Test;

class PickupSimulatorTest {
	private static final ItemKey STONE = ItemKey.of("minecraft:stone");
	private static final ItemKey PEARL = ItemKey.of("minecraft:ender_pearl");
	private static final ToIntFunction<ItemKey> MAX = key -> key.equals(PEARL) ? 16 : 64;

	private static ContainerState state(SlotStack slot0, SlotStack carried) {
		List<SlotStack> slots = new ArrayList<>(Collections.nCopies(2, null));
		slots.set(0, slot0);
		return new ContainerState(slots, carried);
	}

	private static ContainerState click(ContainerState state) {
		return PickupSimulator.leftClick(state, 0, MAX);
	}

	@Test
	void pickUpWholeStack() {
		ContainerState after = click(state(new SlotStack(STONE, 40), null));
		assertNull(after.slot(0));
		assertEquals(new SlotStack(STONE, 40), after.carried());
	}

	@Test
	void putWholeStackDown() {
		ContainerState after = click(state(null, new SlotStack(STONE, 40)));
		assertEquals(new SlotStack(STONE, 40), after.slot(0));
		assertNull(after.carried());
	}

	@Test
	void mergeUpToMaxStackSizeKeepsRestOnCursor() {
		ContainerState after = click(state(new SlotStack(PEARL, 10), new SlotStack(PEARL, 12)));
		assertEquals(new SlotStack(PEARL, 16), after.slot(0));
		assertEquals(new SlotStack(PEARL, 6), after.carried());
	}

	@Test
	void mergeOntoFullStackChangesNothing() {
		ContainerState before = state(new SlotStack(STONE, 64), new SlotStack(STONE, 3));
		assertEquals(before, click(before));
	}

	@Test
	void differentItemsSwap() {
		ContainerState after = click(state(new SlotStack(STONE, 5), new SlotStack(PEARL, 2)));
		assertEquals(new SlotStack(PEARL, 2), after.slot(0));
		assertEquals(new SlotStack(STONE, 5), after.carried());
	}

	@Test
	void emptySlotAndEmptyCursorChangesNothing() {
		ContainerState before = state(null, null);
		assertEquals(before, click(before));
	}
}
