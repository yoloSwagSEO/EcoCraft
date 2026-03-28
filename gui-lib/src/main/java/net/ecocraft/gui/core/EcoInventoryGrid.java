package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Reusable inventory grid widget (V2) extending {@link BaseWidget}.
 * Displays a player's inventory organized by sections (Main, Hotbar, Armor, Offhand, Other).
 * Each section has a configurable label and can be shown/hidden independently.
 *
 * <p>Usage:
 * <pre>
 * EcoInventoryGrid grid = EcoInventoryGrid.builder()
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
public class EcoInventoryGrid extends BaseWidget {

    // --- SlotSize enum ---

    public enum SlotSize {
        SMALL(16), MEDIUM(20), BIG(24);

        public final int pixels;

        SlotSize(int pixels) {
            this.pixels = pixels;
        }
    }

    // --- Section labels ---

    public static class SectionLabels {
        public final String main;
        public final String hotbar;
        public final String armor;
        public final String offhand;
        public final String other;

        public SectionLabels(String main, String hotbar, String armor, String offhand, String other) {
            this.main = main;
            this.hotbar = hotbar;
            this.armor = armor;
            this.offhand = offhand;
            this.other = other;
        }

        /** Default French labels for backward compatibility. */
        public static SectionLabels defaultLabels() {
            return new SectionLabels("Inventaire", "Hotbar", "Armure", "Main gauche", "Autre");
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
    private final SectionLabels sectionLabels;
    private @Nullable IntConsumer onSlotClicked;

    private final boolean showMain;
    private final boolean showHotbar;
    private final boolean showArmor;
    private final boolean showOffhand;
    private final boolean showOther;

    private int selectedSlot = -1;
    private int hoveredSlot = -1;

    // Scrollbar (child widget)
    private final EcoScrollbar scrollbar;

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
        private IntConsumer onSlotClicked;
        private boolean showMain = true;
        private boolean showHotbar = true;
        private boolean showArmor = false;
        private boolean showOffhand = false;
        private boolean showOther = true;
        private SectionLabels sectionLabels = SectionLabels.defaultLabels();

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

        public Builder onSlotClicked(IntConsumer callback) {
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

        public Builder sectionLabels(SectionLabels labels) {
            this.sectionLabels = labels;
            return this;
        }

        public Builder sectionLabels(String main, String hotbar, String armor, String offhand, String other) {
            this.sectionLabels = new SectionLabels(main, hotbar, armor, offhand, other);
            return this;
        }

        public EcoInventoryGrid build() {
            if (inventory == null) {
                throw new IllegalStateException("inventory is required");
            }
            return new EcoInventoryGrid(this);
        }
    }

    // --- Constructor ---

    private EcoInventoryGrid(Builder builder) {
        super(0, 0, 0, 0);
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
        this.sectionLabels = builder.sectionLabels;
        this.scrollbar = new EcoScrollbar(0, 0, 0, theme);
        addChild(scrollbar);
        buildSections();
    }

    // --- Section building ---

    private void buildSections() {
        sections.clear();
        int containerSize = inventory.getContainerSize();

        if (showMain) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 9; i < Math.min(36, containerSize); i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section(sectionLabels.main, slots));
        }

        if (showHotbar) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < Math.min(9, containerSize); i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section(sectionLabels.hotbar, slots));
        }

        if (showArmor) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 36; i < Math.min(40, containerSize); i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section(sectionLabels.armor, slots));
        }

        if (showOffhand && containerSize > 40) {
            List<Integer> slots = new ArrayList<>();
            slots.add(40);
            sections.add(new Section(sectionLabels.offhand, slots));
        }

        if (showOther && containerSize > 41) {
            List<Integer> slots = new ArrayList<>();
            for (int i = 41; i < containerSize; i++) slots.add(i);
            if (!slots.isEmpty()) sections.add(new Section(sectionLabels.other, slots));
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

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        updateScrollbar();
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public void setSelectedSlot(int slot) {
        this.selectedSlot = slot;
    }

    public void onSlotClicked(IntConsumer callback) {
        this.onSlotClicked = callback;
    }

    public void refresh() {
        buildSections();
        updateScrollbar();
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    // --- Layout helpers ---

    private int cellSize() {
        return slotSize.pixels + SLOT_PADDING;
    }

    private boolean needsScrollbar() {
        return scrollable && totalContentHeight > getHeight();
    }

    private int availableWidth() {
        return needsScrollbar() ? getWidth() - EcoScrollbar.SCROLLBAR_WIDTH - 2 : getWidth();
    }

    private void updateScrollbar() {
        if (needsScrollbar()) {
            float ratio = (float) getHeight() / totalContentHeight;
            scrollbar.setContentRatio(ratio);
            scrollbar.setBounds(
                    getX() + getWidth() - EcoScrollbar.SCROLLBAR_WIDTH,
                    getY(),
                    EcoScrollbar.SCROLLBAR_WIDTH,
                    getHeight()
            );
            scrollbar.setVisible(true);
        } else {
            scrollbar.setVisible(false);
            scrollbar.setScrollValue(0f);
        }
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int cell = cellSize();
        int aw = availableWidth();

        // Calculate scroll offset in pixels
        if (needsScrollbar()) {
            scrollPixelOffset = Math.round(scrollbar.getScrollValue() * Math.max(0, totalContentHeight - getHeight()));
        } else {
            scrollPixelOffset = 0;
        }

        hoveredSlot = -1;
        ItemStack tooltipStack = null;
        int tooltipX = 0, tooltipY = 0;

        // Enable scissor to clip content within bounds
        graphics.enableScissor(getX(), getY(), getX() + aw, getY() + getHeight());

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
                if (sy + slotSize.pixels > getY() && sy < getY() + getHeight()) {
                    ItemStack stack = inventory.getItem(slotIndex);
                    boolean isEmpty = stack.isEmpty();
                    boolean isSelected = (slotIndex == selectedSlot);
                    boolean isHover = mouseX >= sx && mouseX < sx + slotSize.pixels
                            && mouseY >= sy && mouseY < sy + slotSize.pixels
                            && mouseY >= getY() && mouseY < getY() + getHeight();

                    if (isHover) hoveredSlot = slotIndex;

                    // Slot background panel
                    int bgColor = isEmpty ? theme.bgDarkest : theme.bgDark;
                    int borderColor = isSelected ? theme.accent
                            : isHover ? theme.borderLight : theme.border;
                    DrawUtils.drawPanel(graphics, sx, sy, slotSize.pixels, slotSize.pixels, bgColor, borderColor);

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

        // Scrollbar is rendered via the widget tree (child), but we also render tooltip after
        // Tooltip last (outside scissor so it can overflow)
        if (tooltipStack != null) {
            graphics.renderTooltip(font, tooltipStack, tooltipX, tooltipY);
        }
    }

    // --- Input handling ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!containsPoint(mouseX, mouseY)) return false;

        // Delegate to scrollbar if applicable
        if (needsScrollbar() && scrollbar.containsPoint(mouseX, mouseY)) {
            return scrollbar.onMouseClicked(mouseX, mouseY, button);
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
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (needsScrollbar()) {
            return scrollbar.onMouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (needsScrollbar()) {
            return scrollbar.onMouseReleased(mouseX, mouseY, button);
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!containsPoint(mouseX, mouseY)) return false;
        if (needsScrollbar()) {
            return scrollbar.onMouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }
}
