package com.zanon.chunkregenerator.block.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.zanon.chunkregenerator.ChunkRegeneratorMod;
import com.zanon.chunkregenerator.regen.ChunkRegenService;
import com.zanon.chunkregenerator.regen.ChunkRemoveService;
import com.zanon.chunkregenerator.regen.RegenResult;
import com.zanon.chunkregenerator.registry.ModBlockEntities;
import com.zanon.chunkregenerator.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;

import com.zanon.chunkregenerator.block.ChunkDevourerBlock;
import com.zanon.chunkregenerator.inventory.ChunkDevourerMenu;

/**
 * Eats one block per tick while powered. Tick accelerators that call this ticker
 * speed that up. The block never requests a chunk ticket.
 */
public class ChunkDevourerBlockEntity extends BlockEntity implements Container, MenuProvider, ContainerData {
    public static final int CAPACITY = 100_000;
    public static final int FE_PER_BLOCK = 1;
    public static final int MAX_NETHERITE = 9;
    public static final int NETHERITE_FE = 1;
    public static final int NETHER_STAR_FE = 10;
    public static final int HASTE_TICKS = 20 * 60 * 3;
    public static final int HASTE_BLOCKS = 10;
    public static final int FILTER_SLOTS = 9;
    public static final int CHARGE_SLOT = 9;
    public static final int FEED_SLOT = 10;
    public static final int SLOT_COUNT = 11;

    private static final int CLEAR_FLAGS = Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_SKIP_ON_PLACE
            | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
    private final DevourerEnergy energy = new DevourerEnergy();
    private @Nullable UUID placer;
    private int netherite;
    private boolean netherStar;
    private int hasteTicks;
    private int scanIndex;
    private int idleTicks;
    private int sinceRefresh;
    private boolean retired;
    private boolean suppressDrops;
    private @Nullable RegenResult lastDenial;

