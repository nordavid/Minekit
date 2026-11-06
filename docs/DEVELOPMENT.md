# Developing Minekit

Building, testing and running Minekit from source. Architecture decisions are in [adr/](adr/), the phase checklist
and verification log in [PROGRESS.md](PROGRESS.md), the product plan in [PROJECT_PLAN.md](../PROJECT_PLAN.md).

## Versions

Checked against https://fabricmc.net/develop/ on 2026-11-06. The authoritative values are in
[`gradle.properties`](../gradle.properties). Rationale: [ADR 0001](adr/0001-fabric-toolchain-and-client-only-setup.md).

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
resource sessions, the storage index, Smart Deposit, Quick Action settings, loadouts, blueprint files (every format
built by the tests, compressed and plain, broken files, placement turns and mirrors, progress bookkeeping) and the 3D
view's painter. The sorting, deposit and
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
- read real `.litematic`, `.schem` and `.schematic` files (one broken, one from 1.20.1 with a renamed block, one
  uncompressed MCEdit file with numeric ids), check that the screen says why the broken one failed, compare the guide
  with blocks placed by commands (correct, wrong block, wrong orientation, missing), turn the placement, switch
  between the whole structure in the world and the view around the player, draw a 40 x 20 x 40 structure and check
  the cost per tick, open the blueprint screen's tabs with the 3D view and the plan, import a copy by drag and drop,
  and measure between two blocks

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
[Minekit] Minekit 0.8.0 initialized (client-only) on Minecraft 26.3 with Fabric Loader 0.20.1
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

## Troubleshooting the build

- **`Unsupported class file major version` or Loom asks for Java 25:** Gradle is not running on JDK 25.
  Check `JAVA_HOME`.
