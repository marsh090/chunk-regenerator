package com.zanon.chunkregenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;
import com.zanon.chunkregenerator.block.ChunkDevourerBlock;
import com.zanon.chunkregenerator.block.entity.ChunkDevourerBlockEntity;
import com.zanon.chunkregenerator.block.entity.ChunkRegeneratorBlockEntity;
import com.zanon.chunkregenerator.claim.ClaimAccess;
import com.zanon.chunkregenerator.claim.ClaimProbe;
import com.zanon.chunkregenerator.claim.CompositeClaimAccess;
import com.zanon.chunkregenerator.regen.ChunkRegenService;
import com.zanon.chunkregenerator.regen.ChunkRemoveService;
import com.zanon.chunkregenerator.regen.RegenResult;
import com.zanon.chunkregenerator.item.AnalyzerTags;
import com.zanon.chunkregenerator.item.ChunkAnalyzer;
import com.zanon.chunkregenerator.registry.ModBlocks;
import com.zanon.chunkregenerator.registry.ModDataComponents;
import com.zanon.chunkregenerator.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

public final class ModGameTests {
    private ModGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        registerFunction("unclaimed_regen", ModGameTests::unclaimedRegen);
        registerFunction("foreign_claim_denied", ModGameTests::foreignClaimDenied);
        registerFunction("player_present_denied", ModGameTests::playerPresentDenied);
        registerFunction("recipe_loaded", ModGameTests::recipeLoaded);
        registerFunction("recipe_override", ModGameTests::recipeOverride);
        registerFunction("chunk_remove_keeps_bedrock", ModGameTests::chunkRemoveKeepsBedrock);
        registerFunction("analyzer_cost_and_scan", ModGameTests::analyzerCostAndScan);
        registerFunction("command_registered", ModGameTests::commandRegistered);
        registerFunction("devourer_eats_one_block", ModGameTests::devourerEatsOneBlock);
        registerFunction("regen_keeps_devourer", ModGameTests::regenKeepsDevourer);

        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.parse("chunkregenerator:test"));
        registerInstance(event, "unclaimed_regen", environment, 600);
        registerInstance(event, "foreign_claim_denied", environment, 100);
        registerInstance(event, "player_present_denied", environment, 100);
        registerInstance(event, "recipe_loaded", environment, 100);
        registerInstance(event, "recipe_override", environment, 400);
        registerInstance(event, "chunk_remove_keeps_bedrock", environment, 200);
        registerInstance(event, "analyzer_cost_and_scan", environment, 100);
        registerInstance(event, "command_registered", environment, 100);
        registerInstance(event, "devourer_eats_one_block", environment, 200);
        registerInstance(event, "regen_keeps_devourer", environment, 600);
    }

    private static void registerFunction(String name, Consumer<GameTestHelper> function) {
        Identifier id = Identifier.parse(ChunkRegeneratorMod.MOD_ID + ":" + name);
        if (BuiltInRegistries.TEST_FUNCTION instanceof MappedRegistry<Consumer<GameTestHelper>> registry) {
            registry.unfreeze(false);
            Registry.register(registry, id, function);
            registry.freeze();
        }
    }

    private static void registerInstance(RegisterGameTestsEvent event, String name, Holder<TestEnvironmentDefinition<?>> environment, int maxTicks) {
        Identifier id = Identifier.parse(ChunkRegeneratorMod.MOD_ID + ":" + name);
        ResourceKey<Consumer<GameTestHelper>> function = ResourceKey.create(Registries.TEST_FUNCTION, id);
        event.registerTest(id, new FunctionGameTestInstance(function, new TestData<>(environment, Identifier.parse("minecraft:empty"), maxTicks, 0, true)));
    }

    private static void unclaimedRegen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(90, 90);
        int terrainY = level.getMinY() + 8;
        BlockPos marker = new BlockPos(chunk.getMinBlockX() + 4, terrainY, chunk.getMinBlockZ() + 4);
        BlockPos regenerator = marker.above();
        level.setBlockAndUpdate(marker, Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(regenerator, ModBlocks.CHUNK_REGENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(regenerator) instanceof ChunkRegeneratorBlockEntity blockEntity) {
            blockEntity.setPlacer(UUID.randomUUID());
        }
        RegenResult result = ChunkRegenService.tryActivate(level, regenerator);
        helper.assertTrue(result == RegenResult.STARTED, "expected regeneration to start, got " + result);
        helper.succeedWhen(() -> {
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            if (loaded == null) {
                return;
            }
            helper.assertTrue(!loaded.getBlockState(marker).is(Blocks.DIAMOND_BLOCK), "diamond marker survived regeneration");
            boolean solid = false;
            for (int y = level.getMinY(); y < level.getMinY() + 48; y++) {
                if (loaded.getBlockState(marker.atY(y)).isSolid()) {
                    solid = true;
                    break;
                }
            }
            helper.assertTrue(solid, "regenerated column has no solid blocks");
        });
    }

    private static void foreignClaimDenied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ClaimAccess deny = (serverLevel, pos, placer) -> ClaimProbe.DENY;
        CompositeClaimAccess.addTestingProvider(deny);
        try {
            ChunkPos chunk = new ChunkPos(91, 90);
            RegenResult result = ChunkRegenService.validate(level, chunk, UUID.randomUUID());
            helper.assertTrue(result == RegenResult.DENIED_CLAIM, "foreign claim should deny, got " + result);
            helper.succeed();
        } finally {
            CompositeClaimAccess.clearTestingProviders();
        }
    }

    private static void playerPresentDenied(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        UUID placer = UUID.randomUUID();
        ChunkPos chunk = new ChunkPos(92, 90);
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(placer, "RegenTester"));
        player.setPos(chunk.getMinBlockX() + 2.5D, 80.0D, chunk.getMinBlockZ() + 2.5D);
        level.players().add(player);
        try {
            RegenResult result = ChunkRegenService.validate(level, chunk, placer);
            helper.assertTrue(result == RegenResult.DENIED_PLAYERS, "occupied chunk should deny, got " + result);
            helper.succeed();
        } finally {
            level.players().remove(player);
        }
    }

    private static void chunkRemoveKeepsBedrock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(93, 90);
        UUID placerId = UUID.randomUUID();
        FakePlayer player = FakePlayerFactory.get(level, new GameProfile(placerId, "RemoverTester"));
        player.setGameMode(GameType.CREATIVE);
        player.setPos(0.5D, 80.0D, 0.5D);
        level.players().add(player);
        try {
            int minX = chunk.getMinBlockX();
            int minZ = chunk.getMinBlockZ();
            BlockPos bedrock = new BlockPos(minX + 2, level.getMinY(), minZ + 2);
            BlockPos stone = new BlockPos(minX + 4, level.getMinY() + 10, minZ + 4);
            BlockPos remover = stone.above();
            level.setBlockAndUpdate(bedrock, Blocks.BEDROCK.defaultBlockState());
            level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(remover, ModBlocks.CHUNK_REMOVER.get().defaultBlockState());
            if (level.getBlockEntity(remover) instanceof ChunkRegeneratorBlockEntity blockEntity) {
                blockEntity.setPlacer(placerId);
            }
            RegenResult result = ChunkRemoveService.tryActivate(level, remover);
            helper.assertTrue(result == RegenResult.STARTED, "expected removal to start, got " + result);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = level.getBlockState(cursor.set(minX + x, y, minZ + z));
                        helper.assertTrue(state.isAir() || state.is(Blocks.BEDROCK), "left " + state + " at " + cursor);
                    }
                }
            }
            helper.assertTrue(level.getBlockState(bedrock).is(Blocks.BEDROCK), "bedrock was removed");
            helper.succeed();
        } finally {
            level.players().remove(player);
        }
    }

    private static void recipeLoaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ShapedRecipe recipe = shapedRecipe(level);
        helper.assertTrue(recipe.assemble(null).is(ModBlocks.CHUNK_REGENERATOR.get().asItem()), "default recipe result mismatch");
        helper.succeed();
    }

    private static void recipeOverride(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        try {
            Path pack = level.getServer().getWorldPath(LevelResource.DATAPACK_DIR).resolve("chunkregenerator_override");
            Path recipe = pack.resolve("data").resolve(ChunkRegeneratorMod.MOD_ID).resolve("recipe").resolve("chunk_regenerator.json");
            Files.createDirectories(recipe.getParent());
            Files.writeString(pack.resolve("pack.mcmeta"), """
                    {
                      "pack": {
                        "description": "Chunk Regenerator recipe override",
                        "min_format": [101, 1],
                        "max_format": [101, 1]
                      }
                    }
                    """);
            Files.writeString(recipe, """
                    {
                      "type": "minecraft:crafting_shaped",
                      "pattern": ["P"],
                      "key": { "P": "minecraft:paper" },
                      "result": { "id": "minecraft:dirt", "count": 1 }
                    }
                    """);
            var repository = level.getServer().getPackRepository();
            repository.reload();
            String packId = repository.getAvailableIds().stream().filter(id -> id.contains("chunkregenerator_override")).findFirst().orElse(null);
            helper.assertTrue(packId != null, "override datapack was not discovered: " + repository.getAvailableIds());
            List<String> selected = new ArrayList<>(repository.getSelectedIds());
            if (!selected.contains(packId)) {
                selected.add(packId);
            }
            level.getServer().reloadResources(selected);
        } catch (Exception exception) {
            helper.fail("could not install recipe override: " + exception.getMessage());
            return;
        }
        helper.succeedWhen(() -> {
            ShapedRecipe recipe = shapedRecipe(level);
            helper.assertTrue(recipe.assemble(null).is(Items.DIRT), "datapack did not replace the recipe");
        });
    }

    private static void analyzerCostAndScan(GameTestHelper helper) {
        helper.assertTrue(ChunkAnalyzer.energyCost(0) == 0, "empty height should be free");
        helper.assertTrue(ChunkAnalyzer.energyCost(1) == 1000, "one layer costs 1000");
        helper.assertTrue(ChunkAnalyzer.energyCost(100) == 1000, "100 layers cost 1000");
        helper.assertTrue(ChunkAnalyzer.energyCost(101) == 2000, "101 layers cost 2000");
        helper.assertTrue(
                ChunkAnalyzer.sanitize(List.of("minecraft:iron_ores", "bad tag", "#c:ores/iron")).equals(List.of("minecraft:iron_ores", "c:ores/iron")),
                "tag sanitize mismatch");

        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(94, 90);
        List<String> tags = List.of("minecraft:iron_ores");
        ChunkAnalyzer.Report before = ChunkAnalyzer.scan(level, chunk, tags);
        BlockPos spot = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = level.getMinY(); y < level.getMaxY() && spot == null; y++) {
            cursor.set(chunk.getMinBlockX(), y, chunk.getMinBlockZ());
            if (level.getBlockState(cursor).isAir()) {
                spot = cursor.immutable();
            }
        }
        helper.assertTrue(spot != null, "chunk has no air to place an ore");
        level.setBlockAndUpdate(spot, Blocks.IRON_ORE.defaultBlockState());
        ChunkAnalyzer.Report after = ChunkAnalyzer.scan(level, chunk, tags);
        helper.assertTrue(after.nonAir() == before.nonAir() + 1, "solid block count");
        helper.assertTrue(after.count("minecraft:iron_ores") == before.count("minecraft:iron_ores") + 1, "iron ore count");
        helper.assertTrue(ChunkAnalyzer.scan(level, chunk, List.of("minecraft:iron_ore")).count("minecraft:iron_ore") >= 1, "block id should count");
        int layerDelta = after.occupiedLayers() - before.occupiedLayers();
        helper.assertTrue(layerDelta == 0 || layerDelta == 1, "occupied layer delta " + layerDelta);

        ItemStack stack = new ItemStack(ModItems.CHUNK_ANALYZER.get());
        stack.set(ModDataComponents.ANALYZER_TAGS.get(), new AnalyzerTags(tags));
        int cost = ChunkAnalyzer.energyCost(after.occupiedLayers());
        stack.set(ModDataComponents.ENERGY.get(), cost);
        helper.assertTrue(ChunkAnalyzer.tryAnalyze(level, chunk, stack, null) != null, "scan should spend a full charge");
        helper.assertTrue(stack.getOrDefault(ModDataComponents.ENERGY.get(), -1) == 0, "energy was not spent");
        int shortCharge = Math.max(0, cost - 1);
        stack.set(ModDataComponents.ENERGY.get(), shortCharge);
        helper.assertTrue(ChunkAnalyzer.tryAnalyze(level, chunk, stack, null) == null, "short charge should refuse");
        helper.assertTrue(stack.getOrDefault(ModDataComponents.ENERGY.get(), -1) == shortCharge, "refused scan spent energy");

        ItemStack creative = new ItemStack(ModItems.CREATIVE_CHUNK_ANALYZER.get());
        helper.assertTrue(creative.getItem().isFoil(creative), "creative analyzer should glint");
        creative.set(ModDataComponents.ENERGY.get(), 0);
        helper.assertTrue(ChunkAnalyzer.tryAnalyze(level, chunk, creative, null) != null, "creative scan should ignore energy");
        helper.assertTrue(creative.getOrDefault(ModDataComponents.ENERGY.get(), -1) == 0, "creative analyzer spent energy");
        helper.succeed();
    }

    private static void devourerEatsOneBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(96, 90);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos machinePos = null;
        BlockPos bedrockPos = null;
        for (int y = level.getMinY(); y < level.getMaxY(); y++) {
            cursor.set(chunk.getMinBlockX() + 2, y, chunk.getMinBlockZ() + 2);
            if (bedrockPos == null && level.getBlockState(cursor).is(Blocks.BEDROCK)) {
                bedrockPos = cursor.immutable();
            }
            if (machinePos == null && level.getBlockState(cursor).isAir()) {
                machinePos = cursor.immutable();
                break;
            }
        }
        helper.assertTrue(machinePos != null && bedrockPos != null, "devourer test chunk has no space");
        BlockPos foodPos = machinePos.offset(2, 0, 2);
        BlockPos shellPos = machinePos.above();
        BlockPos keptPos = machinePos.offset(2, 0, 0);
        level.setBlockAndUpdate(machinePos, ModBlocks.CHUNK_DEVOURER.get().defaultBlockState());
        helper.assertTrue(level.getBlockEntity(machinePos) instanceof ChunkDevourerBlockEntity, "devourer block entity missing");
        ChunkDevourerBlockEntity blockEntity = (ChunkDevourerBlockEntity) level.getBlockEntity(machinePos);
        blockEntity.setPlacer(UUID.randomUUID());
        level.setBlockAndUpdate(foodPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(shellPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(keptPos, Blocks.OAK_LOG.defaultBlockState());
        blockEntity.setItem(0, new ItemStack(Items.OAK_LOG));

        helper.assertTrue(blockEntity.wouldSpare(bedrockPos), "bedrock should be spared");
        helper.assertTrue(blockEntity.wouldSpare(keptPos), "filtered oak log should be spared");
        helper.assertTrue(blockEntity.wouldSpare(shellPos), "blocks touching the devourer should be spared");
        helper.assertTrue(!blockEntity.wouldSpare(foodPos), "diamond block should be eaten");
        BlockPos regeneratorPos = machinePos.offset(3, 0, 0);
        BlockPos removerPos = machinePos.offset(3, 0, 1);
        level.setBlockAndUpdate(regeneratorPos, ModBlocks.CHUNK_REGENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(removerPos, ModBlocks.CHUNK_REMOVER.get().defaultBlockState());
        helper.assertTrue(blockEntity.wouldSpare(regeneratorPos), "devourer should spare the regenerator");
        helper.assertTrue(blockEntity.wouldSpare(removerPos), "devourer should spare the remover");
        level.setBlockAndUpdate(regeneratorPos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(removerPos, Blocks.AIR.defaultBlockState());

        BlockState unpowered = blockEntity.getBlockState();
        ChunkDevourerBlockEntity.serverTick(level, machinePos, unpowered, blockEntity);
        helper.assertTrue(level.getBlockState(foodPos).is(Blocks.DIAMOND_BLOCK), "unpowered devourer ate a block");

        blockEntity.setScanIndex(ChunkDevourerBlockEntity.indexFor(level, foodPos));
        BlockState powered = unpowered.setValue(ChunkDevourerBlock.POWERED, true);
        ChunkDevourerBlockEntity.serverTick(level, machinePos, powered, blockEntity);
        helper.assertTrue(level.getBlockState(foodPos).isAir(), "powered devourer left the diamond block");
        helper.assertTrue(blockEntity.getEnergy() == ChunkDevourerBlockEntity.FE_PER_BLOCK, "energy was " + blockEntity.getEnergy());
        helper.assertTrue(level.getBlockState(keptPos).is(Blocks.OAK_LOG), "filter did not protect the oak log");
        helper.assertTrue(level.getBlockState(shellPos).is(Blocks.DIAMOND_BLOCK), "devourer ate a block in its 3x3x3");

        level.setBlockAndUpdate(foodPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        helper.assertTrue(blockEntity.tryFeed(new ItemStack(Items.NETHERITE_INGOT), true) == 1, "netherite was refused");
        blockEntity.setScanIndex(ChunkDevourerBlockEntity.indexFor(level, foodPos));
        int before = blockEntity.getEnergy();
        ChunkDevourerBlockEntity.serverTick(level, machinePos, powered, blockEntity);
        helper.assertTrue(level.getBlockState(foodPos).isAir(), "upgraded devourer left the diamond block");
        helper.assertTrue(blockEntity.getEnergy() == before + 2, "netherite yield was " + blockEntity.getEnergy());

        level.setBlockAndUpdate(foodPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        blockEntity.setStoredEnergy(ChunkDevourerBlockEntity.CAPACITY - 1);
        blockEntity.setScanIndex(ChunkDevourerBlockEntity.indexFor(level, foodPos));
        ChunkDevourerBlockEntity.serverTick(level, machinePos, powered, blockEntity);
        helper.assertTrue(level.getBlockState(foodPos).isAir(), "partial buffer left the diamond block");
        helper.assertTrue(blockEntity.getEnergy() == ChunkDevourerBlockEntity.CAPACITY, "partial buffer did not fill, energy was " + blockEntity.getEnergy());

        level.setBlockAndUpdate(foodPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        blockEntity.setStoredEnergy(ChunkDevourerBlockEntity.CAPACITY);
        blockEntity.setScanIndex(ChunkDevourerBlockEntity.indexFor(level, foodPos));
        ChunkDevourerBlockEntity.serverTick(level, machinePos, powered, blockEntity);
        helper.assertTrue(level.getBlockState(foodPos).is(Blocks.DIAMOND_BLOCK), "full devourer ate a block");
        helper.assertTrue(blockEntity.getEnergy() == ChunkDevourerBlockEntity.CAPACITY, "full buffer changed");

        helper.assertTrue(blockEntity.tryFeed(new ItemStack(Items.NETHER_STAR), true) == 1, "nether star was refused");
        helper.assertTrue(blockEntity.yield() == 12, "star yield was " + blockEntity.yield());
        helper.assertTrue(blockEntity.tryFeed(new ItemStack(Items.NETHER_STAR), true) == 0, "second nether star was accepted");
        ItemStack extraIngots = new ItemStack(Items.NETHERITE_INGOT, 20);
        helper.assertTrue(blockEntity.tryFeed(extraIngots, true) == 8, "netherite cap was " + blockEntity.netheriteCount());
        helper.assertTrue(extraIngots.getCount() == 12, "leftover netherite was " + extraIngots.getCount());
        helper.assertTrue(blockEntity.tryFeed(new ItemStack(Items.GOLDEN_APPLE), true) == 1, "golden apple was refused");
        helper.assertTrue(blockEntity.blocksPerTick() == 10, "haste should void 10 blocks per tick");
        helper.assertTrue(blockEntity.hasteTicks() == ChunkDevourerBlockEntity.HASTE_TICKS, "haste should last 3 minutes");

        level.setBlockAndUpdate(foodPos, Blocks.DIAMOND_BLOCK.defaultBlockState());
        var saved = ChunkDevourerBlockEntity.preserveInChunk(level, chunk);
        helper.assertTrue(saved.size() == 1, "preserve missed the devourer");
        helper.assertTrue(saved.get(0).energy() == ChunkDevourerBlockEntity.CAPACITY, "preserved energy was " + saved.get(0).energy());
        helper.assertTrue(saved.get(0).netherite() == 9, "preserved netherite was " + saved.get(0).netherite());
        blockEntity.setScanIndex(ChunkDevourerBlockEntity.indexFor(level, foodPos));
        ChunkDevourerBlockEntity.serverTick(level, machinePos, powered, blockEntity);
        helper.assertTrue(level.getBlockState(foodPos).is(Blocks.DIAMOND_BLOCK), "retired devourer voided a block");
        helper.succeed();
    }

    private static void regenKeepsDevourer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos chunk = new ChunkPos(97, 90);
        BlockPos devourerPos = new BlockPos(chunk.getMinBlockX() + 4, level.getMinY() + 12, chunk.getMinBlockZ() + 4);
        BlockPos marker = devourerPos.offset(3, 0, 0);
        BlockPos regeneratorPos = devourerPos.above(2);
        level.setBlockAndUpdate(marker, Blocks.DIAMOND_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(devourerPos, ModBlocks.CHUNK_DEVOURER.get().defaultBlockState());
        level.setBlock(devourerPos, level.getBlockState(devourerPos).setValue(ChunkDevourerBlock.POWERED, true), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        helper.assertTrue(level.getBlockEntity(devourerPos) instanceof ChunkDevourerBlockEntity, "devourer missing before regen");
        ChunkDevourerBlockEntity devourer = (ChunkDevourerBlockEntity) level.getBlockEntity(devourerPos);
        devourer.setPlacer(UUID.randomUUID());
        devourer.setStoredEnergy(4321);
        helper.assertTrue(devourer.tryFeed(new ItemStack(Items.NETHERITE_INGOT, 4), true) == 4, "could not feed netherite");
        devourer.setItem(0, new ItemStack(Items.OAK_LOG));
        level.setBlockAndUpdate(regeneratorPos, ModBlocks.CHUNK_REGENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(regeneratorPos) instanceof ChunkRegeneratorBlockEntity regenerator) {
            regenerator.setPlacer(devourer.getPlacer());
        }
        RegenResult result = ChunkRegenService.tryActivate(level, regeneratorPos);
        helper.assertTrue(result == RegenResult.STARTED, "expected regeneration to start, got " + result);
        helper.succeedWhen(() -> {
            LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
            helper.assertTrue(loaded != null, "chunk missing");
            helper.assertTrue(loaded.getBlockEntity(devourerPos) instanceof ChunkDevourerBlockEntity, "devourer was not restored");
            ChunkDevourerBlockEntity restored = (ChunkDevourerBlockEntity) loaded.getBlockEntity(devourerPos);
            helper.assertTrue(restored != devourer, "restored the retired devourer");
            helper.assertTrue(restored.getEnergy() >= 4321, "energy was " + restored.getEnergy());
            helper.assertTrue(restored.netheriteCount() == 4, "netherite was " + restored.netheriteCount());
            helper.assertTrue(restored.getBlockState().getValue(ChunkDevourerBlock.POWERED), "devourer lost its powered state");
            helper.assertTrue(restored.getItem(0).is(Items.OAK_LOG), "filter was not restored");
            helper.assertTrue(!loaded.getBlockState(marker).is(Blocks.DIAMOND_BLOCK), "diamond marker survived regeneration");
        });
    }

    private static void commandRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var dispatcher = level.getServer().getCommands().getDispatcher();
        var source = level.getServer().createCommandSourceStack();
        helper.assertTrue(dispatcher.parse("chunkregenerator destroy", source).getContext().getNodes().size() >= 2, "destroy command missing");
        helper.assertTrue(dispatcher.parse("chunk rebuild 4 -2", source).getContext().getNodes().size() >= 4, "rebuild command missing");
        helper.succeed();
    }

    private static ShapedRecipe shapedRecipe(ServerLevel level) {
        ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, Identifier.parse(ChunkRegeneratorMod.MOD_ID + ":chunk_regenerator"));
        return level.getServer().getRecipeManager().byKey(key)
                .map(holder -> holder.value())
                .filter(ShapedRecipe.class::isInstance)
                .map(ShapedRecipe.class::cast)
                .orElseThrow(() -> new IllegalStateException("chunk regenerator recipe is missing"));
    }
}
