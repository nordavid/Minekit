# Changelog

All notable changes to Minekit. Versions follow [semantic versioning](https://semver.org/); before 1.0.0, a minor
version may change behavior.

## 0.10.0 (2026-11-07)

- Blueprints stand in the world as ghost blocks: the real block models with their textures, translucent, the way
  building mods show schematics. Wrong blocks get a red box, correct ones are not drawn. The opacity is a setting
  (30, 50 or 70 %).
- "Around me" shows the missing and wrong blocks within ten blocks in every direction (a sphere); before, it was a box
  only six blocks high, so blocks came and went while walking.

## 0.9.0 (2026-11-07)

- Blueprints look like the blocks they are: the 3D view and the plan use the game's textures (also those of resource
  packs) and block shapes, tinted like in the game (grass, leaves, redstone); in the world the blocks still to place
  show the colors of their sides and their shape.
- The Overview warns that old `.schematic` files cannot hold newer blocks; converted files often lack them.
- With Mod Menu installed, Minekit's settings button opens the dashboard.
- Tested together with Sodium, Iris and Mod Menu: everything works as without them.
- The user guide lists the known limitations in one place.
- `-Pminekit.extraMods=<folder>` adds other mods to the development runs for compatibility tests.

## 0.8.1 (2026-11-07)

Phase 7, first part: robustness and small screens.

- A bug in one part of Minekit no longer crashes the game: that part stops for the session, the others keep running,
  and a notification says so. A failure in the middle of moving items stops the job and moves nothing else.
- Texts fit on small screens: list rows put their status on a second line when needed, the Smart Deposit panel shows
  whole profile descriptions and grows for its help, the Loadout panel shows its progress on two lines, the hotbar
  rule picker is wider.
- The project builds and runs its unit tests on GitHub Actions for every change; `gradlew` is executable on Linux and
  macOS.

## 0.8.0 (2026-11-07)

The first public release. Minekit runs on Minecraft Java Edition 26.3 with Fabric Loader 0.20.1+ and Fabric API
0.165.2+26.3, on the client only.

### Features

- **Dashboard and Quick Actions:** settings, modules, shortcuts and a page per feature (`K`); a searchable command
  palette with pinned, numbered and hidden actions (`J`).
- **Smart Inventory:** sorting after a preview, favorites, profiles and a hotbar layout per slot (`R`).
- **Crafting Assistant:** plans down to raw materials from your recipe book and the vanilla recipes, counts what you
  carry and what your storage holds, browsing by category, saved plans.
- **Resource Tracker:** sessions with goals per world, split into collected, used and unclear.
- **Storage Finder:** remembers what was in the containers you opened, per world and server.
- **Smart Deposit:** puts items into the open container after a preview; hotbar and favorites stay (`G`).
- **Loadout Manager:** saves and restores where your items go, from your inventory or from a container.
- **Blueprints and building tools:** `.litematic`, `.schem` and `.schematic` files (compressed or not), the whole
  structure in the world or the blocks around you, progress, a 3D view and a layer plan, materials, measuring.

### Changes since 0.7.1

- The whole blueprint is shown in the world: blocks still to place see-through in their own color, wrong blocks in
  red. The previous view (only the blocks around you) is a toggle away.
- The layer plan is centered in its box.

### Changes in 0.7.1

- `.litematic` files saved without compression (as some download sites serve them) load.
- MCEdit `.schematic` files (Minecraft 1.12 and older) load; their numeric block ids are translated.
- The blueprint screen shows why a file could not be loaded; error notifications are no longer cut off.
- The *Layers* tab has a 3D view of the whole structure, cut at any layer and turnable.
