# MINEKIT --- Product & Technical Development Plan

> **Project type:** Minecraft Java Edition client-side utility mod\
> **Target Minecraft version:** 26.3\
> **Mod loader:** Fabric\
> **Launcher / test environment:** Modrinth App\
> **Working name:** MINEKIT\
> **Document status:** Initial implementation specification\
> **Primary goal:** Make ordinary Minecraft gameplay more convenient
> through practical tools, intelligent organization, planning and safe
> client-side automation.

------------------------------------------------------------------------

## 1. Product vision

MINEKIT is a modular, client-side utility suite for Minecraft Java
Edition. It combines inventory management, crafting and resource
planning, storage discovery, building assistance, quick actions,
equipment loadouts and smart item depositing in one consistent
interface.

MINEKIT must work when installed only on the local player's client.
Other players must not need MINEKIT, and the server must not need a
plugin, datapack, configuration change, custom command or custom network
protocol.

The mod should improve normal Minecraft gameplay rather than turn it
into an automated bot. It may organize information, calculate plans,
show overlays and execute convenient sequences of ordinary player
inventory interactions. It must not silently bypass server rules,
anti-cheat systems, permissions or normal game mechanics.

### Product principles

1.  **Client-only by design:** no required server-side component.
2.  **Vanilla-compatible behavior:** use normal client interactions and
    vanilla-supported actions.
3.  **Modular:** every feature can be enabled, disabled and configured
    independently.
4.  **Consistent UX:** one design system, shared navigation, shortcuts
    and settings.
5.  **Local-first data:** user data is stored locally and scoped to the
    relevant world/server.
6.  **Safe automation:** preview actions where useful, provide
    cancellation, respect server responses and never spam interactions.
7.  **Maintainable implementation:** clear boundaries, typed models,
    tests and minimal use of Mixins.
8.  **Incremental delivery:** implement a small vertical slice, validate
    it in-game, then expand.

------------------------------------------------------------------------

## 2. Confirmed scope

The following features are in scope.

  -----------------------------------------------------------------------
  ID                Module            Main purpose      Initial priority
  ----------------- ----------------- ----------------- -----------------
  A1                Smart Inventory   Sort, categorize  High
                    Manager           and manage the    
                                      player's          
                                      inventory         

  A2                Crafting          Calculate         High
                    Assistant         recipes, material 
                                      requirements and  
                                      crafting chains   

  A3                Storage Finder    Index known       High
                                      containers and    
                                      search their last 
                                      observed contents 

  A4                Resource Tracker  Track collected   Medium
                                      and consumed      
                                      resources and     
                                      mining sessions   

  B5/B6             Blueprint &       Import external   High
                    Building Tools    structure files,  
                                      preview builds    
                                      and assist manual 
                                      construction      

  D11               Quick Actions     Provide           High
                                      configurable      
                                      shortcuts for     
                                      common actions    

  D12               Loadout Manager   Save equipment    Medium
                                      and inventory     
                                      arrangements as   
                                      loadouts          

  D14               Smart Deposit     Move selected     High
                                      items into a      
                                      container using   
                                      normal inventory  
                                      interactions      
  -----------------------------------------------------------------------

Do not add unrelated feature modules without explicit approval. Small
supporting utilities are allowed when they directly support these
modules.

### Explicitly out of scope for the initial product

-   New blocks, items, entities, mobs, recipes or dimensions.
-   Server plugins, datapacks or required server configuration.
-   Custom server commands or custom networking protocols.
-   Autonomous farming, mining, combat or movement.
-   Automatically placing blueprint blocks.
-   Reading unopened or inaccessible container inventories.
-   Features that depend on every other player installing MINEKIT.
-   A cloud account, online backend or mandatory telemetry.

------------------------------------------------------------------------

## 3. Target platform and project setup

### 3.1 Minecraft and Fabric

Target Minecraft Java Edition **26.3**.

Use the current stable Fabric toolchain compatible with 26.3. Do not
copy dependency versions from old tutorials or hard-code versions from
this document. Before implementation:

1.  Check the official Fabric developer version selector:
    https://fabricmc.net/develop/
2.  Check the official Fabric documentation: https://docs.fabricmc.net/
3.  Confirm the current Minecraft, Fabric Loader, Fabric API, Fabric
    Loom, Gradle and Java requirements.
4.  Use the versions explicitly recommended for Minecraft 26.3 at
    project creation time.
