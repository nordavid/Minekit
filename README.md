# Minekit

Client-side utility suite for Minecraft Java Edition (Fabric). Minekit runs only on your own client.
Servers and other players need nothing installed.

> **Status (0.7.0):** Phases 1 to 6 are implemented and tested in the real game client: dashboard, settings, Quick
> Actions (pinned, numbered, hidden, recent), notifications, inventory sorting with preview and hotbar layout (also on
> a dedicated server), the Crafting Assistant, the Resource Tracker, the Storage Finder, Smart Deposit, the Loadout
> Manager and blueprints with building tools. Manual checks in the Modrinth instance are still open. See
> [docs/PROGRESS.md](docs/PROGRESS.md) and [PROJECT_PLAN.md](PROJECT_PLAN.md).

## Using Minekit

| Key (default) | What it does |
|---|---|
| `K` | Opens the Minekit dashboard. Press `K` or `Esc` again to close it. |
| `J` | Opens Quick Actions: type to search, `↑`/`↓` to select, `Enter` to run, `Esc` to close. `1`–`9` run the pinned actions, `Ctrl`+`P` pins the selected one. |
| `R` | Opens the sort preview, in game or in your inventory. While a sort runs, `R` cancels it. |
| `G` | In an open chest, barrel, shulker box or similar: opens the Smart Deposit preview. While a deposit runs, `G` stops it. |

All keys can be changed under *Options → Controls → Key Binds → Minekit*, or from the dashboard
(*Settings → Change shortcuts…*). `K` and `J` work in game with no other screen open. The Crafting Assistant, the
resource session, the Storage Finder and the Loadout Manager also have shortcuts there; they are unbound by
default.

- **Overview** shows the version, the state of each module, the shortcuts, and whether a shortcut shares its key
  with another mapping.
- **Inventory** selects the sort profile, shows what goes in each hotbar slot (with a suggested layout), changes
  the category order and lists favorites.
- **Crafting** opens the Crafting Assistant and lists saved plans.
- **Resources** controls the resource session, its goals and the history.
- **Storage** shows what the Storage Finder knows about this world, opens it, and forgets everything on request.
- **Deposit** selects the Smart Deposit profile, the categories of the custom profile and whether the hotbar may be
  emptied.
- **Loadouts** lists the saved loadouts and opens the Loadout Manager.
- **Blueprints** shows the active blueprint and its progress, the measuring tool and the import limits.
- **Quick Actions** pins and orders actions for the popup, and hides the ones you never use.
- **Settings** turns modules on and off and controls the notification cards (shown or hidden, which screen corner).
  Every change is saved immediately.

### Sorting your inventory (Smart Inventory)

1. Press `R` (or use Quick Actions → *Sort inventory*). A preview shows your inventory **now** and **after sorting**.
   Nothing has changed yet.
2. Optional: click an item to mark it as a **favorite**; favorites always come first. Switch the **profile** (General,
   Mining, Building, Adventure) with the button at the top.
3. Optional: choose what goes in each **hotbar slot**. The small boxes under the hotbar of the result show it; click
   one to pick a rule: *leave as is* (the default), *any item*, *melee weapon*, *bow or crossbow*, *pickaxe*, *axe*,
   *shovel*, *hoe*, *food*, *blocks*, *utility*, *this item* (whatever is in that slot now) or *keep empty*. A tool or
   weapon slot gets your best one (by material), a food slot your most filling food. The preview updates at once.
   The *Inventory* page of the dashboard has a suggested layout to start from.
4. Press **Sort**. Your inventory opens and Minekit moves the items with normal clicks, about ten per second, so you
   can watch. Press `R` or close the inventory to stop at any time.

Rules: hotbar slots set to *leave as is* are never touched, armor, offhand and the crafting grid neither,
partial stacks of the same item are merged, and nothing is ever dropped or put into a container. If anything
unexpected happens (you pick up an item, the server corrects a click), sorting stops and tells you. Sorting is not
available in creative or spectator mode, while riding or while a chest or other container is open.

