# ADR 0008: Blueprints and building tools

- **Status:** Accepted
- **Date:** 2026-11-06
- **Phase:** 6 (blueprint and building tools)

## Context

PROJECT_PLAN.md §6.5 (B5/B6) asks for importing Litematica (`.litematic`) and Sponge (`.schem`) files, a preview,
a ghost structure in the world that can be moved, turned and mirrored, a comparison with the loaded world, progress,
material lists and measuring tools, without ever placing blocks. It asks to investigate parser libraries first, to
treat files as untrusted input, and to parse large files off the render thread.

## Format investigation and parser selection

- **Sponge schematic** (official specification, versions 2 and 3): gzip-compressed NBT. Version 3 nests everything in
  a compound `Schematic`; versions 1 and 2 use the root. Width, Height and Length are unsigned shorts. Blocks are a
  palette (`"minecraft:oak_stairs[facing=north]"` to index) and a byte array of varints, indexed
  `x + z * Width + y * Width * Length`. Version 3 keeps both under `Blocks` (`Palette`, `Data`); its change log
  mentions `BlockPalette`, its text says `Palette`, so both are accepted. Version 2 uses `Palette` and `BlockData`.
- **Litematica** (format facts taken from the mod's source, LGPL-3.0; no code copied): gzip-compressed NBT with
  `Metadata` (Name, Author, ...) and `Regions`. Each region has `Position` and `Size` (x, y, z; a negative size extends
  from the position towards smaller coordinates), a `BlockStatePalette` list and `BlockStates`, a long array with
  `max(2, ceil(log2(palette size)))` bits per value, packed tightly across long boundaries from the least significant
  bit, indexed `y * sizeX * sizeZ + z * sizeX + x`.
- **Block states in Minecraft 26.x** are stored as `{id, properties}` since data version 5006 (before: `{Name,
  Properties}`). Files from either era must be read.
- **Libraries considered:** EngineHub's lin-bus (GPL-3.0, would make Minekit GPL), Querz NBT (license not confirmed),
  BitBuf nbt (MIT), and Litematica itself (LGPL-3.0, a whole mod). All of them would add a dependency only to read
  NBT, which Minecraft already reads, with a memory quota and a nesting limit.

**Decision:** Minekit reads the NBT with Minecraft's own `NbtIo` and `NbtAccounter`, and has its own small readers for
the two formats (`BlueprintReader`, about 300 lines), written from the specifications above and unit tested with
files the tests build, including values spanning two longs, multi-byte varints, negative region sizes, both field
spellings and a set of broken files. No third-party code, no license question.

## Decisions

1. **Untrusted files.** Before reading: extension and file size. While reading: Minecraft's NBT accounter limits the
   decompressed size and the nesting depth (zip bombs, deep nesting). After reading: every size against the position
   limit, palette sizes, every palette index and the exact data length. Every failure becomes a reason the player can
   read ("the block data is incomplete", "16 000 000 positions, the limit is ..."), never a crash. Limits are
   configurable in `blueprints.json` (`maxFileMegabytes`, default 32; `maxPositions`, default 16 million, two bytes
   each in memory). Legacy MCEdit `.schematic` files are recognized and refused with a hint (superseded in 0.7.1:
   they are read; see the addendum).
2. **Files live in `config/minekit/blueprints/`.** Minecraft has no file dialog, so files are dropped onto the
   blueprint screen (the way resource packs are added) or put into that folder ("Open folder"). A dropped file is
   copied; the original is never changed, and a name that exists gets " (2)".
3. **Parsing happens on a background thread**; only the small palette is resolved on the client thread. Files saved
   by an older version run through Minecraft's data fixer (so `grass` becomes `short_grass`). Block names this version
   does not know are listed, never guessed.
4. **Placement** is a corner, a quarter-turn rotation and a mirror (`x` flipped before turning, like Minecraft's
   `FRONT_BACK`), per blueprint and per world (world ids as for the storage index). Block states are turned and
   mirrored with Minecraft's own `rotate` and `mirror`.
5. **World comparison is honest.** Only chunks the client has loaded are read (`getChunk(..., false)`; note that
   `ClientLevel.hasChunk` always says yes). A position is *correct* (same block, same orientation properties: facing,
   axis, half, type, rotation, face, orientation, part, hinge), *wrong* (another block, or turned another way),
   *missing* (air or something replaceable like grass or water), *not loaded*, or *unknown block*. Positions start
   not loaded and only change when looked at. A slice of 4096 positions is checked per tick, round and round; around
   the player the world is read every tick so the guide reacts at once. Air in the blueprint is not checked.
6. **The guide in the world uses Minecraft's gizmos** (added during the client tick, drawn by the level renderer):
   the box, the layers being built, missing blocks as translucent teal boxes and wrong ones in red within 10 blocks of
   the player (at most 1500), and the next block (the nearest open one of the lowest unfinished layer) in amber with
   its name. Textured ghost blocks would need a custom render pass for every block; boxes are cheap, honest and
   readable, and large structures cost no more than small ones.
7. **The blueprint screen** has three tabs. *Overview*: placement (move by 1 or 10 with Shift, turn, mirror, move here,
   remove), show in the world, progress and the next block. *Layers*: one layer from above, drawn as a texture with one
   pixel per block in its map color and tinted by status, north up as placed; the build range (from/up to this layer)
   limits progress, materials and the guide. *Materials*: per item what is still to place, what the player carries and
   what storage holds; what is missing can become resource goals.
8. **Measuring** marks two blocks (Quick Action or key) and shows the box, its size, volume and the distance, in the
   world and as a message.
9. **No automatic placement.** Minekit never places, breaks or changes a block; the module only reads.

## Consequences

- Block entities (chest contents, sign text), entities and biomes in the files are ignored.
- Properties outside the orientation list (waterlogged, powered, fence connections) are not compared.
- The comparison does not report blocks where the blueprint has air ("extra" blocks).
- Modded blocks that do not implement `rotate`/`mirror` keep their orientation when the placement turns.
- Each file is loaded completely into memory: 16 million positions take 32 MB.

## Addendum (2026-11-07, version 0.7.1)

The first manual test used files from a schematic download site. Its `.litematic` files did not load, and the error
notification was cut off. Findings and decisions:

1. **Plain NBT.** The site serves `.litematic` and `.schematic` files as uncompressed NBT. Litematica itself always
   compresses, but nothing in the format requires it. Minekit now looks at the first two bytes: the gzip magic number
   means compressed, a compound tag means plain NBT, anything else is "not a blueprint". A gzip stream that breaks off
   is reported as damaged. The memory quota and nesting limit apply to both.
2. **Nested bodies.** The same site nests MCEdit and Sponge v2 data in a compound named `Schematic`, which only Sponge
   v3 specifies. The reader now looks for the content there for every format and decides by the fields it finds
   (`Blocks` as a byte array or `Materials`: MCEdit; `Version`, `Palette`, `BlockData` or a `Blocks` compound:
   Sponge).
3. **MCEdit `.schematic` is read.** Files from Minecraft 1.12 and older store numeric block ids and four-bit data
   values (`Blocks`, `Data`, optional `AddBlocks` for ids above 255). Minecraft keeps the table it uses to upgrade old
   worlds (`BlockStateData`, id << 4 | data to a 1.13 block state), so Minekit translates with it and then runs the
   result through the data fixer from data version 1452, like any old file. Ids above 255 are modded and stay unknown
   (`legacy_<id>_<data>`). Only `Materials` "Alpha" (Java Edition) is accepted; "Pocket" and "Classic" are refused
   with the reason. Not translated: tile entities, entities, and the few blocks the table maps to a state that
   depends on neighbors (such as fence connections), which are compared only by block and orientation anyway.
4. **Readable errors.** The module keeps the reason of the last failure; the blueprint screen shows the file name and
   the full reason, and the reason is logged at WARN. Warning and error cards wrap to six lines instead of three.
5. **A 3D view of the whole structure.** The layer view showed one layer at a time, which made it hard to see what a
   blueprint is. The *Layers* tab now switches between *Plan* (the layer view) and *3D*: the structure seen from one
   of four corners at a 2:1 isometric angle, cut at a chosen layer so the inside can be seen, in the same colors as
   the plan, including build status. It is painted in software into one texture (`IsometricView`, pure and unit
   tested; `StructureTexture` uploads it): cubes are drawn from far to near along the viewing direction (`u + y + v`),
   with three shades for the visible faces and edges on larger cubes; a cube whose three visible faces are covered by
   opaque neighbors is skipped. Blocks without a map color (glass, torches) are drawn see-through. The texture is as
   large as the box on the screen in real pixels (at most 2048 x 2048), painted on a background thread, and keeps
   the cell drawn at each pixel, so the block under the mouse is shown like in the plan. Rejected: rendering real
   block models in the screen (much more code against fast-changing rendering APIs, and slow for 100 000 blocks) and
   a ghost of every block in the world (thousands of gizmo boxes per tick; the guide around the player stays as it
   is).

## Addendum (2026-11-07, version 0.8.0)

The 0.7.1 addendum rejected a ghost of every block in the world as too expensive. The owner asked for exactly that
("the view in the world"), so it was measured instead of assumed:

1. **The whole structure in the world** is the default world view; *around me* (the 0.7 guide) stays as an option
   (`wholeStructure` in `blueprints.json`, a toggle on the Overview tab and a Quick Action). Every block still to place
   is drawn as see-through faces in its map color, shaded per side like Minecraft's block lighting (top 1.0, north and
   south 0.8, east and west 0.6, bottom 0.5); wrong blocks get red faces just outside the real block; correct blocks
   are not drawn, so the built part looks like itself. Positions not checked yet are fainter.
2. **Only the outside is drawn.** `GhostShell` (pure, unit tested) lists the faces of the layers being built that do
   not touch a solid block of those layers; see-through blocks (no map color) hide nothing. It runs on a background
   thread when the blueprint, placement or layer range changes. The layer range therefore also cuts the structure
   open in the world.
3. **Gizmos, with a cap.** Faces are emitted as gizmo rectangles in the client tick, depth-tested, so real blocks hide
   them. At most 12 000 faces are drawn, the nearest to the player; the selection is renewed in the background when
   the player has moved six blocks. In the gametest a 40 x 20 x 40 stone and glass checkerboard (96 000 visible faces)
   drew 11 994 faces in 0.6 ms per tick. Rendering a vertex buffer through Fabric's level render events would scale
   further, but needs the fast-changing 26.x render APIs; gizmos were enough for the measured sizes.
4. **The plan is centered** in its box like the 3D view.
