# ADR 0005: Storage index and Smart Deposit

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 4 (storage index and Smart Deposit)

## Context

PROJECT_PLAN.md §6.3 (A3 Storage Finder) and §6.8 (D14 Smart Deposit) build on the same question: which container
is the player looking at? Minecraft does not tell the client. A container screen only carries a menu type, a title
and the slot contents, so a chest screen opened by a server plugin (a shop, a virtual backpack) looks exactly like a
real chest. A client-only mod also learns nothing about containers the player never opens.

## Decisions

1. **One shared `ContainerTracker` in the core.** A container screen is trusted only if three things agree:
   - it opens within 60 ticks after the player right-clicked a supported container block (`UseBlockCallback`, client
     side; a sneaking click with an item in hand does not count, because it does not open the block);
   - its menu has the type that block opens (`ChestMenu` with 3 or 6 rows, `ShulkerBoxMenu`, `HopperMenu`,
     `DispenserMenu`) and the expected size;
   - within 40 ticks the block **visibly opens**: the server animates the lid of a chest or ender chest
     (`LidBlockEntity.getOpenNess`), the shulker box starts opening, a barrel gets `open=true`. A plugin that cancels
     the real opening and shows its own chest menu instead (a crate preview, a sell menu) passes the first two checks
     but fails this one. Hoppers, dispensers and droppers have no such signal and rely on the first two checks.

   Supported: chests (including trapped and copper chests), barrels, ender chests, shulker boxes, hoppers, dispensers
   and droppers. Anything else, in particular every menu a server opens on its own, is ignored: it is neither indexed
   nor offered Smart Deposit, so a click can never accidentally "buy" or "sell" in a plugin menu. The tracker
   re-registers its close listener after every screen re-initialization, because Fabric resets per-screen events on
   resize; an end-of-tick check catches every other way a menu goes away.
2. **Observation only on close, only if synced.** Contents are recorded when an identified container closes, and
   only if the server had sent them: a new menu has state id 0, and the server's first full update always carries a
   higher one. Items are counted by item id. Item components are not stored, and the contents of shulker boxes or
   bundles inside a container are not counted.
3. **Identity and scope.**
   - **Record key:** dimension, normalized block id and sorted block positions. A double chest is one record with
     both positions, whichever half was clicked. Copper chests count as `minecraft:copper_chest` in every oxidation
     or wax stage. The ender chest has neither dimension nor position: its contents are the same everywhere.
   - **Overlapping records:** a new observation drops older records that share a position, for example a single chest
     that became a double chest, or a chest replaced by a barrel.
   - **Files:** each world and server has its own file `storage-index/<id>.json`, with at most 2,000 records; the
     oldest observation is dropped first. The id is `sp-` plus a SHA-256 prefix of the save folder name, or `mp-` plus
     a SHA-256 prefix of the server address. No address is stored in plain text. The display name of the world or
     server list entry is stored for the UI.
4. **Honest freshness.** Every record keeps its observation time. The UI says "just now / n min / n h / n days ago"
   and colors records by age: under 10 minutes, under 24 hours, older and "potentially stale". When the client can
   see that the block at a recorded position is no longer that container (the chunk is loaded), the record is marked
   "block is gone". It is not deleted without the player's action. The finder states on screen that only opened
   containers are known, as they were when closed.
5. **Smart Deposit plans fully before it clicks.** `DepositPlanner` (pure) picks eligible stacks:
   - **Profiles:** *Matching* (the default: item ids the container already holds), *Everything*, *Blocks*,
     *Resources*, *Food and farming*, and *Custom* categories.
   - **Protection:** the hotbar and favorites (shared with Smart Inventory) stay with the player. Favorites can be
     included for one run in the preview, never by default.
   - **Container rules:** items the container's slots reject (shulker boxes in a shulker box) are not eligible.

   The planner then simulates the whole deposit with `DepositClickPlanner` and `PickupSimulator`, so the preview shows
   exactly what execution will do. The click planner only ever:
   - picks up an eligible source stack that still has room in the container;
   - tops up a partial stack of the same item, or fills an empty container slot;
   - puts a remainder back into the slot it came from.

   It never clicks a container slot holding a different item, so nothing is swapped out of the container and bundles
   are never filled by accident. 3,000 random inventories check: item conservation, preview equals execution, only
   eligible items move, protected and ineligible slots stay untouched, container stacks only grow, remainders stay
   in place, nothing eligible is left behind while there is room, and the click limit holds.
6. **Execution reuses the Phase 2 executor** (ADR 0003): one left click every 2 ticks, a prediction check before
   every click, a 10-tick settle time, and cursor recovery into the player's inventory. The job stops when the screen
   closes, the menu or game mode changes, or anything deviates from the prediction. In spectator mode it does not
   start. The result notification is computed from the menu state after the job.
7. **The preview lives inside the container screen.** Opening another screen would close the container, so Smart
   Deposit adds a small ⇩ button and a panel to the container screen, left of the container, where item list mods
   rarely are. The panel highlights the target slots (green) and the source slots (amber). Vanilla treats a click
   outside the container as "drop the cursor item" when the mouse is released, so the panel swallows both the press
   and the release (`ScreenMouseEvents.allowMouseRelease`). The default key is G inside a container screen; Enter
   confirms. There is no Quick Action for Smart Deposit, because opening the palette would close the container.
8. **Crafting integration through a core bridge.** `StorageBridge` lets the crafting planner show "64 stored" (or
   "(old)" after 24 hours) next to a missing material and on intermediate steps, without depending on the storage
   module. The plan itself still counts only the inventory: stored items are not in reach.

## Consequences

- Containers opened in other ways are not indexed, for example by a redstone contraption, by a plugin command, or
  as a modded block that does not extend the vanilla classes. On servers that suppress the opening animation (rare
  "silent open" features), chests, barrels and shulker boxes are not recognized, so nothing is recorded and Smart
  Deposit is not offered: the safe failure.
- A plugin menu that replaces a hopper, dispenser or dropper right after a right-click would still be accepted,
  because those blocks have no visible open signal. Such plugins are rare; the executor still stops at the first
  click the server handles differently.
- The storage index never claims more than what the player saw. It cannot notice changes made by hoppers, other
  players or the player's own later actions until the container is opened again.
- On a LAN world joined from another computer, the address changes with every LAN session, so the index starts empty
  each time.
- A server that changes container contents during a deposit, for example a hopper feeding the chest, stops the
  deposit at the first deviation. That is intended.
