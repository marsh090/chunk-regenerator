package com.zanon.chunkregenerator.registry;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.block.ChunkDevourerBlock;
import com.zanon.chunkregenerator.block.ChunkRegeneratorBlock;
import com.zanon.chunkregenerator.block.ChunkRemoverBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChunkRegeneratorMod.MOD_ID);

    public static final DeferredBlock<ChunkRegeneratorBlock> CHUNK_REGENERATOR = BLOCKS.registerBlock(
            "chunk_regenerator",
            ChunkRegeneratorBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ChunkRegeneratorBlock.POWERED) ? 7 : 0));

    public static final DeferredBlock<ChunkRemoverBlock> CHUNK_REMOVER = BLOCKS.registerBlock(
            "chunk_remover",
            ChunkRemoverBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ChunkRemoverBlock.POWERED) ? 7 : 0));

    public static final DeferredBlock<ChunkDevourerBlock> CHUNK_DEVOURER = BLOCKS.registerBlock(
            "chunk_devourer",
            ChunkDevourerBlock::new,
            properties -> properties
                    .mapColor(MapColor.COLOR_RED)
                    .strength(3.5F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ChunkDevourerBlock.POWERED) ? 7 : 0));

    private ModBlocks() {}
}
