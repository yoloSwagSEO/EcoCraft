package net.ecocraft.gui.theme;

import net.minecraft.client.gui.GuiGraphics;

public final class EcoTheme {
    private EcoTheme() {}

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                 int bgColor, int borderColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawPanel(graphics, x, y, width, height, EcoColors.BG_DARK, EcoColors.BORDER);
    }

    public static void drawSeparator(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
    }

    public static void drawGoldSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 2, EcoColors.BORDER_GOLD);
    }

    public static void drawLeftAccent(GuiGraphics graphics, int x, int y, int height, int color) {
        graphics.fill(x, y, x + 3, y + height, color);
    }

    public static void drawBadge(GuiGraphics graphics, int x, int y, int width, int height, int bgColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);
    }
}
