# ADR 0004: Crafting planner and resource tracker

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 3 (crafting and resource planning)

## Context

PROJECT_PLAN.md §6.2 asks for recipe calculations "from Minecraft's recipe definitions, not hard-coded examples",
and §6.4 for resource tracking that never invents a cause. In 26.3 the client does not receive the full recipe list
from the server. It only receives recipe-book entries for the recipes the player has unlocked. The vanilla recipe
files are part of the client JAR, because the client contains the integrated server.

## Decisions

1. **Recipe sources (owner decision "variant A"):**
   - **Server recipes:** recipe-book entries sent by the server (`ClientRecipeBook`), marked as such.
   - **Vanilla defaults:** the client's built-in recipe files, loaded with `ServerPacksSource.createVanillaPackSource()`
     and decoded with `Recipe.DIRECT_CODEC` against the client's registries, including the tags the server synced.
     They are marked "vanilla default; the server may differ".

   Both sources go through Minecraft's `RecipeDisplay`, so items and tags resolve exactly as in the recipe book.
   Recipes whose result is also an input (trims, dyeing, copying) are skipped, because they never help to obtain an
   item. A test world loads 1,663 recipes for 1,054 items in about 110 ms, so the index is built synchronously when
   the planner opens. It is rebuilt when the world or the number of unlocked recipes changes.
2. **Pure planner.** `CraftingPlanner` resolves one recipe per item and one item per "any of" ingredient, then
   propagates demand in topological order, so every item is crafted once for the combined need of all its consumers
   and batch leftovers are shared. It detects cycles and treats the looping item as gathered, with a warning. It runs
   once without and once with the inventory, which gives "total needed" and "still missing". Inventory copies of the
   target are never subtracted.
3. **Natural defaults.** A recipe is *penalized* if it unpacks (an input can be made from the output: ingot from
   block, cobblestone from stone) or if an input slot accepts only ore blocks or damageable gear. When the best recipe
   of an intermediate item is penalized, the item is gathered by default, unless the inventory already holds the inputs
   of one of its recipes. Players can override every choice: switch the recipe, switch the alternative, or toggle
   gather and craft. Ties between alternatives go to the item listed first by the game's tag (oak planks, cobblestone).
4. **Saved plans** store the request (target, quantity, choices). Recipe choices are stored by a content-based
   *signature* (station, result, inputs), not by a session-specific id, so they survive restarts and match server and
   vanilla copies alike. The limit is one plan per target and 50 in total.
5. **Resource tracker counting** uses only item *totals* (main inventory, hotbar, offhand, cursor):
   - Moving items between slots can never count.
   - With no screen open, gains count as *collected* and losses as *used or given away*.
   - If a screen was open at either observation, or the player was in creative or spectator mode or dead, changes
     count as *unclear*.
   - The page explains these rules.
6. **Observation and saving.** Observations run every 10 ticks and stay in memory. They are saved every 30 seconds
   and on every start, pause, resume and stop. Leaving the world pauses the session. A session that was running when
   the game closed is restored paused.
7. **Goals** belong to the open session: an item and a target amount. Progress is the collected amount. Goals come
   from the item in hand or from the Crafting Assistant's "Add as goals". That button uses a small core `GoalBridge`,
   so the two features do not depend on each other.
8. **HUD:** the session panel is the second Minekit HUD element (top left by default). It can be moved and hidden
   like every Minekit HUD element.

## Consequences

- On servers with custom recipes, vanilla defaults can be wrong; the plan says which steps use them.
- Fuel for furnaces is not part of the plan (noted in the plan).
- Containers remain invisible to the planner until Phase 4 (Storage Finder). Until then only the inventory counts as
  available.
- The tracker cannot tell mining from picking up a dropped item, nor placing a block from throwing it. Both count
  under the deliberately broad labels "collected" and "used or given away".
