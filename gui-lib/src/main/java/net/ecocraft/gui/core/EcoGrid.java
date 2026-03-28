package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * A 12-column grid layout system inspired by Bootstrap.
 *
 * <p>Usage:</p>
 * <pre>
 * EcoGrid grid = new EcoGrid(x, y, width, height, 4);
 *
 * EcoRow row1 = grid.addRow();
 * row1.addCol(6).addChild(leftWidget);   // half width
 * row1.addCol(6).addChild(rightWidget);  // half width
 *
 * EcoRow row2 = grid.addRow();
 * row2.addCol(12).addChild(fullWidget);  // full width
 *
 * EcoRow row3 = grid.addRow();
 * row3.addCol(6, 3).addChild(centered);  // 6 cols wide, offset 3 (centered)
 * </pre>
 *
 * <p>The grid automatically positions rows vertically and columns horizontally.
 * Call {@link #relayout()} after adding all rows/cols if you need to force recalculation.</p>
 */
public class EcoGrid extends BaseWidget {

    private final List<EcoRow> rows = new ArrayList<>();
    private int gap;
    private int rowGap;

    /**
     * Create a grid with the specified gap between columns and rows.
     */
    public EcoGrid(int x, int y, int width, int height, int gap) {
        super(x, y, width, height);
        this.gap = gap;
        this.rowGap = gap;
    }

    /**
     * Create a grid with default gap of 4px.
     */
    public EcoGrid(int x, int y, int width, int height) {
        this(x, y, width, height, 4);
    }

    /** Set the horizontal gap between columns. */
    public EcoGrid gap(int gap) {
        this.gap = gap;
        return this;
    }

    /** Set the vertical gap between rows. */
    public EcoGrid rowGap(int rowGap) {
        this.rowGap = rowGap;
        return this;
    }

    /** Add a new row to the grid. */
    public EcoRow addRow() {
        EcoRow row = new EcoRow(gap);
        rows.add(row);
        addChild(row);
        relayout();
        return row;
    }

    /** Add a new row with a fixed height. */
    public EcoRow addRow(int height) {
        EcoRow row = new EcoRow(gap);
        row.height(height);
        rows.add(row);
        addChild(row);
        relayout();
        return row;
    }

    /** Recalculate all row and column positions. Called automatically on addRow. */
    public void relayout() {
        int currentY = getY();

        for (EcoRow row : rows) {
            row.setPosition(getX(), currentY);
            row.setSize(getWidth(), row.getHeight());
            row.relayout();
            currentY += row.getHeight() + rowGap;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Grid is invisible — just a layout container
    }

    @Override
    public void setPosition(int x, int y) {
        super.setPosition(x, y);
        relayout();
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        relayout();
    }
}
