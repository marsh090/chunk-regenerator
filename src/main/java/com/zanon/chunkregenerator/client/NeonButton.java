package com.zanon.chunkregenerator.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

final class NeonButton extends Button {
    static final int NEON = 0xFF3DDCFF;
    static final int NEON_DIM = 0xFF1C6C78;
    static final int FILL = 0xCC101418;
    static final int FILL_HOT = 0xE0182228;
    static final int TEXT = 0xFFE7F6F8;

    private final boolean selected;

    private NeonButton(Builder builder, boolean selected) {
        super(builder);
        this.selected = selected;
    }

    static NeonButton create(Component message, OnPress onPress, int x, int y, int width, int height, boolean selected) {
        return new NeonButton(Button.builder(message, onPress).bounds(x, y, width, height), selected);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int right = x + this.getWidth();
        int bottom = y + this.getHeight();
        boolean hot = this.isHoveredOrFocused();
        int border = this.selected || hot ? NEON : NEON_DIM;
        graphics.fill(x, y, right, bottom, hot ? FILL_HOT : FILL);
        graphics.fill(x, y, right, y + 1, border);
        graphics.fill(x, bottom - 1, right, bottom, border);
        graphics.fill(x, y, x + 1, bottom, border);
        graphics.fill(right - 1, y, right, bottom, border);

        Font font = Minecraft.getInstance().font;
        Component message = this.getMessage();
        int textWidth = font.width(message);
        int textX = x + (this.getWidth() - textWidth) / 2;
        int textY = y + (this.getHeight() - 8) / 2;
        graphics.text(font, message, textX, textY, this.selected || hot ? NEON : TEXT);
    }
}