### Planning a craft (Crafting Assistant)

Open it from the dashboard (*Crafting*) or Quick Actions → *Open Crafting Assistant*. Search an item on the left,
or browse: without a search, the list shows every craftable item, grouped and ordered like the creative inventory
(Building Blocks, Redstone Blocks, Tools & Utilities, ...); the arrows above the list show one category at a time.
Set the quantity at the top right. The plan shows:

- **Materials to gather.** For each one you see how much you need in total and how much is still missing after
  counting your inventory. Switch off *Use what is in my inventory* to see the full amounts.
- **Crafting steps.** They come in an order you can follow, each with its station and inputs.
- **Notes** on leftovers, fuel and recipes taken from the game files.

Change the plan with the small buttons:

- **1/3** switches to another recipe.
- **⇄** picks another item of the same kind, for example birch instead of oak planks.
- **⛏** gathers an item instead of crafting it.
- **⚒** crafts it instead of gathering it.

*Save plan* keeps the plan, and *Add as goals* puts what is missing into your resource session.

Recipes come from your recipe book, i.e. what the server unlocked for you, plus the vanilla recipes built into the
game. Steps using a vanilla recipe are marked "vanilla default", because a server can change recipes. Raw materials
such as cobblestone, raw iron or redstone are planned as "gather". If you hold the inputs of an unusual recipe, for
example iron blocks, Minekit plans to use them.

### Tracking resources (Resource Tracker)

Start a session from the dashboard (*Resources*) or Quick Actions → *Start or stop resource session*. A session
belongs to the world or server you start it in: it only counts there, it pauses when you leave, and every world has
its own (the dashboard mentions sessions paused in other worlds). While it runs, Minekit compares the item totals
in your inventory twice a second:

- **Collected:** items gained while no screen is open (mining, picking up, receiving).
- **Used or given away:** items lost while no screen is open (placing, eating, throwing).
- **Unclear:** changes while your inventory, a chest or another screen is open, in creative mode or when you die.
  Minekit does not guess their cause.

Moving items between slots never counts. Goals ("128 iron ingots") show their progress in the HUD panel at the top
left and on the page. Sessions can be paused, renamed and ended. Ended sessions stay in the history (the last 30).

### Finding items in your storage (Storage Finder)

Minekit remembers what was in every chest, barrel, shulker box, hopper, dispenser, dropper and your ender chest
**when you close it**. Open the finder with Quick Actions → *Open Storage Finder* or from the dashboard (*Storage*).
Search an item on the left. The right side then lists every container holding it, with:

- coordinates, and the distance (or the dimension, if it is elsewhere)
- the count, as last seen
- how old the observation is: green for recently, grey for older, amber for older than a day

Filter by container type or by your current dimension. Click ✕ to forget a container. If you are close enough to see
that a container's block is gone, it is marked "block is gone".

Limits: a client-only mod only knows what the server showed you. Containers you never opened are unknown, and
contents may have changed since you last looked. Only containers you open by right-clicking the block are recorded,
and only when the block visibly opens (lid, barrel); menus a server shows on its own or instead of the real
container (shops, crate previews, virtual chests) are ignored. Items inside shulker boxes that are stored in a
chest are not counted. Each world and each server has its own index. The Crafting Assistant shows how many of a
missing material or intermediate product your storage holds ("64 stored").

### Putting items away (Smart Deposit)

Smart Deposit puts items from your inventory into the open container, for example everything the chest already
collects. Open a container and press `G` or the ⇩ button left of it. A panel shows what would move; the slots it
would fill light up green, the slots it would empty amber. Nothing has changed yet. The **?** button in the panel
explains it in the game. Choose the **profile**:

| Profile | Moves |
|---|---|
| Matching (default) | items the container already holds some of |
| Everything | everything that is not protected |
| Blocks / Resources | that category |
| Food and farming | food, seeds, crops, saplings, bone meal |
| Custom | the categories you select on the *Deposit* page |

