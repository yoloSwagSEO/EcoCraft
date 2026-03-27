package net.ecocraft.gui.table;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Scrollbar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic table widget with three navigation modes: NONE, SCROLL, PAGINATED.
 * Use {@link #builder()} to configure and build.
 */
public class Table extends AbstractWidget {

    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 18;
    private static final int ICON_MARGIN = 8;
    private static final int SCROLLBAR_WIDTH = 8;

    public enum Navigation {
        NONE,       // All rows visible, no navigation
        SCROLL,     // Scrollbar, smooth scrolling
        PAGINATED   // Page-based with prev/next buttons
    }

    private final List<TableColumn> columns;
    private final List<TableRow> rows = new ArrayList<>();
    private final Theme theme;
    private final Navigation navigation;
    private final int scrollLines;
    private final boolean showScrollbar;
    private final int pageSize;
    private final boolean tooltipsEnabled;

    // Pagination state
    private int page = 0;
    private int rowsPerPage;

    // Scroll state
    private int scrollOffset = 0; // row index offset for SCROLL mode
    private Scrollbar scrollbar;

    // Shared state
    private int hoveredRow = -1;
    private int lastMouseX, lastMouseY;

    private Table(int x, int y, int width, int height, List<TableColumn> columns, Theme theme,
                  Navigation navigation, int scrollLines, boolean showScrollbar,
                  int pageSize, boolean tooltipsEnabled) {
        super(x, y, width, height, Component.empty());
        this.columns = columns;
        this.theme = theme;
        this.navigation = navigation;
        this.scrollLines = scrollLines;
        this.showScrollbar = showScrollbar;
        this.pageSize = pageSize;
        this.tooltipsEnabled = tooltipsEnabled;

        if (navigation == Navigation.PAGINATED) {
            this.rowsPerPage = pageSize > 0 ? pageSize : Math.max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT);
        } else {
            this.rowsPerPage = Math.max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT);
        }

        if (navigation == Navigation.SCROLL && showScrollbar) {
            int sbX = x + width - SCROLLBAR_WIDTH - 1;
            int sbY = y + HEADER_HEIGHT + 1;
            int sbH = height - HEADER_HEIGHT - 2;
            this.scrollbar = new Scrollbar(sbX, sbY, sbH, theme);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- Row data ---

    public void setRows(List<TableRow> rows) {
        this.rows.clear();
        this.rows.addAll(rows);
        this.page = 0;
        this.scrollOffset = 0;
        updateScrollbar();
    }

    // --- Pagination API (for PAGINATED mode) ---

    public int getPage() { return page; }

    public int getTotalPages() {
        return Math.max(1, (int) Math.ceil((double) rows.size() / rowsPerPage));
    }

    public int getTotalRows() { return rows.size(); }

    public void nextPage() {
        if (page < getTotalPages() - 1) page++;
    }

    public void previousPage() {
        if (page > 0) page--;
    }

    // --- Hover ---

    /** Returns the hovered row's icon, or null if no row is hovered or row has no icon. */
    public ItemStack getHoveredIcon() {
        if (hoveredRow >= 0 && hoveredRow < rows.size()) {
            ItemStack icon = rows.get(hoveredRow).icon();
            return icon != null ? icon : null;
        }
        return null;
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, theme);

        boolean hasIcon = rows.stream().anyMatch(r -> r.icon() != null);
        int iconSpace = hasIcon ? ICON_SIZE + ICON_MARGIN : 0;

        float totalWeight = 0;
        for (var col : columns) totalWeight += col.weight();

        int scrollbarSpace = (navigation == Navigation.SCROLL && showScrollbar) ? SCROLLBAR_WIDTH + 2 : 0;
        int contentWidth = width - 2 - iconSpace - scrollbarSpace;
        int[] colX = new int[columns.size()];
        int[] colW = new int[columns.size()];
        int cx = getX() + 1 + iconSpace;
        for (int i = 0; i < columns.size(); i++) {
            colX[i] = cx;
            colW[i] = (int) (contentWidth * columns.get(i).weight() / totalWeight);
            cx += colW[i];
        }

        // Header
        int headerY = getY() + 1;
        graphics.fill(getX() + 1, headerY, getX() + width - 1, headerY + HEADER_HEIGHT, theme.bgMedium);
        DrawUtils.drawAccentSeparator(graphics, getX() + 1, headerY + HEADER_HEIGHT - 2, width - 2, theme);

        for (int i = 0; i < columns.size(); i++) {
            var col = columns.get(i);
            int textX = getAlignedX(font, col.header(), colX[i], colW[i], col.align());
            int textY = headerY + (HEADER_HEIGHT - 8) / 2;
            graphics.drawString(font, col.header(), textX, textY, theme.accent, false);
        }

        // Row rendering with scissor clipping
        int rowAreaY = headerY + HEADER_HEIGHT;
        int rowAreaH = height - HEADER_HEIGHT - 2;
        graphics.enableScissor(getX() + 1, rowAreaY, getX() + width - 1, rowAreaY + rowAreaH);

        int startIdx;
        int endIdx;

        switch (navigation) {
            case PAGINATED -> {
                startIdx = page * rowsPerPage;
                endIdx = Math.min(startIdx + rowsPerPage, rows.size());
            }
            case SCROLL -> {
                startIdx = scrollOffset;
                endIdx = Math.min(startIdx + rowsPerPage + 1, rows.size()); // +1 for partial row
            }
            default -> { // NONE
                startIdx = 0;
                endIdx = rows.size();
            }
        }

        hoveredRow = -1;

        for (int i = startIdx; i < endIdx; i++) {
            var row = rows.get(i);
            int rowIndex = i - startIdx;
            int rowY = rowAreaY + rowIndex * ROW_HEIGHT;

            boolean alt = (i % 2 == 1);
            boolean isHovered = mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= rowAreaY && mouseY < rowAreaY + rowAreaH;

            if (isHovered) {
                hoveredRow = i;
                graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.bgMedium);
            } else if (alt) {
                graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.bgRowAlt);
            }

            graphics.fill(getX() + 1, rowY + ROW_HEIGHT - 1, getX() + width - 1,
                    rowY + ROW_HEIGHT, theme.bgMedium);

            // Icon
            if (row.icon() != null) {
                int iconX = getX() + 4;
                int iconY2 = rowY + (ROW_HEIGHT - 16) / 2;
                graphics.fill(iconX - 1, iconY2 - 1, iconX + 17, iconY2 + 17, row.iconRarityColor());
                graphics.fill(iconX, iconY2, iconX + 16, iconY2 + 16, theme.bgLight);
                graphics.renderItem(row.icon(), iconX, iconY2);
            }

            // Cells with auto-truncate
            int cellCount = Math.min(row.cells().size(), columns.size());
            for (int c = 0; c < cellCount; c++) {
                var cell = row.cells().get(c);
                var col = columns.get(c);

                String rawText = cell.text().getString();
                int maxTextWidth = colW[c] - 8;
                String truncated = DrawUtils.truncateText(font, rawText, maxTextWidth);
                Component displayText = truncated.equals(rawText)
                        ? cell.text()
                        : Component.literal(truncated);

                int textX = getAlignedX(font, displayText, colX[c], colW[c], col.align());
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                graphics.drawString(font, displayText, textX, textY, cell.color(), false);
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (navigation == Navigation.SCROLL && showScrollbar && scrollbar != null) {
            scrollbar.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        // Built-in tooltip for hovered row with icon (if enabled)
        if (tooltipsEnabled && hoveredRow >= 0 && hoveredRow < rows.size()) {
            ItemStack hoveredIcon = rows.get(hoveredRow).icon();
            if (hoveredIcon != null && !hoveredIcon.isEmpty()) {
                graphics.renderTooltip(font, hoveredIcon, mouseX, mouseY);
            }
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (navigation == Navigation.SCROLL && showScrollbar && scrollbar != null) {
            if (scrollbar.mouseClicked(mouseX, mouseY, button)) {
                syncScrollFromScrollbar();
                return true;
            }
        }
        if (button != 0 || hoveredRow < 0 || hoveredRow >= rows.size()) return false;
        var row = rows.get(hoveredRow);
        if (row.onClick() != null) {
            row.onClick().run();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (navigation == Navigation.SCROLL && showScrollbar && scrollbar != null) {
            if (scrollbar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
                syncScrollFromScrollbar();
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (navigation == Navigation.SCROLL && showScrollbar && scrollbar != null) {
            scrollbar.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;

        switch (navigation) {
            case SCROLL -> {
                if (scrollY > 0) {
                    scrollOffset = Math.max(0, scrollOffset - scrollLines);
                } else if (scrollY < 0) {
                    int maxOffset = Math.max(0, rows.size() - rowsPerPage);
                    scrollOffset = Math.min(maxOffset, scrollOffset + scrollLines);
                }
                updateScrollbar();
                return true;
            }
            case PAGINATED -> {
                if (getTotalPages() > 1) {
                    if (scrollY > 0) previousPage();
                    else if (scrollY < 0) nextPage();
                    return true;
                }
            }
            default -> { /* NONE: no scrolling */ }
        }
        return false;
    }

    // --- Scrollbar sync ---

    private void updateScrollbar() {
        if (scrollbar == null) return;
        int totalRows = rows.size();
        if (totalRows <= rowsPerPage) {
            scrollbar.setContentRatio(1f);
            scrollbar.setScrollValue(0f);
        } else {
            scrollbar.setContentRatio((float) rowsPerPage / totalRows);
            int maxOffset = totalRows - rowsPerPage;
            scrollbar.setScrollValue(maxOffset > 0 ? (float) scrollOffset / maxOffset : 0f);
        }
    }

    private void syncScrollFromScrollbar() {
        if (scrollbar == null) return;
        int totalRows = rows.size();
        int maxOffset = Math.max(0, totalRows - rowsPerPage);
        scrollOffset = Math.round(scrollbar.getScrollValue() * maxOffset);
    }

    // --- Alignment helper ---

    private int getAlignedX(Font font, Component text, int colX, int colWidth, TableColumn.Align align) {
        int textWidth = font.width(text);
        return switch (align) {
            case LEFT -> colX + 4;
            case CENTER -> colX + (colWidth - textWidth) / 2;
            case RIGHT -> colX + colWidth - textWidth - 4;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    // --- Builder ---

    public static class Builder {
        private List<TableColumn> columns = List.of();
        private Theme theme = Theme.dark();
        private Navigation navigation = Navigation.NONE;
        private int scrollLines = 1;
        private boolean showScrollbar = true;
        private int pageSize = 0; // 0 means auto-calculate from height
        private boolean tooltips = true;

        private Builder() {}

        public Builder columns(List<TableColumn> columns) {
            this.columns = columns;
            return this;
        }

        public Builder theme(Theme theme) {
            this.theme = theme;
            return this;
        }

        public Builder navigation(Navigation navigation) {
            this.navigation = navigation;
            return this;
        }

        /** Lines per mouse wheel tick (only for SCROLL mode). Default 1. */
        public Builder scrollLines(int scrollLines) {
            this.scrollLines = scrollLines;
            return this;
        }

        /** Show visual scrollbar (only for SCROLL mode). Default true. */
        public Builder showScrollbar(boolean showScrollbar) {
            this.showScrollbar = showScrollbar;
            return this;
        }

        /** Rows per page (only for PAGINATED mode). 0 means auto-calculate from height. */
        public Builder pageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        /** Show item tooltips on hover. Default true. */
        public Builder tooltips(boolean tooltips) {
            this.tooltips = tooltips;
            return this;
        }

        public Table build(int x, int y, int width, int height) {
            return new Table(x, y, width, height, columns, theme, navigation,
                    scrollLines, showScrollbar, pageSize, tooltips);
        }
    }
}
