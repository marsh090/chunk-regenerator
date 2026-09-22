package com.zanon.chunkregenerator.regen;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;

import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForgeMod;

/**
 * Loads a neighbor halo, drops the saved center chunk, then runs that chunk through full world generation again.
 */
public final class ChunkRegenTask {
    private final ServerLevel level;
    private final ChunkPos center;
    private final int halo;
    private final AtomicBoolean finished = new AtomicBoolean();

    public ChunkRegenTask(ServerLevel level, ChunkPos center, int halo) {
        this.level = level;
        this.center = center;
        this.halo = halo;
    }

    public boolean isFinished() {
        return this.finished.get();
    }

    public void start() {
        try {
            this.loadHalo();
            this.discardEntities();
            this.evictCenter();
            this.purgeSavedChunk();
            this.generate();
        } catch (RuntimeException exception) {
            ChunkRegeneratorMod.LOGGER.error("Chunk regeneration failed at {}", this.center, exception);
            this.finished.set(true);
        }
    }

    private void loadHalo() {
        // FULL generation acquires every holder out to the pyramid radius (structure starts are 8 chunks away).
        int radius = Math.max(this.halo, ChunkPyramid.GENERATION_PYRAMID.getStepTo(ChunkStatus.FULL).getAccumulatedRadiusOf(ChunkStatus.EMPTY));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                this.level.getChunk(this.center.x() + dx, this.center.z() + dz);
            }
        }
    }

    private void discardEntities() {
        int minX = this.center.getMinBlockX();
        int minZ = this.center.getMinBlockZ();
        AABB box = new AABB(minX, this.level.getMinY(), minZ, minX + 16, this.level.getMaxY(), minZ + 16);
        for (Entity entity : this.level.getEntitiesOfClass(Entity.class, box, entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }

    private void evictCenter() {
        long key = this.center.pack();
        ChunkMap chunkMap = this.level.getChunkSource().chunkMap;
        chunkMap.pendingUnloads.remove(key);
        chunkMap.updatingChunkMap.remove(key);
        chunkMap.visibleChunkMap.remove(key);
        chunkMap.chunkTypeCache.remove(key);
        chunkMap.chunksToEagerlySave.remove(key);
        this.level.getChunkSource().clearCache();
        this.clearPoi();
    }

    private void clearPoi() {
        Object poi = this.level.getChunkSource().getPoiManager();
        Method remove = null;
        for (Method method : poi.getClass().getMethods()) {
            if ("remove".equals(method.getName()) && method.getParameterCount() == 1 && method.getParameterTypes()[0] == long.class) {
                remove = method;
                break;
            }
        }
        if (remove == null) {
            return;
        }
        int minSection = SectionPos.blockToSectionCoord(this.level.getMinY());
        int maxSection = SectionPos.blockToSectionCoord(this.level.getMaxY() - 1);
        try {
            for (int sectionY = minSection; sectionY <= maxSection; sectionY++) {
                remove.invoke(poi, SectionPos.asLong(this.center.x(), sectionY, this.center.z()));
            }
        } catch (ReflectiveOperationException exception) {
            ChunkRegeneratorMod.LOGGER.warn("Could not clear POI data for {}", this.center, exception);
        }
    }

    private void purgeSavedChunk() {
        ChunkMap chunkMap = this.level.getChunkSource().chunkMap;
        chunkMap.synchronize(true).join();
        chunkMap.worker.store(this.center, IOWorker.STORE_EMPTY).join();
        chunkMap.synchronize(true).join();
    }

    private void generate() {
        ServerChunkCache cache = this.level.getChunkSource();
        ChunkMap chunkMap = cache.chunkMap;
        long key = this.center.pack();
        // A removed holder is not recreated unless its ticket level changes, so install a fresh one and
        // run the same future promotion the distance manager uses after a ticket update.
        ChunkHolder holder = chunkMap.updateChunkScheduling(key, ChunkLevel.byStatus(FullChunkStatus.FULL), null, ChunkLevel.MAX_LEVEL);
        chunkMap.promoteChunkMap();
        if (holder == null) {
            ChunkRegeneratorMod.LOGGER.error("Chunk {} was not scheduled after its saved data was removed", this.center);
            this.finished.set(true);
            return;
        }
        holder.updateHighestAllowedStatus(chunkMap);
        holder.updateFutures(chunkMap, this.level.getServer());
        chunkMap.runGenerationTasks();
        cache.addTicketWithRadius(NeoForgeMod.GENERATE_FORCED_TICKET.value(), this.center, 0);
        holder.getFullChunkFuture().whenCompleteAsync((result, throwable) -> {
            cache.removeTicketWithRadius(NeoForgeMod.GENERATE_FORCED_TICKET.value(), this.center, 0);
            LevelChunk chunk = result == null ? null : result.orElse(null);
            if (throwable != null || chunk == null) {
                ChunkRegeneratorMod.LOGGER.error("Worldgen did not finish for {}: {}", this.center, throwable == null ? result : throwable.toString());
            } else {
                ChunkRegeneratorMod.LOGGER.info("Regenerated chunk {} in {}", this.center, this.level.dimension().identifier());
            }
            this.finished.set(true);
        }, chunkMap::scheduleOnMainThreadMailbox);
    }
}
