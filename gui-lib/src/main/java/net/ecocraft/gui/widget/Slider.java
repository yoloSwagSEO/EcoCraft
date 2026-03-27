package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Range slider with configurable min/max/step, horizontal/vertical orientation,
 * and label placement. Uses Theme colors for consistent styling.
 */
public class Slider extends AbstractWidget {

    public enum Orientation { HORIZONTAL, VERTICAL }
    public enum LabelPosition { BEFORE, CENTER, AFTER }

    private static final int RAIL_THICKNESS = 2;
    private static final int DEFAULT_CURSOR_SIZE = 8;

    private final Font font;
    private final Theme theme;

    private double min = 0;
    private double max = 100;
    private double step = 1;
    private double value = 0;
    private String suffix = "";
    private Orientation orientation = Orientation.HORIZONTAL;
    private LabelPosition labelPosition = LabelPosition.AFTER;
    private int cursorSize = DEFAULT_CURSOR_SIZE;
    private int trackColor;
    private int cursorColor;
    private int cursorBorderColor;
    private Consumer<Double> responder;
    private boolean dragging = false;

    public Slider(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.theme = theme;
        this.trackColor = theme.success;
        this.cursorColor = theme.success;
        this.cursorBorderColor = theme.textWhite;
    }

    // --- Fluent API ---

    public Slider min(double min) { this.min = min; return this; }
    public Slider max(double max) { this.max = max; return this; }
    public Slider step(double step) { this.step = step; return this; }

    public Slider value(double value) {
        this.value = snapToStep(Math.max(min, Math.min(max, value)));
        return this;
    }

    public Slider suffix(String suffix) { this.suffix = suffix; return this; }
    public Slider orientation(Orientation orientation) { this.orientation = orientation; return this; }
    public Slider labelPosition(LabelPosition labelPosition) { this.labelPosition = labelPosition; return this; }
    public Slider cursorSize(int cursorSize) { this.cursorSize = cursorSize; return this; }
    public Slider trackColor(int color) { this.trackColor = color; return this; }
    public Slider cursorColor(int color) { this.cursorColor = color; return this; }
    public Slider cursorBorderColor(int color) { this.cursorBorderColor = color; return this; }

    public Slider responder(Consumer<Double> responder) {
        this.responder = responder;
        return this;
    }

    public double getValue() { return value; }

    // --- Value formatting ---

    private boolean isIntegerStep() {
        return step >= 1 && step % 1 == 0;
    }

    private String formatValue(double val) {
        String formatted;
        if (isIntegerStep()) {
            formatted = String.valueOf((int) val);
        } else {
            // Determine decimal places from step precision
            String stepStr = String.valueOf(step);
            int dotIndex = stepStr.indexOf('.');
            int decimals = (dotIndex < 0) ? 0 : stepStr.length() - dotIndex - 1;
            formatted = String.format("%." + decimals + "f", val);
        }
        return formatted + suffix;
    }

    private double snapToStep(double val) {
        if (step <= 0) return val;
        double snapped = Math.round((val - min) / step) * step + min;
        return Math.max(min, Math.min(max, snapped));
    }

    // --- Rail bounds calculation ---

    /**
     * Returns the maximum label width/height used to reserve space.
     */
    private int maxLabelSize() {
        // Measure widest possible label (min and max values)
        String minLabel = formatValue(min);
        String maxLabel = formatValue(max);
        if (orientation == Orientation.HORIZONTAL) {
            return Math.max(font.width(minLabel), font.width(maxLabel));
        } else {
            return font.lineHeight;
        }
    }

    /**
     * Returns [railStart, railEnd] along the slider axis.
     * For HORIZONTAL: x-coordinates. For VERTICAL: y-coordinates.
     */
    private int[] railBounds() {
        int labelSpace = maxLabelSize() + 4; // 4px gap
        int start, end;

        if (orientation == Orientation.HORIZONTAL) {
            start = getX();
            end = getX() + width;
            if (labelPosition == LabelPosition.BEFORE) {
                start += labelSpace;
            } else if (labelPosition == LabelPosition.AFTER) {
                end -= labelSpace;
            }
            // CENTER: label floats above cursor, no space reservation on rail axis
        } else {
            start = getY();
            end = getY() + height;
            if (labelPosition == LabelPosition.BEFORE) {
                start += labelSpace;
            } else if (labelPosition == LabelPosition.AFTER) {
                end -= labelSpace;
            }
        }
        return new int[]{start, end};
    }

    /**
     * Ratio of (value - min) / (max - min), clamped to [0, 1].
     */
    private float ratio() {
        if (max <= min) return 0f;
        return (float) ((value - min) / (max - min));
    }

    /**
     * Pixel position of the cursor center along the rail axis.
     */
    private int cursorPos() {
        int[] bounds = railBounds();
        int halfCursor = cursorSize / 2;
        int trackStart = bounds[0] + halfCursor;
        int trackEnd = bounds[1] - halfCursor;
        return trackStart + (int) ((trackEnd - trackStart) * ratio());
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int[] bounds = railBounds();
        float r = ratio();
        int cPos = cursorPos();

        if (orientation == Orientation.HORIZONTAL) {
            renderHorizontal(graphics, bounds, r, cPos, mouseX, mouseY);
        } else {
            renderVertical(graphics, bounds, r, cPos, mouseX, mouseY);
        }
    }

