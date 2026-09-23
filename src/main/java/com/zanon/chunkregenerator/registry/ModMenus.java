package com.zanon.chunkregenerator.registry;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.inventory.ChunkDevourerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ChunkRegeneratorMod.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ChunkDevourerMenu>> CHUNK_DEVOURER = MENUS.register(
            "chunk_devourer",
            () -> new MenuType<>(ChunkDevourerMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {}
}
