package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Contract for any widget in the gui-lib widget tree.
 * Widgets form a parent->children hierarchy. Rendering is depth-first.
 * Events bubble from target up to root.
 */
public interface WidgetNode {

    // --- Tree structure ---
    @Nullable WidgetNode getParent();
    void setParent(@Nullable WidgetNode parent);
    List<WidgetNode> getChildren();
    void addChild(WidgetNode child);
    void removeChild(WidgetNode child);

    // --- Bounds (absolute screen coordinates) ---
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    void setPosition(int x, int y);
    void setSize(int width, int height);

    /** Returns true if the point (mx, my) is within this widget's bounds. */
    default boolean containsPoint(double mx, double my) {
        return mx >= getX() && mx < getX() + getWidth()
                && my >= getY() && my < getY() + getHeight();
    }

    // --- Rendering ---
    /** Render this widget (not children). */
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** Render children (called after render). Override for scissor clipping etc. */
    default void renderChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (WidgetNode child : getChildren()) {
            if (child.isVisible()) {
                child.render(graphics, mouseX, mouseY, partialTick);
                child.renderChildren(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    // --- Mouse events (return true to consume / stop bubbling) ---
    default boolean onMouseClicked(double mouseX, double mouseY, int button) { return false; }
    default boolean onMouseReleased(double mouseX, double mouseY, int button) { return false; }
    default boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) { return false; }
    default boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) { return false; }

    // --- Key events (sent to focused widget, bubble up) ---
    default boolean onKeyPressed(int keyCode, int scanCode, int modifiers) { return false; }
    default boolean onCharTyped(char codePoint, int modifiers) { return false; }

    // --- Focus ---
    default boolean isFocusable() { return false; }
    default void onFocusGained() {}
    default void onFocusLost() {}

    // --- Options ---
    default boolean isClipChildren() { return false; }
    default boolean isModal() { return false; }
    boolean isVisible();
    void setVisible(boolean visible);
}
