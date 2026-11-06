package io.github.nordavid.minekit.feature.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nordavid.minekit.core.item.ItemCategory;
import io.github.nordavid.minekit.core.persistence.JsonCodec;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventorySettingsTest {
	@Test
	void defaultsKeepTheHotbarAndUseGeneralProfile() {
		InventorySettings settings = InventorySettings.DEFAULT;

		assertTrue(settings.hotbar().keepsAll());
		assertEquals(SortProfile.GENERAL, settings.profile());
		assertEquals(SortProfile.GENERAL.defaultOrder(), settings.rules().categoryOrder());
	}

	@Test
	void movingCategoriesCustomizesOnlyThatProfileAndMovingBackRestoresDefault() {
		InventorySettings moved = InventorySettings.DEFAULT.withCategoryMoved(SortProfile.MINING, ItemCategory.BLOCKS, -1);

		assertEquals(ItemCategory.BLOCKS, moved.categoryOrder(SortProfile.MINING).get(1));
		assertTrue(moved.isCustomized(SortProfile.MINING));
		assertFalse(moved.isCustomized(SortProfile.GENERAL));

		InventorySettings back = moved.withCategoryMoved(SortProfile.MINING, ItemCategory.BLOCKS, +1);
		assertFalse(back.isCustomized(SortProfile.MINING));
		assertEquals(InventorySettings.DEFAULT, back);
	}

	@Test
	void movingBeyondTheEndsChangesNothing() {
		InventorySettings settings = InventorySettings.DEFAULT;
		ItemCategory first = settings.categoryOrder(SortProfile.GENERAL).getFirst();

		assertEquals(settings, settings.withCategoryMoved(SortProfile.GENERAL, first, -1));
	}

	@Test
	void profilesCycleAndFavoritesToggle() {
		InventorySettings settings = InventorySettings.DEFAULT.withNextProfile();
		assertEquals(SortProfile.MINING, settings.profile());
		assertEquals(SortProfile.GENERAL, settings.withNextProfile().withNextProfile().withNextProfile().profile());

		InventorySettings favorite = settings.withFavoriteToggled("minecraft:torch");
		assertEquals(Set.of("minecraft:torch"), favorite.favorites());
		assertEquals(Set.of(), favorite.withFavoriteToggled("minecraft:torch").favorites());
	}

	@Test
	void codecRoundTrip() throws Exception {
		InventorySettings settings = InventorySettings.DEFAULT
				.withActiveProfile(SortProfile.BUILDING)
				.withCategoryMoved(SortProfile.BUILDING, ItemCategory.FOOD, -2)
				.withHotbar(HotbarLayout.SUGGESTED)
				.withHotbarRule(6, HotbarRule.item("minecraft:torch"))
				.withHotbarRule(7, HotbarRule.EMPTY)
				.withFavoriteToggled("minecraft:diamond_pickaxe");

		JsonCodec.Decoded<InventorySettings> decoded = InventorySettingsCodec.INSTANCE.decode(InventorySettingsCodec.INSTANCE.encode(settings));

		assertEquals(settings, decoded.value());
		assertTrue(decoded.warnings().isEmpty(), decoded.warnings().toString());
	}

	@Test
	void invalidValuesFallBackWithWarnings() throws Exception {
		JsonCodec.Decoded<InventorySettings> decoded = InventorySettingsCodec.INSTANCE.decode(JsonParser.parseString("""
				{
				  "schemaVersion": 1,
				  "activeProfile": "speedrun",
				  "protectHotbar": "yes",
				  "categoryOrders": { "mining": ["TOOLS"], "unknown": ["TOOLS"] },
				  "favorites": ["minecraft:torch", "Not An Id", 5]
				}
				"""));

		InventorySettings value = decoded.value();
		assertEquals(SortProfile.GENERAL, value.profile());
		assertTrue(value.hotbar().keepsAll());
		assertTrue(value.customOrders().isEmpty());
		assertEquals(Set.of("minecraft:torch"), value.favorites());
		assertEquals(6, decoded.warnings().size(), decoded.warnings().toString());
	}

	@Test
	void schemaOneHotbarProtectionIsMigrated() throws Exception {
		JsonCodec.Decoded<InventorySettings> open = InventorySettingsCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "activeProfile": "mining", "protectHotbar": false }
				"""));
		assertEquals(HotbarLayout.ANY_ALL, open.value().hotbar());
		assertTrue(open.warnings().isEmpty(), open.warnings().toString());

		JsonCodec.Decoded<InventorySettings> kept = InventorySettingsCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 1, "protectHotbar": true }
				"""));
		assertEquals(HotbarLayout.KEEP_ALL, kept.value().hotbar());
	}

	@Test
	void invalidHotbarRulesKeepTheirSlotWithWarnings() throws Exception {
		JsonCodec.Decoded<InventorySettings> decoded = InventorySettingsCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 2,
				  "hotbar": ["melee", "laser", "item:Not An Id", "food", "any", "keep", "empty", "item:minecraft:torch", 7] }
				"""));

		HotbarLayout hotbar = decoded.value().hotbar();
		assertEquals(HotbarRule.of(HotbarRule.Kind.MELEE), hotbar.rule(0));
		assertEquals(HotbarRule.KEEP, hotbar.rule(1));
		assertEquals(HotbarRule.KEEP, hotbar.rule(2));
		assertEquals(HotbarRule.item("minecraft:torch"), hotbar.rule(7));
		assertEquals(HotbarRule.KEEP, hotbar.rule(8));
		assertEquals(3, decoded.warnings().size(), decoded.warnings().toString());

		JsonCodec.Decoded<InventorySettings> wrongSize = InventorySettingsCodec.INSTANCE.decode(JsonParser.parseString("""
				{ "schemaVersion": 2, "hotbar": ["melee"] }
				"""));
		assertEquals(HotbarLayout.KEEP_ALL, wrongSize.value().hotbar());
		assertEquals(1, wrongSize.warnings().size());
	}

	@Test
	void everyBuiltInProfileHasACompleteOrder() {
		for (SortProfile profile : SortProfile.ALL) {
			assertTrue(SortProfile.isCompleteOrder(profile.defaultOrder()), profile.id());
		}
		assertFalse(SortProfile.isCompleteOrder(List.of(ItemCategory.TOOLS)));
	}
}
