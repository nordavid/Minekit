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
  writing them takes 0.9 to 1.6 ms per frame. In the same scene the game ran at 82 fps with the ghost and 100 without
  it; with Sodium, Iris and Mod Menu loaded at 36 and 39 fps.
- With Sodium and Iris (shaders off) the ghost looks the same.