5.  Record the selected versions in the project README and Gradle
    configuration.

Use Fabric Loader and Fabric API. Use Fabric Loom for the Gradle build.

Prefer Java unless a specific, justified technical reason requires
another JVM language. Keep the project easy to build and maintain with
standard Java tooling.

### 3.2 Modrinth workflow

The Modrinth App is the user's launcher and test environment; it is not
the build system.

The project must: - Build a distributable mod JAR with Gradle. - Support
copying the built JAR into the active Modrinth instance's `mods`
folder. - Declare Minecraft and Fabric dependencies correctly in
`fabric.mod.json`. - Include a clear local development and testing
guide. - Avoid depending on private launcher paths or one particular
machine. - Be suitable for eventual publishing to Modrinth, but
publishing is not part of the MVP.

### 3.3 Client-only enforcement

The mod must declare itself as client-only in Fabric metadata and use a
client entrypoint.

Architecture rules: - Do not register server entrypoints. - Do not
register custom payloads or custom networking. - Do not add server-side
dependencies. - Do not assume the integrated server is available. - Do
not use server internals to obtain hidden information. - Use normal
Minecraft client APIs for UI, screens, input, rendering and inventory
interactions. - If a feature cannot work without server support, explain
the limitation and disable or omit that behavior.

Normal vanilla inventory clicks may result in the same standard
inventory packets Minecraft normally sends. This is expected client
behavior, not a custom MINEKIT protocol. The server remains
authoritative and may reject actions.

------------------------------------------------------------------------

## 4. Product architecture

Build MINEKIT as a modular application with a small core and independent
feature modules.

### 4.1 Suggested package structure

The exact package name should use a unique reverse-domain namespace
chosen by the project owner. Do not use the example namespace below
literally without confirming it.

``` text
src/main/java/<namespace>/minekit/
├── MinekitClient.java
├── core/
│   ├── module/
│   │   ├── MinekitModule.java
│   │   ├── ModuleManager.java
│   │   └── ModuleMetadata.java
│   ├── config/
│   │   ├── MinekitConfig.java
│   │   └── ConfigManager.java
│   ├── event/
│   ├── input/
│   ├── screen/
│   ├── hud/
│   ├── data/
│   ├── persistence/
│   ├── context/
│   └── util/
├── feature/
│   ├── inventory/
│   ├── crafting/
│   ├── storage/
│   ├── resources/
│   ├── blueprint/
│   ├── quickactions/
│   ├── loadout/
│   └── smartdeposit/
└── integration/
    ├── minecraft/
    └── fabric/

src/main/resources/
├── fabric.mod.json
├── minekit.mixins.json
├── assets/minekit/
│   ├── textures/
│   ├── fonts/
│   └── lang/
└── data/minekit/
```

Adapt this structure to the actual APIs and conventions of Minecraft
26.3. Do not create empty abstractions just to match the tree.

### 4.2 Module contract

Each feature module should: - Have a stable module ID and display
name. - Expose an enabled/disabled state. - Register and unregister its
event listeners cleanly. - Own its settings and persistence models. -
Avoid direct dependencies on unrelated feature implementations. - Use
shared core services through interfaces. - Provide a concise description
and default keybinds where applicable. - Fail safely: a broken optional
module must not crash the entire mod where practical.

Suggested conceptual interface:

``` java
public interface MinekitModule {
    String id();
    String displayName();

    void initialize();
    void onEnable();
    void onDisable();

    boolean isEnabled();
}
```

This is illustrative, not a demand to use this exact API. The coding
agent should adapt it to Fabric and Minecraft 26.3.

### 4.3 Shared core services

Implement only the services needed by actual features:

-   **ModuleManager:** module lifecycle and enabled state.
-   **ConfigManager:** load, validate, migrate and save configuration.
-   **KeybindManager:** central registration and conflict handling for
    shortcuts.
-   **Screen navigation:** shared dashboard and feature screens.
-   **HudManager:** optional HUD elements with visibility and
    positioning settings.
-   **WorldContext:** identifies the current world/server context
    without storing sensitive connection data unnecessarily.
-   **Item identity utilities:** consistent item and component
    comparison.
-   **Recipe service:** access to client-available recipe data.
-   **Container observation service:** receives updates only from
    containers the client legitimately opens.
