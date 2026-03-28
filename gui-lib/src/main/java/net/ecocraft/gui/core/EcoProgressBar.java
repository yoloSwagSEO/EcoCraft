package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * Non-interactive horizontal progress bar widget.
 * <p>
 * Displays a track with a filled portion proportional to the current progress (0.0 to 1.0).
 * Optionally shows a centered percentage text and a label above the bar.
 */
public class EcoProgressBar extends BaseWidget {

    private final Theme theme;
    private double progress;
    private int fillColor;
    private boolean showPercent;
    private @Nullable Component label;

    public EcoProgressBar(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.progress = 0.0;
        this.fillColor = theme.success;
        this.showPercent = false;
    }

    /** Set the progress value, clamped to [0.0, 1.0]. */
    public EcoProgressBar progress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
        return this;
    }

    /** Get the current progress value. */
    public double getProgress() {
        return progress;
    }

    /** Override the fill color (default: theme.success). */
    public EcoProgressBar fillColor(int fillColor) {
        this.fillColor = fillColor;
        return this;
    }

    /** Show or hide the percentage text centered on the bar. */
    public EcoProgressBar showPercent(boolean showPercent) {
        this.showPercent = showPercent;
        return this;
    }

    /** Set an optional label displayed above the bar. */
    public EcoProgressBar label(@Nullable Component label) {
        this.label = label;
        return this;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        int barX = getX();
        int barY = getY();
        int barW = getWidth();
        int barH = getHeight();

        // Draw optional label above the bar
        if (label != null) {
            graphics.drawString(font, label, barX, barY, theme.textLight, false);
            barY += font.lineHeight + 2;
            barH -= font.lineHeight + 2;
        }

        // Draw track (background + border)
        DrawUtils.drawPanel(graphics, barX, barY, barW, barH, theme.bgMedium, theme.border);

        // Draw fill rectangle inside the border
        int innerX = barX + 1;
        int innerY = barY + 1;
        int innerW = barW - 2;
        int innerH = barH - 2;
        int fillW = (int) (innerW * progress);

        if (fillW > 0) {
            graphics.fill(innerX, innerY, innerX + fillW, innerY + innerH, fillColor);
        }

        // Draw percentage text centered on the bar
        if (showPercent) {
            String text = (int) (progress * 100) + "%";
            int textW = font.width(text);
            int textX = barX + (barW - textW) / 2;
            int textY = barY + (barH - font.lineHeight) / 2;
            graphics.drawString(font, text, textX, textY, theme.textWhite, false);
        }
    }
}
