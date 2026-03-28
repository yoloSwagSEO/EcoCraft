package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A column within an {@link EcoRow}. Sized by span (out of 12) and optional offset.
 * Children are positioned inside the column bounds based on alignment settings.
 */
public class EcoCol extends BaseWidget {

    /** Horizontal alignment of children within the column. */
    public enum HAlign { LEFT, CENTER, RIGHT }

    /** Vertical alignment of children within the column. */
    public enum VAlign { TOP, MIDDLE, BOTTOM }

    private final int span;   // 1-12
    private final int offset; // 0-11
    private HAlign hAlign = HAlign.LEFT;
    private VAlign vAlign = VAlign.TOP;

    public EcoCol(int span) {
        this(span, 0);
    }

    public EcoCol(int span, int offset) {
        super(0, 0, 0, 0);
        this.span = Math.max(1, Math.min(12, span));
        this.offset = Math.max(0, Math.min(11, offset));
    }

    public int getSpan() { return span; }
    public int getOffset() { return offset; }

    /** Set horizontal alignment of children. Default LEFT. */
    public EcoCol align(HAlign align) { this.hAlign = align; return this; }

    /** Set vertical alignment of children. Default TOP. */
    public EcoCol valign(VAlign align) { this.vAlign = align; return this; }

    /** Shortcut: center both horizontally and vertically. */
    public EcoCol center() { this.hAlign = HAlign.CENTER; this.vAlign = VAlign.MIDDLE; return this; }

    public HAlign getHAlign() { return hAlign; }
    public VAlign getVAlign() { return vAlign; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Col is invisible — just a layout container
    }

    /** Recalculate children positions based on alignment and column bounds. */
    void relayout() {
        for (WidgetNode child : getChildren()) {
            int childW = child.getWidth();
            int childH = child.getHeight();

            // Horizontal alignment
            int childX;
            switch (hAlign) {
                case CENTER -> childX = getX() + (getWidth() - childW) / 2;
                case RIGHT -> childX = getX() + getWidth() - childW;
                default -> childX = getX();
            }

            // Vertical alignment
            int childY;
            switch (vAlign) {
                case MIDDLE -> childY = getY() + (getHeight() - childH) / 2;
                case BOTTOM -> childY = getY() + getHeight() - childH;
                default -> childY = getY();
            }

            child.setPosition(childX, childY);
        }
    }

    /**
     * Set child width to fill the column. Call after relayout if you want
     * the child to stretch to column width instead of keeping its own width.
     */
    public EcoCol fillWidth() {
        for (WidgetNode child : getChildren()) {
            child.setSize(getWidth(), child.getHeight());
        }
        return this;
    }
}
