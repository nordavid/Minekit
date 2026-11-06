# ADR 0007: Loadouts and Quick Action configuration

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 5 (loadouts and Quick Actions)

## Context

PROJECT_PLAN.md §6.6 (D11 Quick Actions) and §6.7 (D12 Loadout Manager) ask for pinned and ordered actions with
keyboard navigation, cross-module actions, and loadouts that can be saved, compared and applied, also partly, with
ordinary inventory interactions. The exit criterion is that common workflows work without repeatedly navigating the
dashboard.

## Decisions

1. **Quick Actions are arranged by the player** (`quick-actions.json`, schema 1).
   - **Pinned** actions come first, in the player's order (at most 20). The first nine are numbered; with an empty
     search, the keys 1 to 9 run them. The action runs on the next tick, so the character event that follows the key
     press is swallowed by the popup instead of landing in the search field of the screen the action opens.
   - **Recently used** actions (the last five) follow, then all others in registry order.
   - **Hidden** actions are left out of the list; the search still finds them. Hiding unpins, pinning shows again.
   - Pinning works with the star at the end of each row (the row is one widget; a click on the star area pins, a click
     elsewhere runs) or `Ctrl`+`P`. The dashboard page orders pinned actions and hides others.
2. **Dynamic actions.** The registry accepts providers that are asked each time the list is shown. The Loadout
   Manager provides one action per loadout ("Loadout: Mining"), which opens the manager with that loadout and its
   preview: actions that move items still confirm through a preview.
3. **Cross-module actions for the held item**, each registered by the module that owns the feature: find it in the
   storage index, plan crafting it, raise its resource goal by a stack (starting a session if needed). Without an
   item in the main hand they are listed as unavailable with the reason.
4. **Loadout model** (`loadouts.json`, schema 1, at most 50, per player and not per world).
   - Positions are vanilla `Inventory` indices (0-8 hotbar, 9-35 main, 36-39 armor, 40 offhand), so a loadout does
     not depend on the screen. A layout maps them to the slots of the open menu.
   - Each wanted item stores its id, its components without damage and repair cost, and a count. A loadout matches by
     item id, or, with **Match exactly**, also by those components: wear never breaks a match.
   - Positions are wanted, free or **locked**. Locked positions are never clicked, not even to make room.
5. **Planning applies whole stacks and simulates every click.** For each wanted position, in a fixed order (armor,
   offhand, hotbar, main inventory), the planner keeps what already matches there and adds whole stacks of the same
   item: first from free inventory positions, then from positions that want something else, last from the open
   container. The rest of the last stack goes back where it came from. What a position held before goes to a free
   inventory slot or swaps places with the first stack taken; if neither works, the position stays
   (**no room**), with a second chance after the other positions. The complete click sequence is simulated with the
   pickup simulator, and execution replays exactly this script (`ScriptClickPlanner`), checking before every click that
   the menu is in the simulated state. So the preview is the execution, and the general layout click planner is not
   used: with only left clicks it cannot reach every split of one item across several positions, which the random
   test found within the first ten cases.
6. **Applying in the inventory** opens the vanilla inventory screen and clicks there, like sorting (not in creative or
   spectator mode, while riding or with a container open). **Taking from a container** happens in a confirmed
   container's screen through a side panel (⚒ button, under the ⇩ of Smart Deposit): the container only gives; the
   only thing that ever goes into a container slot is the rest of the stack just taken from it. Armor and offhand are
   not part of a container screen and are reported as "not in this screen".
7. **Side panels share their space** (`ContainerPanels`): at most one of Smart Deposit and the loadout panel is open,
   opening one closes the other, and the opener buttons hide while a panel is open. Both panels follow the input rules
   of ADR 0006.
8. **Modifier keys are also read from the key state.** Synthetic input (the Fabric gametest API, and some accessibility
   tools) sends key and mouse events without modifier flags; `Ctrl`+`P` and shift-click check the held key as well.
9. **The dashboard sidebar adapts its spacing** to the window height, because nine pages no longer fit at the full
   row height on small windows.

## Consequences

- Taking whole stacks means a position can get more than saved (40 torches where 32 were saved) and the container
  keeps less. Exact splitting would need right-click and drag clicks, which the executor does not model.
- Stackable items in armor positions (a carved pumpkin on the head) are not supported: armor slots hold one item, and
  the simulator would mispredict. They are reported as missing.
- Armor cannot be equipped from a chest screen; apply the loadout in the inventory afterwards.
- An item with the Curse of Binding cannot leave its armor slot; the executor stops at that click.
- Loadouts do not try to be clever about which of several equal items is better; "Match exactly" is the way to insist
  on a particular one.