    public ChunkDevourerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CHUNK_DEVOURER.get(), pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ModBlockEntities.CHUNK_DEVOURER.get(), (blockEntity, side) -> blockEntity.energy);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChunkDevourerBlockEntity blockEntity) {
        if (level instanceof ServerLevel server) {
            blockEntity.tickServer(server, state);
        }
    }

    /**
     * Copies every devourer in the chunk, then retires those copies so a rebuild cannot leave them voiding blocks.
     */
    public static List<SavedMachine> preserveInChunk(ServerLevel level, ChunkPos chunk) {
        LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
        if (loaded == null) {
            return List.of();
        }
        List<ChunkDevourerBlockEntity> found = new ArrayList<>();
        for (BlockEntity blockEntity : loaded.getBlockEntities().values()) {
            if (blockEntity instanceof ChunkDevourerBlockEntity devourer) {
                found.add(devourer);
            }
        }
        List<SavedMachine> saved = new ArrayList<>();
        for (ChunkDevourerBlockEntity devourer : found) {
            saved.add(devourer.capture());
            devourer.retire();
        }
        return saved;
    }

    public static void restoreAll(ServerLevel level, List<SavedMachine> saved) {
        for (SavedMachine machine : saved) {
            machine.restore(level);
        }
    }

    public static int indexFor(Level level, BlockPos pos) {
        int fromTop = (level.getMaxY() - 1) - pos.getY();
        int localX = pos.getX() & 15;
        int localZ = pos.getZ() & 15;
        return fromTop * 256 + localZ * 16 + localX;
    }

    public void setPlacer(UUID placer) {
        this.placer = placer;
        this.setChanged();
    }

    public @Nullable UUID getPlacer() {
        return this.placer;
    }

    public int netheriteCount() {
        return this.netherite;
    }

    public boolean hasNetherStar() {
        return this.netherStar;
    }

    public int hasteTicks() {
        return this.hasteTicks;
    }

    public int blocksPerTick() {
        return this.hasteTicks > 0 ? HASTE_BLOCKS : 1;
    }

    public int getEnergy() {
        return this.energy.getAmountAsInt();
    }

    public void setStoredEnergy(int amount) {
        this.energy.set(Math.clamp(amount, 0, CAPACITY));
    }

    public void setScanIndex(int index) {
        this.scanIndex = Math.max(0, index);
    }

    public int yield() {
        return FE_PER_BLOCK + this.netherite * NETHERITE_FE + (this.netherStar ? NETHER_STAR_FE : 0);
    }

    public static boolean canFeed(ItemStack stack) {
        return stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHER_STAR) || stack.is(Items.GOLDEN_APPLE);
    }

    /**
     * Consumes netherite, a nether star, or a golden apple from the stack.
     * Returns how many items were accepted.
     */
    public int tryFeed(ItemStack stack, boolean consume) {
        if (stack.isEmpty()) {
            return 0;
        }
        int taken = 0;
        if (stack.is(Items.NETHERITE_INGOT)) {
            int room = MAX_NETHERITE - this.netherite;
            taken = Math.min(room, stack.getCount());
            if (taken > 0) {
                this.netherite += taken;
            }
        } else if (stack.is(Items.NETHER_STAR)) {
            if (!this.netherStar) {
                this.netherStar = true;
                taken = 1;
            }
        } else if (stack.is(Items.GOLDEN_APPLE)) {
            this.hasteTicks = HASTE_TICKS;
            taken = 1;
        }
        if (taken > 0) {
            if (consume) {
                stack.shrink(taken);
            }
            this.setChanged();
        }
        return taken;
    }

    public boolean wouldSpare(BlockPos pos) {
        Level level = this.level;
        if (level == null) {
            return true;
        }
        return this.isSpared(level.getBlockState(pos), pos);
    }

    private void tickServer(ServerLevel level, BlockState state) {
        if (!this.canVoid(level)) {
            return;
        }
        this.chargeHeldItem();
        if (!state.getValue(ChunkDevourerBlock.POWERED)) {
            return;
        }
        try {
            this.tickPowered(level);
        } finally {
            if (this.hasteTicks > 0) {
                this.hasteTicks--;
                if (this.hasteTicks % 20 == 0) {
                    this.setChanged();
                }
            }
        }
    }

    private void tickPowered(ServerLevel level) {
        if (this.idleTicks > 0) {
            this.idleTicks--;
            return;
        }
        if (this.placer == null) {
            this.deny(level, RegenResult.DENIED_NO_PLACER);
            return;
        }
        if (!this.hasRoom()) {
            return;
        }
        RegenResult denial = ChunkRegenService.authorizeMachine(level, ChunkPos.containing(this.worldPosition), this.placer);
        if (denial != null) {
            this.deny(level, denial);
            return;
        }
        this.lastDenial = null;

        int volume = 256 * level.getHeight();
        if (volume <= 0) {
            return;
        }
        int quota = this.blocksPerTick();
        int eaten = 0;
        int steps = 0;
        while (eaten < quota && steps < volume) {
            steps++;
            int index = this.scanIndex;
            this.scanIndex = (this.scanIndex + 1) % volume;
            BlockPos target = this.positionFor(level, index);
            BlockState targetState = level.getBlockState(target);
            if (this.isSpared(targetState, target)) {
                continue;
            }
            if (!this.canVoid(level) || !this.hasRoom()) {
                this.scanIndex = index;
                break;
            }
            this.eat(level, target);
            eaten++;
        }
        if (eaten == 0) {
            this.idleTicks = 20;
            this.flushRefresh(level);
        }
    }

    private void chargeHeldItem() {
        ItemStack stack = this.items.get(CHARGE_SLOT);
        if (stack.isEmpty() || this.energy.getAmountAsInt() <= 0) {
            return;
        }
        ItemAccess access = ItemAccess.forHandlerIndexStrict(VanillaContainerWrapper.of(this), CHARGE_SLOT);
        EnergyHandler itemEnergy = access.getCapability(Capabilities.Energy.ITEM);
        if (itemEnergy == null) {
            return;
        }
        EnergyHandlerUtil.move(this.energy, itemEnergy, this.energy.getAmountAsInt(), null);
    }

    private void eat(ServerLevel level, BlockPos target) {
        if (!this.canVoid(level)) {
            return;
        }
        int gained = Math.min(this.yield(), this.freeEnergy());
        if (gained <= 0) {
            return;
        }
        level.setBlock(target, Blocks.AIR.defaultBlockState(), CLEAR_FLAGS);
        this.energy.set(this.energy.getAmountAsInt() + gained);
        this.sinceRefresh++;
        if (this.sinceRefresh >= 20) {
            this.flushRefresh(level);
        }
        this.setChanged();
    }

    private void flushRefresh(ServerLevel level) {
        if (this.sinceRefresh <= 0) {
            return;
        }
        this.sinceRefresh = 0;
        ChunkRemoveService.refreshAround(level, ChunkPos.containing(this.worldPosition));
    }

    private boolean hasRoom() {
        return this.freeEnergy() > 0;
    }

    private int freeEnergy() {
        return CAPACITY - this.energy.getAmountAsInt();
    }

    private boolean canVoid(ServerLevel level) {
        if (this.retired || this.isRemoved()) {
            return false;
        }
        if (level.getChunkSource().getChunkNow(this.worldPosition.getX() >> 4, this.worldPosition.getZ() >> 4) == null) {
            return false;
        }
        return level.getBlockEntity(this.worldPosition) == this;
    }

    private void retire() {
        this.retired = true;
        this.suppressDrops = true;
        this.items.clear();
        this.netherite = 0;
        this.netherStar = false;
        this.setRemoved();
    }

    private SavedMachine capture() {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : this.items) {
            copies.add(stack.copy());
        }
        boolean powered = this.getBlockState().getValue(ChunkDevourerBlock.POWERED);
        return new SavedMachine(this.worldPosition.immutable(), powered, this.energy.getAmountAsInt(), this.netherite, this.netherStar, this.hasteTicks, this.scanIndex, this.placer, List.copyOf(copies));
    }

    private void applySaved(SavedMachine saved) {
        this.energy.set(Math.clamp(saved.energy(), 0, CAPACITY));
        this.netherite = Math.clamp(saved.netherite(), 0, MAX_NETHERITE);
        this.netherStar = saved.netherStar();
        this.hasteTicks = Math.max(0, saved.hasteTicks());
        this.scanIndex = Math.max(0, saved.scanIndex());
        this.placer = saved.placer();
        for (int slot = 0; slot < SLOT_COUNT && slot < saved.items().size(); slot++) {
            this.items.set(slot, saved.items().get(slot).copy());
        }
        this.setChanged();
    }

    private boolean isSpared(BlockState state, BlockPos pos) {
        if (state.isAir() || state.is(Blocks.BEDROCK) || this.isBesideMachine(pos) || this.isModBlock(state)) {
            return true;
        }
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack filter = this.items.get(slot);
            if (filter.getItem() instanceof BlockItem blockItem && state.is(blockItem.getBlock())) {
                return true;
            }
        }
        return false;
    }

    private boolean isModBlock(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id != null && ChunkRegeneratorMod.MOD_ID.equals(id.getNamespace());
    }

    private boolean isBesideMachine(BlockPos pos) {
        return Math.abs(pos.getX() - this.worldPosition.getX()) <= 1
                && Math.abs(pos.getY() - this.worldPosition.getY()) <= 1
                && Math.abs(pos.getZ() - this.worldPosition.getZ()) <= 1;
    }

    private BlockPos positionFor(ServerLevel level, int index) {
        ChunkPos chunk = ChunkPos.containing(this.worldPosition);
        int fromTop = index / 256;
        int remainder = index % 256;
        int y = level.getMaxY() - 1 - fromTop;
        int localZ = remainder / 16;
        int localX = remainder % 16;
        return new BlockPos(chunk.getMinBlockX() + localX, y, chunk.getMinBlockZ() + localZ);
    }

    private void deny(ServerLevel level, RegenResult denial) {
        this.idleTicks = 20;
        if (this.lastDenial == denial || this.placer == null || denial.messageKey() == null) {
            this.lastDenial = denial;
            return;
        }
        this.lastDenial = denial;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(this.placer);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(denial.messageKey()));
        }
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : this.items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack removed = ContainerHelper.removeItem(this.items, slot, count);
        if (!removed.isEmpty()) {
            this.setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.setItem(slot, stack, false);
    }

    @Override
    public void setItem(int slot, ItemStack stack, boolean insideTransaction) {
        if (slot == FEED_SLOT) {
            this.tryFeed(stack, true);
        }
        this.items.set(slot, stack);
        if (!stack.isEmpty() && slot != FEED_SLOT) {
            stack.limitSize(this.getMaxStackSize(stack));
        }
        if (!insideTransaction) {
            this.setChanged();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == FEED_SLOT) {
            return stack.is(Items.NETHERITE_INGOT) || stack.is(Items.NETHER_STAR) || stack.is(Items.GOLDEN_APPLE);
        }
        if (slot == CHARGE_SLOT) {
            return true;
        }
        return stack.getItem() instanceof BlockItem;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("chunkregenerator.screen.devourer");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChunkDevourerMenu(containerId, inventory, this, this);
    }

    @Override
    public int get(int index) {
        int stored = this.energy.getAmountAsInt();
        return switch (index) {
            case 0 -> stored & 0xFFFF;
            case 1 -> (stored >>> 16) & 0xFFFF;
            case 2 -> this.netherite;
            case 3 -> this.netherStar ? 1 : 0;
            case 4 -> this.hasteTicks;
            default -> 0;
        };
    }

    @Override
    public void set(int index, int value) {
    }

    @Override
    public int getCount() {
        return 5;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this.suppressDrops) {
            return;
        }
        super.preRemoveSideEffects(pos, state);
        if (this.level == null) {
            return;
        }
        if (this.netherite > 0) {
            Block.popResource(this.level, pos, new ItemStack(Items.NETHERITE_INGOT, this.netherite));
        }
        if (this.netherStar) {
            Block.popResource(this.level, pos, new ItemStack(Items.NETHER_STAR));
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("Energy", this.energy.getAmountAsInt());
        output.putInt("Netherite", this.netherite);
        output.putInt("NetherStar", this.netherStar ? 1 : 0);
        output.putInt("Haste", this.hasteTicks);
        output.putInt("Scan", this.scanIndex);
        if (this.placer != null) {
            output.store("Placer", UUIDUtil.CODEC, this.placer);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input, this.items);
        this.energy.set(Math.max(0, input.getIntOr("Energy", 0)));
        this.netherite = Math.clamp(input.getIntOr("Netherite", 0), 0, MAX_NETHERITE);
        this.netherStar = input.getIntOr("NetherStar", 0) != 0;
        this.hasteTicks = Math.max(0, input.getIntOr("Haste", 0));
        if (this.netherite == 0 && input.getIntOr("Upgraded", 0) != 0) {
            this.netherite = 1;
        }
        this.scanIndex = Math.max(0, input.getIntOr("Scan", 0));
        this.placer = input.read("Placer", UUIDUtil.CODEC).orElse(null);
    }

    private final class DevourerEnergy extends SimpleEnergyHandler {
        private DevourerEnergy() {
            super(CAPACITY, 0, CAPACITY, 0);
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            ChunkDevourerBlockEntity.this.setChanged();
        }
    }

    public record SavedMachine(
            BlockPos pos,
            boolean powered,
            int energy,
            int netherite,
            boolean netherStar,
            int hasteTicks,
            int scanIndex,
            @Nullable UUID placer,
            List<ItemStack> items) {
        public void restore(ServerLevel level) {
            BlockState state = ModBlocks.CHUNK_DEVOURER.get().defaultBlockState().setValue(ChunkDevourerBlock.POWERED, this.powered);
            level.setBlock(this.pos, state, Block.UPDATE_CLIENTS);
            BlockState placed = level.getBlockState(this.pos);
            if (placed.is(ModBlocks.CHUNK_DEVOURER.get()) && placed.getValue(ChunkDevourerBlock.POWERED) != this.powered) {
                level.setBlock(this.pos, placed.setValue(ChunkDevourerBlock.POWERED, this.powered), Block.UPDATE_CLIENTS);
            }
            if (level.getBlockEntity(this.pos) instanceof ChunkDevourerBlockEntity devourer) {
                devourer.applySaved(this);
            }
        }
    }
}
