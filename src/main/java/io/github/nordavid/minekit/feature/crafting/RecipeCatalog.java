package io.github.nordavid.minekit.feature.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Collects the recipes the client can know about (project decision "variant A", ADR 0004):
 * <ul>
 *   <li><b>Server recipes:</b> the recipe book entries the server sent for this player (unlocked recipes).</li>
 *   <li><b>Vanilla defaults:</b> the recipe files built into the game, read from the client's own vanilla data and
 *       decoded with the tags the server synchronized. A server may have changed or removed these, so they are
 *       marked {@link RecipeOption.Source#VANILLA} and the UI says so.</li>
 * </ul>
 * Both are converted through Minecraft's recipe displays, so they look exactly like the recipe book. Recipes whose
 * result is also one of their inputs (armor trims, dyeing, copying books) are left out: they never help to obtain an
 * item. Client thread only.
 */
public final class RecipeCatalog {
	private static final Logger LOGGER = LoggerFactory.getLogger("MINEKIT");

	private RecipeCatalog() {
	}

	public static RecipeIndex load(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return new RecipeIndex(List.of());
		}
		long start = System.nanoTime();
		ContextMap context = SlotDisplayContext.fromLevel(level);
		FeatureFlagSet features = level.enabledFeatures();
		List<RecipeOption> recipes = new ArrayList<>();

		int serverEntries = 0;
		for (RecipeCollection collection : player.getRecipeBook().getCollections()) {
			for (RecipeDisplayEntry entry : collection.getRecipes()) {
				serverEntries++;
				convert(entry.display(), context, features, RecipeOption.Source.SERVER).ifPresent(recipes::add);
			}
		}

		int vanillaFiles = 0;
		int vanillaFailures = 0;
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
		List<JsonElement> files = new ArrayList<>();
		try (PackResources pack = ServerPacksSource.createVanillaPackSource().fullResources()) {
			pack.listResources(PackType.SERVER_DATA, "minecraft", "recipe", (id, supplier) -> {
				try (Reader reader = new InputStreamReader(supplier.get(), StandardCharsets.UTF_8)) {
					files.add(JsonParser.parseReader(reader));
				} catch (Exception e) {
					LOGGER.debug("Could not read vanilla recipe {}", id, e);
				}
			});
		} catch (Exception e) {
			LOGGER.warn("Could not open the vanilla game data; only server recipes are available", e);
		}
		for (JsonElement json : files) {
			vanillaFiles++;
			Optional<Recipe<?>> recipe = Recipe.DIRECT_CODEC.parse(ops, json).result();
			if (recipe.isEmpty()) {
				vanillaFailures++;
				continue;
			}
			for (RecipeDisplay display : recipe.get().display()) {
				convert(display, context, features, RecipeOption.Source.VANILLA).ifPresent(recipes::add);
			}
		}

		RecipeIndex index = new RecipeIndex(recipes, discouragedInputs(recipes));
		LOGGER.info("Recipe index: {} recipes for {} items ({} server entries, {} vanilla files, {} not decodable) in {} ms",
				index.size(), index.craftableItems().size(), serverEntries, vanillaFiles, vanillaFailures,
				(System.nanoTime() - start) / 1_000_000);
		return index;
	}

	/** Ore blocks (normally mined into their drop) and damageable gear (smelting it is recycling). */
	private static Set<String> discouragedInputs(List<RecipeOption> recipes) {
		Set<String> discouraged = new java.util.HashSet<>();
		Set<String> seen = new java.util.HashSet<>();
		for (RecipeOption recipe : recipes) {
			for (RecipeOption.Input input : recipe.inputs()) {
				for (String id : input.ingredient().alternatives()) {
					if (!seen.add(id)) {
						continue;
					}
					Identifier identifier = Identifier.tryParse(id);
					if (identifier == null || !BuiltInRegistries.ITEM.containsKey(identifier)) {
						continue;
					}
					ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(identifier));
					if (stack.is(ConventionalItemTags.ORES) || stack.isDamageableItem()) {
						discouraged.add(id);
					}
				}
			}
		}
		return discouraged;
	}

	static Optional<RecipeOption> convert(RecipeDisplay display, ContextMap context, FeatureFlagSet features, RecipeOption.Source source) {
		if (!display.isEnabled(features)) {
			return Optional.empty();
		}
		List<SlotDisplay> slots;
		RecipeOption.Station fallback;
		switch (display) {
			case ShapedCraftingRecipeDisplay shaped -> {
				slots = shaped.ingredients();
				fallback = RecipeOption.Station.CRAFTING;
			}
			case ShapelessCraftingRecipeDisplay shapeless -> {
				slots = shapeless.ingredients();
				fallback = RecipeOption.Station.CRAFTING;
			}
			case FurnaceRecipeDisplay furnace -> {
				slots = List.of(furnace.ingredient());
				fallback = RecipeOption.Station.FURNACE;
			}
			case StonecutterRecipeDisplay stonecutter -> {
				slots = List.of(stonecutter.input());
				fallback = RecipeOption.Station.STONECUTTER;
			}
			case SmithingRecipeDisplay smithing -> {
				slots = List.of(smithing.template(), smithing.base(), smithing.addition());
				fallback = RecipeOption.Station.SMITHING;
			}
			default -> {
				return Optional.empty();
			}
		}

		ItemStack result = display.result().resolveForFirstStack(context);
		if (result.isEmpty()) {
			return Optional.empty();
		}
		String output = itemId(result);
		List<Ingredient> ingredients = new ArrayList<>();
		for (SlotDisplay slot : slots) {
			if (slot instanceof SlotDisplay.Empty) {
				continue;
			}
			Set<String> items = new LinkedHashSet<>();
			for (ItemStack stack : slot.resolveForStacks(context)) {
				if (!stack.isEmpty()) {
					items.add(itemId(stack));
				}
			}
			if (items.isEmpty() || items.contains(output)) {
				// Unresolvable slot, or a recipe that only modifies its own result (trims, dyeing, copying).
				return Optional.empty();
			}
			ingredients.add(Ingredient.ofOrdered(new ArrayList<>(items)));
		}
		if (ingredients.isEmpty()) {
			return Optional.empty();
		}
		RecipeOption.Station station = station(display.craftingStation().resolveForFirstStack(context), fallback);
		return Optional.of(new RecipeOption(station, output, result.getCount(), RecipeOption.combine(ingredients), source));
	}

	private static final Map<net.minecraft.world.item.Item, RecipeOption.Station> STATIONS = Map.of(
			Items.CRAFTING_TABLE, RecipeOption.Station.CRAFTING,
			Items.FURNACE, RecipeOption.Station.FURNACE,
			Items.BLAST_FURNACE, RecipeOption.Station.BLAST_FURNACE,
			Items.SMOKER, RecipeOption.Station.SMOKER,
			Items.CAMPFIRE, RecipeOption.Station.CAMPFIRE,
			Items.SOUL_CAMPFIRE, RecipeOption.Station.CAMPFIRE,
			Items.STONECUTTER, RecipeOption.Station.STONECUTTER,
			Items.SMITHING_TABLE, RecipeOption.Station.SMITHING);

	private static RecipeOption.Station station(ItemStack stationStack, RecipeOption.Station fallback) {
		return stationStack.isEmpty() ? fallback : STATIONS.getOrDefault(stationStack.getItem(), fallback);
	}

	static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}
}
