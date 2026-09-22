package com.zanon.chunkregenerator.client;

import java.util.ArrayList;
import java.util.List;

import com.zanon.chunkregenerator.item.ChunkAnalyzer;
import com.zanon.chunkregenerator.network.SetAnalyzerTagsPayload;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class AnalyzerTagsScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int VISIBLE_ROWS = 4;
    private static final int ROW_HEIGHT = 22;
    private static final int NEON = 0xFF3DDCFF;
    private static final int TEXT = 0xFFE7F6F8;
    private static final int MUTED = 0xFF8AA0A6;
    private static final String[][] ORE_CHIPS = {
            {"Coal", "minecraft:coal_ores"},
            {"Iron", "minecraft:iron_ores"},
            {"Copper", "minecraft:copper_ores"},
            {"Gold", "minecraft:gold_ores"},
            {"Redstone", "minecraft:redstone_ores"},
            {"Lapis", "minecraft:lapis_ores"},
            {"Diamond", "minecraft:diamond_ores"},
            {"Emerald", "minecraft:emerald_ores"}
    };

    private final boolean mainHand;
    private final List<String> tags = new ArrayList<>();
    private final List<EditBox> rowBoxes = new ArrayList<>();
    private EditBox addBox;
    private int scroll;

    public AnalyzerTagsScreen(boolean mainHand, List<String> current) {
        super(Component.translatable("chunkregenerator.screen.analyzer_tags"));
        this.mainHand = mainHand;
        this.tags.addAll(current);
    }

    @Override
    protected void init() {
        this.rowBoxes.clear();
        int left = this.panelLeft();
        int listTop = this.panelTop() + 28;
        int shown = this.shownRows();
        for (int row = 0; row < shown; row++) {
            int index = this.scroll + row;
            int y = listTop + row * ROW_HEIGHT;
            EditBox box = this.field(left + 8, y + 2, PANEL_WIDTH - 44, this.tags.get(index));
            this.rowBoxes.add(box);
            this.addRenderableWidget(box);
            int removeIndex = index;
            this.addRenderableWidget(NeonButton.create(Component.literal("x"), button -> this.removeTag(removeIndex), left + PANEL_WIDTH - 28, y + 1, 20, 18, false));
        }

        int addY = listTop + this.listHeight() + 8;
        this.addBox = this.field(left + 8, addY + 2, PANEL_WIDTH - 72, "");
        this.addBox.setHint(Component.translatable("chunkregenerator.screen.analyzer_add_hint").withStyle(Style.EMPTY.withColor(0x8AA0A6)));
        this.addRenderableWidget(this.addBox);
        this.addRenderableWidget(NeonButton.create(
                Component.translatable("chunkregenerator.screen.analyzer_add"),
                button -> this.addTag(),
                left + PANEL_WIDTH - 60,
                addY,
                52,
                20,
                false));

        int chipTop = addY + 36;
        int chipWidth = 74;
        for (int i = 0; i < ORE_CHIPS.length; i++) {
            String label = ORE_CHIPS[i][0];
            String id = ORE_CHIPS[i][1];
            int x = left + 8 + (i % 4) * (chipWidth + 4);
            int y = chipTop + (i / 4) * 22;
            this.addRenderableWidget(NeonButton.create(Component.literal(label), button -> this.toggle(id), x, y, chipWidth, 18, this.tags.contains(id)));
        }

        int actions = chipTop + 50;
        this.addRenderableWidget(NeonButton.create(Component.translatable("chunkregenerator.screen.analyzer_clear"), button -> this.clearTags(), left + 8, actions, 70, 20, false));
        this.addRenderableWidget(NeonButton.create(Component.translatable("gui.cancel"), button -> this.onClose(), left + 84, actions, 70, 20, false));
        this.addRenderableWidget(NeonButton.create(Component.translatable("gui.done"), button -> this.save(), left + PANEL_WIDTH - 78, actions, 70, 20, true));

        if (this.tags.size() > VISIBLE_ROWS) {
            this.addRenderableWidget(NeonButton.create(Component.literal("^"), button -> this.scrollBy(-1), left + PANEL_WIDTH - 28, this.panelTop() + 4, 20, 16, false));
        }
    }

    @Override
    protected void setInitialFocus() {
        if (this.addBox != null && this.tags.isEmpty()) {
            this.setInitialFocus(this.addBox);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.panelLeft();
        int top = this.panelTop();
        int bottom = top + this.panelHeight();
        int right = left + PANEL_WIDTH;
        graphics.fill(left, top, right, bottom, 0xC014181C);
        graphics.fill(left, top, right, top + 1, NEON);
        graphics.fill(left, bottom - 1, right, bottom, NEON);
        graphics.fill(left, top, left + 1, bottom, NEON);
        graphics.fill(right - 1, top, right, bottom, NEON);
        graphics.centeredText(this.font, this.title, this.width / 2, top + 6, TEXT);
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.analyzer_count", this.tags.size(), 16), left + 8, top + 16, MUTED);
        if (this.tags.isEmpty()) {
            graphics.text(this.font, Component.translatable("chunkregenerator.screen.analyzer_empty"), left + 8, top + 32, MUTED);
        }
        for (EditBox box : this.rowBoxes) {
            this.drawField(graphics, box);
        }
        if (this.addBox != null) {
            this.drawField(graphics, this.addBox);
        }
        graphics.text(this.font, Component.translatable("chunkregenerator.screen.analyzer_ores"), left + 8, this.panelTop() + 28 + this.listHeight() + 28, MUTED);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && this.tags.size() > VISIBLE_ROWS) {
            this.scrollBy(scrollY > 0.0D ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private EditBox field(int x, int y, int width, String value) {
        EditBox box = new EditBox(this.font, x, y, width, 16, Component.translatable("chunkregenerator.screen.analyzer_tag"));
        box.setBordered(false);
        box.setMaxLength(128);
        box.setValue(value);
        box.setTextColor(NEON);
        box.setTextColorUneditable(NEON);
        box.setInvertHighlightedTextColor(false);
        box.addFormatter((text, offset) -> FormattedCharSequence.forward(text, Style.EMPTY.withColor(0x3DDCFF)));
        return box;
    }

    private void drawField(GuiGraphicsExtractor graphics, EditBox box) {
        int x = box.getX() - 4;
        int y = box.getY() - 3;
        int right = box.getX() + box.getWidth() + 2;
        int bottom = box.getY() + box.getHeight() + 1;
        boolean invalid = !box.getValue().isBlank() && ChunkAnalyzer.sanitize(List.of(box.getValue())).isEmpty();
        int border = invalid ? 0xFFFF5555 : (box.isFocused() ? NEON : 0xFF245E68);
        graphics.fill(x, y, right, bottom, 0xF0000000);
        graphics.fill(x, y, right, y + 1, border);
        graphics.fill(x, bottom - 1, right, bottom, border);
        graphics.fill(x, y, x + 1, bottom, border);
        graphics.fill(right - 1, y, right, bottom, border);
    }

    private void addTag() {
        this.readRows();
        if (this.addBox == null || this.tags.size() >= 16) {
            return;
        }
        String value = this.addBox.getValue().trim();
        if (value.startsWith("#")) {
            value = value.substring(1).trim();
        }
        if (value.isEmpty() || this.tags.contains(value)) {
            return;
        }
        this.tags.add(value);
        this.scroll = Math.max(0, this.tags.size() - VISIBLE_ROWS);
        this.rebuildWidgets();
    }

    private void removeTag(int index) {
        this.readRows();
        if (index >= 0 && index < this.tags.size()) {
            this.tags.remove(index);
        }
        this.scroll = Math.min(this.scroll, Math.max(0, this.tags.size() - VISIBLE_ROWS));
        this.rebuildWidgets();
    }

    private void toggle(String id) {
        this.readRows();
        if (this.tags.contains(id)) {
            this.tags.remove(id);
        } else if (this.tags.size() < 16) {
            this.tags.add(id);
            this.scroll = Math.max(0, this.tags.size() - VISIBLE_ROWS);
        }
        this.scroll = Math.min(this.scroll, Math.max(0, this.tags.size() - VISIBLE_ROWS));
        this.rebuildWidgets();
    }

    private void clearTags() {
        this.tags.clear();
        this.scroll = 0;
        this.rebuildWidgets();
    }

    private void scrollBy(int delta) {
        this.readRows();
        int max = Math.max(0, this.tags.size() - VISIBLE_ROWS);
        this.scroll = Math.clamp(this.scroll + delta, 0, max);
        this.rebuildWidgets();
    }

    private void readRows() {
        for (int i = 0; i < this.rowBoxes.size(); i++) {
            int index = this.scroll + i;
            if (index < this.tags.size()) {
                this.tags.set(index, this.rowBoxes.get(i).getValue().trim());
            }
        }
    }

    private void save() {
        this.readRows();
        List<String> parsed = new ArrayList<>();
        for (String tag : this.tags) {
            if (parsed.size() >= 16) {
                break;
            }
            String trimmed = tag.trim();
            if (trimmed.startsWith("#")) {
                trimmed = trimmed.substring(1).trim();
            }
            if (!trimmed.isEmpty()) {
                parsed.add(trimmed);
            }
        }
        ClientPacketDistributor.sendToServer(new SetAnalyzerTagsPayload(this.mainHand, parsed));
        this.onClose();
    }

    private int shownRows() {
        return Math.min(VISIBLE_ROWS, Math.max(0, this.tags.size() - this.scroll));
    }

    private int listHeight() {
        return this.tags.isEmpty() ? 16 : this.shownRows() * ROW_HEIGHT;
    }

    private int panelHeight() {
        return 28 + this.listHeight() + 8 + 20 + 16 + 44 + 8 + 20 + 12;
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return Math.max(12, (this.height - this.panelHeight()) / 2);
    }
}
