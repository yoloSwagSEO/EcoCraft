package net.ecocraft.gui.theme;

import net.minecraft.client.gui.GuiGraphics;

public final class DrawUtils {
    private DrawUtils() {}

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, Theme theme) {
        drawPanel(g, x, y, w, h, theme.bgDark, theme.border);
    }

    public static void drawSeparator(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void drawAccentSeparator(GuiGraphics g, int x, int y, int w, Theme theme) {
        g.fill(x, y, x + w, y + 2, theme.borderAccent);
    }

    public static void drawLeftAccent(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 3, y + h, color);
    }

    public static String truncateText(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisW = font.width(ellipsis);
        while (text.length() > 1 && font.width(text) + ellipsisW > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
