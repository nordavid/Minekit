# ADR 0006: Hotbar layout for sorting, and input fixes from the first manual test

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 4.1 (fixes after the project owner's first manual test of 0.5.0)

## Context

The first manual test in the Modrinth instance found three problems:

1. **The Smart Deposit panel did not react to the mouse.** Only Tab and Enter worked. Vanilla gives a click to the
   *first* child widget under the mouse (`ContainerEventHandler.getChildAt`). The panel background is added first, so
   it is drawn below the buttons, and it covers them, so it took every click. The gametests had not noticed: Fabric's
   `clickScreenButton` presses a button directly and skips the screen's own click dispatch. The panel also did not
   explain what Smart Deposit is for.
2. **Sorting never touched the hotbar**, and the option to include it was easy to miss. When the hotbar was included,
   it was filled *after* the main inventory, so it got the least important items. Players expect their tools,
   weapons and food there, each in a slot of their choice.
3. **The list scrollbar could not be dragged.** It was 2 pixels wide and only reacted to the mouse wheel.

## Decisions

1. **One rule per hotbar slot** (`HotbarLayout`, nine `HotbarRule`s) replaces the "protect hotbar" switch.
   - **Rules:** *leave as is*, *any item*, *melee weapon*, *bow or crossbow*, *pickaxe*, *axe*, *shovel*, *hoe*,
     *food*, *blocks*, *utility*, *this item* (one item id, any components) and *keep empty*.
   - **Default:** every slot *leave as is*. The plan asks for a protected hotbar by default, and the hotbar is muscle
     memory. A *suggested layout* (melee, pickaxe, axe, shovel, bow, blocks, blocks, utility, food) is one click away
     on the Inventory page.
   - **Planning order:** slots to leave are not part of the sort. Slots with a rule that picks a match take the best
     matching stack, left to right. Best means a favorite first, then quality (maximum durability for tools and
     weapons, which follows the material tier; nutrition for food), then the larger stack, then the item already in
     the slot, then sort order. *Any item* slots and then the main inventory take the rest in sort order. A slot whose
     rule matches nothing, and a slot to keep empty, stay empty unless nothing else has room.
   - **Guarantees, tested on 3,000 random inventories with random layouts:** nothing is lost or duplicated, slots to
     leave are untouched, and planning a sorted inventory again changes nothing. The "item already in the slot" tie
     breaker is what keeps a sorted hotbar still.
   - **Matching uses tags**, so modded tools that join the vanilla tool tags work: swords, spears, maces and tridents
     are melee weapons, axes are not (they have their own rule), and anything that shoots projectiles is a bow or
     crossbow. Food, blocks and utility use the item category.
2. **The rules are chosen in the sort preview**, where their effect is visible. A row of small boxes under the
   result's hotbar shows each slot's rule as a dimmed icon. A click opens a picker with all rules, a description of
   the selected one, and *this item* for whatever is in that slot now. The picker takes all input while open
   (arrow keys, Enter, Escape). The Inventory page lists the layout and offers *suggested layout* and *leave the
   whole hotbar as is*.
3. **Settings schema 2.** `inventory.json` stores `"hotbar": [9 rule ids]`, for example `"pickaxe"` or
   `"item:minecraft:torch"`. Schema 1 files are migrated: `protectHotbar: true` (or missing) becomes *leave as is* in
   every slot, `false` becomes *any item* in every slot. Unknown rules fall back to *leave as is* with a warning.
4. **Widgets inside another mod's or vanilla's screen must not hide each other.** The Smart Deposit panel background
   still comes first (drawn below), but it claims a point only where none of its controls is. The panel got a
   **?** button that switches to a short explanation, and the profile's description now stands under the profile
   button. Swallowing the mouse release over the panel also ends the screen's drag state, which the skipped vanilla
   release would have done.
5. **Gametests click Minekit controls with the real mouse** (`GameTestMouse`: cursor position plus mouse button),
   not with `clickScreenButton`, wherever Minekit adds widgets to a screen it does not own, and for every new control.
   The deposit test now clicks *?*, cycles all profiles and starts the deposit this way.
6. **Draggable scrollbars in every list** (`ContentPane`, used by the dashboard, the Crafting Assistant and the
   Storage Finder). The bar is 4 pixels wide with an 8 pixel grab area; it lights up on hover and while dragged. A
   press on the thumb keeps the grab point, a press elsewhere on the track jumps there, and the thumb follows the
   mouse until it is released.

## Consequences

- ADR 0003, decision 7 ("the hotbar is filled after the main inventory when not protected") is replaced by
  decision 1 above.
- Smart Deposit keeps protecting the whole hotbar, independent of the hotbar layout: depositing and sorting are
  different intents.
- "Best" is a heuristic. Durability ranks netherite over diamond over iron, but it does not see enchantments; a
  favorite or a *this item* rule overrides it.
- The sort preview needs about 20 more pixels of height. In the stacked layout for very narrow windows, it can be
  taller than the screen, as before.
