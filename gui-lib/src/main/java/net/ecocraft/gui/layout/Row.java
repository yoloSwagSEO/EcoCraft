package net.ecocraft.gui.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal flex container. Distributes children by weight or fixed width.
 */
public class Row extends AbstractWidget {

    private int gap = 0;
    private int padding = 0;
    private final List<LayoutEntry> entries = new ArrayList<>();

    public Row(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public Row gap(int gap) { this.gap = gap; return this; }
    public Row padding(int padding) { this.padding = padding; return this; }

    public Row add(AbstractWidget widget, float weight) {
        entries.add(LayoutEntry.weighted(widget, weight));
        return this;
    }

    public Row addFixed(AbstractWidget widget, int fixedWidth) {
        entries.add(LayoutEntry.fixed(widget, fixedWidth));
        return this;
    }

    /** Recalculate all children positions based on layout rules. */
    public void layout() {
        if (entries.isEmpty()) return;

        int availableWidth = width - padding * 2 - gap * (entries.size() - 1);
        int innerY = getY() + padding;
        int innerH = height - padding * 2;

        // Subtract fixed sizes
        float totalWeight = 0;
        for (var e : entries) {
            if (e.isWeighted()) {
                totalWeight += e.weight();
            } else {
                availableWidth -= e.fixedSize();
            }
        }

        // Position children
        int currentX = getX() + padding;
        for (var e : entries) {
            int childWidth;
            if (e.isWeighted()) {
                childWidth = totalWeight > 0 ? (int) (availableWidth * e.weight() / totalWeight) : 0;
            } else {
                childWidth = e.fixedSize();
            }

            AbstractWidget w = e.widget();
            w.setX(currentX);
            w.setY(innerY);
            w.setWidth(childWidth);
            w.setHeight(innerH);

            // Recursively layout children if they are containers
            if (w instanceof Row row) row.layout();
            if (w instanceof Column col) col.layout();

            currentX += childWidth + gap;
        }
    }

    public List<AbstractWidget> getChildren() {
        return entries.stream().map(LayoutEntry::widget).toList();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Row itself is invisible — children render themselves via Screen
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