-   **Notification service:** consistent unobtrusive status messages.
-   **Persistence service:** versioned local data storage.
-   **Action executor:** executes approved, ordinary client interactions
    with state checks, rate limits and cancellation.

Do not build a generic event bus, dependency injection framework or
scripting engine unless a concrete requirement justifies it.

------------------------------------------------------------------------

## 5. UI and interaction design

### 5.1 Overall experience

MINEKIT should feel like a polished, modern utility interface integrated
into Minecraft.

Use three layers:

1.  **Main dashboard:** a full-screen interface for managing modules and
    complex workflows.
2.  **Quick Actions:** a compact radial, grid or searchable popup opened
    with a configurable key.
3.  **HUD widgets:** small optional indicators for relevant live
    information.

Avoid permanently covering important gameplay areas. Every HUD element
must be movable, hideable and configurable.

### 5.2 Dashboard information architecture

Suggested navigation:

``` text
MINEKIT
├── Overview
├── Inventory
│   ├── Sort
│   ├── Categories
│   └── Profiles
├── Crafting
│   ├── Item Search
│   ├── Recipe Tree
│   └── Material List
├── Storage
│   ├── Search
│   ├── Known Containers
│   └── Container Details
├── Resources
│   ├── Current Session
│   ├── Goals
│   └── History
├── Blueprints
│   ├── Imported Files
│   ├── Preview
│   └── Build Progress
├── Quick Actions
├── Loadouts
└── Settings
```

The dashboard should provide: - Search where it improves navigation. -
Clear empty, loading, unavailable and error states. - Keyboard
navigation and tooltips. - Consistent icons, spacing, typography and
colors. - Scalable layouts for different GUI scales and screen sizes. -
A way to return to the game without losing the current workflow.

### 5.3 Visual direction

Use a restrained dark interface with clear contrast and a small accent
palette. The UI should look like a coherent tool, not a collection of
unrelated vanilla screens.

Do not spend the first development phase polishing every pixel.
Establish reusable UI components and interaction patterns first, then
refine them after the first playable version.

------------------------------------------------------------------------

## 6. Feature specifications

## 6.1 A1 --- Smart Inventory Manager

### Purpose

Make the player's own inventory easier to organize and manage.

### MVP behavior

-   Sort the player's own inventory using deterministic rules.
-   Support categories such as blocks, tools, weapons, armor, food,
    resources, utility and miscellaneous.
-   Allow the player to mark items as favorites.
-   Provide a preview of the resulting order before applying it.
-   Allow sorting profiles, initially:
    -   General
    -   Mining
    -   Building
    -   Adventure
-   Allow the player to configure category order and selected sorting
    rules.
-   Keep hotbar slots protected by default.
-   Never discard items.
-   Never move items into a container unless the player explicitly
    invokes a relevant action.

### Sorting rules

Define a stable ordering system: 1. Favorites / pinned items. 2.
Configured category priority. 3. Item identity. 4. Stack size or other
selected tie-breaker. 5. Stable fallback order.

Do not use display names as the only item identity. Use registry
identifiers and relevant item components.

### Important technical constraints

-   Inventory state can change while a screen is open.
-   Only act on the currently valid screen handler and slots.
-   Respect cursor-stack state.
-   Do not assume a click succeeded immediately.
-   Re-read actual inventory state after actions.
-   If the server rejects or delays a click, stop or reconcile instead
    of continuing blindly.
-   Clearly distinguish player inventory slots from container slots.

### Acceptance criteria

-   Sorting produces the same result for the same inventory and profile.
-   Protected slots remain unchanged.
-   No item is lost or duplicated.
-   The user can preview and cancel.
-   Sorting works in the normal player inventory.
-   Server rejection or unexpected state changes do not trigger repeated
    clicks.

------------------------------------------------------------------------

## 6.2 A2 --- Crafting Assistant

### Purpose

Let the player choose a target item and quantity, then calculate the
resources and intermediate crafting steps needed.

### MVP behavior

-   Search available vanilla items.
-   Select a target item and desired quantity.
-   Display the recipe or recipes available to the client.
-   Calculate required ingredients recursively.
-   Aggregate repeated ingredients across the crafting tree.
-   Compare requirements with the player's current inventory.
-   Display available, missing and total quantities.
-   Let the user mark items as already owned or sourced from storage.
-   Generate a persistent shopping/material list.
-   Show intermediate crafting steps in a readable tree.