Press **Deposit** (or `Enter`). Minekit tops up partial stacks first, then fills empty slots, with normal clicks
about ten times per second. Whatever does not fit stays in your inventory, in its slot. Your **hotbar** and your
**favorites** (from the sort preview) always stay with you; favorites can be included for a single deposit in the
panel. Smart Deposit stops when you close the container, press `G`, or anything unexpected happens, and then tells
you what was moved. It is not offered in menus a server opens on its own, and not in spectator mode.

### Quick Actions

Press `J` and type a few letters, or pick from the list. Pin the actions you use most with the star at the end of a
row (or `Ctrl`+`P`): pinned actions come first and are numbered, so `J` then `2` runs the second one. The actions you
used last follow. Hide actions you never use on the dashboard page *Quick Actions*; the search still finds them.

Some actions work with the item in your main hand: *Find held item in storage*, *Plan crafting of held item* and
*Collect more of held item* (raises its resource goal by 64, starting a session if none runs). Every saved loadout
has its own action, *Loadout: name*.

### Loadouts (Loadout Manager)

A loadout remembers which item goes where: armor, offhand, hotbar and inventory. Put on your gear and arrange your
inventory, then open the Loadout Manager (Quick Actions → *Save inventory as loadout*, or the dashboard page
*Loadouts*) and press **Save inventory**.

The manager shows the selected loadout laid out like your inventory. Each position is outlined by how it compares
with your inventory now: green (already there), teal (will be filled), amber (only partly available), red (missing,
or no free slot for what is there now). Items the loadout does not mention stay where they are.

- **Apply** opens your inventory and puts every item in its position with normal clicks. What was in a position moves
  to a free slot. Stacks are moved whole, so a position may get a few more than saved.
- **Next to a chest** (or barrel, shulker box, …) the ⚒ button opens a panel that takes from the container what the
  loadout still needs, and nothing else; nothing is ever put into the container. Armor and the offhand are not part
  of a container screen, so they stay as they are there.
- **Click a position** to remove it from the loadout or to lock it (applying never touches a locked slot);
  **shift-click** takes what is in your inventory there now. **Update** replaces the whole loadout with your
  inventory. **Match exactly** makes enchantments, names and other details count (wear never does).

Applying is not available in creative or spectator mode. It stops when you close the screen or anything unexpected
happens, and then tells you what is still missing.

### Blueprints and building tools

Minekit reads Litematica (`.litematic`) and Sponge schematic (`.schem`, versions 1 to 3) files and shows them as a
building guide. It never places a block; you build.

1. Open the blueprint screen (Quick Actions → *Open blueprints*, or the dashboard page *Blueprints*). Drop a file onto
   the window, or put it into the blueprint folder (*Open folder*). Minekit copies dropped files; the original stays
   untouched. Choose a blueprint on the left.
2. *Place in front of me*. Move it block by block (Shift: ten), turn it, mirror it, or move it to where you stand.
3. Build. Around you, missing blocks glow teal and wrong ones red; the next block (the nearest open one in the lowest
   unfinished layer) is amber, with its name. *Overview* shows how much is built; *Layers* shows one layer from above
   in its colors, with what is correct, missing or wrong, and lets you build only some layers; *Materials* lists what
   is still to place, what you carry and what your storage holds, and turns what is missing into resource goals.

Only chunks your game has loaded are compared: everything else counts as "not loaded", never as built. Old files are
updated to this version's block names; blocks it does not know are listed. Files and structures above the limits
(32 MB, 16 million positions, configurable in `blueprints.json`) are refused with the reason.

**Measuring:** look at a block and use Quick Actions → *Measure: set point*, then again on the other corner. Minekit
shows the box between them, its width, height and length, the volume and the distance. Both blueprints and measuring
have shortcuts in the controls, unbound by default.

