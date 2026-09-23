package com.zanon.chunkregenerator.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class ChunkDevourerBlockItem extends BlockItem {
    public ChunkDevourerBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("chunkregenerator.tooltip.devourer").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("chunkregenerator.tooltip.devourer_feed").withStyle(ChatFormatting.DARK_GRAY));
    }
}