### Example

Target: 64 pistons.

The assistant should calculate: - Total pistons required. - Total planks
/ wooden components required by the recipe chain. - Total cobblestone. -
Total iron ingots. - Total redstone dust. - Intermediate crafting
operations such as planks, sticks and other recipe outputs where
applicable.

The actual quantities must come from Minecraft's recipe definitions, not
hard-coded examples.

### Recipe selection

Some items have multiple recipes. The MVP should: - Show available
alternatives. - Let the user select a preferred recipe. - Avoid silently
selecting an unexpected expensive or inconvenient recipe. - Detect
cycles or recursive recipe dependencies safely. - Explain when a recipe
is unavailable to the client.

### Inventory and storage integration

-   Inventory counts should be read from the current local player
    inventory.
-   Storage counts may only use known, last-observed container contents.
-   Display the source and freshness of each count.
-   Do not imply that stale container data is current.

### Acceptance criteria

-   Quantities are correct for recipes with multiple ingredients and
    output counts.
-   Recursive crafting does not loop infinitely.
-   Multiple recipe alternatives are selectable.
-   Missing resources are clearly calculated.
-   The plan can be saved and reopened.

------------------------------------------------------------------------

## 6.3 A3 --- Storage Finder

### Purpose

Allow the player to search known storage containers without manually
opening every chest again.

### Core limitation

A client-only mod cannot inspect arbitrary unopened containers in the
world. It must not pretend otherwise.

MINEKIT can catalogue contents only when those contents are legitimately
sent to the client, for example when the player opens a chest or other
supported container and the client receives its inventory state.

### MVP behavior

-   Detect supported container screens opened by the player.
-   Capture the container's visible inventory contents.
-   Associate the observation with a reliable location and world/server
    context.
-   Store item identifiers, relevant components, quantities and
    observation time.
-   Allow searching known container contents.
-   Display container coordinates and distance from the player.
-   Show when contents were last observed.
-   Mark records as potentially stale.
-   Update the record the next time the container is opened.
-   Allow the player to forget a container record.
-   Provide filters for item, container type and location.

### Location identity

Use a stable container key where possible: - World/server context. -
Dimension identifier. - Block position. - Container type.

For multi-block containers, avoid duplicate records where practical. If
reliable merging is not possible, represent the two halves explicitly
rather than inventing a combined inventory.

### Data freshness

Every stored observation must have: - Last observed timestamp. -
Dimension. - Position. - Container type. - Observed item stacks. -
Optional confidence/freshness status.

Suggested UI statuses: - Recently observed. - Older observation. -
Potentially stale. - Not yet observed.

Never claim that a container still contains an item just because it did
so during an earlier visit.

### Privacy and storage

-   Keep records local.
-   Scope records by world/server.
-   Do not store account credentials or unnecessary server identifiers.
-   Provide clear/delete controls.

### Acceptance criteria

-   Opening a supported container creates or updates its local record.
-   Searching finds items in previously observed containers.
-   Results show location and last-observed time.
-   Unopened containers are never indexed.
-   Data from different worlds/servers is not mixed.

------------------------------------------------------------------------

## 6.4 A4 --- Resource Tracker

### Purpose

Track resources collected and consumed during a player-defined session.

### MVP behavior

-   Start, pause, resume and stop a session.
-   Track relevant inventory changes and item pickups where reliably
    observable.
-   Track selected resource categories.
-   Show collected, consumed and net change where the source can be
    determined reliably.
-   Allow the player to select goals such as collecting 128 iron ingots.
-   Display session duration.
-   Persist session history locally.
-   Allow sessions to be renamed and deleted.

### Accuracy rules

Inventory differences alone do not always reveal why an item changed.
Items can be moved between slots, dropped, crafted, consumed, traded or
moved into containers.

Therefore: - Do not count slot movement as collection or consumption. -
Prefer observable events when available. - Reconcile inventory snapshots
to detect changes. - Mark ambiguous changes as unknown rather than
inventing a cause. - Make tracking rules transparent to the user.

### Acceptance criteria

-   Sessions can be controlled and reopened.
-   No double counting from slot rearrangement.
-   Ambiguous changes are not falsely labelled as mined or collected.
-   Goals show current progress and remaining quantity.

------------------------------------------------------------------------

## 6.5 B5/B6 --- Blueprint & Building Tools

### Purpose

