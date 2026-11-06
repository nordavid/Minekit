# Changelog

All notable changes to Minekit. Versions follow [semantic versioning](https://semver.org/); before 1.0.0, a minor
version may change behavior.

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
