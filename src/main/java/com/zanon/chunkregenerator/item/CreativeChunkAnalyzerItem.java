package com.zanon.chunkregenerator.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

public class CreativeChunkAnalyzerItem extends ChunkAnalyzerItem {
    public CreativeChunkAnalyzerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("chunkregenerator.tooltip.creative_analyzer").withStyle(ChatFormatting.LIGHT_PURPLE));
        builder.accept(Component.translatable("chunkregenerator.tooltip.analyzer_hint").withStyle(ChatFormatting.DARK_GRAY));
        for (String tag : ChunkAnalyzer.tagsOf(stack)) {
            builder.accept(Component.literal(tag).withStyle(ChatFormatting.AQUA));
        }
    }
}