Import external structure files, preview them in the world and help the
player build them manually.

This module combines blueprint visualization and practical
measuring/building assistance.

### Initial supported formats

Prioritize: 1. **Litematica `.litematic`** 2. **Sponge Schematic
`.schem`**

Investigate mature, compatible parsing libraries before writing format
parsers from scratch. Verify license compatibility, maintenance status
and support for the required format versions. Do not copy implementation
code without checking its license.

Legacy `.schematic` support may be considered later if there is clear
demand.

### MVP behavior

-   Import a file from a user-selected local file.
-   Validate the file before parsing.
-   Show a preview in the MINEKIT blueprint screen.
-   Render a ghost/hologram structure in the world.
-   Support moving, rotating and mirroring the preview.
-   Show overall dimensions and block count.
-   Show the target structure's bounding box.
-   Allow the user to select a build region.
-   Compare the target structure with currently loaded/visible world
    blocks where safe and technically possible.
-   Show completion progress for positions that can be reliably checked.
-   Highlight the next block or a selected layer/section.
-   Display required block totals.
-   Show missing blocks compared with the player's inventory.
-   Provide basic measurement tools: distance, width, height, bounding
    box and coordinates.

### Manual construction only

MINEKIT must not automatically place blueprint blocks. The player places
blocks through normal gameplay.

The blueprint module is a visual guide and planning tool, not a printer
or bot.

### World comparison limitations

World comparison must be honest about data availability: - Compare only
blocks whose client-side world data is currently loaded and available. -
Do not force-load chunks. - Do not claim completion for unloaded
positions. - Distinguish correct, incorrect, missing/unavailable and
unchecked positions. - Do not attempt to read hidden server state.

### File safety

Blueprint files are untrusted input: - Validate file size before
loading. - Validate dimensions and total block count. - Protect against
malformed or deeply nested compressed data. - Set configurable limits
for memory usage. - Reject invalid palette indexes and out-of-range
positions. - Do not execute scripts or embedded code from imported
files. - Handle parsing failures without crashing Minecraft. - Never
overwrite source files. - Store imported files in a dedicated MINEKIT
directory or reference them safely.

### Acceptance criteria

-   Supported sample files import successfully.
-   Invalid files fail gracefully with useful error messages.
-   Preview can be moved, rotated and mirrored.
-   Rendering does not freeze the game on large structures.
-   Unloaded chunks are clearly marked as unchecked.
-   No automatic block placement occurs.
-   File limits prevent excessive memory allocation.

------------------------------------------------------------------------

## 6.6 D11 --- Quick Actions

### Purpose

Provide fast access to common MINEKIT actions without navigating the
full dashboard.

### MVP behavior

-   Open a compact popup with a configurable keybind.
-   Display actions as searchable entries or a compact grid.
-   Allow actions to be pinned and reordered.
-   Support keyboard navigation.
-   Allow per-action keybinds where useful.
-   Show unavailable actions as disabled with a reason.
-   Provide confirmation for actions that move items or change inventory
    state.

### Initial actions

-   Open MINEKIT dashboard.
-   Sort inventory using selected profile.
-   Open Crafting Assistant.
-   Open Storage Finder.
-   Start/stop Resource Tracker session.
-   Open Blueprint list.
-   Open Loadout Manager.
-   Run Smart Deposit for the currently open supported container.

### Action execution

Use a central action executor: - Check preconditions. - Check the
current screen and player state. - Present a preview for multi-slot
changes. - Execute only ordinary client interactions. - Reconcile actual
state. - Stop on unexpected changes or rejected actions. - Provide a
concise result notification.

Do not implement arbitrary command execution or a scripting language in
the MVP.

------------------------------------------------------------------------

## 6.7 D12 --- Loadout Manager

### Purpose

Save and manage preferred equipment and inventory arrangements for
different activities.

### MVP behavior

-   Create, rename, duplicate and delete loadouts.
-   Save desired items and target slots.
-   Support examples such as Mining, Building, Adventure and Nether.
-   Compare the active inventory with a selected loadout.
-   Show present, missing, different and extra items.
-   Allow the player to manually apply a loadout using normal inventory
    interactions.
-   Protect configured slots.
-   Allow partial application if some items are unavailable.

### Matching rules

-   Use item registry identifiers and relevant components.
-   Handle stackable and non-stackable items correctly.
-   Do not assume two items are interchangeable if enchantments,
    durability, custom names or other components matter.
