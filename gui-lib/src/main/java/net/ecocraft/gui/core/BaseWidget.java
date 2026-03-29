package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private boolean enabled = true;
    private boolean clipChildren = false;
    private boolean modal = false;
    private @Nullable String id;
    private @Nullable Map<String, Object> userData;

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

    /** Move this widget to the end of its parent's children list (rendered/hit-tested last = on top). */
    public void bringToFront() {
        if (parent instanceof BaseWidget bw) {
            bw.children.remove(this);
            bw.children.add(this);
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

    /** Whether this widget is enabled (accepts input). Disabled widgets render with dimmed colors. */
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    // --- ID ---

    /** Optional identifier for finding this widget in the tree. */
    public @Nullable String getId() { return id; }
    public void setId(String id) { this.id = id; }

    /** Find a descendant widget by id (depth-first). */
    @SuppressWarnings("unchecked")
    public <T extends BaseWidget> @Nullable T findById(String id) {
        for (WidgetNode child : children) {
            if (child instanceof BaseWidget bw) {
                if (id.equals(bw.getId())) return (T) bw;
                T found = bw.findById(id);
                if (found != null) return found;
            }
        }
        return null;
    }

    // --- User Data (data attributes) ---

    /** Set an arbitrary data attribute on this widget. */
    public void setData(String key, Object value) {
        if (userData == null) userData = new HashMap<>();
        userData.put(key, value);
    }

    /** Get a data attribute, or null if not set. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getData(String key) {
        if (userData == null) return null;
        return (T) userData.get(key);
    }

    /** Check if a data attribute exists. */
    public boolean hasData(String key) {
        return userData != null && userData.containsKey(key);
    }

    /** Remove a data attribute. */
    public void removeData(String key) {
        if (userData != null) userData.remove(key);
    }
}
