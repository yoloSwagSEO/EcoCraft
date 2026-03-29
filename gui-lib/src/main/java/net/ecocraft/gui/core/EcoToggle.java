package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Pill-shaped toggle switch that slides a circle between ON/OFF states.
 * Supports configurable colors and optional ON/OFF labels inside the pill.
 * Extends BaseWidget (not focusable — click-only interaction).
 */
public class EcoToggle extends BaseWidget {

    private final Theme theme;
    private boolean value;
    private boolean showLabels;
    private int onColor;
    private int onBg;
    private @Nullable Consumer<Boolean> responder;

    public EcoToggle(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.value = false;
        this.showLabels = false;
        this.onColor = theme.success;
        this.onBg = theme.successBg;
    }

    // --- Fluent API ---

    public EcoToggle value(boolean value) {
        this.value = value;
        return this;
    }

    public EcoToggle showLabels(boolean show) {
        this.showLabels = show;
        return this;
    }

    public EcoToggle onColor(int color) {
        this.onColor = color;
        return this;
    }

    public EcoToggle onBg(int color) {
        this.onBg = color;
        return this;
    }

    public EcoToggle responder(Consumer<Boolean> responder) {
        this.responder = responder;
        return this;
    }

    // --- Getters ---

    public boolean getValue() {
        return value;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

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
     * Fills a circle approximated via horizontal scanlines.
     */
    private void fillCircle(GuiGraphics graphics, int cx, int cy, int diameter, int color) {
        int radius = diameter / 2;
        int centerX = cx + radius;
        int centerY = cy + radius;

        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt(radius * radius - dy * dy);
            graphics.fill(centerX - dx, centerY + dy, centerX + dx, centerY + dy + 1, color);
        }
    }

    // --- Interaction ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!isEnabled()) return false;
        value = !value;
        if (responder != null) {
            responder.accept(value);
        }
        return true;
    }

    @Override
    public boolean isFocusable() {
        return false;
    }
}
