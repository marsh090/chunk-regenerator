package com.zanon.chunkregenerator;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.zanon.chunkregenerator.config.ModServerConfig;
import com.zanon.chunkregenerator.item.ChunkAnalyzerItem;
import com.zanon.chunkregenerator.network.SetAnalyzerTagsPayload;
import com.zanon.chunkregenerator.registry.ModBlockEntities;
import com.zanon.chunkregenerator.registry.ModBlocks;
import com.zanon.chunkregenerator.registry.ModDataComponents;
import com.zanon.chunkregenerator.registry.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(ChunkRegeneratorMod.MOD_ID)
public class ChunkRegeneratorMod {
    public static final String MOD_ID = "chunkregenerator";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> CREATIVE_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chunkregenerator"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> ModItems.CHUNK_REGENERATOR.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.CHUNK_REGENERATOR.get());
                output.accept(ModItems.CHUNK_REMOVER.get());
                ItemStack analyzer = new ItemStack(ModItems.CHUNK_ANALYZER.get());
                analyzer.set(ModDataComponents.ENERGY.get(), ChunkAnalyzerItem.CAPACITY);
                output.accept(analyzer);
                output.accept(ModItems.CREATIVE_CHUNK_ANALYZER.get());
            })
            .build());

    public ChunkRegeneratorMod(IEventBus modEventBus, ModContainer modContainer) {
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(ModGameTests::register);
        modEventBus.addListener(ChunkAnalyzerItem::registerCapabilities);
        modEventBus.addListener(SetAnalyzerTagsPayload::register);
        modContainer.registerConfig(ModConfig.Type.SERVER, ModServerConfig.SPEC);
    }
}
