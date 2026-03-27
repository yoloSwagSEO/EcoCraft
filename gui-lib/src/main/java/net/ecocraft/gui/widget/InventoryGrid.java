package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Reusable inventory grid widget that displays a player's inventory
 * organized by sections (Main, Hotbar, Armor, Offhand, Other).
 * Each section has a label and can be shown/hidden independently.
 *
 * <p>Usage:
 * <pre>
 * InventoryGrid grid = InventoryGrid.builder()
 *     .inventory(player.getInventory())
 *     .showMain(true)        // slots 9-35
 *     .showHotbar(true)      // slots 0-8
 *     .showArmor(false)      // slots 36-39
 *     .showOffhand(false)    // slot 40
 *     .showOther(true)       // mod slots 41+
 *     .columns(9)
 *     .slotSize(SlotSize.MEDIUM)
 *     .scrollable(true)
 *     .onSlotClicked(slotIndex -> { ... })
 *     .theme(Theme.dark())
 *     .build();
 * grid.setBounds(x, y, width, height);
 * </pre>
 */
public class InventoryGrid extends AbstractWidget {

    // --- SlotSize enum ---

    public enum SlotSize {
        SMALL(16), MEDIUM(20), BIG(24);

        public final int pixels;

        SlotSize(int pixels) {
            this.pixels = pixels;
        }
    }

    // --- Section ---

    private record Section(String label, List<Integer> slotIndices) {}

    // --- Fields ---

    private final Inventory inventory;
    private final int columns;
    private final SlotSize slotSize;
    private final boolean scrollable;
    private final Theme theme;
    private @Nullable Consumer<Integer> onSlotClicked;

    private final boolean showMain;
    private final boolean showHotbar;
    private final boolean showArmor;
    private final boolean showOffhand;
    private final boolean showOther;

    private int selectedSlot = -1;
    private int hoveredSlot = -1;

    // Scrollbar
    private final Scrollbar scrollbar;

    // Computed layout
    private List<Section> sections = new ArrayList<>();
    private int totalContentHeight = 0;
    private int scrollPixelOffset = 0;

    private static final int SLOT_PADDING = 2;
    private static final int SECTION_LABEL_HEIGHT = 12;
    private static final int SECTION_GAP = 6;

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Inventory inventory;
        private int columns = 9;
        private SlotSize slotSize = SlotSize.MEDIUM;
        private boolean scrollable = true;
        private Theme theme = Theme.dark();
        private Consumer<Integer> onSlotClicked;
        private boolean showMain = true;
        private boolean showHotbar = true;
        private boolean showArmor = false;
        private boolean showOffhand = false;
        private boolean showOther = true;

        public Builder inventory(Inventory inventory) {
            this.inventory = inventory;
            return this;
        }

        public Builder columns(int columns) {
            this.columns = columns;
            return this;
        }

        public Builder slotSize(SlotSize slotSize) {
            this.slotSize = slotSize;
            return this;
        }

        public Builder scrollable(boolean scrollable) {
            this.scrollable = scrollable;
            return this;
        }

        public Builder onSlotClicked(Consumer<Integer> callback) {
            this.onSlotClicked = callback;
            return this;
        }

        public Builder theme(Theme theme) {
            this.theme = theme;
            return this;
        }

        public Builder showMain(boolean show) {
            this.showMain = show;
            return this;
        }

        public Builder showHotbar(boolean show) {
            this.showHotbar = show;
            return this;
        }

        public Builder showArmor(boolean show) {
            this.showArmor = show;
            return this;
        }

        public Builder showOffhand(boolean show) {
            this.showOffhand = show;
            return this;
        }

        public Builder showOther(boolean show) {
            this.showOther = show;
            return this;
        }

