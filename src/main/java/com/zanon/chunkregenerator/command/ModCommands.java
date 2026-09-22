package com.zanon.chunkregenerator.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.regen.ChunkRegenService;
import com.zanon.chunkregenerator.regen.ChunkRemoveService;
import com.zanon.chunkregenerator.regen.RegenResult;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ChunkRegeneratorMod.MOD_ID)
public final class ModCommands {
    private ModCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher(), "chunkregenerator");
        register(event.getDispatcher(), "chunk");
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, String root) {
        dispatcher.register(Commands.literal(root)
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("destroy")
                        .executes(context -> destroy(context, false))
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(context -> destroy(context, true)))))
                .then(Commands.literal("rebuild")
                        .executes(context -> rebuild(context, false))
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .executes(context -> rebuild(context, true))))));
    }

    private static int destroy(CommandContext<CommandSourceStack> context, boolean positioned) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level() instanceof ServerLevel server ? server : null;
        if (level == null) {
            return 0;
        }
        ChunkPos chunk = chunk(context, player, positioned);
        RegenResult denial = ChunkRegenService.authorize(level, chunk, player.getUUID(), player.getUUID(), false);
        if (denial != null) {
            fail(context.getSource(), denial);
            return 0;
        }
        ChunkRemoveService.clear(level, chunk);
        ChunkRegeneratorMod.LOGGER.info("Command cleared chunk {} in {}", chunk, level.dimension().identifier());
        context.getSource().sendSuccess(
                () -> Component.translatable("chunkregenerator.message.command_destroyed", chunk.x(), chunk.z()),
                true);
        return 1;
    }

    private static int rebuild(CommandContext<CommandSourceStack> context, boolean positioned) {
        ServerPlayer player = player(context);
        if (player == null) {
            return 0;
        }
        ServerLevel level = player.level() instanceof ServerLevel server ? server : null;
        if (level == null) {
            return 0;
        }
        ChunkPos chunk = chunk(context, player, positioned);
        RegenResult result = ChunkRegenService.queue(level, chunk, player.getUUID(), player.getUUID(), false);
        if (result != RegenResult.STARTED) {
            fail(context.getSource(), result);
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.translatable("chunkregenerator.message.command_rebuilt", chunk.x(), chunk.z()),
                true);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.translatable("chunkregenerator.message.command_players_only"));
        }
        return player;
    }

    private static ChunkPos chunk(CommandContext<CommandSourceStack> context, ServerPlayer player, boolean positioned) {
        if (!positioned) {
            return ChunkPos.containing(player.blockPosition());
        }
        return new ChunkPos(IntegerArgumentType.getInteger(context, "chunkX"), IntegerArgumentType.getInteger(context, "chunkZ"));
    }

    private static void fail(CommandSourceStack source, RegenResult result) {
        String key = result.messageKey() == null ? "chunkregenerator.message.command_failed" : result.messageKey();
        source.sendFailure(Component.translatable(key));
    }
}
