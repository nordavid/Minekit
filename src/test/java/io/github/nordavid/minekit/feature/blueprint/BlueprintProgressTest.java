package io.github.nordavid.minekit.feature.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nordavid.minekit.feature.blueprint.BlueprintProgress.Status;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlueprintProgressTest {
	private static final BlockSpec STONE = BlockSpec.parse("minecraft:stone");
	private static final BlockSpec PLANKS = BlockSpec.parse("minecraft:oak_planks");

	/** 2x3x1: layer 0 stone, stone; layer 1 planks, air; layer 2 stone, planks. */
	private static Blueprint tower() {
		return new Blueprint("t", "", Blueprint.Format.SPONGE_V3, 0, 2, 3, 1, List.of(BlockSpec.AIR, STONE, PLANKS),
				new short[] {1, 1, 2, 0, 1, 2});
	}

	@Test
	void airIsNotTrackedAndEverythingStartsUnchecked() {
		BlueprintProgress progress = new BlueprintProgress(tower(), new BlueprintSettings.LayerRange(0, 2));
		assertEquals(5, progress.total());
		assertEquals(5, progress.count(Status.UNCHECKED));
		assertEquals(0.0, progress.completion());
		assertEquals(3, progress.needed(1));
		assertEquals(-1, progress.lowestOpenLayer(), "nothing is known yet, so nothing is open");
	}

	@Test
	void countsFollowReportsAndTheLowestOpenLayerMoves() {
		BlueprintProgress progress = new BlueprintProgress(tower(), new BlueprintSettings.LayerRange(0, 2));
		progress.set(0, Status.CORRECT);
		progress.set(1, Status.MISSING);
		progress.set(2, Status.WRONG);
		progress.set(3, Status.CORRECT); // air: ignored
		assertEquals(1, progress.count(Status.CORRECT));
		assertEquals(0, progress.lowestOpenLayer());
		assertEquals(1, progress.placed(1));

		progress.set(1, Status.CORRECT);
		assertEquals(1, progress.lowestOpenLayer());
		progress.set(2, Status.CORRECT);
		progress.set(4, Status.MISSING);
		assertEquals(2, progress.lowestOpenLayer());
		assertEquals(3, progress.count(Status.CORRECT));
		assertEquals(1, progress.count(Status.MISSING));
		assertEquals(1, progress.count(Status.UNCHECKED));
		assertEquals(0.6, progress.completion(), 1e-9);

		progress.set(4, Status.UNCHECKED);
		assertEquals(-1, progress.lowestOpenLayer(), "a position whose chunk unloaded is unchecked again, not missing");
	}

	@Test
	void aLayerRangeLimitsWhatIsTrackedAndTheSweepVisitsEverythingInTurn() {
		BlueprintProgress upper = new BlueprintProgress(tower(), new BlueprintSettings.LayerRange(1, 2));
		assertEquals(3, upper.total());
		upper.set(0, Status.CORRECT);
		assertEquals(0, upper.count(Status.CORRECT), "layer 0 is outside the range");

		Set<Integer> visited = new HashSet<>();
		for (int round = 0; round < 3; round++) {
			for (int index : upper.nextToCheck(2)) {
				visited.add(index);
			}
		}
		assertEquals(Set.of(2, 4, 5), visited);
	}
}
