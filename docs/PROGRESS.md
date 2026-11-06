# Minekit progress

Phase definitions: [PROJECT_PLAN.md §11](../PROJECT_PLAN.md).

## Phase 0: Technical validation (done, 2026-11-06)

Exit criterion met: Minekit launches on Minecraft 26.3 through the Modrinth App and is confirmed client-only.

- [x] Verify the current Fabric toolchain for Minecraft 26.3 ([ADR 0001](adr/0001-fabric-toolchain-and-client-only-setup.md))
- [x] Generate the Fabric project (based on the official example mod, branch `26.3`)
- [x] Configure Gradle 9.8.2 (checksum-verified wrapper) and Java 25
- [x] Client-only metadata (`"environment": "client"`) and a single client entrypoint
- [x] Minimal initialization log message
- [x] Unit test that guards the client-only metadata
- [x] Build the JAR
- [x] Launch in the development client (`runClient`)
- [x] Join an unmodified vanilla 26.3 server with Minekit on the client only
- [x] Launch through a Modrinth App instance
- [x] README with setup, build and Modrinth installation instructions

### Verification log (2026-11-06, Windows 11, JDK 25.0.1)

| Check | Result |
|---|---|
| `gradlew build` | Successful. No compiler or Gradle deprecation warnings from the project. |
| Unit tests | `ClientOnlyMetadataTest`: 5 of 5 passed. |
| Regression check of the test | With `"environment": "*"` the build fails as intended. Reverted. |
| JAR contents | `minekit-0.1.0.jar` (3.5 KB): `MinekitClient.class`, `fabric.mod.json` with expanded versions, `LICENSE_minekit`. |
| Reproducibility | `gradlew clean build` produces a byte-identical JAR (SHA-256 `509ce7e3…71ebbba`, same as the copy tested in Modrinth). |
| `runClient` | Fabric Loader lists `minekit 0.1.0`, `minecraft 26.3`, `fabricloader 0.20.1`, `fabric-api 0.165.2+26.3`, `java 25`. Log line: `Minekit 0.1.0 initialized (client-only) on Minecraft 26.3 with Fabric Loader 0.20.1`. |
| Vanilla server join | Official `server.jar` 26.3 (SHA-1 verified), no mods, offline mode on 127.0.0.1. The dev client joined (`Player687 joined the game`), stayed connected with no errors, and disconnected cleanly. The server needs nothing from Minekit. |
| `installToModrinth` | Without `minekit.modsDir`, and with a folder that does not exist, it fails with a clear message. With a valid folder it copies the JAR, removes an older `minekit-*.jar` and leaves Fabric API untouched. |
| Modrinth App launch | New instance "Minekit Dev": Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3 from Modrinth, plus the Minekit JAR. `logs/latest.log` lists `minekit 0.1.0` and the init line. The player entered a single-player world and left it cleanly. No crash reports. Only warning: the vanilla `end_of_frame` message. |

Log noise that is **not** caused by Minekit and also appears with the vanilla server alone:

- `Unable to locate English counter names in registry Perflib 009`: Windows performance-counter registry
  on this machine, read by Minecraft's system-info collection (OSHI).
- `Could not authorize you against Realms server`: expected with the offline development account.
- `Requested post effect does not exist: minecraft:end_of_frame`: vanilla 26.3 warning.

### Known risks and limitations

- **Moving targets:** Fabric API for 26.3 still gets frequent updates (0.165.x). Minecraft 26.4 is in snapshots.
  The mod accepts `~26.3` (26.3.x) only. Each Minecraft update needs a deliberate version bump.
- **Loom pin:** pinned to 1.19.3 instead of the recommended `1.19-SNAPSHOT` (ADR 0001). Upgrades are manual.
- **Java 25 is required for building.** Gradle prints a `java.lang.System::load` native-access warning on
  Java 25. It comes from Gradle itself and is harmless.
- **Test servers:** vanilla 26.3 servers enable `white-list=true` by default. A local test server needs
  `white-list=false` (or the dev player on the whitelist) and `online-mode=false` for the dev account.
- **Dev client first start:** a new `run/` folder shows the accessibility onboarding screen, which blocks
  `--quickPlayMultiplayer`. Set `onboardAccessibility:false` in `run/options.txt` or click through once.

## Phase 1: Core shell and UI (done, 2026-11-06, version 0.2.0)

