package com.zanon.chunkregenerator.claim;

import java.lang.reflect.Method;
import java.util.UUID;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

/**
 * Soft integration with Open Parties and Claims. Lookups stay on reflection so the dependency stays optional.
 * A failed lookup while the mod is loaded denies regeneration.
 */
public final class OpacClaimAccess implements ClaimAccess {
    public static final OpacClaimAccess INSTANCE = new OpacClaimAccess();

    private OpacClaimAccess() {}

    @Override
    public ClaimProbe probe(ServerLevel level, ChunkPos pos, UUID placer) {
        if (!ModList.get().isLoaded("openpartiesandclaims")) {
            return ClaimProbe.ABSENT;
        }
        try {
            Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Object api = apiClass.getMethod("get", net.minecraft.server.MinecraftServer.class).invoke(null, level.getServer());
            if (api == null) {
                return ClaimProbe.DENY;
            }
            Object manager = api.getClass().getMethod("getServerClaimsManager").invoke(api);
            Identifier dimension = level.dimension().identifier();
            Object claim = manager.getClass().getMethod("get", Identifier.class, int.class, int.class)
                    .invoke(manager, dimension, pos.x(), pos.z());
            if (claim == null) {
                return ClaimProbe.ABSENT;
            }
            UUID owner = (UUID) claim.getClass().getMethod("getPlayerId").invoke(claim);
            if (placer.equals(owner) || sameParty(api, placer, owner)) {
                return ClaimProbe.ALLOW;
            }
            return ClaimProbe.DENY;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            ChunkRegeneratorMod.LOGGER.warn("Open Parties and Claims check failed; denying regeneration", exception);
            return ClaimProbe.DENY;
        }
    }

    private static boolean sameParty(Object api, UUID placer, UUID owner) throws ReflectiveOperationException {
        Method partyManagerMethod = find(api.getClass(), "getPartyManager");
        if (partyManagerMethod == null) {
            return false;
        }
        Object partyManager = partyManagerMethod.invoke(api);
        if (partyManager == null) {
            return false;
        }
        Method byMember = find(partyManager.getClass(), "getPartyByMember", UUID.class);
        if (byMember == null) {
            return false;
        }
        Object placerParty = byMember.invoke(partyManager, placer);
        Object ownerParty = byMember.invoke(partyManager, owner);
        if (placerParty == null || ownerParty == null) {
            return false;
        }
        Method id = find(placerParty.getClass(), "getId");
        if (id == null) {
            return placerParty.equals(ownerParty);
        }
        return id.invoke(placerParty).equals(id.invoke(ownerParty));
    }

    private static Method find(Class<?> type, String name, Class<?>... params) {
        try {
            return type.getMethod(name, params);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
