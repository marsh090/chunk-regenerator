package com.zanon.chunkregenerator.claim;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zanon.chunkregenerator.config.ModServerConfig;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class CompositeClaimAccess {
    private static final List<ClaimAccess> TESTING = new ArrayList<>();

    private CompositeClaimAccess() {}

    public static void addTestingProvider(ClaimAccess access) {
        TESTING.add(access);
    }

    public static void clearTestingProviders() {
        TESTING.clear();
    }

    public static @Nullable String denialKey(ServerLevel level, ChunkPos pos, UUID placer) {
        for (ClaimAccess access : TESTING) {
            if (access.probe(level, pos, placer) == ClaimProbe.DENY) {
                return "chunkregenerator.message.denied_claim";
            }
        }
        if (SpawnProtectionAccess.denies(level, pos, placer)) {
            return "chunkregenerator.message.denied_spawn";
        }

        ClaimProbe ftb = FtbChunksClaimAccess.INSTANCE.probe(level, pos, placer);
        ClaimProbe opac = OpacClaimAccess.INSTANCE.probe(level, pos, placer);
        if (ftb == ClaimProbe.DENY || opac == ClaimProbe.DENY) {
            return "chunkregenerator.message.denied_claim";
        }
        if (ftb == ClaimProbe.ALLOW || opac == ClaimProbe.ALLOW) {
            return null;
        }
        if (!ModServerConfig.ALLOW_UNCLAIMED.getAsBoolean()) {
            return "chunkregenerator.message.denied_unclaimed";
        }
        return null;
    }
}
