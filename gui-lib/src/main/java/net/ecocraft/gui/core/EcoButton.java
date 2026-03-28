package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * V2 styled button extending {@link BaseWidget}.
 * <p>
 * Supports a builder pattern and theme-based factory methods
 * ({@link #primary}, {@link #success}, {@link #danger}, {@link #warning}, {@link #ghost}).
 * <p>
 * Disabled state renders with theme disabled colors and ignores clicks.
 */
public class EcoButton extends BaseWidget {

    private Component label;
    private final int bgColor;
    private final int borderColor;
    private final int textColor;
    private final int hoverBg;
    private final Theme theme;
    private final Runnable onPress;
    private boolean enabled = true;

    /** Update the button label text. */
    public void setLabel(Component label) { this.label = label; }

    // Mouse position cached from last render for hover detection
    private double lastMouseX;
    private double lastMouseY;

    private EcoButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height);
        this.label = builder.label;
        this.bgColor = builder.bgColor;
        this.borderColor = builder.borderColor;
        this.textColor = builder.textColor;
        this.hoverBg = builder.hoverBg;
        this.theme = builder.theme;
        this.onPress = builder.onPress;
    }

    // --- Builder entry point ---

    public static Builder builder(Component label, Runnable onPress) {
        return new Builder(label, onPress);
    }

    // --- Theme preset factory methods ---

    public static EcoButton primary(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.accentBg).borderColor(theme.borderAccent)
                .textColor(theme.accent).hoverBg(theme.accentBgDim).build();
    }

    public static EcoButton success(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.successBg).borderColor(theme.success)
                .textColor(theme.textWhite).hoverBg(theme.successBgDim).build();
    }

    public static EcoButton danger(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.dangerBg).borderColor(theme.danger)
                .textColor(theme.danger).hoverBg(theme.dangerBgDim).build();
    }

    public static EcoButton warning(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.warningBg).borderColor(theme.warning)
                .textColor(theme.textWhite).hoverBg(theme.warningBgDim).build();
    }

    public static EcoButton ghost(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.bgMedium).borderColor(theme.borderLight)
                .textColor(theme.textGrey).hoverBg(theme.bgLight).build();
    }

    // --- Enabled state ---

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        int bg, border, text;

        if (!enabled && theme != null) {
            bg = theme.disabledBg;
            border = theme.disabledBorder;
            text = theme.disabledText;
        } else {
            boolean hovered = containsPoint(lastMouseX, lastMouseY);
            bg = hovered ? hoverBg : bgColor;
            border = borderColor;
            text = textColor;
        }

        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), bg, border);

        Font font = Minecraft.getInstance().font;
        int textWidth = font.width(label);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = getY() + (getHeight() - 8) / 2;
        graphics.drawString(font, label, textX, textY, text, false);
    }

    // --- Events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (enabled && containsPoint(mouseX, mouseY)) {
            onPress.run();
            return true;
        }
        return false;
    }

    // --- Builder class ---

    public static class Builder {
        private final Component label;
        private final Runnable onPress;
        private int x = 0, y = 0, width = 60, height = 20;
        private int bgColor = 0xFF1A1A2E;
        private int borderColor = 0xFF444444;
        private int textColor = 0xFFCCCCCC;
        private int hoverBg = 0xFF2A2A3E;
        private Theme theme;

        private Builder(Component label, Runnable onPress) {
            this.label = label;
            this.onPress = onPress;
        }

        public Builder pos(int x, int y) { this.x = x; this.y = y; return this; }
        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder bounds(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; return this; }
        public Builder bgColor(int c) { this.bgColor = c; return this; }
        public Builder borderColor(int c) { this.borderColor = c; return this; }
        public Builder textColor(int c) { this.textColor = c; return this; }
        public Builder hoverBg(int c) { this.hoverBg = c; return this; }
        public Builder theme(Theme t) { this.theme = t; return this; }

        public EcoButton build() {
            return new EcoButton(this);
        }
    }
}