-   Let the user choose whether exact component matching is required.
-   Do not silently delete or discard extra items.

### Acceptance criteria

-   Loadouts persist locally.
-   The comparison accurately identifies missing and mismatched items.
-   Applying a loadout never discards items.
-   The user can preview and cancel.
-   Server rejection or slot changes stop the process safely.

------------------------------------------------------------------------

## 6.8 D14 --- Smart Deposit

### Purpose

Make it faster to put items into a container without manually moving
every stack.

### MVP behavior

When a supported container is open, the user invokes Smart Deposit.
MINEKIT: - Identifies the currently open container and player inventory
slots. - Uses the selected deposit profile or category rules. - Finds
matching stacks and compatible partial stacks. - Previews which items
will be moved. - Executes normal inventory clicks or other standard
client interactions. - Re-checks the inventory and container state after
each action. - Stops when the target is full, the screen changes or an
unexpected state occurs. - Leaves protected/favorited items untouched. -
Shows a summary of what was moved and what remained.

### Profiles

Initial profiles: - Deposit all eligible items. - Deposit blocks. -
Deposit resources. - Deposit food and farming items. - Custom category
selection.

### Safety rules

-   Never delete items.
-   Never use creative-mode or privileged actions.
-   Never bypass server inventory validation.
-   Do not send rapid click sequences without waiting for state
    reconciliation.
-   Use conservative timing and stop if the server does not acknowledge
    expected changes.
-   Respect cursor stack state.
-   Do not act if the container screen is no longer active.
-   Provide cancellation.
-   Do not deposit favorites or protected items unless the user
    explicitly overrides the rule.

### Acceptance criteria

-   Only eligible items are moved.
-   Protected items remain in the player's inventory.
-   Partial stacks are handled correctly.
-   Full containers are handled gracefully.
-   Unexpected server responses stop the operation.
-   The final inventory is reconciled with actual server-confirmed
    state.

------------------------------------------------------------------------

## 7. Data model and persistence

Use local, versioned data. Start with JSON for human-readable
configuration and small records. Use SQLite only when the volume or
query requirements justify it, especially for container indexes and
longer resource histories.

Avoid introducing a database dependency before it is needed.

### Suggested local data areas

``` text
.minecraft/
└── config/
    └── minekit/
        ├── config.json
        ├── keybinds.json
        ├── inventory-profiles.json
        ├── loadouts.json
        ├── crafting-plans.json
        ├── resource-sessions.json
        ├── storage-index/
        └── blueprints/
```

Use the Fabric/Minecraft config directory API rather than assuming a
fixed operating-system path.

### Data requirements

-   Include a schema version in persisted data.
-   Validate data during loading.
-   Migrate older schemas safely.
-   Write files atomically where practical.
-   Handle corrupt files without preventing the game from launching.
-   Keep backups before destructive migrations.
-   Separate configuration from world-specific data.
-   Scope storage records and other world observations by world/server
    context.
-   Do not persist credentials or unnecessary personal information.

### Item identity

Use stable registry identifiers and relevant data components. Do not
rely only on localized display names or item icon textures.

Where relevant, distinguish: - Item type. - Components. -
Enchantments. - Custom name. - Durability. - Container contents or other
item-specific data.

Do not serialize arbitrary Minecraft runtime objects directly. Convert
to explicit, versioned data-transfer models.

------------------------------------------------------------------------

## 8. Performance and reliability

MINEKIT must not introduce noticeable gameplay lag.

Requirements: - Avoid scanning the entire world every tick. - Prefer
event-driven updates. - Use bounded work queues for expensive tasks. -
Parse large blueprint files off the render thread. - Keep all Minecraft
world and UI access on the appropriate game thread. - Cache derived
recipe and item data where safe. - Avoid unnecessary allocations in
frequently called render paths. - Make HUD rendering optional. - Set
safe upper limits for imported blueprint size and stored container
observations. - Cancel background work when leaving a world or closing
the relevant screen.

Never access mutable Minecraft world state from a background thread
unless the relevant API explicitly permits it. Background workers may
parse immutable file data, but game state must be read and updated on
the correct thread.

------------------------------------------------------------------------

## 9. Security and multiplayer compatibility

This is a core product requirement, not an optional quality attribute.

