package com.zanon.chunkregenerator.regen;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.block.entity.ChunkRegeneratorBlockEntity;
import com.zanon.chunkregenerator.claim.CompositeClaimAccess;
import com.zanon.chunkregenerator.config.ModServerConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = ChunkRegeneratorMod.MOD_ID)
public final class ChunkRegenService {
    private static final ArrayDeque<ChunkRegenTask> QUEUE = new ArrayDeque<>();
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();
    private static ChunkRegenTask active;

    private ChunkRegenService() {}

    public static RegenResult tryActivate(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChunkRegeneratorBlockEntity blockEntity)) {
            return RegenResult.DENIED_NO_PLACER;
        }
        UUID placer = blockEntity.getPlacer();
        if (placer == null) {
            return RegenResult.DENIED_NO_PLACER;
        }

        ChunkPos chunk = ChunkPos.containing(pos);
        RegenResult denial = queue(level, chunk, placer);
        if (denial != RegenResult.STARTED) {
            return denial;
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 24, 0.4D, 0.4D, 0.4D, 0.05D);
        return RegenResult.STARTED;
    }

    public static RegenResult queue(ServerLevel level, ChunkPos chunk, UUID placer) {
        return queue(level, chunk, placer, null);
    }

    public static RegenResult queue(ServerLevel level, ChunkPos chunk, UUID placer, @Nullable UUID ignoreOccupant) {
        return queue(level, chunk, placer, ignoreOccupant, true);
    }

    public static RegenResult queue(ServerLevel level, ChunkPos chunk, UUID placer, @Nullable UUID ignoreOccupant, boolean applyCooldown) {
        RegenResult denial = authorize(level, chunk, placer, ignoreOccupant, applyCooldown);
        if (denial != null) {
            return denial;
        }
        QUEUE.add(new ChunkRegenTask(level, chunk, ModServerConfig.HALO_LOAD_RADIUS.get()));
        ChunkRegeneratorMod.LOGGER.info("Queued chunk regeneration at {} in {}", chunk, level.dimension().identifier());
        return RegenResult.STARTED;
    }

    public static RegenResult authorize(ServerLevel level, ChunkPos chunk, UUID placer) {
        return authorize(level, chunk, placer, null);
    }

    public static RegenResult authorize(ServerLevel level, ChunkPos chunk, UUID placer, @Nullable UUID ignoreOccupant) {
        return authorize(level, chunk, placer, ignoreOccupant, true);
    }

    public static RegenResult authorize(ServerLevel level, ChunkPos chunk, UUID placer, @Nullable UUID ignoreOccupant, boolean applyCooldown) {
        RegenResult denial = validate(level, chunk, placer, ignoreOccupant, applyCooldown);
        if (denial == null && applyCooldown) {
            rememberCooldown(level, chunk, placer);
        }
        return denial;
    }

    public static RegenResult validate(ServerLevel level, ChunkPos chunk, UUID placer) {
        return validate(level, chunk, placer, null);
    }

    public static RegenResult validate(ServerLevel level, ChunkPos chunk, UUID placer, @Nullable UUID ignoreOccupant) {
        return validate(level, chunk, placer, ignoreOccupant, true);
    }

    public static RegenResult validate(ServerLevel level, ChunkPos chunk, UUID placer, @Nullable UUID ignoreOccupant, boolean applyCooldown) {
        String dimension = level.dimension().identifier().toString();
        for (String denied : ModServerConfig.DENIED_DIMENSIONS.get()) {
            if (Identifier.parse(denied).toString().equals(dimension)) {
                return RegenResult.DENIED_DIMENSION;
            }
        }
        if (applyCooldown && onCooldown(level, chunk, placer)) {
            return RegenResult.DENIED_COOLDOWN;
        }
        if (ModServerConfig.DENY_IF_PLAYERS_PRESENT.get() && playersPresent(level, chunk, ignoreOccupant)) {
            return RegenResult.DENIED_PLAYERS;
        }
        String claimDenial = CompositeClaimAccess.denialKey(level, chunk, placer);
        if (claimDenial != null) {
            return switch (claimDenial) {
                case "chunkregenerator.message.denied_spawn" -> RegenResult.DENIED_SPAWN;
                case "chunkregenerator.message.denied_unclaimed" -> RegenResult.DENIED_UNCLAIMED;
                default -> RegenResult.DENIED_CLAIM;
            };
        }
        return null;
    }

    public static void playDenied(ServerLevel level, BlockPos pos, RegenResult result) {
        level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
        level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 8, 0.2D, 0.2D, 0.2D, 0.01D);
        if (!(level.getBlockEntity(pos) instanceof ChunkRegeneratorBlockEntity blockEntity) || blockEntity.getPlacer() == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(blockEntity.getPlacer());
        if (player != null && result.messageKey() != null) {
            player.sendOverlayMessage(Component.translatable(result.messageKey()));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (active != null) {
            if (active.isFinished()) {
                active = null;
            } else {
                return;
            }
        }
        ChunkRegenTask next = QUEUE.poll();
        if (next != null) {
            active = next;
            next.start();
        }
    }

    private static boolean playersPresent(ServerLevel level, ChunkPos chunk, @Nullable UUID ignoreOccupant) {
        for (Player player : level.players()) {
            if (player.isSpectator() || player.getUUID().equals(ignoreOccupant)) {
                continue;
            }
            if (ChunkPos.containing(player.blockPosition()).equals(chunk)) {
                return true;
            }
        }
        return false;
    }

    private static boolean onCooldown(ServerLevel level, ChunkPos chunk, UUID placer) {
        int seconds = ModServerConfig.COOLDOWN_SECONDS.get();
        if (seconds <= 0) {
            return false;
        }
        long now = level.getGameTime();
        long wait = seconds * 20L;
        Long chunkAt = COOLDOWNS.get(chunkKey(level, chunk));
        Long placerAt = COOLDOWNS.get(placerKey(level, placer));
        return (chunkAt != null && now - chunkAt < wait) || (placerAt != null && now - placerAt < wait);
    }

    private static void rememberCooldown(ServerLevel level, ChunkPos chunk, UUID placer) {
        long now = level.getGameTime();
        COOLDOWNS.put(chunkKey(level, chunk), now);
        COOLDOWNS.put(placerKey(level, placer), now);
    }

    private static String chunkKey(ServerLevel level, ChunkPos chunk) {
        return level.dimension().identifier() + "@" + chunk.x() + "," + chunk.z();
    }

    private static String placerKey(ServerLevel level, UUID placer) {
        return level.dimension().identifier() + "#" + placer;
    }
}
