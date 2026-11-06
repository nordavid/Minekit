# ADR 0002: Core shell architecture (modules, config, UI, HUD, in-game tests)

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 1 (core shell and UI)

## Context

Phase 1 builds the parts every later feature depends on: module lifecycle, configuration, shortcuts, dashboard,
Quick Actions, notifications and HUD. PROJECT_PLAN.md §4 asks for a small core with isolated modules and no generic
frameworks (no event bus, no DI container, no scripting).

Facts about Minecraft 26.3 and Fabric API 0.165.2 that shaped the design:

- Key mappings must be registered before Minecraft creates its `Options`, i.e. during client initialization, and
  cannot be removed later. Fabric event callbacks cannot be unregistered either.
- Screens are opened with `minecraft.gui.setScreen(...)`. Drawing goes through `GuiGraphicsExtractor` and
  `extractRenderState(...)`. Key codes are SDL scancodes exposed as `InputConstants.KEY_*`.
- The vanilla HUD is skipped while it is hidden (F1, `gui.hud.isHidden()`). Screens are drawn on top of it.
- The Fabric client gametest API (`fabric-client-gametest-api-v1`, part of Fabric API) can drive a real client:
  create worlds, press keys, type, click vanilla `Button`s and take screenshots.

## Decisions

1. **Composition root instead of a framework.** `MinekitClient` creates every service and wires it. The services
   are bundled in the `MinekitRuntime` record and passed to screens and modules explicitly. `MinekitRuntime.get()`
   exists only for code that cannot receive it, namely gametests and the dashboard shortcut, which is registered
   before the runtime exists.
2. **Module lifecycle.** `MinekitModule.initialize` runs once at startup and registers key mappings and callbacks.
   Every callback checks `ModuleContext.isEnabled()`. `onEnable`/`onDisable` only switch state, for example closing the
   module's screen. `ModuleManager` catches exceptions from all lifecycle methods. A failing module is marked `FAILED`
   and stays inactive for the session, while the rest of MINEKIT keeps working.
3. **Configuration.** One JSON file, `config/minekit/config.json`, in the Fabric config directory. It is read and
   written with Gson, which Minecraft already ships, so there is no new dependency. A hand-written codec validates each
   field. Invalid entries are dropped with a warning instead of rejecting the file. Only values the player changed are
   stored, because defaults belong to the owning code. The file carries a `schemaVersion`. The safety rules:
   - A corrupt file is moved to `config.json.corrupt-<time>`, then defaults are written.
   - A file from a newer MINEKIT is copied to `config.json.newer-schema-<time>.bak` before its first overwrite.
   - A file that cannot be read is never overwritten during that session.
   - Every write goes to a temporary file first, which then atomically replaces the original.

   No migration exists yet. The first incompatible format change bumps `ConfigCodec.SCHEMA_VERSION` and adds the step.
4. **UI toolkit.** MINEKIT uses its own small widgets (`FlatButton`, `NavButton`, `ToggleButton`, `ContentPane`) on top
   of vanilla `Screen`. Colors and spacing are shared through `MinekitTheme`. All widgets extend vanilla `Button`, so
   keyboard focus, narration and tools that look for buttons (including the gametest API) work the same as with
   vanilla buttons. Rebinding keys uses the vanilla controls screen. MINEKIT does not have its own key-binding UI.
5. **Dashboard scope.** The dashboard lists only pages backed by real functionality: Overview and Settings. Later
   phases add their pages. It remembers the last page for the session.
6. **Shortcuts.** By default, `K` opens the dashboard and `J` opens Quick Actions. Both keys are unbound in vanilla.
   All MINEKIT shortcuts live in a "MINEKIT" category. The Overview page lists MINEKIT shortcuts that share a key with
   another mapping. Vanilla also highlights such conflicts in red.
7. **HUD and notifications.** A single Fabric HUD element (`minekit:hud`) draws all MINEKIT HUD elements in order.
   Each element's `visible`, `anchor` and offsets come from the config. An element that throws is switched off for the
   session. Notifications are the first element. They are also drawn after every screen (`ScreenEvents.afterExtract`),
   so feedback from the dashboard is visible. Their display time starts when they are first drawn, so messages posted
   during startup are not lost.
8. **In-game tests.** Client gametests live in a separate `gametest` source set and test mod (`minekit_gametest`),
   which is never part of the MINEKIT JAR. They are not wired into `check` because they open a game window. They are
   run with `gradlew runClientGameTest` before each phase is signed off.
9. **Still no mixins, reflection or networking.**

## Consequences

- New modules follow one pattern: metadata, register in `initialize`, check `isEnabled()` in callbacks.
- Settings of uninstalled or renamed modules stay in the file. They are harmless, and the player keeps them if the
  module returns.
- Config writes happen on the client thread. The file is tiny. If it grows (for example storage indexes in Phase 4),
  that data goes into separate files under `config/minekit/` with their own persistence service, as the plan suggests.
- Translation keys are part of the UI contract: the gametests click buttons by their English labels.
