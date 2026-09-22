package com.zanon.chunkregenerator.claim;

import java.lang.reflect.Method;
import java.util.UUID;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

/**
 * Soft integration with FTB Chunks. The API is called by reflection so this mod does not require the jar at compile time.
 * If FTB Chunks is installed and the lookup fails, the chunk is denied.
 */
public final class FtbChunksClaimAccess implements ClaimAccess {
    public static final FtbChunksClaimAccess INSTANCE = new FtbChunksClaimAccess();

    private FtbChunksClaimAccess() {}

    @Override
    public ClaimProbe probe(ServerLevel level, ChunkPos pos, UUID placer) {
        if (!ModList.get().isLoaded("ftbchunks")) {
            return ClaimProbe.ABSENT;
        }
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbchunks.api.FTBChunksAPI");
            Object api = apiClass.getMethod("api").invoke(null);
            if (api == null) {
                return ClaimProbe.DENY;
            }
            Method loaded = findMethod(api.getClass(), "isManagerLoaded");
            if (loaded != null && !Boolean.TRUE.equals(loaded.invoke(api))) {
                return ClaimProbe.DENY;
            }
            Object manager = api.getClass().getMethod("getManager").invoke(api);
            Object dimPos = chunkDimPos(level, pos);
            Object claimed = manager.getClass().getMethod("getChunk", dimPos.getClass()).invoke(manager, dimPos);
            if (claimed == null) {
                return ClaimProbe.ABSENT;
            }
            Object teamData = claimed.getClass().getMethod("getTeamData").invoke(claimed);
            return teamAllows(teamData, placer) ? ClaimProbe.ALLOW : ClaimProbe.DENY;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChunkRegeneratorMod.LOGGER.warn("FTB Chunks claim check failed; denying regeneration", exception);
            return ClaimProbe.DENY;
        }
    }

    private static Object chunkDimPos(ServerLevel level, ChunkPos pos) throws ReflectiveOperationException {
        Class<?> type = Class.forName("dev.ftb.mods.ftblibrary.math.ChunkDimPos");
        try {
            return type.getConstructor(net.minecraft.resources.ResourceKey.class, int.class, int.class)
                    .newInstance(level.dimension(), pos.x(), pos.z());
        } catch (NoSuchMethodException ignored) {
            return type.getConstructor(ServerLevel.class, int.class, int.class)
                    .newInstance(level, pos.x(), pos.z());
        }
    }

    private static boolean teamAllows(Object teamData, UUID placer) throws ReflectiveOperationException {
        Method member = findMethod(teamData.getClass(), "isTeamMember", UUID.class);
        if (member != null) {
            return Boolean.TRUE.equals(member.invoke(teamData, placer));
        }
        Object team = teamData.getClass().getMethod("getTeam").invoke(teamData);
        if (team == null) {
            return false;
        }
        Method isMember = findMethod(team.getClass(), "isMember", UUID.class);
        if (isMember != null) {
            return Boolean.TRUE.equals(isMember.invoke(team, placer));
        }
        Method owner = findMethod(team.getClass(), "getOwner");
        if (owner != null) {
            return placer.equals(owner.invoke(team));
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
