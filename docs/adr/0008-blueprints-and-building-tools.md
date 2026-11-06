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
   each in memory). Legacy MCEdit `.schematic` files are recognized and refused with a hint.
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
