<p align="center"><img src="src/main/resources/assets/minekit/icon.png" width="128" alt="Minekit icon"></p>

# Minekit

A client-side utility mod for Minecraft Java Edition 26.3 (Fabric). Minekit sorts your inventory, plans crafts, finds
items in your chests, puts things away, swaps loadouts and helps you build from blueprints.

Everything runs on your own client. Servers and other players need nothing installed, and Minekit only uses what a
player has anyway: the items it can see and the clicks it could make by hand.

> **Status:** 0.8.0, in active development. Every feature below is covered by unit tests and by automated tests in
> the real game client, in single player and on a dedicated server. See [docs/PROGRESS.md](docs/PROGRESS.md).

## Features

- **Dashboard and Quick Actions.** `K` opens a dashboard with every setting. `J` opens a searchable command palette;
  pinned actions are numbered, so `J` `2` runs the second one.
- **Smart Inventory.** Sorts your inventory after showing a preview, with favorites, profiles and a hotbar layout per
  slot (best sword in slot 1, food in slot 9, ...).
- **Crafting Assistant.** Plans any craft down to the raw materials, counts what you carry and what your chests hold,
  and lets you browse every craftable item by category.
- **Resource Tracker.** Sessions with goals ("128 iron ingots") per world, split into collected, used and unclear.
- **Storage Finder.** Remembers what was in every container you opened and tells you where an item is.
- **Smart Deposit.** Puts matching items into the open chest after a preview; your hotbar and favorites stay with you.
- **Loadout Manager.** Saves where your armor, tools and blocks go, and restores it from your inventory or a chest.
- **Blueprints and building tools.** Shows `.litematic`, `.schem` and `.schematic` files in the world as a building
  guide, with progress, a 3D view, a layer plan, a material list and a measuring tool. It never places blocks.

## Installation

You need Minecraft Java Edition **26.3**, Fabric Loader **0.20.1** or newer and Fabric API **0.165.2+26.3** or
newer.

1. Download `minekit-<version>.jar` from the [latest release](https://github.com/nordavid/Minekit/releases/latest), or
   [build it](#building-from-source) yourself.
2. In the [Modrinth App](https://modrinth.com/app), create an instance with the loader **Fabric** and the game
   version **26.3**.
3. Open the instance, choose *Add content* and install **Fabric API**.
4. Add Minekit: *Add content* → *Add from file*, or drop the JAR into the instance's `mods` folder.
5. Start the instance and press `K`.

Other launchers work the same way: a Fabric 26.3 profile with Fabric API and the Minekit JAR in `mods`. To give
Minekit to a friend, export the instance as a modpack; see the [user guide](docs/USER_GUIDE.md#share-minekit-with-a-friend).

## Getting started

| Key (default) | What it does |
|---|---|
| `K` | Opens the dashboard: modules, settings, shortcuts and a page per feature. |
| `J` | Opens Quick Actions: type to search, `Enter` to run, `1`–`9` for pinned actions. |
| `R` | Opens the sort preview, in game or in your inventory. |
| `G` | In an open chest or other container: opens the Smart Deposit preview. |

Every key can be changed under *Options → Controls → Key Binds → Minekit*. The [user guide](docs/USER_GUIDE.md)
explains each feature step by step.

## Servers and fair play

- Minekit is a client-only mod: no server plugin, no custom network packets, no commands, no mixins.
- It moves items with normal inventory clicks, about ten per second, the way you could by hand. It stops as soon as
  something unexpected happens, such as the server correcting a click, and never drops items or puts them anywhere
  you did not ask for.
- It never places or breaks blocks and does not move your character.
- It only knows what the server showed you. Chests you never opened are unknown to the Storage Finder.

Some servers forbid inventory automation or client mods in general. Check the rules of the server you play on.

## Building from source

Install JDK 25 and run:

```bash
./gradlew build
```

On Windows PowerShell use `.\gradlew.bat build`. The JAR is `build/libs/minekit-<version>.jar`. Tests, the
development client and the project layout are described in [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).

## Documentation

- [User guide](docs/USER_GUIDE.md): every feature, where your data is stored, troubleshooting
- [Development](docs/DEVELOPMENT.md): versions, build, tests, development client, project layout
- [Architecture decisions](docs/adr/): why things are built the way they are
- [Progress](docs/PROGRESS.md) and [project plan](PROJECT_PLAN.md)

## License

[MIT](LICENSE). Minekit is not an official Minecraft product and is not approved by or associated with Mojang or
Microsoft.
