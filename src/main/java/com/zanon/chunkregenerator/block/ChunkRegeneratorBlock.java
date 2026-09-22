package com.zanon.chunkregenerator.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.zanon.chunkregenerator.block.entity.ChunkRegeneratorBlockEntity;
import com.zanon.chunkregenerator.regen.ChunkRegenService;
import com.zanon.chunkregenerator.regen.RegenResult;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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

public class ChunkRegeneratorBlock extends BaseEntityBlock {
    public static final MapCodec<ChunkRegeneratorBlock> CODEC = simpleCodec(ChunkRegeneratorBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ChunkRegeneratorBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChunkRegeneratorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
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
        if (level.getBlockEntity(pos) instanceof ChunkRegeneratorBlockEntity blockEntity && placer != null) {
            blockEntity.setPlacer(placer.getUUID());
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this) && level.hasNeighborSignal(pos)) {
            this.activate((ServerLevel) level, pos, state);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) {
            return;
        }
        boolean signal = level.hasNeighborSignal(pos);
        boolean powered = state.getValue(POWERED);
        if (signal && !powered) {
            this.activate((ServerLevel) level, pos, state);
        } else if (!signal && powered && level.getBlockState(pos).is(this)) {
            level.setBlock(pos, state.setValue(POWERED, false), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    private void activate(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(POWERED, true), Block.UPDATE_CLIENTS);
        RegenResult result = ChunkRegenService.tryActivate(level, pos);
        if (result != RegenResult.STARTED && level.getBlockState(pos).is(this)) {
            ChunkRegenService.playDenied(level, pos, result);
        }
    }
}
