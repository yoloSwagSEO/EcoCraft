package net.ecocraft.gui.table;

import net.minecraft.network.chat.Component;

public record TableColumn(
        Component header,
        float weight,
        Align align,
        boolean sortable
) {
    public enum Align { LEFT, CENTER, RIGHT }

    public static TableColumn left(Component header, float weight) {
        return new TableColumn(header, weight, Align.LEFT, false);
    }

    public static TableColumn center(Component header, float weight) {
        return new TableColumn(header, weight, Align.CENTER, false);
    }

    public static TableColumn right(Component header, float weight) {
        return new TableColumn(header, weight, Align.RIGHT, false);
    }

    public static TableColumn sortableLeft(Component header, float weight) {
        return new TableColumn(header, weight, Align.LEFT, true);
    }

    public static TableColumn sortableCenter(Component header, float weight) {
        return new TableColumn(header, weight, Align.CENTER, true);
    }

    public static TableColumn sortableRight(Component header, float weight) {
        return new TableColumn(header, weight, Align.RIGHT, true);
    }
}
