package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Simple text display widget for the widget tree.
 * Not focusable, no event handling.
 */
public class Label extends BaseWidget {

    public enum Align { LEFT, CENTER, RIGHT }

    private final Font font;
    private final Theme theme;
    private Component text;
    private int color;
    private Align alignment = Align.LEFT;
    private boolean autoWidth;

    public Label(Font font, int x, int y, Component text, Theme theme) {
        super(x, y, font.width(text), font.lineHeight);
        this.font = font;
        this.theme = theme;
        this.text = text;
        this.color = theme.textLight;
        this.autoWidth = true;
    }

    public Label(Font font, int x, int y, int width, Component text, Theme theme) {
        super(x, y, width, font.lineHeight);
        this.font = font;
        this.theme = theme;
        this.text = text;
        this.color = theme.textLight;
        this.autoWidth = false;
    }

    public Label setColor(int color) { this.color = color; return this; }
    public Label setAlignment(Align align) { this.alignment = align; return this; }

    public void setText(Component text) {
        this.text = text;
        if (autoWidth) setSize(font.width(text), getHeight());
    }

    public Component getText() { return text; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        String raw = text.getString();
        // Truncate if fixed width
        if (!autoWidth) {
            raw = DrawUtils.truncateText(font, raw, getWidth());
        }

        int textX;
        int textW = font.width(raw);
        switch (alignment) {
            case CENTER -> textX = getX() + (getWidth() - textW) / 2;
            case RIGHT -> textX = getX() + getWidth() - textW;
            default -> textX = getX();
        }

        int textY = getY() + (getHeight() - font.lineHeight) / 2;
        graphics.drawString(font, raw, textX, textY, color, false);
    }

    @Override
    public boolean isFocusable() { return false; }
}
