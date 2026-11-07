# Testing Minekit

Thanks for helping! This checklist walks through every feature once, with what should happen. It is also the
acceptance list before Minekit 1.0. You do not have to do everything: pick the parts you use, and tick what worked.

Found something? Open an [issue](https://github.com/nordavid/Minekit/issues/new/choose) with the bug form: it asks for
the versions, your other mods and the log. One issue per problem, with a screenshot if you can.

## Before you start

- [ ] Minecraft **26.3** with Fabric Loader **0.20.1** or newer and **Fabric API**, plus the latest
  [Minekit release](https://github.com/nordavid/Minekit/releases/latest). Installing is described in the
  [README](../README.md#installation).
- [ ] Note your other mods; testing once without them and once with them helps to tell whose fault a problem is.
- [ ] Your settings and data are in `config/minekit/` in the instance folder; copy it if you want to keep them.
- [ ] The log is `logs/latest.log` in the instance folder. Minekit's lines start with `(Minekit)`.

Test in a new single-player world first (survival for sorting, deposit and loadouts; creative is fine for blueprints),
then on a server if you play on one.

## Basics

- [ ] `K` opens the dashboard, `K` or `Esc` closes it. *Expect:* a page per feature on the left; the version at the
  top.
- [ ] *Settings*: switch a module off and on again. *Expect:* its keys and Quick Actions stop and come back; the choice
  survives a restart.
- [ ] *Settings → Change shortcuts…* or *Options → Controls → Key Binds → Minekit*: change a key. *Expect:* the new
  key works; the Overview lists keys that another mod or Minecraft also uses.
- [ ] With Mod Menu installed: Minekit's settings button in its list opens the dashboard; closing it returns to Mod Menu.
- [ ] Notifications appear in a corner and fade; the corner can be changed in *Settings*.

## Quick Actions

- [ ] `J` opens the list; typing filters it; `↑`/`↓` and `Enter` run an action; `Esc` closes.
- [ ] Pin an action with the star (or `Ctrl`+`P`). *Expect:* pinned ones come first and are numbered; `J` then `1`
  runs the first.
- [ ] Hide an action on the dashboard page *Quick Actions*. *Expect:* it is gone from the list but the search still
  finds it.
- [ ] With an item in hand: *Find held item in storage*, *Plan crafting of held item*, *Collect more of held item*.

## Sorting your inventory (`R`)

- [ ] Fill your inventory with a mix of items and press `R`. *Expect:* a preview of now and after; nothing has moved
  yet.
- [ ] Click an item to make it a favorite; switch the profile. *Expect:* the preview changes at once.
- [ ] Set hotbar rules (the boxes under the hotbar of the result): sword, pickaxe, food, *this item*, *keep empty*.
  *Expect:* the best matching item lands in each slot.
- [ ] Press *Sort*. *Expect:* the inventory opens and items move about ten per second; nothing is lost, nothing is
  dropped, armor and offhand stay.
- [ ] Press `R` or close the inventory while it sorts. *Expect:* it stops and says so.
- [ ] In creative mode. *Expect:* sorting is refused with a reason.

## Crafting Assistant

- [ ] Open it (*Crafting* page or Quick Actions). Search an item, or browse by category without searching.
- [ ] Set a quantity. *Expect:* materials to gather (with what you already carry), steps in order, notes.
- [ ] Try *1/3* (another recipe), *⇄* (another wood type), *⛏*/*⚒* (gather or craft instead).
- [ ] *Save plan* and *Add as goals*. *Expect:* the plan is listed on the page; the goals show in the resource HUD.

## Resource Tracker

- [ ] Start a session (*Resources* page or Quick Actions). Mine or pick up blocks, place some, eat.
  *Expect:* collected and used go up; changes while a screen is open count as unclear.
- [ ] Add a goal. *Expect:* its progress at the top left of the screen.
- [ ] Leave the world and join another. *Expect:* the session pauses there and continues when you come back.

## Storage Finder

- [ ] Open a few chests, a barrel, a shulker box and your ender chest by right-clicking them, then close them.
- [ ] Open the finder (*Storage* page or Quick Actions) and search an item. *Expect:* every container with it, its
  coordinates, distance, count and how old the information is.
- [ ] Take items out of a chest and open the finder again. *Expect:* the new count after the chest was closed.
- [ ] Break a chest you had opened and come back near it. *Expect:* "block is gone".

## Smart Deposit (`G`)

- [ ] Open a chest that already holds some of your items and press `G` (or the ⇩ button left of it).
  *Expect:* a panel with what would move; the slots light up; nothing has moved yet.
- [ ] Switch the profile; open the help with *?*. *Expect:* the preview changes; the help explains it.
- [ ] Press *Deposit*. *Expect:* only those items move; your hotbar and favorites stay with you.
- [ ] Press `G` during a deposit. *Expect:* it stops and tells you what moved.

## Loadouts

- [ ] Put on armor, arrange your hotbar and open the Loadout Manager; *Save inventory*.
- [ ] Mix up your inventory and *Apply*. *Expect:* everything goes back to its position.
- [ ] Next to a chest with the right items: the ⚒ button takes what the loadout still needs, and nothing else.
- [ ] Click a position (remove or lock), shift-click (take from inventory), *Match exactly*.

## Blueprints

Get a few files: your own from Litematica or WorldEdit, or downloads in `.litematic`, `.schem` and `.schematic`.

- [ ] Open the blueprint screen (*Blueprints* page or Quick Actions) and drop the files onto the window.
  *Expect:* they are copied and listed; a broken file says why on the screen.
- [ ] *Layers* tab: *3D* shows the structure with its textures; scroll to cut away layers, *Turn* turns it; *Plan*
  shows one layer from above. Hover a block. *Expect:* its name.
- [ ] *Place in front of me*; move (Shift: ten blocks), turn, mirror.
  *Expect:* in the world the blocks still to place appear as translucent ghost blocks.
- [ ] Change *Ghost blocks: … % opaque*; switch off *Whole structure in the world*. *Expect:* only the missing (teal)
  and wrong (red) blocks within ten blocks of you.
- [ ] Look at a ghost block. *Expect:* an amber frame and its name below the crosshair.
- [ ] Build a few blocks, one of them wrong or turned the wrong way. *Expect:* correct ones turn into the real block,
  the wrong one gets a red box; *Overview* shows the progress.
- [ ] *From here* / *Up to here* on the *Layers* tab. *Expect:* the world and the materials only count those layers.
- [ ] *Materials*: what is still needed, what you carry, what your chests hold; *Add … as resource goals*.
- [ ] A blueprint standing on the ground: no flickering where it touches the ground.

## Measuring

- [ ] Look at a block and run *Measure: set point*, then at another block. *Expect:* a box between them with width,
  height, length, volume and distance.

## On a server

- [ ] Join a server you play on (or a local one). Sort, deposit, apply a loadout. *Expect:* the same results as in
  single player; nothing is lost; the server does not kick you.
- [ ] Menus the server opens itself (shops, `/enderchest`-like menus) are not treated as chests.

## Other mods and screens

- [ ] With Sodium, Iris (also with a shader pack, if you use one), a minimap or a recipe viewer (JEI, REI, EMI).
  *Expect:* Minekit works the same; keys used twice show up on the dashboard's Overview.
- [ ] A large GUI scale or a small window: texts fit or wrap; nothing important is cut off.

## Robustness

- [ ] Close the game in the middle of sorting or a deposit, then start it again. *Expect:* nothing is lost.
- [ ] Damage a settings file in `config/minekit/` (for example delete half of `config.json`) and start the game.
  *Expect:* Minekit starts with defaults, keeps a copy of the damaged file and tells you.
