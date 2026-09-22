package com.zanon.chunkregenerator.registry;

import com.mojang.serialization.Codec;
import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.item.AnalyzerTags;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ChunkRegeneratorMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY = COMPONENTS.registerComponentType(
            "energy",
            builder -> builder.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AnalyzerTags>> ANALYZER_TAGS = COMPONENTS.registerComponentType(
            "analyzer_tags",
            builder -> builder.persistent(AnalyzerTags.CODEC).networkSynchronized(AnalyzerTags.STREAM_CODEC));

    private ModDataComponents() {}
}
