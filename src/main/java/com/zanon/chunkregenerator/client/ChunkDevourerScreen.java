package com.zanon.chunkregenerator.client;

import com.zanon.chunkregenerator.block.entity.ChunkDevourerBlockEntity;
import com.zanon.chunkregenerator.inventory.ChunkDevourerMenu;
import com.zanon.chunkregenerator.registry.ModMenus;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ChunkDevourerScreen extends AbstractContainerScreen<ChunkDevourerMenu> {
    private static final int NEON = 0xFF3DDCFF;
    private static final int NEON_DIM = 0xFF1C6C78;
    private static final int TEXT = 0xFFE7F6F8;
    private static final int MUTED = 0xFF8AA0A6;
    private static final int PANEL = 0xE8101418;
    private static final int CARD = 0xF0141A20;
    private static final int FIELD = 0xFF07090C;
    private static final int BAR = 0xFF145E78;

    public ChunkDevourerScreen(ChunkDevourerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 196, 308);
        this.titleLabelX = 12;
        this.titleLabelY = 8;
        this.inventoryLabelX = 17;
        this.inventoryLabelY = 214;
    }

    public static void register(RegisterMenuScreensEvent event) {
        event.register(ModMenus.CHUNK_DEVOURER.get(), ChunkDevourerScreen::new);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.imageWidth, this.imageHeight, PANEL);
        border(graphics, 0, 0, this.imageWidth, this.imageHeight, NEON);

        card(graphics, 8, 24, this.imageWidth - 8, 73);
        card(graphics, 8, 79, this.imageWidth - 8, 151);
        card(graphics, 8, 157, this.imageWidth - 8, 206);

        for (Slot slot : this.menu.slots) {
            boolean machine = slot.index < ChunkDevourerBlockEntity.SLOT_COUNT;
            int edge = machine ? NEON : NEON_DIM;
            graphics.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y + 17, edge);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, FIELD);
        }

        int energy = this.menu.energy();
        int barX = 42;
        int barY = 165;
        int barRight = this.imageWidth - 16;
        int filled = (int) ((long) (barRight - barX) * energy / ChunkDevourerBlockEntity.CAPACITY);
        graphics.fill(barX, barY, barRight, barY + 16, FIELD);
        graphics.fill(barX, barY, barX + filled, barY + 16, BAR);
        border(graphics, barX, barY, barRight, barY + 16, NEON_DIM);

        int haste = this.menu.hasteTicks();
        int seconds = (haste + 19) / 20;
        Component speed = haste > 0
                ? Component.translatable("chunkregenerator.screen.devourer_haste", seconds / 60, String.format("%02d", seconds % 60))
                : Component.translatable("chunkregenerator.screen.devourer_speed", this.menu.blocksPerTick());
        Component star = this.menu.netherStar()
                ? Component.translatable("chunkregenerator.screen.devourer_star")
                : Component.translatable("chunkregenerator.screen.devourer_no_star");

        graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, NEON, false);
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.devourer_keep"), 16, 32, MUTED, false);
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.devourer_feed"), 16, 87, MUTED, false);
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.devourer_netherite", this.menu.netherite()), 42, 106, TEXT, false);
        graphics.text(this.font, star, 42, 120, this.menu.netherStar() ? NEON : MUTED, false);
        graphics.text(this.font, speed, 42, 134, haste > 0 ? NEON : MUTED, false);
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.devourer_energy", energy, ChunkDevourerBlockEntity.CAPACITY), barX + 4, barY + 4, TEXT, false);
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.devourer_rate", this.menu.yield()), 42, 189, NEON, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MUTED, false);
    }

    private static void card(GuiGraphicsExtractor graphics, int x, int y, int right, int bottom) {
        graphics.fill(x, y, right, bottom, CARD);
        border(graphics, x, y, right, bottom, NEON_DIM);
    }

    private static void border(GuiGraphicsExtractor graphics, int x, int y, int right, int bottom, int color) {
        graphics.fill(x, y, right, y + 1, color);
        graphics.fill(x, bottom - 1, right, bottom, color);
        graphics.fill(x, y, x + 1, bottom, color);
        graphics.fill(right - 1, y, right, bottom, color);
    }
}
