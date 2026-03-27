package net.ecocraft.gui.table;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public record TableRow(
        @Nullable ItemStack icon,
        int iconRarityColor,
        List<Cell> cells,
        @Nullable Runnable onClick
) {
    public record Cell(Component text, int color, @Nullable Comparable<?> sortValue) {
        public static Cell of(Component text, int color) {
            return new Cell(text, color, null);
        }

        public static Cell of(Component text, int color, Comparable<?> sortValue) {
            return new Cell(text, color, sortValue);
        }
    }

    public static TableRow of(List<Cell> cells, @Nullable Runnable onClick) {
        return new TableRow(null, 0xFFFFFFFF, cells, onClick);
    }

    public static TableRow withIcon(ItemStack icon, int rarityColor, List<Cell> cells, @Nullable Runnable onClick) {
        return new TableRow(icon, rarityColor, cells, onClick);
    }
}
