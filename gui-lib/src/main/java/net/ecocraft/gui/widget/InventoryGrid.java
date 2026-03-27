package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Reusable inventory grid widget that displays a player's inventory as a grid
 * of clickable item slots with optional scrollbar, selection, and tooltips.
 *
 * <p>Usage:
 * <pre>
 * InventoryGrid grid = InventoryGrid.builder()
 *     .inventory(player.getInventory())
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

    // --- Fields ---

    private final Container inventory;
    private final int columns;
    private final SlotSize slotSize;
    private final boolean scrollable;
    private final Theme theme;
    private @Nullable Consumer<Integer> onSlotClicked;

    private int selectedSlot = -1;
    private int hoveredSlot = -1;

    // Scrollbar
    private final Scrollbar scrollbar;
    private int scrollOffset = 0; // first visible row index

    private static final int SLOT_PADDING = 2;

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Container inventory;
        private int columns = 9;
        private SlotSize slotSize = SlotSize.MEDIUM;
        private boolean scrollable = true;
        private Theme theme = Theme.dark();
        private Consumer<Integer> onSlotClicked;

        public Builder inventory(Container inventory) {
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
        this.scrollbar = new Scrollbar(0, 0, 0, theme);
    }

    /**
     * Sets the position and dimensions of this grid, and recalculates
     * the scrollbar geometry.
     */
    public void setBounds(int x, int y, int width, int height) {
        setX(x);
        setY(y);
        this.width = width;
        this.height = height;
        updateScrollbar();
    }

    // --- Selection ---

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        this.selectedSlot = slot;
    }

    // --- Refresh ---

    /**
     * Re-reads all items from the inventory. Call this when the inventory
     * changes so the grid reflects the current state. The inventory Container
     * is read live each frame during render, so this mainly resets scroll/hover
     * state as needed.
     */
    public void refresh() {
        // The grid reads inventory live in renderWidget, so refresh is mostly
        // about resetting transient state and updating scrollbar in case
        // container size changed (mod inventories).
        updateScrollbar();
    }

    // --- Layout helpers ---

    private int cellSize() {
        return slotSize.pixels + SLOT_PADDING;
    }

    private int totalSlots() {
        return inventory.getContainerSize();
    }

    private int totalRows() {
        return (totalSlots() + columns - 1) / columns;
    }

    private int visibleRows() {
        return Math.max(1, height / cellSize());
    }

    private boolean needsScrollbar() {
        return scrollable && totalRows() > visibleRows();
    }

    private int gridWidth() {
        int base = columns * cellSize() - SLOT_PADDING;
        return needsScrollbar() ? base : base;
    }

    private int availableWidth() {
        return needsScrollbar() ? width - 10 : width; // 10 = scrollbar width + gap
    }

    private void updateScrollbar() {
        if (needsScrollbar()) {
            float ratio = (float) visibleRows() / totalRows();
            scrollbar.setContentRatio(ratio);
            scrollbar.setX(getX() + width - 8);
            scrollbar.setY(getY());
            scrollbar.setHeight(height);
        }
        clampScrollOffset();
    }

    private void clampScrollOffset() {
        int maxOffset = Math.max(0, totalRows() - visibleRows());
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxOffset));
    }

    private int scrollOffsetFromScrollbar() {
        int maxOffset = Math.max(0, totalRows() - visibleRows());
        return Math.round(scrollbar.getScrollValue() * maxOffset);
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (needsScrollbar()) {
            scrollOffset = scrollOffsetFromScrollbar();
        }

        int gw = gridWidth();
        int aw = availableWidth();
        int offsetX = getX() + (aw - gw) / 2;
        int offsetY = getY();
        int cell = cellSize();
        int visRows = visibleRows();

        hoveredSlot = -1;

        // Tooltip data (rendered last, after all slots)
        ItemStack tooltipStack = null;
        int tooltipMouseX = 0, tooltipMouseY = 0;

        for (int row = 0; row < visRows; row++) {
            int dataRow = row + scrollOffset;
            for (int col = 0; col < columns; col++) {
                int slotIndex = dataRow * columns + col;
                if (slotIndex >= totalSlots()) break;

                int sx = offsetX + col * cell;
                int sy = offsetY + row * cell;
                int size = slotSize.pixels;

                // Read item live from inventory
                ItemStack stack = inventory.getItem(slotIndex);
                boolean isEmpty = stack.isEmpty();
                boolean isSelected = (slotIndex == selectedSlot);
                boolean isHover = mouseX >= sx && mouseX < sx + size
                        && mouseY >= sy && mouseY < sy + size;

                if (isHover) {
                    hoveredSlot = slotIndex;
                }

                // Background
                int bgColor = isEmpty ? theme.bgDarkest : theme.bgDark;
                graphics.fill(sx, sy, sx + size, sy + size, bgColor);

                // Border
                int borderColor;
                if (isSelected) {
                    borderColor = theme.accent;
                } else if (isHover) {
                    borderColor = theme.borderLight;
                } else {
                    borderColor = theme.border;
                }
                // Top
                graphics.fill(sx, sy, sx + size, sy + 1, borderColor);
                // Bottom
                graphics.fill(sx, sy + size - 1, sx + size, sy + size, borderColor);
                // Left
                graphics.fill(sx, sy, sx + 1, sy + size, borderColor);
                // Right
                graphics.fill(sx + size - 1, sy, sx + size, sy + size, borderColor);

                // Render item
                if (!isEmpty) {
                    int itemX = sx + (size - 16) / 2;
                    int itemY = sy + (size - 16) / 2;
                    graphics.renderItem(stack, itemX, itemY);
                    graphics.renderItemDecorations(Minecraft.getInstance().font, stack, itemX, itemY);
                }

                // Track hover for tooltip (rendered after all slots)
                if (isHover && !isEmpty) {
                    tooltipStack = stack;
                    tooltipMouseX = mouseX;
                    tooltipMouseY = mouseY;
                }
            }
        }

        // Scrollbar
        if (needsScrollbar()) {
            scrollbar.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        // Tooltip last so it draws on top
        if (tooltipStack != null) {
            graphics.renderTooltip(Minecraft.getInstance().font, tooltipStack, tooltipMouseX, tooltipMouseY);
        }
    }

    // --- Input handling ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        // Scrollbar click
        if (needsScrollbar() && scrollbar.isMouseOver(mouseX, mouseY)) {
            return scrollbar.mouseClicked(mouseX, mouseY, button);
        }

        // Slot click
        if (button == 0 && hoveredSlot >= 0) {
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
