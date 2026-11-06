# ADR 0003: Inventory sorting and safe click execution

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 2 (inventory foundation)

## Context

Phase 2 is the first feature that changes the player's items. PROJECT_PLAN.md §6.1, §6.8 and §9 require
deterministic results, a preview, protected slots, no item loss or duplication, ordinary client interactions only,
and stopping instead of continuing blindly when the server disagrees.

Facts about Minecraft 26.3 that shaped the design:

- A click is `MultiPlayerGameMode.handleContainerInput(containerId, slot, button, ContainerInput, player)`. The
  client applies it locally at once and sends the slots it predicts changed. The server executes the click again,
  even with an outdated state id, and sends corrections only if its result differs. A successful click is therefore
  silent: there is no positive confirmation.
- Fabric API offers no way to read a container screen's position or hovered slot (`AbstractContainerScreen.leftPos`,
  `topPos` and `hoveredSlot` are protected). Getting them would need a mixin accessor.
- Item tags (vanilla and Fabric convention tags such as `c:ingots`, `c:foods`, `c:tools`) are synchronized to the
  client and cover modded items that follow the conventions.

## Decisions

1. **Pure core, thin adapters.** The planning logic is plain Java without Minecraft, and is therefore fully unit
   tested: `ItemKey`, `SlotStack`, `ContainerState`, `PickupSimulator`, `SortPlanner`, `SortClickPlanner`. Adapters
   (`ItemKeyIndex`, `ItemCategorizer`, `MenuReader`) convert Minecraft state.
2. **Item identity.** Two stacks get the same `ItemKey` exactly when `ItemStack.isSameItemSameComponents` says so,
   which is the condition under which Minecraft merges them. The key's variant is a sorted, deterministic description
   of the component changes, so sort orders are the same across sessions. Display names are never used.
3. **Layout first, then clicks.** `SortPlanner` computes the target layout: favorites first, then category in profile
   order, then item id, then variant, then larger stacks first. Partial stacks are merged. It is deterministic and
   idempotent. `SortClickPlanner` then chooses one plain left click (`PICKUP`, button 0) at a time, always from the
   actual state. A randomized test drives it through 3,000 inventories with a simulator of vanilla click rules. It
   checks:
   - the exact target layout is reached
   - nothing is lost or duplicated
   - no slot outside the sort region is touched
   - the cursor ends empty
   - the click budget holds (`4 × slots + 8`)
   - a second sort needs zero clicks
4. **Predict and verify (`ContainerActionExecutor`).** The executor is shared by all future inventory features (Smart
   Deposit, Loadouts). The rules:
   - At most one click every 2 ticks.
   - Before each click it checks the job's preconditions and compares the real menu with the state predicted after
     the previous click. Any difference stops the job. That covers a server correction, a picked-up item and any
     other change.
   - After the last click it waits 10 ticks for late corrections before reporting success.
   - On a stop, an item left on the cursor is put into a free slot of the sort region.
   - It compares the item totals of the whole menu plus the cursor before and after, and reports the result.
5. **Sorting happens in the visible vanilla inventory screen.** After the player confirms the preview, Minekit opens
   the inventory screen exactly as the inventory key does and clicks there, so the player sees every move. It never
   clicks without an open screen. Closing the screen stops the sort.
6. **Where sorting is not offered:** creative and spectator mode (creative inventory actions are privileged), while
   riding (the server controls the inventory screen), while another container is open, and while another job runs.
   The Quick Action shows the reason and stays disabled.
7. **Sort region.** Main inventory, filled row by row. The hotbar is protected by default; when it is not, it is filled
   after the main inventory. Armor, offhand and the crafting grid are never part of a sort. *(The hotbar part is
   replaced by per-slot hotbar rules, see [ADR 0006](0006-hotbar-layout-and-input-fixes.md).)*
8. **Categories** come from tags, data components and the item class, in a fixed rule order: armor, weapons, tools,
   potions as utility, food, resources, other utility, blocks, misc. Ores and storage blocks count as resources.
9. **Favorites are item types** (e.g. `minecraft:diamond_pickaxe`) and apply in all worlds. They are set by clicking
   an item in the sort preview. That screen is Minekit's own, so it needs no mixin. Marking favorites directly in the
   vanilla inventory would need a mixin accessor (see Context). It is postponed until it is clearly worth one.
10. **Shortcut `R`** opens the preview in game and in the inventory screen. In the inventory screen Minekit first closes
    the inventory the vanilla way, which returns crafting-grid and cursor items. While a sort runs, `R` cancels it.
11. **Settings** are stored in `config/minekit/inventory.json`: active profile, changed category orders, hotbar
    protection and favorites. The file is written by the generic `JsonFileStore` extracted from the Phase 1 config
    manager, with the same guarantees (schema version, backups, atomic writes).
12. **Extension points moved to core:** the `QuickActionRegistry`, so every module can add actions, and
    `DashboardPages`, so modules add their own dashboard page without the core knowing them.
13. **Multiplayer test:** the client gametests start a local dedicated server (Fabric test API, `eula = true`
    approved by the project owner) and sort over a real network connection.

## Consequences

- Smart Deposit and Loadouts reuse the executor and simulator. They only need a new `ClickPlanner` and a `ContainerJob`
  with their own preconditions. Shift-click (`QUICK_MOVE`) is deliberately not used yet, because its result depends
  on the menu's move rules. It needs its own simulator before any feature relies on it.
- A server that delays corrections longer than the settle window could make a run report success while a correction is
  still in flight. The final state is server-authoritative anyway, and the next preview shows the real state.
- Items that ignore conventions (some mods) end up in "misc" or "blocks". Players can still order categories and use
  favorites.
