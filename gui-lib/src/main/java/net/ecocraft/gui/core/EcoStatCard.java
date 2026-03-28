package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.util.NumberFormat;
import net.ecocraft.gui.util.NumberFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * V2 metric display card extending {@link BaseWidget}.
 * <p>
 * Shows a label + value with themed colors, optional icon and subtitle.
 * Supports {@link NumberFormat} for automatic value formatting.
 * Not focusable, no events.
 */
public class EcoStatCard extends BaseWidget {

    private final Theme theme;
    private final Component label;
    private Component value;
    private int valueColor;
    private @Nullable Component subtitle;
    private int subtitleColor;
    private @Nullable ItemStack icon;
    private @Nullable NumberFormat numberFormat;

    public EcoStatCard(int x, int y, int width, int height,
                       Component label, Component value, int valueColor, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.label = label;
        this.value = value;
        this.valueColor = valueColor;
        this.subtitleColor = theme.textGrey;
    }

    public EcoStatCard(int x, int y, int width, int height,
                       Component label, Component value, int valueColor) {
        this(x, y, width, height, label, value, valueColor, Theme.dark());
    }

    public void setValue(Component value, int color) {
        this.value = value;
        this.valueColor = color;
    }

    /** Set value from a long, auto-formatted using the configured NumberFormat. */
    public void setValue(long numericValue, int color) {
        NumberFormat fmt = numberFormat != null ? numberFormat : NumberFormat.COMPACT;
        this.value = Component.literal(NumberFormatter.format(numericValue, fmt));
        this.valueColor = color;
    }

    public void setSubtitle(@Nullable Component subtitle, int color) {
        this.subtitle = subtitle;
        this.subtitleColor = color;
    }

    public void setIcon(@Nullable ItemStack icon) {
        this.icon = icon;
    }

    public void setNumberFormat(@Nullable NumberFormat format) {
        this.numberFormat = format;
    }

    /** Returns minimum height needed: 28 for label+value, 42 with subtitle. */
    public int getMinHeight() {
        return subtitle != null ? 42 : 28;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        DrawUtils.drawPanel(graphics, getX(), getY(), getWidth(), getHeight(), theme);

        int padding = 10;
        int innerX = getX() + padding;

        int labelY = getY() + padding;
        graphics.drawString(font, label, innerX, labelY, theme.textDim, false);

        int valueY = labelY + 14;
        int valueX = innerX;

        // Render optional icon to the left of the value
        if (icon != null && !icon.isEmpty()) {
            int iconX = innerX;
            int iconY = valueY - 2;
            graphics.renderItem(icon, iconX, iconY);
            valueX = innerX + 20; // 16px icon + 4px gap
        }

        graphics.drawString(font, value, valueX, valueY, valueColor, false);

        if (subtitle != null) {
            int subtitleY = valueY + 14;
            graphics.drawString(font, subtitle, innerX, subtitleY, subtitleColor, false);
        }
    }
}
