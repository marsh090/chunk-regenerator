package com.zanon.chunkregenerator;

import com.zanon.chunkregenerator.client.AnalyzerTagsScreen;
import com.zanon.chunkregenerator.client.ChunkDevourerScreen;
import com.zanon.chunkregenerator.item.ChunkAnalyzer;
import com.zanon.chunkregenerator.item.ChunkAnalyzerItem;

import net.neoforged.bus.api.IEventBus;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(value = ChunkRegeneratorMod.MOD_ID, dist = Dist.CLIENT)
public class ChunkRegeneratorClient {
    public ChunkRegeneratorClient(IEventBus modEventBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modEventBus.addListener(ChunkDevourerScreen::register);
        NeoForge.EVENT_BUS.addListener(ChunkRegeneratorClient::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ChunkRegeneratorClient::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(ChunkRegeneratorClient::onRightClickEmpty);
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        openTags(event);
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        openTags(event);
    }

    private static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        openTags(event);
    }

    private static void openTags(PlayerInteractEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ChunkAnalyzerItem) || !event.getEntity().isShiftKeyDown()) {
            return;
        }
        if (Minecraft.getInstance().screen instanceof AnalyzerTagsScreen) {
            return;
        }
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        Minecraft.getInstance().setScreen(new AnalyzerTagsScreen(mainHand, ChunkAnalyzer.tagsOf(stack)));
        if (event instanceof ICancellableEvent cancellable) {
            cancellable.setCanceled(true);
        }
    }
}
