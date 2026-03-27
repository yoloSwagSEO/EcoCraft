package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Styled button with builder pattern and theme presets.
 * Supports disabled state — when disabled, uses theme disabled colors and ignores clicks.
 */
public class Button extends AbstractWidget {

    private final int bgColor;
    private final int borderColor;
    private final int textColor;
    private final int hoverBg;
    private final Theme theme;
    private final Runnable onPress;
    private boolean enabled = true;

    private Button(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height, builder.label);
        this.bgColor = builder.bgColor;
        this.borderColor = builder.borderColor;
        this.textColor = builder.textColor;
        this.hoverBg = builder.hoverBg;
        this.theme = builder.theme;
        this.onPress = builder.onPress;
    }

    // --- Builder ---

    public static Builder builder(Component label, Runnable onPress) {
        return new Builder(label, onPress);
    }

    // --- Theme presets ---

    public static Button primary(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.accentBg).borderColor(theme.borderAccent)
                .textColor(theme.accent).hoverBg(theme.accentBgDim).build();
    }

    public static Button success(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.successBg).borderColor(theme.success)
                .textColor(theme.textWhite).hoverBg(0xFF2A4A2A).build();
    }

    public static Button danger(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.dangerBg).borderColor(theme.danger)
                .textColor(theme.danger).hoverBg(0xFF3A1A1A).build();
    }

    public static Button warning(Theme theme, Component label, Runnable onPress) {
        return new Builder(label, onPress).theme(theme)
                .bgColor(theme.warningBg).borderColor(theme.warning)
                .textColor(theme.textWhite).hoverBg(0xFF3A2A1A).build();
    }

    public static Button ghost(Theme theme, Component label, Runnable onPress) {
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
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bg, border, text;

        if (!enabled && theme != null) {
            bg = theme.disabledBg;
            border = theme.disabledBorder;
            text = theme.disabledText;
        } else {
            boolean hovered = isHovered();
            bg = hovered ? hoverBg : bgColor;
            border = borderColor;
            text = textColor;
        }

        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, bg, border);

        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int textWidth = font.width(getMessage());
        int textX = getX() + (width - textWidth) / 2;
        int textY = getY() + (height - 8) / 2;
        graphics.drawString(font, getMessage(), textX, textY, text, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (enabled) {
            onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
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

        public Button build() {
            return new Button(this);
        }
    }
}
