# Minekit user guide

How to use every part of Minekit in the game. For installing it, see the [README](../README.md).

## Keys and the dashboard

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

## Sorting your inventory (Smart Inventory)

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

## Planning a craft (Crafting Assistant)

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

## Tracking resources (Resource Tracker)

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

## Finding items in your storage (Storage Finder)

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

## Putting items away (Smart Deposit)

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

## Quick Actions

Press `J` and type a few letters, or pick from the list. Pin the actions you use most with the star at the end of a
row (or `Ctrl`+`P`): pinned actions come first and are numbered, so `J` then `2` runs the second one. The actions you
used last follow. Hide actions you never use on the dashboard page *Quick Actions*; the search still finds them.

Some actions work with the item in your main hand: *Find held item in storage*, *Plan crafting of held item* and
*Collect more of held item* (raises its resource goal by 64, starting a session if none runs). Every saved loadout
has its own action, *Loadout: name*.

## Loadouts (Loadout Manager)

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

## Blueprints and building tools

Minekit reads Litematica (`.litematic`), Sponge schematic (`.schem`, versions 1 to 3) and MCEdit/WorldEdit schematic
(`.schematic`, Minecraft 1.12 and older) files, compressed or not, and shows them as a building guide. It never
places a block; you build.

1. Open the blueprint screen (Quick Actions → *Open blueprints*, or the dashboard page *Blueprints*). Drop a file onto
   the window, or put it into the blueprint folder (*Open folder*). Minekit copies dropped files; the original stays
   untouched. Choose a blueprint on the left. If a file cannot be read, the screen says why.
2. *Place in front of me*. Move it block by block (Shift: ten), turn it, mirror it, or move it to where you stand.
3. Build. The whole structure stands in the world as ghost blocks: every block still to place is shown as the real
   block, translucent; wrong blocks get a red box, and blocks you placed correctly simply look like themselves. The
   next block (the nearest open one in the lowest unfinished layer) is amber, with its name. *Ghost blocks: 50 %
   opaque* on the Overview switches between 30, 50 and 70 %. Switch off *Whole structure in the world* (or use Quick
   Actions → *Blueprint: whole structure or only around me*) to see only the missing (teal) and wrong blocks within
   ten blocks of you, in every direction. Limiting the layers being built (*Layers* tab), for example to the layer you
   are working on, also limits what the world shows.

The screen has three tabs:

- **Overview**: where the blueprint stands, how much is built, the next block, measuring.
- **Layers**: **3D** shows the whole structure at an angle with the game's textures and block shapes (a repeater
  flat, a slab half high); scroll over it (or use ▼ ▲) to cut away layers from the top and look inside, and turn it
  with *Turn*. **Plan** shows one layer from above, like a building plan, also with textures. Both are tinted by what
  is correct, missing or wrong once the blueprint is placed, and show the block under the mouse. The buttons
  below the arrows limit building to some layers.
- **Materials**: what is still to place, what you carry and what your storage holds; turns what is missing into
  resource goals.

Only chunks your game has loaded are compared: everything else counts as "not loaded", never as built. Old files are
updated to this version's block names (numeric ids of `.schematic` files with the table Minecraft itself uses for old
worlds); blocks it does not know, such as modded ones, are listed. Files and structures above the limits (32 MB,
16 million positions, configurable in `blueprints.json`) are refused with the reason.

**Measuring:** look at a block and use Quick Actions → *Measure: set point*, then again on the other corner. Minekit
shows the box between them, its width, height and length, the volume and the distance. Both blueprints and measuring
have shortcuts in the controls, unbound by default.

## Where your data is stored

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

## Known limitations

What Minekit cannot do, or does differently than one might expect. Most of it follows from running on the client
only.

**Everywhere**

- Minekit only knows what the server showed you, and it moves items with normal clicks, about ten per second; a full
  inventory takes a few seconds.
- After the last click Minekit waits half a second for a correction from the server. A server that corrects later
  makes Minekit report success too early; the next preview shows the real inventory.
- This version runs on Minecraft 26.3 (26.3.x) only. Every Minecraft update needs a new Minekit version.

**Sorting and the hotbar**

- Favorites are marked in the sort preview, not in the vanilla inventory.
- The "best" tool or weapon is chosen by material, not by enchantments. Mark the enchanted one as a favorite, or use a
  *this item* rule.
- Modded items without common tags may be sorted as blocks or miscellaneous.

**Crafting and resources**

- Steps that use a vanilla recipe may differ on servers with custom recipes; they are marked "vanilla default".
- Fuel for furnaces and leftovers such as empty buckets are not counted.
- The Resource Tracker cannot tell mining from picking up, or placing from throwing.

**Storage Finder and Smart Deposit**

- Containers you never opened, or that changed since (hoppers, other players), are unknown or outdated until you
  open them again. Every entry shows how old it is.
- Enchantments and names are not stored; a shulker box inside a chest counts as one item.
- Modded containers count only if they open a vanilla container menu.
- A LAN world joined from another computer gets a new index every time.
- Smart Deposit keeps the whole hotbar unless *Also empty the hotbar* is on (dashboard, *Deposit*).
- There is no Quick Action for Smart Deposit, because the palette would close the container; use `G` or the ⇩ button.

**Loadouts**

- Stacks move whole, so a position may get a few more items than saved.
- Armor cannot be put on in a chest screen; apply the loadout in your inventory afterwards.

**Blueprints**

- Compared are the block and its orientation; not other properties (waterlogged, powered, connections), not what a
  block holds (chest contents, sign text), and not entities.
- The screen's views use the game's textures, but each block as a single box: stairs look like full blocks, a fence
  like its post and arms in one box. The ghost blocks in the world use the real block models.
- The ghost blocks are evenly lit, not shaded by the world's light, and block entities (chests, signs, beds) show
  only their particle texture or nothing.
- In the world, a very large structure shows the blocks nearest to you (24 000 block faces per frame); the rest
  appears as you get closer. The 3D view does not draw structures larger than about 700 x 700 blocks across; the plan
  still works.
- Minecraft has no file dialog: drop files onto the blueprint screen or put them into the blueprint folder.
- Old `.schematic` files cannot hold blocks added after Minecraft 1.12, and files converted to it by download sites
  often lack such blocks (pistons, slime, redstone). The Overview says so; use a `.litematic` or `.schem` if there is
  one. Modded blocks in old `.schematic` files stay unknown.

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

## Troubleshooting

- **Modrinth instance crashes with "Incompatible mods" or "requires fabric-api":** Fabric API is missing or
  too old in the instance, or the instance is not on Minecraft 26.3.
- **"Duplicate mod" error:** two Minekit JARs are in the `mods` folder. Delete the older one, or use
  `installToModrinth`, which does that for you.
- **`K` or `J` does nothing:** another mod may use the same key. The dashboard's Overview lists conflicts. Rebind
  under *Controls → Key Binds → Minekit*. If `J` shows "Quick Actions is off", turn the module on in the dashboard
  settings.
- **`R` in the inventory does something else too:** recipe viewers (JEI, REI, EMI) and other sorting mods often use
  `R` as well. The dashboard's Overview lists such conflicts; give one of them another key.
- **"... stopped after an error":** a part of Minekit ran into a bug. Only that part is switched off, until the game
  restarts; everything else keeps working. The log (`logs/latest.log`) has the details; please report it with that
  part of the log at https://github.com/nordavid/Minekit/issues.
