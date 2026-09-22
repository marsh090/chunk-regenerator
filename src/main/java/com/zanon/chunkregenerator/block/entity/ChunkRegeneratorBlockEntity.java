package com.zanon.chunkregenerator.block.entity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zanon.chunkregenerator.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ChunkRegeneratorBlockEntity extends BlockEntity {
    private @Nullable UUID placer;

    public ChunkRegeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_REGENERATOR.get(), pos, state);
    }

    public @Nullable UUID getPlacer() {
        return this.placer;
    }

    public void setPlacer(UUID placer) {
        this.placer = placer;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.placer != null) {
            output.store("Placer", UUIDUtil.CODEC, this.placer);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.placer = input.read("Placer", UUIDUtil.CODEC).orElse(null);
    }
}
