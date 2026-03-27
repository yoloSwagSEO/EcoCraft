package net.ecocraft.gui.layout;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Associates a widget with its layout properties in a Row or Column.
 */
public record LayoutEntry(
    AbstractWidget widget,
    float weight,      // > 0 means proportional; -1 means fixed size
    int fixedSize      // used when weight == -1
) {
    /** Widget takes proportional space based on weight. */
    public static LayoutEntry weighted(AbstractWidget widget, float weight) {
        return new LayoutEntry(widget, weight, -1);
    }

    /** Widget takes exactly fixedSize pixels. */
    public static LayoutEntry fixed(AbstractWidget widget, int fixedSize) {
        return new LayoutEntry(widget, -1f, fixedSize);
    }

    public boolean isWeighted() {
        return weight > 0;
    }
}
