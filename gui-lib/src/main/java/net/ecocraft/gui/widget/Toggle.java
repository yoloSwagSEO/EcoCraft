package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Pill-shaped toggle switch that slides a circle between ON/OFF states.
 * Supports configurable colors and optional ON/OFF labels inside the pill.
 */
public class Toggle extends AbstractWidget {

    private final Theme theme;
    private boolean value;
    private boolean showLabels;
    private int onColor;
    private int onBg;
    private @Nullable Consumer<Boolean> responder;

    public Toggle(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.value = false;
        this.showLabels = false;
        this.onColor = theme.success;
        this.onBg = theme.successBg;
    }

    // --- Builder-style setters ---

    public Toggle value(boolean value) {
        this.value = value;
        return this;
    }

    public Toggle showLabels(boolean show) {
        this.showLabels = show;
        return this;
    }

    public Toggle onColor(int color) {
        this.onColor = color;
        return this;
    }

    public Toggle onBg(int color) {
        this.onBg = color;
        return this;
    }

    public Toggle responder(Consumer<Boolean> responder) {
        this.responder = responder;
        return this;
    }

    // --- Getters ---

    public boolean getValue() {
        return value;
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;

        // Determine colors based on state
        int bg = value ? onBg : theme.bgMedium;
        int border = value ? onColor : theme.borderLight;
        int circleColor = value ? theme.textWhite : theme.textGrey;

        // Draw pill background with 1px border
        // Outer border
        graphics.fill(x, y, x + w, y + h, border);
        // Inner fill (1px inset)
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg);

        // Draw circle
        int circleDiameter = h - 4; // 2px padding on each side
        int circleY = y + 2;
        int circleX;
        if (value) {
            // Circle on the right
            circleX = x + w - 2 - circleDiameter;
        } else {
            // Circle on the left
            circleX = x + 2;
        }
        fillCircle(graphics, circleX, circleY, circleDiameter, circleColor);

        // Draw optional labels
        if (showLabels) {
            Font font = Minecraft.getInstance().font;
            int textY = y + (h - 8) / 2;

            if (value) {
                // "ON" label on the left side of the pill
                String onLabel = "ON";
                int labelX = x + 4;
                graphics.drawString(font, onLabel, labelX, textY, theme.textWhite, false);
            } else {
                // "OFF" label on the right side of the pill
                String offLabel = "OFF";
                int labelWidth = font.width(offLabel);
                int labelX = x + w - 4 - labelWidth;
                graphics.drawString(font, offLabel, labelX, textY, theme.textGrey, false);
            }
        }
    }

    /**
     * Fills a circle (approximated as a filled rectangle since Minecraft GUI
     * does not provide native circle drawing — uses a rounded look via layered fills).
     */
    private void fillCircle(GuiGraphics graphics, int cx, int cy, int diameter, int color) {
        // Simple circle approximation: draw horizontal lines per row
        int radius = diameter / 2;
        int centerX = cx + radius;
        int centerY = cy + radius;

        for (int dy = -radius; dy <= radius; dy++) {
            // Calculate horizontal extent at this y offset
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - dx, centerY + dy, centerX + dx, centerY + dy + 1, color);
        }
    }

    // --- Interaction ---

    @Override
    public void onClick(double mouseX, double mouseY) {
        value = !value;
        if (responder != null) {
            responder.accept(value);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
