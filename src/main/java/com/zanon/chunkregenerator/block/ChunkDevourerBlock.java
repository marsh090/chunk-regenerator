package com.zanon.chunkregenerator.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.zanon.chunkregenerator.block.entity.ChunkDevourerBlockEntity;
import com.zanon.chunkregenerator.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public class ChunkDevourerBlock extends BaseEntityBlock {
    public static final MapCodec<ChunkDevourerBlock> CODEC = simpleCodec(ChunkDevourerBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ChunkDevourerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkDevourerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.CHUNK_DEVOURER.get(), ChunkDevourerBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ChunkDevourerBlockEntity blockEntity && placer != null) {
            blockEntity.setPlacer(placer.getUUID());
        }
        if (!level.isClientSide()) {
            this.applySignal(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) {
            this.applySignal(level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide()) {
            this.applySignal(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!ChunkDevourerBlockEntity.canFeed(stack)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof ChunkDevourerBlockEntity blockEntity)) {
            return InteractionResult.FAIL;
        }
        int taken = blockEntity.tryFeed(stack, !player.hasInfiniteMaterials());
        if (taken == 0) {
            if (stack.is(Items.NETHERITE_INGOT)) {
                player.sendOverlayMessage(Component.translatable("chunkregenerator.message.devourer_netherite_full"));
            } else if (stack.is(Items.NETHER_STAR)) {
                player.sendOverlayMessage(Component.translatable("chunkregenerator.message.devourer_star_full"));
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.NETHERITE_INGOT)) {
            player.sendOverlayMessage(Component.translatable("chunkregenerator.message.devourer_netherite", blockEntity.netheriteCount(), blockEntity.yield()));
        } else if (stack.is(Items.NETHER_STAR)) {
            player.sendOverlayMessage(Component.translatable("chunkregenerator.message.devourer_star", blockEntity.yield()));
        } else {
            player.sendOverlayMessage(Component.translatable("chunkregenerator.message.devourer_haste"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ChunkDevourerBlockEntity blockEntity) {
            player.openMenu(blockEntity);
        }
        return InteractionResult.SUCCESS;
    }

    private void applySignal(Level level, BlockPos pos, BlockState state) {
        boolean signal = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != signal && level.getBlockState(pos).is(this)) {
            level.setBlock(pos, state.setValue(POWERED, signal), Block.UPDATE_CLIENTS);
        }
    }
}