### Where your data is stored

Everything is stored in `config/minekit/` inside the game folder (the Modrinth instance folder):

- `config.json`: general settings
- `inventory.json`: sorting
- `crafting-plans.json`: saved plans
- `resource-sessions.json`: sessions
- `deposit.json`: Smart Deposit settings
- `quick-actions.json`: pinned, hidden and recently used Quick Actions
- `loadouts.json`: your loadouts (at most 50; they belong to you, not to a world)
- `blueprints.json`: the active blueprint, where it stands in each world, the layers being built and the import limits
- `blueprints/`: your blueprint files
- `storage-index/`: one file per world (`sp-…`) or server (`mp-…`). The name is a hash, so server addresses are
  not stored in plain text.

If a file is damaged, Minekit keeps a copy (`*.corrupt-<time>`), starts with defaults and tells you so.

## Versions

Checked against https://fabricmc.net/develop/ on 2026-11-06. The authoritative values are in
[`gradle.properties`](gradle.properties). Rationale: [ADR 0001](docs/adr/0001-fabric-toolchain-and-client-only-setup.md).

| Component | Version |
|---|---|
| Minecraft Java Edition | 26.3 |
| Fabric Loader | 0.20.1 (mod requires `>=0.20.1`) |
| Fabric API | 0.165.2+26.3 (mod requires `>=0.165.2`) |
| Fabric Loom | 1.19.3 (plugin `net.fabricmc.fabric-loom`, no remapping since 26.x is unobfuscated) |
| Gradle | 9.8.2 (wrapper, checksum-verified) |
| Java | 25 (required by Minecraft 26.3) |

## Requirements

- **JDK 25.** Any distribution works (Temurin, Zulu, Microsoft, Oracle OpenJDK). Gradle must run on it:
  set `JAVA_HOME` to the JDK 25 directory, or add `org.gradle.java.home=<path>` to your personal
  `~/.gradle/gradle.properties` (not to the project file).
- Internet access on the first build. Gradle downloads itself, Fabric and Minecraft.

Check with:

```bash
java -version
```

## Build

```bash
./gradlew build
```

On Windows PowerShell use `.\gradlew.bat build`. The result is `build/libs/minekit-<version>.jar`, which is the
file you install. `minekit-<version>-sources.jar` is only the source archive.

`build` also runs the unit tests. `ClientOnlyMetadataTest` fails the build if `fabric.mod.json` stops being
client-only, for example if a `main` or `server` entrypoint is added.

## Tests

```bash
./gradlew test
```

Unit tests for logic that runs without Minecraft: config loading and recovery, module lifecycle, search,
notification queue, HUD placement, shortcut conflicts, client-only metadata, inventory sorting, crafting plans and
resource sessions, the storage index, Smart Deposit, Quick Action settings, loadouts, and blueprint files (both
formats built by the tests, broken files, placement turns and mirrors, progress bookkeeping). The sorting, deposit and
loadout tests each include a randomized test over 3,000 inventories. They check every time that nothing is lost or
duplicated and that protected slots stay untouched; the deposit and loadout tests also check that the preview equals
the execution, and the loadout test that a container only ever gives and that applying twice changes nothing.

```bash
./gradlew runClientGameTest
```

In-game tests using the Fabric client gametest API. They **open a Minecraft window**, create a test world and use
Minekit like a player would:
- open the dashboard with `K`, switch pages, toggle settings and check the config file
- search and run Quick Actions with `J`
- sort a messy inventory with `R` in single player and on a local dedicated server (real network)
- check that creative mode is refused and that an item arriving mid-sort stops the sort without losing anything
- plan 64 pistons from the real vanilla recipes, use the planner screen and save a plan
- run a resource session and check what counts as collected, used and unclear
- open chests, a double chest, a shulker box and the ender chest with a real right-click, check the storage index,
  search it in the Storage Finder, and check that a menu opened by the server is ignored, even right after a
  right-click on a chest that did not open
