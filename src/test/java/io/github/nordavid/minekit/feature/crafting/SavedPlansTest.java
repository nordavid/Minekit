package io.github.nordavid.minekit.feature.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import org.junit.jupiter.api.Test;

class SavedPlansTest {
	private static final PlanRequest PISTONS = PlanRequest.of("minecraft:piston", 64)
			.withRecipeChoice("minecraft:iron_ingot", "FURNACE:minecraft:iron_ingotx1<1xminecraft:raw_iron")
			.withAlternativeChoice(Ingredient.of("minecraft:oak_planks", "minecraft:birch_planks"), "minecraft:birch_planks")
			.withUseInventory(false);

	@Test
	void savingReplacesThePlanForTheSameTargetAndKeepsNewestFirst() {
		SavedPlans plans = SavedPlans.EMPTY
				.withSaved(PISTONS, 1)
				.withSaved(PlanRequest.of("minecraft:torch", 16), 2)
				.withSaved(PISTONS.withQuantity(10), 3);

		assertEquals(2, plans.plans().size());
		assertEquals(10, plans.plans().getFirst().request().quantity());
		assertEquals("minecraft:torch", plans.plans().get(1).request().target());
		assertEquals(1, plans.without("minecraft:piston").plans().size());
	}

	@Test
	void numberOfPlansIsLimited() {
		SavedPlans plans = SavedPlans.EMPTY;
		for (int i = 0; i < SavedPlans.MAX_PLANS + 5; i++) {
			plans = plans.withSaved(PlanRequest.of("minecraft:item_" + i, 1), i);
		}
		assertEquals(SavedPlans.MAX_PLANS, plans.plans().size());
		assertEquals("minecraft:item_" + (SavedPlans.MAX_PLANS + 4), plans.plans().getFirst().request().target());
	}

	@Test
	void codecRoundTripKeepsChoices() throws Exception {
		SavedPlans plans = SavedPlans.EMPTY.withSaved(PISTONS, 1793980000000L);

		JsonCodec.Decoded<SavedPlans> decoded = SavedPlansCodec.INSTANCE.decode(SavedPlansCodec.INSTANCE.encode(plans));

		assertEquals(plans, decoded.value());
		assertTrue(decoded.warnings().isEmpty());
	}

	@Test
	void invalidPlansAreDroppedWithWarnings() throws Exception {
		JsonCodec.Decoded<SavedPlans> decoded = SavedPlansCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "plans": [
				  { "target": "minecraft:torch", "quantity": 4, "useInventory": true },
				  { "target": "not an id", "quantity": 4, "useInventory": true },
				  { "target": "minecraft:torch", "quantity": 0, "useInventory": true },
				  7
				] }
				"""));

		assertEquals(1, decoded.value().plans().size());
		assertEquals(3, decoded.warnings().size());
	}
}
