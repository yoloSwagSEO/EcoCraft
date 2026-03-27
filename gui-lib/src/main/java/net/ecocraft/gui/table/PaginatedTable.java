package net.ecocraft.gui.table;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
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
 * Paginated, sortable table with auto-truncate and built-in tooltip rendering.
 * Accepts a Theme (defaults to Theme.dark()).
 */
public class PaginatedTable extends AbstractWidget {

    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 18;
    private static final int ICON_MARGIN = 8;

    private final List<TableColumn> columns;
    private final List<TableRow> rows = new ArrayList<>();
    private final Theme theme;
    private int page = 0;
    private int rowsPerPage;
    private int hoveredRow = -1;
    private boolean tooltipsEnabled = true;
    // Store mouse position for tooltip rendering
    private int lastMouseX, lastMouseY;

    public PaginatedTable(int x, int y, int width, int height, List<TableColumn> columns, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.columns = columns;
        this.theme = theme;
        this.rowsPerPage = Math.max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT);
    }

    public PaginatedTable(int x, int y, int width, int height, List<TableColumn> columns) {
        this(x, y, width, height, columns, Theme.dark());
    }

    public void setRows(List<TableRow> rows) {
        this.rows.clear();
        this.rows.addAll(rows);
        this.page = 0;
    }

    public PaginatedTable tooltips(boolean enabled) {
        this.tooltipsEnabled = enabled;
        return this;
    }

    public int getPage() { return page; }
    public int getTotalPages() { return Math.max(1, (int) Math.ceil((double) rows.size() / rowsPerPage)); }
    public int getTotalRows() { return rows.size(); }

    public void nextPage() {
        if (page < getTotalPages() - 1) page++;
    }

    public void previousPage() {
        if (page > 0) page--;
    }

    /** Returns the hovered row's icon, or null if no row is hovered or row has no icon. */
    public ItemStack getHoveredIcon() {
        if (hoveredRow >= 0 && hoveredRow < rows.size()) {
            ItemStack icon = rows.get(hoveredRow).icon();
            return icon != null ? icon : null;
        }
        return null;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, theme);

        float totalWeight = 0;
        boolean hasIcon = rows.stream().anyMatch(r -> r.icon() != null);
        int iconSpace = hasIcon ? ICON_SIZE + ICON_MARGIN : 0;
        for (var col : columns) totalWeight += col.weight();

        int contentWidth = width - 2 - iconSpace;
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

        // Rows
        int startIdx = page * rowsPerPage;
        int endIdx = Math.min(startIdx + rowsPerPage, rows.size());
        hoveredRow = -1;

        for (int i = startIdx; i < endIdx; i++) {
            var row = rows.get(i);
            int rowIndex = i - startIdx;
            int rowY = headerY + HEADER_HEIGHT + rowIndex * ROW_HEIGHT;

            boolean alt = (i % 2 == 1);
            boolean isHovered = mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

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

                // Auto-truncate text to fit column width
                String rawText = cell.text().getString();
                int maxTextWidth = colW[c] - 8; // 4px padding on each side
                String truncated = DrawUtils.truncateText(font, rawText, maxTextWidth);
                Component displayText = truncated.equals(rawText)
                        ? cell.text()
                        : Component.literal(truncated);

                int textX = getAlignedX(font, displayText, colX[c], colW[c], col.align());
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                graphics.drawString(font, displayText, textX, textY, cell.color(), false);
            }
        }

        // Built-in tooltip for hovered row with icon (if enabled)
        if (tooltipsEnabled && hoveredRow >= 0 && hoveredRow < rows.size()) {
            ItemStack hoveredIcon = rows.get(hoveredRow).icon();
            if (hoveredIcon != null && !hoveredIcon.isEmpty()) {
                graphics.renderTooltip(font, hoveredIcon, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || hoveredRow < 0 || hoveredRow >= rows.size()) return false;
        var row = rows.get(hoveredRow);
        if (row.onClick() != null) {
            row.onClick().run();
            return true;
        }
        return false;
    }

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
}