Exit criterion ("open the dashboard, navigate modules, change settings and restart the game without losing
configuration") verified automatically in the real client: the gametests drove the UI, and a restart loaded the
saved settings. A manual run in the Modrinth instance is still open (see below).

Architecture: [ADR 0002](adr/0002-core-shell-architecture.md).

- [x] Module contract (`MinekitModule`, `ModuleMetadata`, `ModuleContext`) and `ModuleManager`, which isolates failing modules
- [x] Configuration in `config/minekit/config.json`: schema version, field validation, atomic writes, backup of
      corrupt or newer files, never overwrites an unreadable file
- [x] Shortcut registration in a "Minekit" category (`K` dashboard, `J` Quick Actions) and conflict detection
- [x] Main dashboard: sidebar with Overview and Settings, scrollable content, remembers the last page, `K`/`Esc` close
- [x] Quick Actions shell (first real module, can be switched off): search that ignores case and accents,
      keyboard navigation, disabled entries with a reason. Actions: open dashboard, open settings, change shortcuts
- [x] Notification service, drawn as HUD cards and on top of screens
- [x] HUD framework: per-element visibility and corner, kept on screen, broken elements are switched off
- [x] Module on/off settings in the dashboard, saved immediately
- [x] Reusable UI widgets and theme (`FlatButton`, `NavButton`, `ToggleButton`, `ContentPane`, `MinekitTheme`)
- [x] English and German translations
- [x] Unit tests for all logic that does not need Minecraft
- [x] In-game tests with the Fabric client gametest API (separate test mod, not shipped)
- [ ] Manual acceptance in the Modrinth instance (0.2.0 is installed in "Minekit Dev"; not yet started)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| `gradlew clean build` | Successful, no compiler warnings (`-Xlint:deprecation,unchecked` now on). |
| Unit tests | 52 of 52 passed. `ConfigCodecTest` 7, `ConfigManagerTest` 7, `ModuleManagerTest` 9, `QuickActionSearchTest` 7, `NotificationQueueTest` 8, `HudLayoutTest` 4, `KeyConflictsTest` 5, `ClientOnlyMetadataTest` 5. |
| Client gametests (`runClientGameTest`) | Passed: open dashboard with `K`, switch pages, close and reopen on the last page; switch Quick Actions off and back on, and the state is in the file on disk each time; `J` with the module off opens nothing and shows a hint; Quick Actions lists all actions, search narrows to one, `Enter` runs it, an unknown query lists nothing; HUD corner change is saved. 7 screenshots checked by eye. |
| Gametest failure path | A deliberately wrong expectation failed the run (exit 1, "Client gametests failed"). Reverted. |
| Restart persistence | The config file written by the UI in the gametest (Quick Actions off, notifications bottom left) loaded in a freshly started dev client: `(LOADED), modules: [quick_actions=DISABLED], hud: [notifications=BOTTOM_LEFT]`. |
| JAR contents | `minekit-0.2.0.jar` (115 KB): only `io/github/nordavid/minekit/**`, `fabric.mod.json` (still `"environment": "client"`, client entrypoint only), translations, license. No gametest classes, no mixins. |
| Modrinth instance | `installToModrinth` replaced 0.1.0 with 0.2.0 (SHA-256 matches the build). While the game was running it refused with "it is in use. Close the game…" and changed nothing. Game start with 0.2.0 not yet checked. |

### Known risks and limitations

- **Small windows:** with three notifications on screen at the same time, the cards can overlap the right edge
  of the Quick Actions search field. They fade after 4 seconds. Possible later polish: move cards away from open
  Minekit popups.
- **Notification wrapping** is limited to three lines; longer messages end with "…".
- **A module that failed to start** shows a disabled switch with the error as tooltip. It cannot be switched off
  from the UI until the next start. `ModuleManager` supports it, but the switch does not offer it yet.
- **Unavailable Quick Actions:** the "disabled with a reason" display is implemented, but no Phase 1 action is ever
  unavailable, so it is not yet seen in game. Phase 2 (sorting) is the first real case.
- **Gametests open a game window** and are therefore not part of `gradlew build`. Run them before signing off a phase.
- **Config writes happen on the client thread.** That is fine for the small settings file. Bulk data in later
  phases needs its own storage (ADR 0002).

**Update in Phase 2:** "Unavailable Quick Actions" is now seen in game. "Sort inventory" in creative mode shows its
reason on a second line, checked by gametest.

## Phase 2: Inventory foundation (done, 2026-11-06, version 0.3.0)

Exit criterion ("inventory sorting is deterministic, safe and tested in multiplayer") verified: randomized unit
tests cover determinism and safety; the client gametests sort a real inventory in single player and on a local
dedicated server over a real network connection.

Architecture: [ADR 0003](adr/0003-inventory-sorting-and-safe-click-execution.md).

Decisions taken without asking, because they are easy to change later:

- Favorites are item types and apply in all worlds.
- Favorites are set in the sort preview, because doing it inside the vanilla inventory needs a mixin.
- `R` opens the sort preview, both in game and in the inventory.
- Sorting runs only while the vanilla inventory screen is open.

- [x] Item identity utilities: `ItemKey` equals Minecraft's merge rule, with a deterministic component description
- [x] Inventory snapshot model (`ContainerState`, `MenuReader`) and a simulator of vanilla left clicks
- [x] Categories from item tags (vanilla and Fabric conventions), data components and item class
- [x] Smart Inventory module: sort planner (favorites, category order, item id, merging) and step-by-step click planner
- [x] Sorting profiles General, Mining, Building and Adventure; category order editable per profile; hotbar protected by default
- [x] Preview and confirmation (`R` or Quick Action): now and after side by side, favorites by click, profile switch
- [x] Safe action executor foundation for later inventory features (Smart Deposit, Loadouts)
- [x] Dashboard page "Inventory" and Quick Action "Sort inventory" (disabled with a reason when not possible)
- [x] Inventory settings in `config/minekit/inventory.json` using the shared `JsonFileStore`
- [x] Extension points: dashboard pages and Quick Actions can be added by any module
- [x] Unit tests, randomized property test and in-game tests (single player and dedicated server)
- [ ] Manual check in the Modrinth instance (0.3.0 is installed in "Minekit Dev"; not yet started)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| `gradlew clean build` | Successful, no compiler or Gradle deprecation warnings. |
| Unit tests | 75 of 75 passed. New: `PickupSimulatorTest` 6, `SortPlannerTest` 7, `SortClickPlannerTest` 3, `InventorySettingsTest` 7. The Phase 1 config tests still pass after the persistence refactor. |
| Randomized sort test | 3,000 random inventories with random rules. Every run reaches the exact layout with an empty cursor and unchanged item totals, touches no slot outside the region, stays within the click budget, and sorting again needs zero clicks. |
| Gametest: single player | Messy inventory created by commands. `R` opens the preview, *Sort* runs it: 15 clicks, `COMPLETED`. Exact order: pickaxe, bread, diamonds, sticks, cobblestone 64 + 16 (30 + 50 merged), dirt. Hotbar unchanged, item totals unchanged, cursor empty. |
| Gametest: sorted again | The preview reports 0 changed slots and the *Sort* button is inactive. |
| Gametest: creative mode | The Quick Action is unavailable with its reason, `R` opens nothing, `Enter` on the disabled entry does nothing. |
| Gametest: item arrives mid-sort | A server command adds an emerald 8 ticks into the sort. The executor detects the change in slot 35, stops (`STOPPED` after 5 clicks) and puts the cursor item back. Totals equal the start plus the emerald, cursor empty. |
| Gametest: dedicated server | Local Fabric dedicated server (no Minekit on it, the mod is client-only). Same sort over the network: 15 clicks, `COMPLETED`, same exact result. |
| Screenshots | 15 checked by eye. Two layout bugs found and fixed: overlapping text in the preview, and a truncated reason in Quick Actions. |
| JAR | `minekit-0.3.0.jar` (195 KB): 131 files, no gametest classes, no mixins, still `"environment": "client"`. Installed in "Minekit Dev" (SHA-256 matches). |

### Known risks and limitations

- **Corrections later than 0.5 s:** success means "no correction within 10 ticks after the last click". A server that
  sends corrections later would make Minekit report success early. The server's state is authoritative anyway, and
  the next preview shows the real inventory. A server that delays or rejects clicks on purpose could not be tested
  with the Fabric test server. The "item arrives mid-sort" test covers the reaction to server-side changes.
- **Speed:** about 10 clicks per second (one click every 2 ticks) by design. A full inventory takes a few seconds.
- **Favorites cannot be marked inside the vanilla inventory**, only in the preview. Doing it in the inventory needs a
  mixin accessor (ADR 0003).
- **Modded items without convention tags** may land in "Blocks" or "Miscellaneous".
- **`R` is a common key in other mods.** Conflicts show up on the dashboard Overview and in the controls screen.
- **Shift-click (`QUICK_MOVE`) is not used yet.** It needs its own simulator first (ADR 0003).
- **Small windows:** the Settings page needs scrolling at very small window sizes. Vanilla toasts (advancements,
  recipes) can cover the Quick Actions popup; that is vanilla behavior.

## Phase 3: Crafting and resource planning (done, 2026-11-06, version 0.4.0)

Exit criterion ("plan a target craft and track a resource session with transparent accuracy limitations") verified
in the real client by gametests.

Architecture: [ADR 0004](adr/0004-crafting-planner-and-resource-tracker.md). Recipe source decision by the project
owner: **variant A**, server recipe book plus vanilla defaults from the game files, marked as such.

- [x] Recipe lookup from both sources through Minecraft's recipe displays (1,663 recipes in about 110 ms)
- [x] Recursive material calculation:
  - batched demand per item, so leftovers are shared
  - output counts and leftovers
  - cycle detection
  - natural defaults: raw materials are gathered, not unpacked or smelted from ore blocks
- [x] Recipe alternatives: switch recipe per item; switch the item for "any of" ingredients; gather or craft
- [x] Material list: needed, in inventory, missing; updates live while the planner is open
- [x] Crafting steps in a followable order, with station and inputs; vanilla-default steps are marked
- [x] Saved plans (`crafting-plans.json`), dashboard page "Crafting", Quick Action, optional shortcut (unbound)
- [x] Resource Tracker: start, pause, resume, end, rename, delete; history of 30 sessions (`resource-sessions.json`)
- [x] Transparent counting: collected, used or given away, unclear (rules shown on the page)
- [x] Goals: from the item in hand, ±64, or from the Crafting Assistant ("Add as goals"); progress in HUD and page
- [x] HUD element "Resource session" (movable and hideable like all HUD elements)
- [ ] Manual check in the Modrinth instance (0.4.0 installed in "Minekit Dev"; not yet started)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| Unit tests | 105 of 105 passed. New: `CraftingPlannerTest` 15, `SavedPlansTest` 4, `ResourceTrackerTest` 9, `ResourceTrackerCodecTest` 2, `TextSearchTest` (moved). |
| Gametest: recipe catalog | 64 pistons from real vanilla recipes need 48 oak logs, 256 cobblestone, 64 raw iron and 64 redstone. Iron ingots are smelted by default. |
| Gametest: planner screen | Opened by Quick Action. Searching "piston" lists it; selected; quantity 64 typed. With 10 logs and 64 redstone in the inventory: 38 logs missing, redstone covered. *Save plan* writes `crafting-plans.json`. The dashboard lists the plan. |
| Gametest: resource session | Started by Quick Action. With no screen open: 32 cobblestone and 5 raw iron received count as collected, 2 cobblestone removed count as used. 4 oak logs received while the inventory was open count as unclear. *Add as goals* for 2 pistons adds the missing redstone (raw iron in the inventory is not missing). The HUD shows the goal. Ending moves the session to the history, saved to disk. |
| All gametests | Core shell, crafting, resources and inventory sorting (single player and dedicated server) pass together. 20 screenshots checked. Two layout issues fixed: wide buttons cut off material names, now compact icon buttons with tooltips; session buttons slightly narrower. |

### Known risks and limitations

- **Vanilla defaults can differ on servers** with custom or disabled recipes; such steps are marked in the plan.
- **Fuel** for furnace steps is not counted. Remainders (e.g. empty buckets) are not tracked.
- **Containers are not counted** as available materials until the Storage Finder (Phase 4).
- **The tracker cannot tell mining from picking up**, nor placing from throwing. It uses the broad labels "collected"
  and "used or given away". Anything during open screens is "unclear".
- **History rename** uses an inline name field per session (✎). Goals are added from the item in hand or from plans;
  there is no free item picker for goals yet.

## Phase 4: Storage index and Smart Deposit (done, 2026-11-06, version 0.5.0)

Exit criterion ("previously opened containers can be searched, and eligible items can be deposited safely using
ordinary interactions") verified in the real client by gametests, in single player and on a dedicated server.

Architecture: [ADR 0005](adr/0005-storage-index-and-smart-deposit.md).

- [x] Container identification in the core (`ContainerTracker`): only screens opened by right-clicking a supported
  container block count, and only once the block visibly opens (lid, barrel state); menus a server opens on its own
  or instead of the real container are ignored
- [x] Container observation on close, only after the server sent the contents
- [x] Local storage index per world or server (`storage-index/<hash>.json`, at most 2,000 records), double chests
  as one record, ender chest as "everywhere", overlapping records replaced; no file for worlds without records
- [x] Storage Finder screen: item search with totals, containers with coordinates, distance or dimension, count and
  age; type and dimension filters; forget per container; "block is gone" marker; limitation note on screen
- [x] Stale-data indicators: just now / minutes / hours / days, colored by age (10 min, 24 h)
- [x] Dashboard pages "Storage" (summary, open, forget all with confirmation) and "Deposit" (profile, custom
  categories, hotbar)
- [x] Smart Deposit profiles: Matching (default), Everything, Blocks, Resources, Food and farming, Custom
- [x] Safe deposit preview inside the container screen (highlighted slots, what moves, what stays) and execution with
  ordinary clicks on the shared executor; hotbar and favorites protected, favorites can be included for one run
- [x] Panel swallows mouse press and release, so the cursor item is never dropped by a click on it
- [x] Crafting Assistant shows stored amounts of missing materials and intermediate products
- [x] Container/world scoping (hashed ids, no addresses in plain text)
- [ ] Manual check in the Modrinth instance (0.5.0 installed in "Minekit Dev"; not yet started)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| Unit tests | 125 of 125 passed. New: `StorageIndexTest` 10 (incl. context ids, copper chests), `StorageViewTest` 3, `DepositPlannerTest` 7, among them 3,000 random deposits (preview equals execution, nothing lost, only eligible items move, protected slots untouched, remainders stay in their slot, nothing left behind while there is room). |
| Gametest: deposit, single player | Chest opened with a real right-click (`lookAt` + right mouse button). `G` opens the preview: 40 cobblestone and 10 iron ingots move, 16 cobblestone in the hotbar and 2 favorite diamonds stay. *Deposit*: 5 clicks, completed, totals unchanged. The server's chest holds 100 cobblestone, 13 iron ingots and 1 diamond; bread, hotbar and favorites stay with the player. Closing the chest indexes exactly these contents. |
| Gametest: cursor protection | Bread on the cursor: the preview refuses ("put down the item on your cursor first"). A real left click into the panel keeps the bread on the cursor; no item entity appears in the world. |
| Gametest: container kinds | Double chest opened from both halves: one record with 2 positions and 54 slots. Shulker box with profile *Everything*: the logs go in, the shulker box item stays (2 clicks). Ender chest: one record without position, 7 emeralds. |
| Gametest: server menu | A chest menu opened by the server: no ⇩ button, `G` does nothing, nothing indexed. The plugin case: right-click on a chest that cannot open (block on top), then the server shows a chest menu. The lid never opens, so the menu is not confirmed: no Smart Deposit, nothing indexed. |
| Gametest: finder, crafting, pages | Opened by Quick Action: 4 containers, 6 item types. Clicking *Cobblestone* shows 100 in 1 container with coordinates, 2 m distance and "just now". The storage bridge reports 13 iron ingots; the crafting plan for an iron pickaxe shows the stored logs. Dashboard pages *Storage* and *Deposit* render. After leaving the world, exactly one `sp-…json` exists and contains the chest. |
| Gametest: dedicated server | Same deposit on a local dedicated server (real network): 5 clicks, server-side chest contents as expected, one `mp-…json` index file. |
| All gametests | All five gametest classes pass together (core shell, crafting, resources, inventory sorting, storage and deposit). Screenshots checked; three layout issues fixed on the narrow test window: deposit panel controls moved to the top (a notification card covered the buttons), summary and notes wrap, finder rows show coordinates, distance and fill level instead of a cut-off name. A worlds-without-containers rule was added after the first run: no empty index files. |

### Known risks and limitations

- **Only what the player saw.** Containers opened by other means, changed by hoppers or other players, or never
  opened are unknown or outdated until opened again. Every record shows its age.
- **Item identity in the index is the item id.** Components (enchantments, names) are not stored, and shulker boxes
  or bundles inside a container are counted as one item, not by their contents.
- **Modded containers** count only if their block extends a vanilla container class and opens the matching vanilla
  menu.
- **LAN worlds joined from another computer** get a new index per LAN session, because the address changes.
- **The deposit panel** sits left of the container. On very small windows it covers part of the container while it
  is open.
- **No Quick Action for Smart Deposit**, because opening the palette closes the container. Use `G` or the ⇩ button.

## Phase 4.1: Fixes after the first manual test (done, 2026-11-06, version 0.5.1)

The project owner tested 0.5.0 in the Modrinth instance and reported three problems. Architecture:
[ADR 0006](adr/0006-hotbar-layout-and-input-fixes.md).

- [x] Smart Deposit panel: buttons did not react to the mouse (only Tab and Enter). The panel background took every
  click because vanilla gives a click to the first widget under the mouse. It now leaves the points of its controls
  to them.
- [x] Smart Deposit explains itself: a **?** button shows a short explanation in the panel, and the profile's
  description stands under the profile button. The opener tooltip says what the button does.
- [x] Sorting and the hotbar: one rule per hotbar slot instead of an easy-to-miss "protect hotbar" switch. Rules:
  leave as is (default), any item, melee weapon, bow or crossbow, pickaxe, axe, shovel, hoe, food, blocks, utility,
  this item, keep empty. Chosen with boxes under the hotbar in the sort preview; the Inventory page lists the layout
  and has a suggested layout. Settings schema 2 with migration from schema 1.
- [x] Scrollbars in all lists (dashboard, Crafting Assistant, Storage Finder) can be dragged and clicked.
- [x] Gametests click Minekit controls with the real mouse where it matters (`GameTestMouse`), so a covered control
  can no longer pass a test.
- [ ] Manual check in the Modrinth instance (0.5.1)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| Unit tests | 131 of 131 passed. New: hotbar rules pick the best match (diamond before stone pickaxe), favorites and item rules win, slots without a match and slots to keep empty fill only on overflow, a sorted hotbar stays as it is; settings schema 2 round trip, schema 1 migration, invalid rules. The 3,000 random sort runs now use random hotbar layouts and also check that planning a sorted inventory again changes nothing. |
| Gametest: deposit with the mouse | Single player and dedicated server: a real click on **?** shows the explanation and a second one returns to the preview; six clicks on the profile button cycle through all profiles back to *Matching* with the same preview; a click on *Deposit* runs the deposit (5 clicks, server contents as expected). A click on an empty part of the panel with bread on the cursor still drops nothing. |
| Gametest: hotbar layout | In the sort preview, a mouse click on the box under hotbar slot 1 opens the picker; *Pickaxe* is chosen with the mouse, then *Food* for slot 9. Escape closes only the picker. A mouse click on *Sort*: the iron pickaxe lands in slot 1, the bread in slot 9, the sword in slot 5 (left as is) stays, the torch from slot 1 moves to the main inventory, totals unchanged. The Inventory page lists the layout. |
| Gametest: scrollbar | Crafting Assistant with the full item list: dragging the thumb 60 pixels down scrolls; releasing ends the drag; a click at the end of the track scrolls to the end; dragging far up returns to the top. |
| All gametests | All five gametest classes pass. Screenshots checked; the help text, the hotbar hint and the Inventory page were made shorter for narrow windows. |

### Known risks and limitations

- **"Best" tool or weapon** is decided by maximum durability (material tier) and does not see enchantments. Mark the
  enchanted one as a favorite, or use a *this item* rule.
- **Smart Deposit still protects the whole hotbar**, independent of the hotbar layout ("Also empty the hotbar" on the
  Deposit page changes that).

## Phase 5: Loadouts and Quick Actions (done, 2026-11-06, version 0.6.0)

Exit criterion ("common workflows can be performed without repeatedly navigating the dashboard") verified in the real
client by gametests, in single player and on a dedicated server.

Architecture: [ADR 0007](adr/0007-loadouts-and-quick-action-configuration.md).

- [x] Quick Action configuration: pinned actions in the player's order, recently used actions, hidden actions;
  dashboard page *Quick Actions*; `quick-actions.json`
- [x] Pinned actions and keyboard navigation: star or `Ctrl`+`P` to pin, `1`-`9` run the first nine, `↑`/`↓`/`Enter`
- [x] Cross-module actions: find the held item in storage, plan crafting it, raise its resource goal; one action per
  loadout; registry providers for actions that come and go
- [x] Loadout creation and management: save the inventory, rename, copy, delete, update from the inventory, lock and
  free positions, shift-click to take a position from the inventory, exact matching; `loadouts.json`
- [x] Loadout comparison: per position ready, will be filled, partly available, missing, no room; missing items and
  items the loadout does not mention
- [x] Partial loadout application with ordinary clicks in the inventory, with a simulated click script (preview equals
  execution), and from an open container (taking only) through a side panel
- [x] Smart Deposit and the loadout panel share the space next to the container (one open at a time)
- [x] Dashboard sidebar fits nine pages on small windows
- [ ] Manual check in the Modrinth instance (0.6.0)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| Unit tests | All passed. New: `QuickActionSettingsTest` 6, `LoadoutPlannerTest` 7 (among them 3,000 random inventories and loadouts in the inventory and in a chest screen, with exact matching and locked positions: nothing lost or created, only shown and unlocked slots clicked, the container only gives, armor only gets fitting items, the click script reaches the preview exactly, applying twice changes nothing), `LoadoutsTest` 3. The first random run found a real gap in the general layout click planner (several partial stacks of one item); loadouts now use simulated click scripts. |
| Gametest: Quick Actions | `Ctrl`+`P` pins *Sort inventory*; a real mouse click on the star pins *Open Crafting Assistant* without running it; the list starts with the pinned actions; `2` opens the Crafting Assistant with an empty search field; the run is remembered as recent. |
| Gametest: held item | Without an item, *Find held item* is unavailable. With an iron pickaxe: the planner opens for it, the Storage Finder opens with it selected, and the resource goal action starts a session with a goal of 64. |
| Gametest: loadout in the inventory | *Save inventory as loadout* saves five positions (hotbar, main, helmet). After mixing everything up (dirt in the pickaxe's slot, helmet in the main inventory): 4 positions will be filled, 1 is ready. A mouse click on *Apply*: completed, every item in its position, the dirt moved to a free slot, totals unchanged; planning again has nothing to do. A click on a grid position frees it, a shift-click takes it from the inventory again. |
| Gametest: loadout from a chest | Single player and dedicated server: the ⚒ button opens the panel; it takes the pickaxe, the sword, the larger torch stack (40 of 16 + 40, 32 saved) and the bread, not the helmet. A mouse click on *Take*: completed; the server's chest keeps exactly the 16 torches, the helmet and the cobblestone; the items are in their positions; the dirt moved. |
| Gametest: panels and pages | With the loadout panel open, `G` opens Smart Deposit and closes the loadout panel. Dashboard pages *Loadouts* and *Quick Actions* render. |
| All gametests | All six gametest classes pass together. The core test now presses the Quick Actions module switch directly: Fabric's `clickScreenButton` matches visible text, and the new sidebar entry has the same text. Screenshots checked; the sidebar spacing, the loadout panel's button label, notification texts and a few labels were adjusted. |

### Known risks and limitations

- **Whole stacks.** A position may get more than saved, and the container keeps less.
- **Armor from a chest** cannot be equipped in the chest screen; apply the loadout in the inventory afterwards.
- **Stackable items in armor positions** (carved pumpkin) are reported as missing.
- **Quick Actions close an open container**, so container features stay on the buttons next to the container (⇩, ⚒).

## Phase 5.1: Fixes after the second manual test (done, 2026-11-06, version 0.6.1)

The project owner tested 0.6.0. From this version on, the project uses git: `main` holds verified versions (tags
`v0.6.0`, `v0.6.1`), each round of work happens on a branch with one commit per change.

- [x] Name written as **Minekit** instead of MINEKIT (UI, mod metadata, logs, docs)
- [x] Inventory page: "Reset hotbar layout" instead of "Leave the whole hotbar as is", with a tooltip
- [x] Crafting Assistant: every craftable item is listed (it stopped at 200, "854 more"); without a search the list is
  grouped like the creative inventory, and arrows above it show one category; the hint before an item is chosen is
  centered and mentions browsing
- [x] Resource sessions belong to the world they were started in (they were global)
- [x] Mod icon (`assets/minekit/icon.png`, drawn by `tools/make_icon.py`), current description in `fabric.mod.json`
- [x] README: how to share Minekit with a friend (Modrinth modpack export, or the JAR plus Fabric API)
- [ ] Manual check in the Modrinth instance (0.6.1)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| Unit tests | 149 of 149 passed. New: sessions of two worlds are independent (only the world's own session counts, pausing all, ending one keeps the other); schema 1 migration ends the old global session into the history. |
| Gametests | All six classes pass. The crafting test checks that every craftable item is listed (no cut-off) and that the creative categories are offered; the phase 5 test checks on the dedicated server that the singleplayer world's session is neither shown nor running there. |

## Phase 6: Blueprints and building tools (done, 2026-11-06, version 0.7.0)

Exit criterion ("external blueprints can be imported and used as a manual building guide without automatic block
placement") verified in the real client by a gametest. Architecture, format research and library decision:
[ADR 0008](adr/0008-blueprints-and-building-tools.md).

- [x] Format investigation and parser selection: Sponge schematic v1-v3 (official specification), Litematica (format
  facts from its source), Minecraft 26.x block state field names; libraries compared (licenses, scope); decision:
  Minecraft's NBT reader plus own small readers, no third-party code
- [x] `.litematic` importer (several regions, negative region sizes, tightly packed block states)
- [x] `.schem` importer (versions 1 to 3, varint block data, unsigned sizes)
- [x] File validation and limits: file size, NBT memory quota and nesting limit, positions, palette, indexes, data
  length; readable reasons for every failure; configurable limits; legacy `.schematic` refused with a hint
- [x] Import by drag and drop onto the blueprint screen or the blueprint folder; originals never changed
- [x] Background parsing; data fixer for files from older versions; unknown blocks listed
- [x] Blueprint preview: layer view from above (texture, status colors, north up), size, block count, format
- [x] Transformations: move (1 or 10), turn in quarter turns, mirror; per blueprint and world
- [x] World comparison for loaded chunks only: correct, wrong (block or orientation), missing, not loaded, unknown
- [x] Guide in the world: box, build range, missing and wrong blocks around the player, next block with its name
- [x] Build progress, build range (layers), materials (to place, carried, stored, missing as resource goals)
- [x] Measuring: two corners give width, height, length, volume and distance, in the world and as a message
- [x] No block is ever placed by Minekit
- [ ] Manual check in the Modrinth instance (0.7.0)

### Verification log (2026-11-06)

| Check | Result |
|---|---|
| Unit tests | 163 of 163 passed. New: `BlueprintReaderTest` 8 (block specs; Litematica regions with negative sizes merged into one box; 5-bit values spanning longs; Sponge v2 and v3 with 300-entry palettes and multi-byte varints; v3 `BlockPalette` alias and unsigned sizes; 26.x `id`/`properties` palette entries; eleven kinds of broken files; not compressed, too large, unsupported files), `PlacementTest` 3 (500 random boxes: every turn and mirror maps the box onto itself one to one and back), `BlueprintProgressTest` 3 (air not tracked, counts, lowest open layer, ranges, the sweep). |
| Gametest: files | Three files in the blueprint folder; the broken one fails with "this is not a compressed blueprint file" and is not shown. A Litematica file saved by 1.20.1 loads: 3 x 2 x 3, 11 blocks; its "grass" is updated to short grass by the data fixer. (The first run found that Minecraft 26.x renamed the block state fields; both spellings are read now.) |
| Gametest: guide | Placed next to the player: 11 missing, nothing unchecked. After placing stone, dirt in a stone position, short grass, and a stair facing the wrong way with commands: 2 correct, 2 wrong, 7 missing, the next block in the lowest layer. Turning the stair the right way makes it correct. A quarter turn of the placement turns the expected stair from north to east. Screenshot: box, teal missing blocks, red wrong block, amber next block named "Stone". |
| Gametest: screen | Overview, Layers (status colors and legend) and Materials (7 stone to place, short grass and stairs done) render. A dropped `.schem` file is copied into the folder, becomes the active blueprint (Sponge v3, 4 blocks), and the original is unchanged. |
| Gametest: measuring | Two marker blocks via Quick Action: "4 x 2 x 3 blocks (24) · 3.7 apart"; screenshot shows the box, the corners, the line and the label. |
| All gametests | All seven gametest classes pass. The settings page check now presses the Quick Actions switch directly (a sidebar entry has the same text). |

### Known risks and limitations

- **Boxes, not textured ghost blocks.** Missing and wrong blocks are shown as translucent boxes within 10 blocks of
  the player; the whole structure is visible in the layer view.
- **Compared:** block and orientation. Not compared: other properties (waterlogged, powered, connections), block
  entities (chest contents, signs), entities, and blocks where the blueprint has air.
- **No file dialog:** files are dropped onto the window or put into the folder.
- **Legacy MCEdit `.schematic`** files are not supported.
