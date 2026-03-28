package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Container widget that draws a themed background panel.
 * Children are rendered on top of the background.
 * Supports optional title and clipChildren.
 */
public class Panel extends BaseWidget {

    private final Theme theme;
    private int bgColor;
    private int borderColor;
    private @Nullable Component title;
    private @Nullable Font font;

    public Panel(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.bgColor = theme.bgDark;
        this.borderColor = theme.border;
    }

    public Panel bgColor(int color) { this.bgColor = color; return this; }
    public Panel borderColor(int color) { this.borderColor = color; return this; }
    public Panel title(Component title, Font font) { this.title = title; this.font = font; return this; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), bgColor, borderColor);

        if (title != null && font != null) {
            int titleX = getX() + 8;
            int titleY = getY() + 4;
            graphics.drawString(font, title, titleX, titleY, theme.accent, false);
            DrawUtils.drawAccentSeparator(graphics, getX() + 4, titleY + font.lineHeight + 2,
                    getWidth() - 8, theme);
        }
    }
}
