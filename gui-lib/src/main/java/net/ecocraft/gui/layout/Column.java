package net.ecocraft.gui.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical flex container. Distributes children by weight or fixed height.
 */
public class Column extends AbstractWidget {

    private int gap = 0;
    private int padding = 0;
    private final List<LayoutEntry> entries = new ArrayList<>();

    public Column(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public Column gap(int gap) { this.gap = gap; return this; }
    public Column padding(int padding) { this.padding = padding; return this; }

    public Column add(AbstractWidget widget, float weight) {
        entries.add(LayoutEntry.weighted(widget, weight));
        return this;
    }

    public Column addFixed(AbstractWidget widget, int fixedHeight) {
        entries.add(LayoutEntry.fixed(widget, fixedHeight));
        return this;
    }

    /** Recalculate all children positions based on layout rules. */
    public void layout() {
        if (entries.isEmpty()) return;

        int availableHeight = height - padding * 2 - gap * (entries.size() - 1);
        int innerX = getX() + padding;
        int innerW = width - padding * 2;

        // Subtract fixed sizes
        float totalWeight = 0;
        for (var e : entries) {
            if (e.isWeighted()) {
                totalWeight += e.weight();
            } else {
                availableHeight -= e.fixedSize();
            }
        }

        // Position children
        int currentY = getY() + padding;
        for (var e : entries) {
            int childHeight;
            if (e.isWeighted()) {
                childHeight = totalWeight > 0 ? (int) (availableHeight * e.weight() / totalWeight) : 0;
            } else {
                childHeight = e.fixedSize();
            }

            AbstractWidget w = e.widget();
            w.setX(innerX);
            w.setY(currentY);
            w.setWidth(innerW);
            w.setHeight(childHeight);

            if (w instanceof Row row) row.layout();
            if (w instanceof Column col) col.layout();

            currentY += childHeight + gap;
        }
    }

    public List<AbstractWidget> getChildren() {
        return entries.stream().map(LayoutEntry::widget).toList();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Column itself is invisible — children render themselves via Screen
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