- deposit into a chest in single player and on the dedicated server and compare with the server's chest contents;
  check that hotbar, favorites and shulker boxes stay, and that a click on the panel with an item on the cursor drops
  nothing
- click the deposit panel (help, profiles, *Deposit*), the hotbar rule picker and the list scrollbars with the real
  mouse, because Fabric's `clickScreenButton` would skip the screen's own click handling
- pin Quick Actions with `Ctrl`+`P` and the star, run one with a number key, and use the held-item actions
- save a loadout, mix up the inventory, compare and apply it in the inventory screen, and take a loadout from a chest
  in single player and on the dedicated server (the server's chest only gives); check that Smart Deposit and the
  loadout panel never stay open together
- read real `.litematic` and `.schem` files (one broken, one from 1.20.1 with a renamed block), compare the guide with
  blocks placed by commands (correct, wrong block, wrong orientation, missing), turn the placement, open the blueprint
  screen's tabs, import a copy by drag and drop, and measure between two blocks

They are a separate test mod (`src/gametest`), never packaged into the Minekit JAR, and not part of `build`. The
dedicated test server accepts the Minecraft EULA (`eula = true` in `build.gradle`) and runs only locally in
`build/run`. Screenshots go to `build/run/clientGameTest/screenshots/`. A failure ends the run with "Client
gametests failed" in the log and a failed Gradle task.

## Run in the development client

```bash
./gradlew runClient
```

This starts Minecraft 26.3 with Minekit and Fabric API, using an offline development account. Game files go
to `run/` (ignored by git). To join a server straight away:

```bash
./gradlew runClient --args="--quickPlayMultiplayer 127.0.0.1:25565"
```

A successful start logs:

```text
[Minekit] Minekit 0.7.0 initialized (client-only) on Minecraft 26.3 with Fabric Loader 0.20.1
```

On the first start of a fresh `run/` folder Minecraft shows the accessibility onboarding screen, and the
`--quickPlayMultiplayer` join only happens after you leave it. Click through once, or set
`onboardAccessibility:false` in `run/options.txt`.

### Test against a vanilla server

The development account is offline and its name changes on every start (`Player123`). A local test server
therefore needs these lines in `server.properties`:

```properties
server-ip=127.0.0.1
online-mode=false
white-list=false
```

Vanilla 26.3 creates `white-list=true` by default. The server needs nothing else: no Fabric, no Minekit.

## Install into a Modrinth App instance

The Modrinth App is only the launcher. Gradle builds the JAR.

1. **Create a test instance (once).** In the Modrinth App choose *Create instance*. Select loader **Fabric**,
   game version **26.3** and Fabric Loader **0.20.1** or newer. A name such as `Minekit Dev` works.
2. **Add Fabric API.** Open the instance, *Add content*, search for **Fabric API** and install a 26.3 version
   (0.165.2+26.3 or newer).
3. **Find the instance's `mods` folder.** In the instance menu choose *Open folder*. The `mods` folder is
   inside it. On Windows it is usually `%APPDATA%\ModrinthApp\profiles\<instance name>\mods`.
4. **Copy the JAR**, either by hand from `build/libs/` or with Gradle:

   ```bash
   ./gradlew installToModrinth -Pminekit.modsDir="C:/Users/<you>/AppData/Roaming/ModrinthApp/profiles/Minekit Dev/mods"
   ```

   To avoid typing the path each time, put it in your personal `~/.gradle/gradle.properties`:

   ```properties
   minekit.modsDir=C:/Users/<you>/AppData/Roaming/ModrinthApp/profiles/Minekit Dev/mods
   ```

   The task builds the JAR, removes older `minekit-*.jar` files from that folder so Fabric does not load the
   mod twice, and copies the new one. It does not touch any other file.
5. **Start the instance** in the Modrinth App. Check the log (instance, *Logs*, or `logs/latest.log` in the
   instance folder) for the `Minekit ... initialized (client-only)` line.

## Share Minekit with a friend

Your friend needs the Modrinth App and nothing on any server. The easiest way is a modpack file:

1. In the Modrinth App, open your Minekit instance, then the instance menu (⋮) → **Export modpack**. Keep Minekit
   (under `mods`) selected and export. You get a `.mrpack` file. It contains the Minekit JAR and only a reference to
   Fabric API, which the Modrinth App downloads from Modrinth.
2. Send the `.mrpack` file. Your friend chooses **Create instance** → **Import from file** (or drops the file onto
   the app). The instance has Minecraft 26.3, Fabric Loader, Fabric API and Minekit, ready to play.

Without a modpack: send `build/libs/minekit-<version>.jar`. Your friend creates a Fabric 26.3 instance, installs
**Fabric API** through *Add content*, and drops the JAR into the instance (*Add content* → *Add from file*, or into
the `mods` folder). For later updates, a new JAR replaces the old one in the same instance; settings stay in
`config/minekit/`.

## Client-only guarantees

- `fabric.mod.json` declares `"environment": "client"`. Fabric Loader does not load Minekit on dedicated
  servers.
- The only entrypoint is the `client` entrypoint `io.github.nordavid.minekit.MinekitClient`.
- No mixins, no custom network payloads, no commands, no server-side dependencies.
- Checked in Phase 0: the development client with Minekit joined an unmodified vanilla 26.3 server, and a
  Modrinth App instance with Minekit and Fabric API started and loaded a single-player world.

## Project layout

```text
src/main/java/io/github/nordavid/minekit/
  MinekitClient.java        client entrypoint, wires all services
  MinekitRuntime.java       the shared services
  core/persistence/         versioned JSON files with backups and atomic writes
  core/config/              general settings file
  core/module/              module contract and lifecycle
  core/input/               shortcuts and conflict detection
  core/notification/        status messages and their HUD cards
  core/hud/                 HUD element placement
  core/screen/              dashboard, pages and shared UI widgets (widget/)
  core/item/                item identity and categories
  core/container/           pure inventory model, click simulator, container screen identification
  core/action/              Quick Action registry and the safe click executor
  core/storage/             bridge to what the storage index knows
  feature/quickactions/     D11 Quick Actions
  feature/inventory/        A1 Smart Inventory (sorting)
  feature/crafting/         A2 Crafting Assistant (recipe catalog, planner, screen)
  feature/resources/        A4 Resource Tracker (sessions, goals, HUD)
  feature/storage/          A3 Storage Finder (index, finder screen)
  feature/deposit/          D14 Smart Deposit (planner, preview panel)
  feature/loadout/          D12 Loadout Manager (planner, manager screen, container panel)
  feature/blueprint/        B5/B6 Blueprints (readers, placement, progress, world guide, screen, measuring)
  core/world/               world and server ids for per-world data
src/main/resources/         fabric.mod.json, translations (assets/minekit/lang)
src/test/java/              unit tests
src/gametest/               in-game tests (separate test mod, not shipped)
docs/adr/                   architecture decision records
docs/PROGRESS.md            phase checklist and verification log
```

## Troubleshooting

- **`Unsupported class file major version` or Loom asks for Java 25:** Gradle is not running on JDK 25.
  Check `JAVA_HOME`.
- **Modrinth instance crashes with "Incompatible mods" or "requires fabric-api":** Fabric API is missing or
  too old in the instance, or the instance is not on Minecraft 26.3.
- **"Duplicate mod" error:** two Minekit JARs are in the `mods` folder. Delete the older one, or use
  `installToModrinth`, which does that for you.
- **`K` or `J` does nothing:** another mod may use the same key. The dashboard's Overview lists conflicts. Rebind
  under *Controls → Key Binds → Minekit*. If `J` shows "Quick Actions is off", turn the module on in the dashboard
  settings.

## License

[MIT](LICENSE)
