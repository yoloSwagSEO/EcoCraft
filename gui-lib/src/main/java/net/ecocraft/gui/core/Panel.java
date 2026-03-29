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
 * Supports optional title (with uppercase style) and padding.
 *
 * <p>Use {@link #getContentX()}, {@link #getContentY()}, {@link #getContentWidth()},
 * {@link #getContentHeight()} to position children inside the panel accounting for
 * padding and title.</p>
 */
public class Panel extends BaseWidget {

    /** Separator style under the title. */
    public enum SeparatorStyle { ACCENT, SUBTLE, NONE }

    private final Theme theme;
    private int bgColor;
    private int borderColor;
    private @Nullable Component title;
    private @Nullable Font font;
    private int padding = 0;
    private boolean titleUppercase = false;
    private SeparatorStyle separatorStyle = SeparatorStyle.ACCENT;
    private int titleMarginBottom = 4;

    private static final int SEPARATOR_HEIGHT = 2; // separator line + 1px gap

    public Panel(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.bgColor = theme.bgDark;
        this.borderColor = theme.border;
    }

    public Panel bgColor(int color) { this.bgColor = color; return this; }
    public Panel borderColor(int color) { this.borderColor = color; return this; }
    public Panel title(Component title, Font font) { this.title = title; this.font = font; return this; }
    public Panel padding(int padding) { this.padding = padding; return this; }
    public Panel titleUppercase(boolean uppercase) { this.titleUppercase = uppercase; return this; }
    /** Set the separator style under the title. Default: ACCENT (gold line). */
    public Panel separatorStyle(SeparatorStyle style) { this.separatorStyle = style; return this; }
    /** Set extra margin below the title+separator before content. Default: 4. */
    public Panel titleMarginBottom(int margin) { this.titleMarginBottom = margin; return this; }

    private int getTitleBlockHeight() {
        if (title == null || font == null) return 0;
        return font.lineHeight + SEPARATOR_HEIGHT + titleMarginBottom;
    }

    /** X coordinate of the content area (after padding). */
    public int getContentX() { return getX() + padding; }

    /** Y coordinate of the content area (after padding + title). */
    public int getContentY() {
        return getY() + padding + getTitleBlockHeight();
    }

    /** Width of the content area (minus padding on both sides). */
    public int getContentWidth() { return getWidth() - padding * 2; }

    /** Height of the content area (minus padding, title). */
    public int getContentHeight() {
        return getHeight() - padding * 2 - getTitleBlockHeight();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), bgColor, borderColor);

        if (title != null && font != null) {
            int titleX = getX() + padding;
            int titleY = getY() + padding;
            Component displayTitle = titleUppercase
                    ? Component.literal(title.getString().toUpperCase())
                    : title;
            graphics.drawString(font, displayTitle, titleX, titleY, theme.accent, false);

            int sepY = titleY + font.lineHeight + 1;
            int sepW = getWidth() - padding * 2;
            switch (separatorStyle) {
                case ACCENT -> DrawUtils.drawAccentSeparator(graphics, getX() + padding, sepY, sepW, theme);
                case SUBTLE -> DrawUtils.drawSeparator(graphics, getX() + padding, sepY, sepW, theme.borderLight);
                case NONE -> {}
            }
        }
    }
}