    private void renderHorizontal(GuiGraphics graphics, int[] bounds, float ratio, int cursorX, int mouseX, int mouseY) {
        int railY = getY() + height / 2 - RAIL_THICKNESS / 2;
        int railStart = bounds[0];
        int railEnd = bounds[1];

        // Background rail
        graphics.fill(railStart, railY, railEnd, railY + RAIL_THICKNESS, theme.bgLight);

        // Filled portion (min to current value)
        int filledEnd = railStart + (int) ((railEnd - railStart) * ratio);
        graphics.fill(railStart, railY, filledEnd, railY + RAIL_THICKNESS, trackColor);

        // Cursor
        int halfCursor = cursorSize / 2;
        int cursorTop = getY() + height / 2 - halfCursor;
        int cursorLeft = cursorX - halfCursor;
        // Border
        graphics.fill(cursorLeft, cursorTop, cursorLeft + cursorSize, cursorTop + cursorSize, cursorBorderColor);
        // Inner fill
        graphics.fill(cursorLeft + 1, cursorTop + 1, cursorLeft + cursorSize - 1, cursorTop + cursorSize - 1, cursorColor);

        // Label
        String label = formatValue(value);
        int labelWidth = font.width(label);
        int labelY = getY() + (height - font.lineHeight) / 2;

        switch (labelPosition) {
            case BEFORE -> {
                int labelX = getX();
                graphics.drawString(font, label, labelX, labelY, theme.textLight, false);
            }
            case CENTER -> {
                int labelX = cursorX - labelWidth / 2;
                int aboveY = cursorTop - font.lineHeight - 2;
                graphics.drawString(font, label, labelX, aboveY, theme.textLight, false);
            }
            case AFTER -> {
                int labelX = railEnd + 4;
                graphics.drawString(font, label, labelX, labelY, theme.textLight, false);
            }
        }
    }

    private void renderVertical(GuiGraphics graphics, int[] bounds, float ratio, int cursorY, int mouseX, int mouseY) {
        int railX = getX() + width / 2 - RAIL_THICKNESS / 2;
        int railStart = bounds[0];
        int railEnd = bounds[1];

        // Background rail
        graphics.fill(railX, railStart, railX + RAIL_THICKNESS, railEnd, theme.bgMedium);

        // Filled portion (min to current value) - top to cursor
        int filledEnd = railStart + (int) ((railEnd - railStart) * ratio);
        graphics.fill(railX, railStart, railX + RAIL_THICKNESS, filledEnd, theme.accent);

        // Cursor
        int halfCursor = cursorSize / 2;
        int cursorLeft = getX() + width / 2 - halfCursor;
        int cursorTop = cursorY - halfCursor;
        // Border
        graphics.fill(cursorLeft, cursorTop, cursorLeft + cursorSize, cursorTop + cursorSize, cursorBorderColor);
        // Inner fill
        graphics.fill(cursorLeft + 1, cursorTop + 1, cursorLeft + cursorSize - 1, cursorTop + cursorSize - 1, cursorColor);

        // Label
        String label = formatValue(value);
        int labelWidth = font.width(label);
        int centerX = getX() + width / 2;

        switch (labelPosition) {
            case BEFORE -> {
                int labelX = centerX - labelWidth / 2;
                int labelY = getY();
                graphics.drawString(font, label, labelX, labelY, theme.textLight, false);
            }
            case CENTER -> {
                int labelX = cursorLeft + cursorSize + 4;
                int labelY = cursorY - font.lineHeight / 2;
                graphics.drawString(font, label, labelX, labelY, theme.textLight, false);
            }
            case AFTER -> {
                int labelX = centerX - labelWidth / 2;
                int labelY = railEnd + 4;
                graphics.drawString(font, label, labelX, labelY, theme.textLight, false);
            }
        }
    }

    // --- Interaction ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        dragging = true;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return false;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        double newValue = value + scrollY * step;
        setValueInternal(newValue);
        return true;
    }

    private void updateValueFromMouse(double mouseX, double mouseY) {
        int[] bounds = railBounds();
        int halfCursor = cursorSize / 2;
        int trackStart = bounds[0] + halfCursor;
        int trackEnd = bounds[1] - halfCursor;
        int trackLength = trackEnd - trackStart;
        if (trackLength <= 0) return;

        double pos = (orientation == Orientation.HORIZONTAL) ? mouseX : mouseY;
        double ratio = (pos - trackStart) / trackLength;
        ratio = Math.max(0, Math.min(1, ratio));
        double newValue = min + ratio * (max - min);
        setValueInternal(newValue);
    }

    private void setValueInternal(double newValue) {
        double snapped = snapToStep(Math.max(min, Math.min(max, newValue)));
        if (snapped != this.value) {
            this.value = snapped;
            if (responder != null) {
                responder.accept(this.value);
            }
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
