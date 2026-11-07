# ADR 0009: Ghost blocks in the world

- Status: accepted
- Date: 2026-11-07
- Context: version 0.10.0, follows [ADR 0008](0008-blueprints-and-building-tools.md)

## Context

Since 0.8.0 the whole blueprint was shown in the world as colored, see-through faces (gizmo rectangles in the average
color of each side). The owner asked whether that is the best and most common way. It is not: the established
building mods (Litematica, Schematica before it, Create's schematics, Axiom) show the structure as the real blocks,
translucent, with colored overlays for what is wrong, and with ways to show only some layers. The colored faces also
cost per frame (gizmos are rebuilt every frame) and were capped at 12 000 faces.

The "around me" view showed missing blocks in a box of 21 x 13 x 21 blocks that moves with the player, so blocks
appeared and disappeared while walking, and upper layers of tall builds were missing.

## Decision

1. **Real block models, translucent.** In Fabric's `LevelRenderEvents.COLLECT_SUBMITS` Minekit submits custom
   geometry with the game's `entityTranslucent` render type on the block atlas: the quads of each block's own model
   (positions, texture coordinates from the atlas, tint like in the game), lit evenly, with the ghost opacity as vertex
   alpha. This is the path the game uses for translucent block items; it needs no mixin and no own shader, works with
   resource packs, and draws in the normal feature pass (Sodium replaces only terrain rendering).
2. **What is drawn.** Blocks still to place in the layers being built; correct blocks are not drawn (the real block is
   there); wrong blocks get a red box (gizmo), as before; positions not checked yet are fainter. Quads facing a solid
   blueprint neighbor are left out, so a filled wall is drawn as its surface.
3. **Budget.** The vertices are written every frame. The blocks nearest the player come first, up to 24 000 quads per
   frame (about what a heavy particle scene costs); which blocks and which of their sides are hidden is computed on a
   background thread when the structure changes or the player has moved six blocks. Block models are prepared once per
   block state and cached until the models reload.
4. **Opacity** is a setting (30, 50 or 70 %), on the blueprint's Overview.
5. **"Around me"** shows the missing and wrong blocks within a sphere of ten blocks, in every direction alike.

## Alternatives considered

- **Keep the colored faces:** cheap and simple, but the blocks are not recognizable; rejected by the owner's test.
- **Own vertex buffers on the GPU, drawn in a render pass of our own:** scales best (Litematica builds chunk-like
  buffers), but needs the low-level 26.x rendering API (buffers, pipelines, uniforms), which changes from version to
  version. Kept as the next step if the per-frame budget turns out too small.
- **A fake world rendered by the chunk renderer:** what Litematica does; needs mixins into the renderer and conflicts
  with Sodium-like mods. Rejected.

## Consequences

- The ghost looks like the real blocks, including their shape and orientation.
- Large structures show their nearest part (24 000 quads, a few thousand blocks); the rest appears as the player gets
  closer.
- Shader packs (Iris) draw the ghost with their entity shaders; it is tested with Iris loaded, shaders off.

## Verification (2026-11-07)

- In the real client the ghost blocks appear where the blueprint is placed, with the blocks' textures (stone,
  planks, cobblestone, slime, a dispenser, glass), turned like the placement; switching to "around me" stops them.
- A 40 x 20 x 40 checkerboard of stone and glass (every block shows all its sides) fills the budget of 24 000 quads;
  writing them takes 0.9 to 1.6 ms per frame. In the same scene, three runs gave 82 fps with the ghost and 100
  without it, 36 and 39 (with Sodium, Iris and Mod Menu), and 28 and 39: between 8 and 28 % fewer frames in this
  worst case. Ordinary builds hide most sides behind their neighbors and stay far below the budget.
- With Sodium and Iris (shaders off) the ghost looks the same.

## Addendum (2026-11-07, version 0.10.1)

The first manual test found flicker where a blueprint stands on the ground: the bottom of the lowest ghost blocks and
the top of the real ground lie in one plane, and the depth test picks one or the other from frame to frame
(z-fighting). Two measures:

1. Like the game's own cull faces, ghost sides that touch a real solid block (`isSolidRender`) are left out. The world
   is read for a slice of 2048 selected blocks per tick, so the whole selection is current within a few ticks and
   blocks placed or broken later are noticed.
2. Each ghost block is drawn 0.4 % smaller than its cell, around its center, so two ghost blocks (glass next to stone)
   or a ghost and a real block that is not solid never share a plane either.