        public InventoryGrid build() {
            if (inventory == null) {
                throw new IllegalStateException("inventory is required");
            }
            return new InventoryGrid(this);
        }
    }

    // --- Constructor ---

    private InventoryGrid(Builder builder) {
        super(0, 0, 0, 0, Component.empty());
        this.inventory = builder.inventory;
        this.columns = builder.columns;
        this.slotSize = builder.slotSize;
        this.scrollable = builder.scrollable;
        this.theme = builder.theme;
        this.onSlotClicked = builder.onSlotClicked;
        this.showMain = builder.showMain;
        this.showHotbar = builder.showHotbar;
        this.showArmor = builder.showArmor;
        this.showOffhand = builder.showOffhand;
        this.showOther = builder.showOther;
        this.scrollbar = new Scrollbar(0, 0, 0, theme);
        buildSections();
    }

    // --- Section building ---

    private void buildSections() {
        sections.clear();
        int containerSize = inventory.getContainerSize();

        if (showMain) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 9; i < Math.min(36, containerSize); i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section("Inventaire", slots));
        }

        if (showHotbar) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < Math.min(9, containerSize); i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section("Hotbar", slots));
        }

        if (showArmor) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 36; i < Math.min(40, containerSize); i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section("Armure", slots));
        }

        if (showOffhand && containerSize > 40) {
            List<Integer> slots = new ArrayList<>();
            slots.add(40);
            sections.add(new Section("Main gauche", slots));
        }

        if (showOther && containerSize > 41) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 41; i < containerSize; i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section("Autre", slots));
        }

        recalcContentHeight();
    }

    private void recalcContentHeight() {
        int cell = cellSize();
        totalContentHeight = 0;
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) totalContentHeight += SECTION_GAP;
            totalContentHeight += SECTION_LABEL_HEIGHT;
            int rows = (sections.get(i).slotIndices.size() + columns - 1) / columns;
            totalContentHeight += rows * cell;
        }
    }

    // --- Public API ---

    public void setBounds(int x, int y, int width, int height) {
        setX(x);
        setY(y);
        this.width = width;
        this.height = height;
        updateScrollbar();
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        this.selectedSlot = slot;
    }

    public void refresh() {
        buildSections();
        updateScrollbar();
    }

    // --- Layout helpers ---

    private int cellSize() {
        return slotSize.pixels + SLOT_PADDING;
    }

    private boolean needsScrollbar() {
        return scrollable && totalContentHeight > height;
    }

    private int availableWidth() {
        return needsScrollbar() ? width - 10 : width;
    }

    private void updateScrollbar() {
        if (needsScrollbar()) {
            float ratio = (float) height / totalContentHeight;
            scrollbar.setContentRatio(ratio);
            scrollbar.setX(getX() + width - 8);
            scrollbar.setY(getY());
            scrollbar.setHeight(height);
        }
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int cell = cellSize();
        int aw = availableWidth();

        // Calculate scroll offset in pixels
        if (needsScrollbar()) {
            scrollPixelOffset = Math.round(scrollbar.getScrollValue() * Math.max(0, totalContentHeight - height));
        } else {
            scrollPixelOffset = 0;
        }

        hoveredSlot = -1;
        ItemStack tooltipStack = null;
        int tooltipX = 0, tooltipY = 0;

        // Enable scissor to clip content within bounds
        graphics.enableScissor(getX(), getY(), getX() + aw, getY() + height);

        int currentY = getY() - scrollPixelOffset;

        for (int s = 0; s < sections.size(); s++) {
            Section section = sections.get(s);

            if (s > 0) currentY += SECTION_GAP;

            // Section label
            graphics.drawString(font, section.label, getX() + 2, currentY + 2,
                    theme.textGrey, false);
            currentY += SECTION_LABEL_HEIGHT;

            // Grid width centered
            int gridW = Math.min(columns * cell - SLOT_PADDING, aw);
            int gridX = getX() + (aw - gridW) / 2;

            // Slots
            int col = 0;
            for (int slotIndex : section.slotIndices) {
                int sx = gridX + col * cell;
                int sy = currentY;

                // Only process if visible
                if (sy + slotSize.pixels > getY() && sy < getY() + height) {
                    ItemStack stack = inventory.getItem(slotIndex);
                    boolean isEmpty = stack.isEmpty();
                    boolean isSelected = (slotIndex == selectedSlot);
                    boolean isHover = mouseX >= sx && mouseX < sx + slotSize.pixels
                            && mouseY >= sy && mouseY < sy + slotSize.pixels
                            && mouseY >= getY() && mouseY < getY() + height;

                    if (isHover) hoveredSlot = slotIndex;

                    // Background
                    int bgColor = isEmpty ? theme.bgDarkest : theme.bgDark;
                    graphics.fill(sx, sy, sx + slotSize.pixels, sy + slotSize.pixels, bgColor);

                    // Border
                    int borderColor = isSelected ? theme.accent
                            : isHover ? theme.borderLight : theme.border;
                    graphics.fill(sx, sy, sx + slotSize.pixels, sy + 1, borderColor);
                    graphics.fill(sx, sy + slotSize.pixels - 1, sx + slotSize.pixels, sy + slotSize.pixels, borderColor);
                    graphics.fill(sx, sy, sx + 1, sy + slotSize.pixels, borderColor);
                    graphics.fill(sx + slotSize.pixels - 1, sy, sx + slotSize.pixels, sy + slotSize.pixels, borderColor);

                    // Render item
                    if (!isEmpty) {
                        int itemX = sx + (slotSize.pixels - 16) / 2;
                        int itemY = sy + (slotSize.pixels - 16) / 2;
                        graphics.renderItem(stack, itemX, itemY);
                        graphics.renderItemDecorations(font, stack, itemX, itemY);
                    }

                    if (isHover && !isEmpty) {
                        tooltipStack = stack;
                        tooltipX = mouseX;
                        tooltipY = mouseY;
                    }
                }

                col++;
                if (col >= columns) {
                    col = 0;
                    currentY += cell;
                }
            }
            // Move to next row if last row wasn't full
            if (col > 0) currentY += cell;
        }

        graphics.disableScissor();

        // Scrollbar
        if (needsScrollbar()) {
            scrollbar.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        // Tooltip last
        if (tooltipStack != null) {
            graphics.renderTooltip(font, tooltipStack, tooltipX, tooltipY);
        }
    }

    // --- Input handling ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        if (needsScrollbar() && scrollbar.isMouseOver(mouseX, mouseY)) {
            return scrollbar.mouseClicked(mouseX, mouseY, button);
        }

        if (button == 0 && hoveredSlot >= 0) {
            selectedSlot = hoveredSlot;
            if (onSlotClicked != null) {
                onSlotClicked.accept(hoveredSlot);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (needsScrollbar()) {
            return scrollbar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (needsScrollbar()) {
            scrollbar.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (needsScrollbar()) {
            return scrollbar.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
