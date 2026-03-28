package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * A row within an {@link EcoGrid}. Contains {@link EcoCol} children.
 * Columns are laid out horizontally based on their span (out of 12 total).
 *
 * <p>Row height modes:</p>
 * <ul>
 *   <li>{@code equalHeight(true)} — all cols get the height of the tallest child (default)</li>
 *   <li>{@code equalHeight(false)} — each col keeps its natural height from its children</li>
 *   <li>{@code height(N)} — fixed row height, overrides both modes</li>
 * </ul>
 */
public class EcoRow extends BaseWidget {

    private static final int TOTAL_COLUMNS = 12;

    private final List<EcoCol> cols = new ArrayList<>();
    private int fixedHeight = 0;     // 0 = auto
    private boolean equalHeight = true;  // all cols same height
    private int gap;

    EcoRow(int gap) {
        super(0, 0, 0, 0);
        this.gap = gap;
    }

    /** Add a column spanning the given number of grid columns (1-12). */
    public EcoCol addCol(int span) {
        return addCol(span, 0);
    }

    /** Add a column with a span and offset. */
    public EcoCol addCol(int span, int offset) {
        EcoCol col = new EcoCol(span, offset);
        cols.add(col);
        addChild(col);
        return col;
    }

    /** Set a fixed height for this row. 0 = auto-calculated. */
    public EcoRow height(int h) {
        this.fixedHeight = h;
        return this;
    }

    /** If true (default), all columns get the height of the tallest child.
     *  If false, each column keeps its own height based on its children. */
    public EcoRow equalHeight(boolean eq) {
        this.equalHeight = eq;
        return this;
    }

    /** Recalculate column positions and sizes based on row bounds. */
    void relayout() {
        int totalWidth = getWidth();
        double unitWidth = (double) totalWidth / TOTAL_COLUMNS;

        // Calculate row height
        int rowH = fixedHeight;
        if (rowH <= 0) {
            // Auto-detect from tallest child across all cols
            rowH = 0;
            for (EcoCol col : cols) {
                int colContentH = getColContentHeight(col);
                rowH = Math.max(rowH, colContentH);
            }
            if (rowH <= 0) rowH = 20; // fallback
        }

        // Position columns
        int currentX = getX();
        for (EcoCol col : cols) {
            int offsetPx = (int) (col.getOffset() * unitWidth);
            int colX = currentX + offsetPx;
            int colW = (int) (col.getSpan() * unitWidth) - gap;

            // Column height: equal or natural
            int colH;
            if (equalHeight || fixedHeight > 0) {
                colH = rowH;
            } else {
                colH = getColContentHeight(col);
                if (colH <= 0) colH = rowH;
            }

            col.setPosition(colX, getY());
            col.setSize(Math.max(1, colW), colH);
            col.relayout();

            currentX = colX + colW + gap;
        }

        // Update row size
        setSize(getWidth(), rowH);
    }

    /** Get the natural height of a col's content (tallest child). */
    private int getColContentHeight(EcoCol col) {
        int maxH = 0;
        for (WidgetNode child : col.getChildren()) {
            maxH = Math.max(maxH, child.getHeight());
        }
        return maxH;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Row is invisible — just a layout container
    }
}
