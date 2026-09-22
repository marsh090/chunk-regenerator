package com.zanon.chunkregenerator.claim;

import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public interface ClaimAccess {
    ClaimProbe probe(ServerLevel level, ChunkPos pos, UUID placer);
}
