package com.zanon.chunkregenerator.network;

import java.util.List;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.item.AnalyzerTags;
import com.zanon.chunkregenerator.item.ChunkAnalyzer;
import com.zanon.chunkregenerator.item.ChunkAnalyzerItem;
import com.zanon.chunkregenerator.registry.ModDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SetAnalyzerTagsPayload(boolean mainHand, List<String> tags) implements CustomPacketPayload {
    public static final Type<SetAnalyzerTagsPayload> TYPE = new Type<>(Identifier.parse(ChunkRegeneratorMod.MOD_ID + ":set_analyzer_tags"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetAnalyzerTagsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            SetAnalyzerTagsPayload::mainHand,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(16)),
            SetAnalyzerTagsPayload::tags,
            SetAnalyzerTagsPayload::new);

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToServer(TYPE, STREAM_CODEC, SetAnalyzerTagsPayload::handle);
    }

    private static void handle(SetAnalyzerTagsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(payload.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (!(stack.getItem() instanceof ChunkAnalyzerItem)) {
                return;
            }
            List<String> clean = ChunkAnalyzer.sanitize(payload.tags());
            if (clean.size() != payload.tags().size()) {
                player.sendOverlayMessage(Component.translatable("chunkregenerator.message.analyzer_bad_tags"));
            }
            stack.set(ModDataComponents.ANALYZER_TAGS.get(), new AnalyzerTags(clean));
            player.sendOverlayMessage(Component.translatable("chunkregenerator.message.analyzer_tags_set", clean.size()));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
