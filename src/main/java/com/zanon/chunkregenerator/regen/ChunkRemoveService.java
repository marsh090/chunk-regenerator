package com.zanon.chunkregenerator.regen;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.block.entity.ChunkRegeneratorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;

/**
 * Creative tool that replaces every non-bedrock block in one chunk with air.
 */
public final class ChunkRemoveService {
    private static final int CLEAR_FLAGS = Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_ON_PLACE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private ChunkRemoveService() {}

    public static RegenResult tryActivate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChunkRegeneratorBlockEntity blockEntity)) {
            return RegenResult.DENIED_NO_PLACER;
        }
        UUID placerId = blockEntity.getPlacer();
        if (placerId == null) {
            return RegenResult.DENIED_NO_PLACER;
        }
        if (!isCreativePlacer(level, placerId)) {
            return RegenResult.DENIED_NOT_CREATIVE;
        }

        ChunkPos chunk = ChunkPos.containing(pos);
        RegenResult denial = ChunkRegenService.authorize(level, chunk, placerId);
        if (denial != null) {
            return denial;
        }

        clear(level, chunk);
        level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.6F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 32, 0.4D, 0.4D, 0.4D, 0.02D);
        ChunkRegeneratorMod.LOGGER.info("Removed non-bedrock blocks at {} in {}", chunk, level.dimension().identifier());
        return RegenResult.STARTED;
    }

    private static boolean isCreativePlacer(ServerLevel level, UUID placerId) {
        ServerPlayer listed = level.getServer().getPlayerList().getPlayer(placerId);
        if (listed != null) {
            return listed.gameMode().isCreative();
        }
        for (Player player : level.players()) {
            if (player.getUUID().equals(placerId) && player instanceof ServerPlayer serverPlayer) {
                return serverPlayer.gameMode().isCreative();
            }
        }
        return false;
    }

    public static void clear(ServerLevel level, ChunkPos chunk) {
        LevelChunk loaded = level.getChunk(chunk.x(), chunk.z());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        for (int y = level.getMinY(); y < level.getMaxY(); y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    cursor.set(minX + x, y, minZ + z);
                    BlockState state = loaded.getBlockState(cursor);
                    if (state.isAir() || state.is(Blocks.BEDROCK)) {
                        continue;
                    }
                    level.setBlock(cursor.immutable(), air, CLEAR_FLAGS);
                }
            }
        }

        AABB box = new AABB(minX, level.getMinY(), minZ, minX + 16, level.getMaxY(), minZ + 16);
        for (Entity entity : level.getEntitiesOfClass(Entity.class, box, entity -> !(entity instanceof Player))) {
            entity.discard();
        }
        refreshClient(level, chunk);
    }

    /**
     * Neighbor chunks keep the faces that used to touch this one, and the emptied column stays unlit,
     * until the client receives a fresh copy of the chunk and its light.
     * Integrated servers run light on a worker thread, so the flush has to be queued there.
     */
    public static void refreshAround(ServerLevel level, ChunkPos center) {
        refreshClient(level, center);
    }

    private static void refreshClient(ServerLevel level, ChunkPos center) {
        ChunkPos[] area = new ChunkPos[9];
        int index = 0;
        int[][] offsets = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] offset : offsets) {
            area[index++] = new ChunkPos(center.x() + offset[0], center.z() + offset[1]);
        }
        LevelLightEngine light = level.getChunkSource().getLightEngine();
        if (light instanceof ThreadedLevelLightEngine threaded) {
            for (ChunkPos pos : area) {
                threaded.propagateLightSources(pos);
            }
            finishLight(level, threaded, area, 0);
            return;
        }
        for (ChunkPos pos : area) {
            light.propagateLightSources(pos);
        }
        int guard = 0;
        while (light.hasLightWork() && guard++ < 8192) {
            light.runLightUpdates();
        }
        sendChunks(level, area);
    }

    private static void finishLight(ServerLevel level, ThreadedLevelLightEngine light, ChunkPos[] area, int attempt) {
        light.tryScheduleUpdate();
        ChunkPos center = area[0];
        light.waitForPendingTasks(center.x(), center.z()).thenRunAsync(() -> {
            if (light.hasLightWork() && attempt < 30) {
                finishLight(level, light, area, attempt + 1);
                return;
            }
            sendChunks(level, area);
        }, level.getServer());
    }

    private static void sendChunks(ServerLevel level, ChunkPos[] area) {
        LevelLightEngine light = level.getChunkSource().getLightEngine();
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (ChunkPos pos : area) {
            LevelChunk neighbor = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (neighbor == null) {
                continue;
            }
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(neighbor, light, null, null);
            for (ServerPlayer player : viewers(level, chunkMap, pos)) {
                player.connection.send(packet);
            }
        }
    }

    private static Set<ServerPlayer> viewers(ServerLevel level, ChunkMap chunkMap, ChunkPos pos) {
        Set<ServerPlayer> viewers = new HashSet<>(chunkMap.getPlayers(pos, false));
        for (Player player : level.players()) {
            if (player instanceof ServerPlayer serverPlayer) {
                ChunkPos at = ChunkPos.containing(serverPlayer.blockPosition());
                if (Math.abs(at.x() - pos.x()) <= serverPlayer.requestedViewDistance() && Math.abs(at.z() - pos.z()) <= serverPlayer.requestedViewDistance()) {
                    viewers.add(serverPlayer);
                }
            }
        }
        return viewers;
    }
}
