package com.zanon.chunkregenerator.registry;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.item.ChunkAnalyzerItem;
import com.zanon.chunkregenerator.item.CreativeChunkAnalyzerItem;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChunkRegeneratorMod.MOD_ID);

    public static final DeferredItem<BlockItem> CHUNK_REGENERATOR = ITEMS.registerSimpleBlockItem(ModBlocks.CHUNK_REGENERATOR);
    public static final DeferredItem<BlockItem> CHUNK_REMOVER = ITEMS.registerSimpleBlockItem(ModBlocks.CHUNK_REMOVER);
    public static final DeferredItem<ChunkAnalyzerItem> CHUNK_ANALYZER = ITEMS.registerItem(
            "chunk_analyzer",
            ChunkAnalyzerItem::new,
            properties -> properties.stacksTo(1));
    public static final DeferredItem<CreativeChunkAnalyzerItem> CREATIVE_CHUNK_ANALYZER = ITEMS.registerItem(
            "creative_chunk_analyzer",
            CreativeChunkAnalyzerItem::new,
            properties -> properties.stacksTo(1).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

    private ModItems() {}
}
