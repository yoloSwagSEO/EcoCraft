package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Convenience base class for widgets in the tree.
 * Handles children, parent, bounds, visibility.
 * Subclasses implement render() and override event handlers as needed.
 */
public abstract class BaseWidget implements WidgetNode {

    private @Nullable WidgetNode parent;
    private final List<WidgetNode> children = new ArrayList<>();
    private int x, y, width, height;
    private boolean visible = true;
    private boolean clipChildren = false;
    private boolean modal = false;

    public BaseWidget(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public BaseWidget() {
        this(0, 0, 0, 0);
    }

    // --- Tree structure ---

    @Override
    public @Nullable WidgetNode getParent() { return parent; }

    @Override
    public void setParent(@Nullable WidgetNode parent) { this.parent = parent; }

    @Override
    public List<WidgetNode> getChildren() { return Collections.unmodifiableList(children); }

    @Override
    public void addChild(WidgetNode child) {
        if (child.getParent() != null) {
            child.getParent().removeChild(child);
        }
        children.add(child);
        child.setParent(this);
    }

    @Override
    public void removeChild(WidgetNode child) {
        if (children.remove(child)) {
            child.setParent(null);
        }
    }

    /** Remove this widget from its parent. */
    public void removeFromParent() {
        if (parent != null) {
            parent.removeChild(this);
        }
    }

    /** Walk up the tree to find a parent of a specific type. */
    @SuppressWarnings("unchecked")
    public <T extends WidgetNode> @Nullable T findParent(Class<T> type) {
        WidgetNode current = parent;
        while (current != null) {
            if (type.isInstance(current)) return (T) current;
            current = current.getParent();
        }
        return null;
    }

    // --- Bounds ---

    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }

    @Override
    public void setPosition(int x, int y) { this.x = x; this.y = y; }

    @Override
    public void setSize(int width, int height) { this.width = width; this.height = height; }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    // --- Options ---

    @Override public boolean isClipChildren() { return clipChildren; }
    public void setClipChildren(boolean clip) { this.clipChildren = clip; }

    @Override public boolean isModal() { return modal; }
    public void setModal(boolean modal) { this.modal = modal; }

    @Override public boolean isVisible() { return visible; }
    @Override public void setVisible(boolean visible) { this.visible = visible; }
}
