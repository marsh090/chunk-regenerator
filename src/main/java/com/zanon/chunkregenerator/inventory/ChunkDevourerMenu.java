package com.zanon.chunkregenerator.inventory;

import com.zanon.chunkregenerator.block.entity.ChunkDevourerBlockEntity;
import com.zanon.chunkregenerator.registry.ModBlocks;
import com.zanon.chunkregenerator.registry.ModMenus;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

public class ChunkDevourerMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final net.minecraft.world.inventory.ContainerLevelAccess access;

    public ChunkDevourerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ChunkDevourerBlockEntity.SLOT_COUNT), new SimpleContainerData(5));
    }

    public ChunkDevourerMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(ModMenus.CHUNK_DEVOURER.get(), containerId);
        this.container = container;
        this.data = data;
        if (container instanceof ChunkDevourerBlockEntity blockEntity && blockEntity.getLevel() != null) {
            this.access = net.minecraft.world.inventory.ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        } else {
            this.access = net.minecraft.world.inventory.ContainerLevelAccess.NULL;
        }
        checkContainerSize(container, ChunkDevourerBlockEntity.SLOT_COUNT);
        container.startOpen(playerInventory.player);

        for (int slot = 0; slot < ChunkDevourerBlockEntity.FILTER_SLOTS; slot++) {
            this.addSlot(new FilterSlot(container, slot, 17 + slot * 18, 49));
        }
        this.addSlot(new ChargeSlot(container, ChunkDevourerBlockEntity.CHARGE_SLOT, 17, 165));
        this.addSlot(new FeedSlot(container, ChunkDevourerBlockEntity.FEED_SLOT, 17, 104));
        this.addStandardInventorySlots(playerInventory, 17, 226);
        this.addDataSlots(data);
    }

    public int energy() {
        int low = this.data.get(0) & 0xFFFF;
        int high = this.data.get(1) & 0xFFFF;
        return (high << 16) | low;
    }

    public int netherite() {
        return this.data.get(2);
    }

    public boolean netherStar() {
        return this.data.get(3) != 0;
    }

    public int hasteTicks() {
        return Math.max(0, this.data.get(4));
    }

    public int yield() {
        return ChunkDevourerBlockEntity.FE_PER_BLOCK
                + this.netherite() * ChunkDevourerBlockEntity.NETHERITE_FE
                + (this.netherStar() ? ChunkDevourerBlockEntity.NETHER_STAR_FE : 0);
    }

    public int blocksPerTick() {
        return this.hasteTicks() > 0 ? ChunkDevourerBlockEntity.HASTE_BLOCKS : 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.CHUNK_DEVOURER.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < ChunkDevourerBlockEntity.SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, ChunkDevourerBlockEntity.SLOT_COUNT, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (ChunkDevourerBlockEntity.canFeed(stack)) {
            if (!this.moveItemStackTo(stack, ChunkDevourerBlockEntity.FEED_SLOT, ChunkDevourerBlockEntity.FEED_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof BlockItem) {
            if (!this.moveItemStackTo(stack, 0, ChunkDevourerBlockEntity.FILTER_SLOTS, false)
                    && !this.moveItemStackTo(stack, ChunkDevourerBlockEntity.CHARGE_SLOT, ChunkDevourerBlockEntity.CHARGE_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, ChunkDevourerBlockEntity.CHARGE_SLOT, ChunkDevourerBlockEntity.CHARGE_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private static final class FilterSlot extends Slot {
        private FilterSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof BlockItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class ChargeSlot extends Slot {
        private ChargeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class FeedSlot extends Slot {
        private FeedSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return ChunkDevourerBlockEntity.canFeed(stack);
        }
    }
}
