package com.zanon.chunkregenerator.item;

import java.util.function.Consumer;

import com.zanon.chunkregenerator.registry.ModDataComponents;
import com.zanon.chunkregenerator.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.ItemAccessEnergyHandler;

public class ChunkAnalyzerItem extends Item {
    public static final int CAPACITY = 100_000;
    public static final int FE_PER_STEP = 1_000;
    public static final int LAYERS_PER_STEP = 100;

    public ChunkAnalyzerItem(Properties properties) {
        super(properties);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(
                Capabilities.Energy.ITEM,
                (stack, access) -> new ItemAccessEnergyHandler(access, ModDataComponents.ENERGY.get(), CAPACITY),
                ModItems.CHUNK_ANALYZER.get());
    }

    @Override
    public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        return activate(context.getLevel(), context.getPlayer(), context.getItemInHand(), ChunkPos.containing(context.getClickedPos()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return activate(level, player, player.getItemInHand(hand), ChunkPos.containing(player.blockPosition()));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int energy = Math.clamp(stack.getOrDefault(ModDataComponents.ENERGY.get(), 0), 0, CAPACITY);
        return Math.round(energy * 13.0F / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x3DDCFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        int energy = Math.max(0, stack.getOrDefault(ModDataComponents.ENERGY.get(), 0));
        builder.accept(Component.translatable("chunkregenerator.tooltip.analyzer_energy", energy, CAPACITY).withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("chunkregenerator.tooltip.analyzer_cost").withStyle(ChatFormatting.DARK_GRAY));
        builder.accept(Component.translatable("chunkregenerator.tooltip.analyzer_hint").withStyle(ChatFormatting.DARK_GRAY));
        for (String tag : ChunkAnalyzer.tagsOf(stack)) {
            builder.accept(Component.literal(tag).withStyle(ChatFormatting.AQUA));
        }
    }

    private InteractionResult activate(Level level, Player player, ItemStack stack, ChunkPos chunkPos) {
        if (player != null && player.isShiftKeyDown()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof CreativeChunkAnalyzerItem && !serverPlayer.gameMode().isCreative()) {
            serverPlayer.sendOverlayMessage(Component.translatable("chunkregenerator.message.creative_analyzer_only"));
            return InteractionResult.FAIL;
        }
        ChunkAnalyzer.Report report = ChunkAnalyzer.tryAnalyze(server, chunkPos, stack, serverPlayer);
        return report == null ? InteractionResult.FAIL : InteractionResult.SUCCESS;
    }
}
