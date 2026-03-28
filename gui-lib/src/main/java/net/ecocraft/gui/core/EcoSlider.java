package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Range slider with configurable min/max/step, horizontal/vertical orientation,
 * and label placement. Uses Theme colors for consistent styling.
 * Extends BaseWidget (widget tree V2).
 */
public class EcoSlider extends BaseWidget {

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

    // Cached formatted value — only recomputed when value changes
    private String cachedLabel;
    private double cachedLabelValue = Double.NaN;

    public EcoSlider(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.font = font;
        this.theme = theme;
        this.trackColor = theme.success;
        this.cursorColor = theme.success;
        this.cursorBorderColor = theme.textWhite;
    }

    // --- Fluent API ---

    public EcoSlider min(double min) { this.min = min; return this; }
    public EcoSlider max(double max) { this.max = max; return this; }
    public EcoSlider step(double step) { this.step = step; return this; }

    public EcoSlider value(double value) {
        setValueInternal(value);
        return this;
    }

    public EcoSlider suffix(String suffix) {
        this.suffix = suffix;
        invalidateCache();
        return this;
    }

    public EcoSlider orientation(Orientation orientation) { this.orientation = orientation; return this; }
    public EcoSlider labelPosition(LabelPosition labelPosition) { this.labelPosition = labelPosition; return this; }
    public EcoSlider cursorSize(int cursorSize) { this.cursorSize = cursorSize; return this; }
    public EcoSlider trackColor(int color) { this.trackColor = color; return this; }
    public EcoSlider cursorColor(int color) { this.cursorColor = color; return this; }
    public EcoSlider cursorBorderColor(int color) { this.cursorBorderColor = color; return this; }

    public EcoSlider responder(Consumer<Double> responder) {
        this.responder = responder;
        return this;
    }

    public double getValue() { return value; }

    // --- Focus ---

    @Override
    public boolean isFocusable() { return false; }

    // --- Value formatting (cached) ---

    private void invalidateCache() {
        cachedLabelValue = Double.NaN;
        cachedLabel = null;
    }

    private boolean isIntegerStep() {
        return step >= 1 && step % 1 == 0;
    }

    private String formatValue(double val) {
        String formatted;
        if (isIntegerStep()) {
            formatted = String.valueOf((int) val);
        } else {
            String stepStr = String.valueOf(step);
            int dotIndex = stepStr.indexOf('.');
            int decimals = (dotIndex < 0) ? 0 : stepStr.length() - dotIndex - 1;
            formatted = String.format("%." + decimals + "f", val);
        }
        return formatted + suffix;
    }

    /**
     * Returns the cached label for the current value.
     * Only recomputes when the value actually changes.
     */
    private String currentLabel() {
        if (cachedLabel == null || cachedLabelValue != value) {
            cachedLabel = formatValue(value);
            cachedLabelValue = value;
        }
        return cachedLabel;
    }

    private double snapToStep(double val) {
        if (step <= 0) return val;
        double snapped = Math.round((val - min) / step) * step + min;
        return Math.max(min, Math.min(max, snapped));
    }

    // --- Rail bounds calculation ---

    private int maxLabelSize() {
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
     */
    private int[] railBounds() {
        int labelSpace = maxLabelSize() + 4;
        int start, end;

        if (orientation == Orientation.HORIZONTAL) {
            start = getX();
            end = getX() + getWidth();
            if (labelPosition == LabelPosition.BEFORE) {
                start += labelSpace;
            } else if (labelPosition == LabelPosition.AFTER) {
                end -= labelSpace;
            }
        } else {
            start = getY();
            end = getY() + getHeight();
            if (labelPosition == LabelPosition.BEFORE) {
                start += labelSpace;
            } else if (labelPosition == LabelPosition.AFTER) {
                end -= labelSpace;
            }
        }
        return new int[]{start, end};
    }

    private float ratio() {
        if (max <= min) return 0f;
        return (float) ((value - min) / (max - min));
    }

    private int cursorPos() {
        int[] bounds = railBounds();
        int halfCursor = cursorSize / 2;
        int trackStart = bounds[0] + halfCursor;
        int trackEnd = bounds[1] - halfCursor;
        return trackStart + (int) ((trackEnd - trackStart) * ratio());
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;

        int[] bounds = railBounds();
        float r = ratio();
        int cPos = cursorPos();

        if (orientation == Orientation.HORIZONTAL) {
            renderHorizontal(graphics, bounds, r, cPos);
        } else {
            renderVertical(graphics, bounds, r, cPos);
        }
    }

    private void renderHorizontal(GuiGraphics graphics, int[] bounds, float ratio, int cursorX) {
        int railY = getY() + getHeight() / 2 - RAIL_THICKNESS / 2;
        int railStart = bounds[0];
        int railEnd = bounds[1];

        // Background rail — bgLight so it doesn't blend with separators
        graphics.fill(railStart, railY, railEnd, railY + RAIL_THICKNESS, theme.bgLight);

        // Filled portion (min to current value)
        int filledEnd = railStart + (int) ((railEnd - railStart) * ratio);
        graphics.fill(railStart, railY, filledEnd, railY + RAIL_THICKNESS, trackColor);

        // Cursor
        int halfCursor = cursorSize / 2;
        int cursorTop = getY() + getHeight() / 2 - halfCursor;
        int cursorLeft = cursorX - halfCursor;
        // Border
        graphics.fill(cursorLeft, cursorTop, cursorLeft + cursorSize, cursorTop + cursorSize, cursorBorderColor);
        // Inner fill
        graphics.fill(cursorLeft + 1, cursorTop + 1, cursorLeft + cursorSize - 1, cursorTop + cursorSize - 1, cursorColor);

        // Label (cached)
        String label = currentLabel();
        int labelWidth = font.width(label);
        int labelY = getY() + (getHeight() - font.lineHeight) / 2;

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

    private void renderVertical(GuiGraphics graphics, int[] bounds, float ratio, int cursorY) {
        int railX = getX() + getWidth() / 2 - RAIL_THICKNESS / 2;
        int railStart = bounds[0];
        int railEnd = bounds[1];

        // Background rail — bgLight so it doesn't blend with separators
        graphics.fill(railX, railStart, railX + RAIL_THICKNESS, railEnd, theme.bgLight);

        // Filled portion (min to current value)
        int filledEnd = railStart + (int) ((railEnd - railStart) * ratio);
        graphics.fill(railX, railStart, railX + RAIL_THICKNESS, filledEnd, trackColor);

        // Cursor
        int halfCursor = cursorSize / 2;
        int cursorLeft = getX() + getWidth() / 2 - halfCursor;
        int cursorTop = cursorY - halfCursor;
        // Border
        graphics.fill(cursorLeft, cursorTop, cursorLeft + cursorSize, cursorTop + cursorSize, cursorBorderColor);
        // Inner fill
        graphics.fill(cursorLeft + 1, cursorTop + 1, cursorLeft + cursorSize - 1, cursorTop + cursorSize - 1, cursorColor);

        // Label (cached)
        String label = currentLabel();
        int labelWidth = font.width(label);
        int centerX = getX() + getWidth() / 2;

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
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !containsPoint(mouseX, mouseY)) return false;
        dragging = true;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return false;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!containsPoint(mouseX, mouseY)) return false;
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
            // Cache is invalidated by value change — currentLabel() will recompute
            if (responder != null) {
                responder.accept(this.value);
            }
        }
    }
}
