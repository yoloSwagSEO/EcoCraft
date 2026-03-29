package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Vertical scrollbar widget extending BaseWidget.
 * Supports draggable thumb, click-to-jump on track, and mouse scroll.
 */
public class EcoScrollbar extends BaseWidget {

    public static final int SCROLLBAR_WIDTH = 8;
    private static final int MIN_THUMB_HEIGHT = 16;

    private final Theme theme;
    private float scrollValue = 0f;
    private float contentRatio = 1f;
    private boolean dragging = false;
    private double dragOffset = 0;
    private Consumer<Float> responder;

    public EcoScrollbar(int x, int y, int height, Theme theme) {
        super(x, y, SCROLLBAR_WIDTH, height);
        this.theme = theme;
    }

    public EcoScrollbar(int x, int y, int height) {
        this(x, y, height, Theme.dark());
    }

    /** Set the visible/total content ratio (0..1). Determines thumb size. */
    public void setContentRatio(float ratio) {
        this.contentRatio = Math.max(0f, Math.min(1f, ratio));
    }

    /** Current scroll position, 0.0 (top) to 1.0 (bottom). */
    public float getScrollValue() {
        return scrollValue;
    }

    /** Set scroll position, clamped to 0.0..1.0. */
    public void setScrollValue(float value) {
        this.scrollValue = Math.max(0f, Math.min(1f, value));
    }

    /** Set a listener called when the scrollbar value changes via user interaction (drag, click, scroll). */
    public void setResponder(Consumer<Float> responder) {
        this.responder = responder;
    }

    /** Set scroll position from user interaction, clamped to 0.0..1.0, and fire responder. */
    private void setScrollValueAndNotify(float value) {
        setScrollValue(value);
        if (responder != null) {
            responder.accept(scrollValue);
        }
    }

    /** Returns true if scrollbar is needed (content exceeds visible area). */
    public boolean needsScrollbar() {
        return contentRatio < 1f;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!needsScrollbar()) return;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Track background
        graphics.fill(x, y, x + w, y + h, theme.bgMedium);

        // Thumb
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (h * contentRatio));
        int trackSpace = h - thumbHeight;
        int thumbY = y + (int) (trackSpace * scrollValue);

        int thumbColor = dragging ? theme.accent : theme.bgLight;
        graphics.fill(x + 1, thumbY, x + w - 1, thumbY + thumbHeight, thumbColor);
    }

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!needsScrollbar() || button != 0 || !containsPoint(mouseX, mouseY)) return false;

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (getHeight() * contentRatio));
        int trackSpace = getHeight() - thumbHeight;
        int thumbY = getY() + (int) (trackSpace * scrollValue);

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            // Start dragging the thumb
            dragging = true;
            dragOffset = mouseY - thumbY;
        } else {
            // Click on track: jump scroll position
            float clickRatio = (float) (mouseY - getY() - thumbHeight / 2.0) / (getHeight() - thumbHeight);
            setScrollValueAndNotify(clickRatio);
        }
        return true;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return false;

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (getHeight() * contentRatio));
        int trackSpace = getHeight() - thumbHeight;
        if (trackSpace <= 0) return false;

        float newValue = (float) (mouseY - getY() - dragOffset) / trackSpace;
        setScrollValueAndNotify(newValue);
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
        if (!needsScrollbar()) return false;
        setScrollValueAndNotify(scrollValue - (float) (scrollY * 0.05));
        return true;
    }
}
