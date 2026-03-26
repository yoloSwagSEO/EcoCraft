package net.ecocraft.gui.table;

import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class EcoPaginatedTable extends AbstractWidget {

    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 18;
    private static final int ICON_MARGIN = 8;

    private final List<TableColumn> columns;
    private final List<TableRow> rows = new ArrayList<>();
    private int page = 0;
    private int rowsPerPage;
    private int hoveredRow = -1;

    public EcoPaginatedTable(int x, int y, int width, int height, List<TableColumn> columns) {
        super(x, y, width, height, Component.empty());
        this.columns = columns;
        this.rowsPerPage = Math.max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT);
    }

    public void setRows(List<TableRow> rows) {
        this.rows.clear();
        this.rows.addAll(rows);
        this.page = 0;
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

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        EcoTheme.drawPanel(graphics, getX(), getY(), width, height);

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

        int headerY = getY() + 1;
        graphics.fill(getX() + 1, headerY, getX() + width - 1, headerY + HEADER_HEIGHT, EcoColors.BG_MEDIUM);
        EcoTheme.drawGoldSeparator(graphics, getX() + 1, headerY + HEADER_HEIGHT - 2, width - 2);

        for (int i = 0; i < columns.size(); i++) {
            var col = columns.get(i);
            int textX = getAlignedX(font, col.header(), colX[i], colW[i], col.align());
            int textY = headerY + (HEADER_HEIGHT - 8) / 2;
            graphics.drawString(font, col.header(), textX, textY, EcoColors.GOLD, false);
        }

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
                graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, EcoColors.BG_MEDIUM);
            } else if (alt) {
                graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, EcoColors.BG_ROW_ALT);
            }

            graphics.fill(getX() + 1, rowY + ROW_HEIGHT - 1, getX() + width - 1,
                    rowY + ROW_HEIGHT, EcoColors.BG_MEDIUM);

            if (row.icon() != null) {
                int iconX = getX() + 4;
                int iconY = rowY + (ROW_HEIGHT - 16) / 2;
                graphics.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, row.iconRarityColor());
                graphics.fill(iconX, iconY, iconX + 16, iconY + 16, EcoColors.BG_LIGHT);
                graphics.renderItem(row.icon(), iconX, iconY);
            }

            int cellCount = Math.min(row.cells().size(), columns.size());
            for (int c = 0; c < cellCount; c++) {
                var cell = row.cells().get(c);
                var col = columns.get(c);
                int textX = getAlignedX(font, cell.text(), colX[c], colW[c], col.align());
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                graphics.drawString(font, cell.text(), textX, textY, cell.color(), false);
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
