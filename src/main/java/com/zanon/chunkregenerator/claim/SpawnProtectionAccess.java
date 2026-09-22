package com.zanon.chunkregenerator.claim;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelData;

public final class SpawnProtectionAccess {
    private SpawnProtectionAccess() {}

    public static boolean denies(ServerLevel level, ChunkPos pos, UUID placer) {
        BlockPos sample = new BlockPos(pos.getMinBlockX() + 8, level.getSeaLevel(), pos.getMinBlockZ() + 8);
        ServerPlayer online = level.getServer().getPlayerList().getPlayer(placer);
        if (online != null) {
            return level.getServer().isUnderSpawnProtection(level, sample, online);
        }
        if (!(level.getServer() instanceof DedicatedServer dedicated) || dedicated.spawnProtectionRadius() <= 0) {
            return false;
        }
        LevelData.RespawnData respawn = level.getRespawnData();
        if (level.dimension() != respawn.dimension()) {
            return false;
        }
        int distance = Math.max(Math.abs(sample.getX() - respawn.pos().getX()), Math.abs(sample.getZ() - respawn.pos().getZ()));
        return distance <= dedicated.spawnProtectionRadius();
    }
}
