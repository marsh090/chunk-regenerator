package com.zanon.chunkregenerator.item;

import java.util.List;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record AnalyzerTags(List<String> tags) {
    public static final Codec<AnalyzerTags> CODEC = Codec.STRING.listOf().xmap(AnalyzerTags::new, AnalyzerTags::tags);
    public static final StreamCodec<ByteBuf, AnalyzerTags> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
            .apply(ByteBufCodecs.list())
            .map(AnalyzerTags::new, AnalyzerTags::tags);

    public AnalyzerTags {
        tags = List.copyOf(tags);
    }
}