MINEKIT must: - Work without any server-side installation. - Avoid
custom network payloads and custom commands. - Avoid exploiting bugs or
bypassing anti-cheat. - Use only information legitimately available to
the local client. - Respect server-authoritative inventory state. - Stop
automation when the server rejects or changes the expected state. -
Avoid excessive packet or click rates. - Never attempt to reveal hidden
containers, entities, players or chunks. - Clearly disclose when
information is stale or unavailable.

Test against a local single-player world and a multiplayer server with
ordinary server settings. Also test with a server that rejects or delays
inventory actions.

------------------------------------------------------------------------

## 10. Testing strategy

### Automated tests

Write unit tests for logic that can be tested without launching
Minecraft: - Inventory sorting order. - Item matching and component
comparison. - Recursive recipe calculations. - Recipe cycle detection. -
Resource aggregation. - Loadout comparison. - Smart Deposit planning. -
Blueprint file validation. - Blueprint coordinate transforms. -
Persistence serialization and migration.

### In-game tests

For each module, verify: - Correct behavior in single-player. - Correct
behavior on a vanilla multiplayer server. - Correct behavior when the
player changes screens during an operation. - Correct behavior when the
server rejects or delays an inventory action. - Correct behavior when
the player disconnects or changes dimension. - No item loss or
duplication. - No crash on malformed local data or imported files.

### Regression tests

After each feature is added: - Build the mod. - Launch the target
Minecraft version. - Verify all previously implemented modules. -
Inspect logs for errors and warnings. - Verify that client-only metadata
and entrypoints remain correct.

------------------------------------------------------------------------

## 11. Development roadmap

Build vertical slices rather than implementing every backend first and
every UI screen later.

### Phase 0 --- Technical validation

Deliverables: - Verify the current Fabric toolchain for Minecraft
26.3. - Generate the Fabric project. - Configure Gradle and Java. -
Create client-only metadata and entrypoint. - Build and launch a minimal
MINEKIT JAR in a Modrinth instance. - Add a README with exact setup and
build instructions. - Confirm no server-side component is required.

**Exit criteria:** MINEKIT launches on Minecraft 26.3 through the
Modrinth App and is confirmed client-only.

### Phase 1 --- Core shell and UI

Deliverables: - Module contract and module manager. - Configuration
loading and saving. - Keybind registration. - Main dashboard. - Quick
Actions shell. - Notification service. - Basic HUD framework. - Module
enable/disable settings.

**Exit criteria:** The user can open the dashboard, navigate modules,
change settings and restart the game without losing configuration.

### Phase 2 --- Inventory foundation

Deliverables: - Item identity utilities. - Inventory snapshot model. -
Smart Inventory Manager. - Sorting profiles. - Preview and confirmation
flow. - Safe action executor foundation.

**Exit criteria:** Inventory sorting is deterministic, safe and tested
in multiplayer.

### Phase 3 --- Crafting and resource planning

Deliverables: - Recipe lookup. - Recursive material calculation. -
Recipe alternatives. - Material lists. - Inventory availability
comparison. - Resource Tracker sessions and goals.

**Exit criteria:** The user can plan a target craft and track a resource
session with transparent accuracy limitations.

### Phase 4 --- Storage index and Smart Deposit

Deliverables: - Container observation. - Local storage index. - Search
UI and stale-data indicators. - Smart Deposit profiles. - Safe deposit
preview and execution. - Container/world scoping.

**Exit criteria:** Previously opened containers can be searched, and
eligible items can be deposited safely using ordinary interactions.

### Phase 5 --- Loadouts and Quick Actions

Deliverables: - Loadout creation and comparison. - Partial loadout
application. - Quick Action configuration. - Pinned actions and keyboard
navigation. - Cross-module action integration.

**Exit criteria:** Common workflows can be performed without repeatedly
navigating the dashboard.

### Phase 6 --- Blueprint & Building Tools

Deliverables: - Format investigation and parser selection. -
`.litematic` importer. - `.schem` importer. - File validation and
limits. - Blueprint preview. - Transformations. - World comparison for
loaded blocks. - Build progress and material requirements. - Measurement
tools.

**Exit criteria:** External blueprints can be imported and used as a
manual building guide without automatic block placement.

### Phase 7 --- Polish and release readiness

Deliverables: - UI refinement. - Settings and keybind polish. - Error
handling. - Performance profiling. - Compatibility testing. - Modrinth
metadata and release packaging. - User documentation and known
limitations.

