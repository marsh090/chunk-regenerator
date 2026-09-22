package com.zanon.chunkregenerator.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.zanon.chunkregenerator.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ChunkAnalyzer {
    public static final List<String> DEFAULT_TAGS = List.of(
            "minecraft:coal_ores",
            "minecraft:copper_ores",
            "minecraft:iron_ores",
            "minecraft:gold_ores",
            "minecraft:redstone_ores",
            "minecraft:lapis_ores",
            "minecraft:diamond_ores",
            "minecraft:emerald_ores");

    private ChunkAnalyzer() {}

    public static int energyCost(int occupiedLayers) {
        if (occupiedLayers <= 0) {
            return 0;
        }
        return ((occupiedLayers + ChunkAnalyzerItem.LAYERS_PER_STEP - 1) / ChunkAnalyzerItem.LAYERS_PER_STEP) * ChunkAnalyzerItem.FE_PER_STEP;
    }

    public static List<String> tagsOf(ItemStack stack) {
        AnalyzerTags stored = stack.get(ModDataComponents.ANALYZER_TAGS.get());
        return stored == null ? DEFAULT_TAGS : stored.tags();
    }

    public static List<String> sanitize(List<String> raw) {
        List<String> clean = new ArrayList<>();
        for (String entry : raw) {
            if (clean.size() >= 16) {
                break;
            }
            String id = normalize(entry);
            if (id != null && !clean.contains(id)) {
                clean.add(id);
            }
        }
        return List.copyOf(clean);
    }

    public static @Nullable Report tryAnalyze(ServerLevel level, ChunkPos chunkPos, ItemStack stack, @Nullable ServerPlayer player) {
        List<String> tags = tagsOf(stack);
        if (tags.isEmpty()) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("chunkregenerator.message.analyzer_no_tags"));
            }
            return null;
        }
        Report report = scan(level, chunkPos, tags);
        boolean free = stack.getItem() instanceof CreativeChunkAnalyzerItem;
        int cost = free ? 0 : energyCost(report.occupiedLayers());
        int energy = Math.max(0, stack.getOrDefault(ModDataComponents.ENERGY.get(), 0));
        if (!free && energy < cost) {
            if (player != null) {
                player.sendOverlayMessage(Component.translatable("chunkregenerator.message.analyzer_energy", cost, energy));
            }
            return null;
        }
        if (!free) {
            stack.set(ModDataComponents.ENERGY.get(), energy - cost);
        }
        if (player != null) {
            if (free) {
                player.sendSystemMessage(Component.translatable(
                        "chunkregenerator.message.analyzer_header_free",
                        chunkPos.x(),
                        chunkPos.z(),
                        report.occupiedLayers(),
                        report.nonAir()));
            } else {
                player.sendSystemMessage(Component.translatable(
                        "chunkregenerator.message.analyzer_header",
                        chunkPos.x(),
                        chunkPos.z(),
                        report.occupiedLayers(),
                        report.nonAir(),
                        cost));
            }
            for (Line line : report.lines()) {
                player.sendSystemMessage(Component.translatable(
                        "chunkregenerator.message.analyzer_line",
                        line.tag(),
                        String.format(Locale.ROOT, "%.1f", line.percent()),
                        line.count()));
            }
        }
        return report;
    }

    public static Report scan(ServerLevel level, ChunkPos chunkPos, List<String> tagIds) {
        List<Target> targets = new ArrayList<>();
        for (String raw : tagIds) {
            String id = normalize(raw);
            if (id == null) {
                continue;
            }
            Identifier parsed = Identifier.parse(id);
            targets.add(new Target(id, TagKey.create(Registries.BLOCK, parsed), BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null)));
        }
        int[] counts = new int[targets.size()];
        int nonAir = 0;
        int occupied = 0;
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinY(); y < level.getMaxY(); y++) {
            boolean layer = false;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    BlockState state = level.getBlockState(cursor.set(minX + x, y, minZ + z));
                    if (state.isAir()) {
                        continue;
                    }
                    layer = true;
                    nonAir++;
                    for (int i = 0; i < targets.size(); i++) {
                        Target target = targets.get(i);
                        if (state.is(target.tag()) || (target.block() != null && state.is(target.block()))) {
                            counts[i]++;
                        }
                    }
                }
            }
            if (layer) {
                occupied++;
            }
        }
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            double percent = nonAir == 0 ? 0.0D : 100.0D * counts[i] / nonAir;
            lines.add(new Line(targets.get(i).name(), counts[i], percent));
        }
        return new Report(occupied, nonAir, List.copyOf(lines));
    }

    private static @Nullable String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Identifier.parse(trimmed).toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record Target(String name, TagKey<Block> tag, @Nullable Block block) {}

    public record Report(int occupiedLayers, int nonAir, List<Line> lines) {
        public int count(String tag) {
            for (Line line : lines) {
                if (line.tag().equals(tag)) {
                    return line.count();
                }
            }
            return 0;
        }
    }

    public record Line(String tag, int count, double percent) {}
}
