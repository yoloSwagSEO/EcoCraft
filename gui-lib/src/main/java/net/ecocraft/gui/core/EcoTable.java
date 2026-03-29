package net.ecocraft.gui.core;

import net.ecocraft.gui.table.TableColumn;
import net.ecocraft.gui.table.TableRow;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Table widget extending {@link BaseWidget} with three navigation modes: NONE, SCROLL, PAGINATED.
 * Port of the legacy {@code net.ecocraft.gui.table.Table} with cached layout computations.
 * Use {@link #builder()} to configure and build.
 */
public class EcoTable extends BaseWidget {

    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 18;
    private static final int ICON_MARGIN = 8;

    public enum Navigation {
        NONE,
        SCROLL,
        PAGINATED
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
    private int scrollOffset = 0;
    private EcoScrollbar scrollbar;

    // Sort state
    private int sortColumn = -1;
    private boolean sortAscending = true;
    private final List<TableRow> sortedRows = new ArrayList<>();
    private boolean sortDirty = true;

    // Selection state
    private int selectedRow = -1;
    private IntConsumer selectionListener;

    // Hover state
    private int hoveredRow = -1;

    // --- Cached layout (recomputed in setRows / on resize) ---
    private boolean hasIcon;
    private int[] cachedColX;
    private int[] cachedColW;

    // Truncation cache: avoids per-frame string allocations in render loop.
    // Keyed by "text\0maxWidth" → truncated result. Cleared when rows change.
    private final Map<String, String> truncationCache = new HashMap<>();

    private EcoTable(int x, int y, int width, int height, List<TableColumn> columns, Theme theme,
                     Navigation navigation, int scrollLines, boolean showScrollbar,
                     int pageSize, boolean tooltipsEnabled) {
        super(x, y, width, height);
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
            int sbX = x + width - EcoScrollbar.SCROLLBAR_WIDTH - 1;
            int sbY = y + HEADER_HEIGHT + 1;
            int sbH = height - HEADER_HEIGHT - 2;
            this.scrollbar = new EcoScrollbar(sbX, sbY, sbH, theme);
            this.scrollbar.setResponder(value -> {
                int totalRows = rows.size();
                int maxOffset = Math.max(0, totalRows - rowsPerPage);
                this.scrollOffset = Math.round(value * maxOffset);
            });
            addChild(this.scrollbar);
        }

        recomputeColumnLayout();
    }

    public static Builder builder() {
        return new Builder();
    }

    // --- Row data ---

    public void setRows(List<TableRow> rows) {
        this.rows.clear();
        this.rows.addAll(rows);
        this.sortDirty = true;
        this.page = 0;
        this.scrollOffset = 0;
        this.hasIcon = rows.stream().anyMatch(r -> r.icon() != null);
        this.truncationCache.clear();
        recomputeColumnLayout();
        updateScrollbar();
    }

    // --- Layout cache ---

    private void recomputeColumnLayout() {
        int iconSpace = hasIcon ? ICON_SIZE + ICON_MARGIN : 0;
        int scrollbarSpace = (navigation == Navigation.SCROLL && showScrollbar)
                ? EcoScrollbar.SCROLLBAR_WIDTH + 2 : 0;
        int contentWidth = getWidth() - 2 - iconSpace - scrollbarSpace;

        float totalWeight = 0;
        for (var col : columns) totalWeight += col.weight();

        cachedColX = new int[columns.size()];
        cachedColW = new int[columns.size()];
        int cx = getX() + 1 + iconSpace;
        for (int i = 0; i < columns.size(); i++) {
            cachedColX[i] = cx;
            cachedColW[i] = (int) (contentWidth * columns.get(i).weight() / totalWeight);
            cx += cachedColW[i];
        }
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        this.rowsPerPage = Math.max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT);
        this.truncationCache.clear();
        recomputeColumnLayout();
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        recomputeColumnLayout();
        if (scrollbar != null) {
            int sbX = x + getWidth() - EcoScrollbar.SCROLLBAR_WIDTH - 1;
            int sbY = y + HEADER_HEIGHT + 1;
            scrollbar.setPosition(sbX, sbY);
        }
    }

    // --- Sorted rows ---

    private List<TableRow> getDisplayRows() {
        if (sortColumn < 0 || sortColumn >= columns.size() || !columns.get(sortColumn).sortable()) {
            return rows;
        }
        if (sortDirty) {
            sortedRows.clear();
            sortedRows.addAll(rows);
            int col = sortColumn;
            sortedRows.sort((a, b) -> {
                if (col >= a.cells().size() || col >= b.cells().size()) return 0;
                var cellA = a.cells().get(col);
                var cellB = b.cells().get(col);
                int cmp = compareCells(cellA, cellB);
                return sortAscending ? cmp : -cmp;
            });
            sortDirty = false;
        }
        return sortedRows;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int compareCells(TableRow.Cell a, TableRow.Cell b) {
        if (a.sortValue() != null && b.sortValue() != null) {
            return ((Comparable) a.sortValue()).compareTo(b.sortValue());
        }
        return a.text().getString().compareToIgnoreCase(b.text().getString());
    }

    // --- Pagination API ---

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

    // --- Hover / Selection ---

    /** Returns the hovered row's icon, or null if no row is hovered or row has no icon. */
    public ItemStack getHoveredIcon() {
        List<TableRow> display = getDisplayRows();
        if (hoveredRow >= 0 && hoveredRow < display.size()) {
            return display.get(hoveredRow).icon();
        }
        return null;
    }

    public int getSelectedRow() { return selectedRow; }

    public void setSelectedRow(int index) {
        this.selectedRow = index;
    }

    public void setSelectionListener(IntConsumer listener) {
        this.selectionListener = listener;
    }

    @Override
    public boolean isFocusable() { return false; }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), theme);

        List<TableRow> displayRows = getDisplayRows();

        // Header
        int headerY = getY() + 1;
        graphics.fill(getX() + 1, headerY, getX() + getWidth() - 1, headerY + HEADER_HEIGHT, theme.bgMedium);
        DrawUtils.drawAccentSeparator(graphics, getX() + 1, headerY + HEADER_HEIGHT - 2, getWidth() - 2, theme);

        for (int i = 0; i < columns.size(); i++) {
            var col = columns.get(i);
            Component headerText;
            if (col.sortable() && sortColumn == i) {
                String arrow = sortAscending ? " \u25B2" : " \u25BC";
                headerText = Component.literal(col.header().getString() + arrow);
            } else {
                headerText = col.header();
            }
            int textX = getAlignedX(font, headerText, cachedColX[i], cachedColW[i], col.align());
            int textY = headerY + (HEADER_HEIGHT - 8) / 2;
            int headerColor = col.sortable() && sortColumn == i ? theme.textWhite : theme.accent;
            graphics.drawString(font, headerText, textX, textY, headerColor, false);
        }

        // Row area with scissor clipping
        int rowAreaY = headerY + HEADER_HEIGHT;
        int rowAreaH = getHeight() - HEADER_HEIGHT - 2;
        int scrollYOff = ScrollPane.getRenderOffsetY();
        graphics.enableScissor(getX() + 1, rowAreaY + scrollYOff, getX() + getWidth() - 1, rowAreaY + rowAreaH + scrollYOff);

        int startIdx;
        int endIdx;

        switch (navigation) {
            case PAGINATED -> {
                startIdx = page * rowsPerPage;
                endIdx = Math.min(startIdx + rowsPerPage, displayRows.size());
            }
            case SCROLL -> {
                startIdx = scrollOffset;
                endIdx = Math.min(startIdx + rowsPerPage + 1, displayRows.size());
            }
            default -> { // NONE
                startIdx = 0;
                endIdx = displayRows.size();
            }
        }

        hoveredRow = -1;

        for (int i = startIdx; i < endIdx; i++) {
            var row = displayRows.get(i);
            int rowIndex = i - startIdx;
            int rowY = rowAreaY + rowIndex * ROW_HEIGHT;

            boolean alt = (i % 2 == 1);
            boolean isHovered = mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseY >= rowAreaY && mouseY < rowAreaY + rowAreaH;

            boolean isSelected = (i == selectedRow);
            if (isHovered) {
                hoveredRow = i;
                graphics.fill(getX() + 1, rowY, getX() + getWidth() - 1, rowY + ROW_HEIGHT, theme.bgMedium);
            } else if (isSelected) {
                graphics.fill(getX() + 1, rowY, getX() + getWidth() - 1, rowY + ROW_HEIGHT, theme.accentBg);
            } else if (alt) {
                graphics.fill(getX() + 1, rowY, getX() + getWidth() - 1, rowY + ROW_HEIGHT, theme.bgRowAlt);
            }

            graphics.fill(getX() + 1, rowY + ROW_HEIGHT - 1, getX() + getWidth() - 1,
                    rowY + ROW_HEIGHT, theme.bgMedium);

            // Icon
            if (row.icon() != null) {
                int iconX = getX() + 4;
                int iconY = rowY + (ROW_HEIGHT - 16) / 2;
                graphics.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, row.iconRarityColor());
                graphics.fill(iconX, iconY, iconX + 16, iconY + 16, theme.bgLight);
                graphics.renderItem(row.icon(), iconX, iconY);
            }

            // Cells with auto-truncate
            int cellCount = Math.min(row.cells().size(), columns.size());
            for (int c = 0; c < cellCount; c++) {
                var cell = row.cells().get(c);
                var col = columns.get(c);

                String rawText = cell.text().getString();
                int maxTextWidth = cachedColW[c] - 8;
                String cacheKey = rawText + '\0' + maxTextWidth;
                String truncated = truncationCache.computeIfAbsent(cacheKey,
                        k -> DrawUtils.truncateText(font, rawText, maxTextWidth));
                Component displayText = truncated.equals(rawText)
                        ? cell.text()
                        : Component.literal(truncated);

                int textX = getAlignedX(font, displayText, cachedColX[c], cachedColW[c], col.align());
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                graphics.drawString(font, displayText, textX, textY, cell.color(), false);
            }
        }

        graphics.disableScissor();

        // Tooltip for hovered row with icon
        if (tooltipsEnabled && hoveredRow >= 0 && hoveredRow < displayRows.size()) {
            ItemStack hoveredIcon = displayRows.get(hoveredRow).icon();
            if (hoveredIcon != null && !hoveredIcon.isEmpty()) {
                graphics.renderTooltip(font, hoveredIcon, mouseX, mouseY);
            }
        }
    }

    // --- Input ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        // Header click for sorting
        if (button == 0 && mouseY >= getY() + 1 && mouseY < getY() + 1 + HEADER_HEIGHT) {
            int clickedCol = getColumnAtX((int) mouseX);
            if (clickedCol >= 0 && columns.get(clickedCol).sortable()) {
                if (sortColumn == clickedCol) {
                    if (!sortAscending) {
                        // 3rd click: remove sort
                        sortColumn = -1;
                        sortAscending = true;
                    } else {
                        // 2nd click: descending
                        sortAscending = false;
                    }
                } else {
                    sortColumn = clickedCol;
                    sortAscending = true;
                }
                sortDirty = true;
                page = 0;
                scrollOffset = 0;
                updateScrollbar();
                return true;
            }
        }

        // Row click
        List<TableRow> display = getDisplayRows();
        if (button != 0 || hoveredRow < 0 || hoveredRow >= display.size()) return false;
        selectedRow = hoveredRow;
        if (selectionListener != null) {
            selectionListener.accept(hoveredRow);
        }
        var row = display.get(hoveredRow);
        if (row.onClick() != null) {
            row.onClick().run();
        }
        return true;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!containsPoint(mouseX, mouseY)) return false;

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

    private int getColumnAtX(int mouseX) {
        for (int i = 0; i < columns.size(); i++) {
            if (mouseX >= cachedColX[i] && mouseX < cachedColX[i] + cachedColW[i]) return i;
        }
        return -1;
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

    // --- Alignment helper ---

    private int getAlignedX(Font font, Component text, int colX, int colWidth, TableColumn.Align align) {
        int textWidth = font.width(text);
        return switch (align) {
            case LEFT -> colX + 4;
            case CENTER -> colX + (colWidth - textWidth) / 2;
            case RIGHT -> colX + colWidth - textWidth - 4;
        };
    }

    // --- Builder ---

    public static class Builder {
        private List<TableColumn> columns = List.of();
        private Theme theme = Theme.dark();
        private Navigation navigation = Navigation.NONE;
        private int scrollLines = 1;
        private boolean showScrollbar = true;
        private int pageSize = 0;
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

        public EcoTable build(int x, int y, int width, int height) {
            return new EcoTable(x, y, width, height, columns, theme, navigation,
                    scrollLines, showScrollbar, pageSize, tooltips);
        }
    }
}