**Exit criteria:** A reproducible release JAR, clear installation
instructions and a tested feature set.

------------------------------------------------------------------------

## 12. Coding-agent instructions

The coding agent (Claude Code or Codex) must follow these rules
throughout the project.

### Before coding

1.  Inspect the existing repository and identify whether it is empty or
    already contains work.
2.  Read the current official Fabric documentation for Minecraft 26.3.
3.  Verify dependency versions rather than guessing.
4.  Write a concise implementation plan for the next phase only.
5.  Identify APIs and libraries before introducing dependencies.
6.  Record important architectural decisions in short ADR documents when
    a decision has long-term consequences.

### During implementation

-   Implement one phase or small vertical slice at a time.
-   Do not implement all modules in one giant change.
-   Keep feature modules isolated.
-   Prefer official Fabric APIs and stable Minecraft APIs.
-   Use Mixins only when there is no suitable supported API or event
    hook. Document every Mixin and why it is required.
-   Avoid reflection unless there is a strong compatibility reason.
-   Do not add dependencies without explaining their purpose, license
    and maintenance status.
-   Do not create placeholder features that appear complete but do
    nothing.
-   Do not silently weaken client-only requirements.
-   Keep code readable and use descriptive names.
-   Separate domain logic from Minecraft UI and rendering code.
-   Add tests for calculation and transformation logic.
-   Update the README and progress checklist as work is completed.

### After each implementation step

1.  Run the relevant tests.
2.  Run the Gradle build.
3.  Fix warnings and errors introduced by the change.
4.  Launch the game when the change affects runtime behavior.
5.  Verify client-only behavior.
6.  Summarize files changed, behavior implemented, tests run and known
    limitations.
7.  Do not claim a feature works unless it has been tested at the
    appropriate level.

### Scope control

If a requirement is ambiguous: - Do not invent behavior that could move,
destroy or alter player items. - Prefer a safe preview over an
irreversible action. - Record the ambiguity and ask the project owner
when it affects product behavior. - Continue with unrelated, clearly
specified work where possible.

If a technical limitation prevents a requested behavior: - Explain the
limitation. - Offer the closest client-only alternative. - Do not add a
server dependency as a workaround without explicit approval.

------------------------------------------------------------------------

## 13. Definition of Done

A feature is complete only when: - It has a clear user-facing purpose. -
It is independently configurable where appropriate. - It uses the shared
MINEKIT UI and architecture. - It respects client-only constraints. - It
handles missing, stale and invalid data. - It has tests for its core
logic. - It has been tested in-game where applicable. - It does not
cause item loss or unintended actions. - Its documentation and known
limitations are recorded.

The overall project is ready for an initial release when: - It builds
reproducibly for Minecraft 26.3. - It launches from a Modrinth
instance. - It requires no server-side installation. - All included
modules have working settings and clear behavior. - No known critical
data-loss, duplication or crash bugs remain. - Installation,
configuration and troubleshooting instructions are documented.

------------------------------------------------------------------------

## 14. Official references

Use current official documentation as the source of truth. Dependency
versions and APIs can change.

-   Fabric home and toolchain: https://fabricmc.net/
-   Fabric development version selector: https://fabricmc.net/develop/
-   Fabric developer documentation: https://docs.fabricmc.net/
-   Fabric Loader documentation:
    https://docs.fabricmc.net/develop/loader/
-   Fabric API on Modrinth: https://modrinth.com/mod/fabric-api
-   Modrinth: https://modrinth.com/

For blueprint formats, inspect the current format specifications and
candidate parser libraries before implementation. Confirm that any
chosen library supports the required format versions and has a
compatible license.

------------------------------------------------------------------------

## 15. Initial task for the coding agent

Start with **Phase 0 only**.

Do not begin implementing inventory sorting, crafting, storage or other
product features yet.

First: 1. Inspect the repository. 2. Verify the current official Fabric
toolchain for Minecraft 26.3. 3. Generate or adapt the minimal Fabric
client project. 4. Configure client-only metadata and the client
entrypoint. 5. Add a minimal MINEKIT initialization log message. 6.
Build the JAR. 7. Provide exact instructions for installing the JAR into
a Modrinth App instance. 8. Verify that the game launches and that no
server-side component is required. 9. Report the exact versions selected
and any compatibility risks.

Only after Phase 0 is complete should the agent propose Phase 1.
