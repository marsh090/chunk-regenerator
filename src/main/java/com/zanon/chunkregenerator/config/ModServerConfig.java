package com.zanon.chunkregenerator.config;

import java.util.List;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue ALLOW_UNCLAIMED = BUILDER
            .comment("When true, chunks that no installed claim mod marks as owned can be regenerated.")
            .define("allow_unclaimed", true);

    public static final ModConfigSpec.IntValue COOLDOWN_SECONDS = BUILDER
            .comment("Seconds that must pass before the same chunk or the same placer can regenerate again.")
            .defineInRange("cooldown_seconds", 5, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue DENY_IF_PLAYERS_PRESENT = BUILDER
            .comment("When true, regeneration is refused while a non-spectator player is standing in the target chunk.")
            .define("deny_if_players_present", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DENIED_DIMENSIONS = BUILDER
            .comment("Dimension ids where the regenerator never runs, for example minecraft:the_end.")
            .defineListAllowEmpty("denied_dimensions", List.of(), () -> "", ModServerConfig::validateDimension);

    public static final ModConfigSpec.IntValue HALO_LOAD_RADIUS = BUILDER
            .comment("Neighbor chunks loaded around the target so worldgen has context. 1 means a 3x3 area. Only the center chunk is regenerated.")
            .defineInRange("halo_load_radius", 1, 0, 4);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ModServerConfig() {}

    private static boolean validateDimension(Object value) {
        if (!(value instanceof String id) || id.isBlank()) {
            return false;
        }
        try {
            return Identifier.parse(id) != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
