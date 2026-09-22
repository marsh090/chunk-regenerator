package com.zanon.chunkregenerator.registry;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.block.entity.ChunkRegeneratorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChunkRegeneratorMod.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChunkRegeneratorBlockEntity>> CHUNK_REGENERATOR =
            BLOCK_ENTITIES.register("chunk_regenerator", () -> new BlockEntityType<>(
                    ChunkRegeneratorBlockEntity::new,
                    ModBlocks.CHUNK_REGENERATOR.get(),
                    ModBlocks.CHUNK_REMOVER.get()));

    private ModBlockEntities() {}
}
