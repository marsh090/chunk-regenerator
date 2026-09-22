# Chunk Regenerator

Craftable block for NeoForge 26.1.2. Place it, power it with redstone, and the chunk it sits in is regenerated from the current world seed, datapacks, and mods. The block is consumed. It does not explode.

Regeneration means "as if this chunk had never been explored" under the world's current generator. It is not a snapshot of how the chunk looked the first time it was generated. Neighbor chunks are loaded for worldgen context and are not wiped, so a seam is still possible where a feature crossed the border.

## Use

1. Craft the Chunk Regenerator (the recipe is a datapack file, so servers can replace it).
2. Place it in a chunk you are allowed to change.
3. Give it a redstone signal. A rising edge starts the regeneration.
4. The block disappears and the chunk is rebuilt, including ores and other population features.

Permission follows the player who placed the block, not the player who flips the lever.

## Chunk Remover

Creative-tab block, no crafting recipe. Place it and give it a redstone signal. Every block in that chunk becomes air, except bedrock. The remover is consumed. It uses the same claim, occupancy, dimension, and cooldown checks, and it only runs while the placer is in creative mode.

## Chunk Analyzer

Craftable item. It stores up to 100,000 FE and can be charged by any Forge Energy charger. Right-click a block to scan that chunk, or right-click the air to scan the chunk you are standing in. The chat lists each configured block or block tag as a percentage of the non-air blocks. A block id such as `minecraft:oak_log` counts that block. A tag such as `minecraft:logs` counts every block in the tag. A block that matches more than one entry is counted in each.

The scan costs 1,000 FE for every 100 Y-levels that contain at least one non-air block, rounded up. Empty layers are free. The creative tab copy is fully charged; a crafted one starts empty.

Shift-right-click opens a text field. Enter comma-separated block tag ids, such as `minecraft:iron_ores` or `c:ores/copper`. A fresh analyzer searches the vanilla ore tags until you change them.

Recipe: iron ingots, redstone, and a compass. The file is `data/chunkregenerator/recipe/chunk_analyzer.json`.

Shift-right-click opens the tag menu. Each row is one block or block tag. The ore buttons toggle the vanilla ore tags, and the text field adds any other id, such as `minecraft:oak_log` or `c:ores/copper`. Invalid ids turn red and are dropped when you save.

## Creative Chunk Analyzer

Creative-tab item, no recipe. It has the enchantment glint, does not store or spend FE, and only scans while you are in creative mode. Shift-right-click uses the same tag menu.

## Commands

Operators (permission level 2) can run these. They use the same claim, spawn-protection, and dimension rules as the blocks, and they do not use the cooldown. The player who runs the command does not count as "standing in the chunk"; another player still blocks it.

- `/chunk destroy` clears the chunk you are standing in, except bedrock. `/chunkregenerator destroy` is the same command.
- `/chunk rebuild` regenerates that chunk from the current seed.
- Add chunk coordinates to target another chunk: `/chunk destroy <chunkX> <chunkZ>` and `/chunk rebuild <chunkX> <chunkZ>`.

## Claims

If FTB Chunks or Open Parties and Claims is installed, a foreign claim refuses the block and leaves it in place. Your own claim, or your FTB team / OPAC party, is allowed. Unclaimed chunks are allowed unless `allow_unclaimed` is turned off. Vanilla spawn protection also blocks the block unless the placer is an operator.

## Server config

`chunkregenerator-server.toml` in the world serverconfig folder:

- `allow_unclaimed` (default true)
- `cooldown_seconds` (default 5)
- `deny_if_players_present` (default true)
- `denied_dimensions` (default empty)
- `halo_load_radius` (default 1, a 3x3 load around the target)

## Recipe override

Replace `data/chunkregenerator/recipe/chunk_regenerator.json` with a datapack of the same path, then run `/reload`.
