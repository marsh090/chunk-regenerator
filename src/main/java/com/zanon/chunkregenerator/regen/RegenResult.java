package com.zanon.chunkregenerator.regen;

public enum RegenResult {
    STARTED(null),
    DENIED_CLAIM("chunkregenerator.message.denied_claim"),
    DENIED_SPAWN("chunkregenerator.message.denied_spawn"),
    DENIED_UNCLAIMED("chunkregenerator.message.denied_unclaimed"),
    DENIED_PLAYERS("chunkregenerator.message.players_present"),
    DENIED_DIMENSION("chunkregenerator.message.denied_dimension"),
    DENIED_COOLDOWN("chunkregenerator.message.cooldown"),
    DENIED_NO_PLACER("chunkregenerator.message.no_placer"),
    DENIED_NOT_CREATIVE("chunkregenerator.message.not_creative");

    private final String messageKey;

    RegenResult(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return this.messageKey;
    }
}
