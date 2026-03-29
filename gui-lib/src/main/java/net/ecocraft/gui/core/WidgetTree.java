package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the widget tree: rendering, event dispatch, focus, and portals.
 *
 * <p>Rendering: depth-first traversal of the tree, then portals on top.</p>
 * <p>Events: hit-test to find target (reverse child order = topmost first),
 * then bubble from target to root. Portals checked first.</p>
 * <p>Focus: single global focused node. Key events go to focused node.</p>
 */
public class WidgetTree {

    private final RootNode root = new RootNode();
    private final List<WidgetNode> portals = new ArrayList<>();
    private @Nullable WidgetNode focusedNode;

    // --- Tree operations ---

    public RootNode getRoot() { return root; }

    public void addChild(WidgetNode child) {
        root.addChild(child);
    }

    public void removeChild(WidgetNode child) {
        root.removeChild(child);
        // Clean up portals owned by removed subtree
        cleanupPortalsFor(child);
    }

    /** Find a widget by its id (depth-first search). Returns null if not found. */
    @Nullable
    public BaseWidget findById(String id) {
        return findByIdIn(root, id);
    }

    @Nullable
    private BaseWidget findByIdIn(WidgetNode node, String id) {
        if (node instanceof BaseWidget bw && id.equals(bw.getId())) return bw;
        for (WidgetNode child : node.getChildren()) {
            BaseWidget found = findByIdIn(child, id);
            if (found != null) return found;
        }
        return null;
    }

    public void clear() {
        for (WidgetNode child : new ArrayList<>(root.getChildren())) {
            root.removeChild(child);
        }
        portals.clear();
        focusedNode = null;
    }

    // --- Portals ---

    public void addPortal(WidgetNode portal) {
        portals.add(portal);
    }

    public void removePortal(WidgetNode portal) {
        portals.remove(portal);
        if (focusedNode != null && (focusedNode == portal || isDescendantOf(focusedNode, portal))) {
            setFocused(null);
        }
    }

    private void cleanupPortalsFor(WidgetNode subtree) {
        portals.removeIf(p -> isDescendantOf(p, subtree) || p == subtree);
        if (focusedNode != null && isDescendantOf(focusedNode, subtree)) {
            setFocused(null);
        }
    }

    private boolean isDescendantOf(WidgetNode node, WidgetNode ancestor) {
        WidgetNode current = node.getParent();
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }

    // --- Focus ---

    public @Nullable WidgetNode getFocused() { return focusedNode; }

    public void setFocused(@Nullable WidgetNode node) {
        if (focusedNode == node) return;
        if (focusedNode != null) focusedNode.onFocusLost();
        focusedNode = node;
        if (focusedNode != null) focusedNode.onFocusGained();
    }

    // --- Rendering ---

    /** Returns true if any visible portal is modal. */
    private boolean hasModalPortal() {
        for (WidgetNode p : portals) {
            if (p.isVisible() && p.isModal()) return true;
        }
        return false;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // If a modal portal is open, hide mouse from root widgets (prevents hover/tooltips)
        boolean modal = hasModalPortal();
        int rootMx = modal ? -1 : mouseX;
        int rootMy = modal ? -1 : mouseY;

        // 1. Render root's children (with hidden mouse if modal)
        renderNode(root, graphics, rootMx, rootMy, partialTick);

        // 2. Render portals (on top)
        for (WidgetNode portal : portals) {
            if (portal.isVisible()) {
                portal.render(graphics, mouseX, mouseY, partialTick);
                renderNode(portal, graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderNode(WidgetNode node, GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (WidgetNode child : node.getChildren()) {
            if (!child.isVisible()) continue;

            child.render(graphics, mouseX, mouseY, partialTick);

            if (child.isClipChildren()) {
                graphics.enableScissor(child.getX(), child.getY(),
                        child.getX() + child.getWidth(), child.getY() + child.getHeight());
            }

            // Delegate to child's renderChildren — allows widgets like EcoRepeater
            // to override and handle their own children rendering (e.g. with scroll translate)
            child.renderChildren(graphics, mouseX, mouseY, partialTick);

            if (child.isClipChildren()) {
                graphics.disableScissor();
            }
        }
    }

    // --- Event dispatch ---

    public boolean mouseClicked(double mx, double my, int button) {
        // 1. Check portals (reverse order — last = top)
        for (int i = portals.size() - 1; i >= 0; i--) {
            WidgetNode portal = portals.get(i);
            if (!portal.isVisible()) continue;

            WidgetNode target = hitTest(portal, mx, my);
            if (target != null) {
                boolean consumed = bubbleMouseClicked(target, mx, my, button);
                if (target.isFocusable()) setFocused(target);
                else setFocused(null);
                return consumed || portal.isModal();
            }

            // Modal portal blocks everything below even if nothing was hit
            if (portal.isModal()) return true;
        }

        // 2. Check root children (reverse order)
        WidgetNode target = hitTest(root, mx, my);
        if (target != null && target != root) {
            boolean consumed = bubbleMouseClicked(target, mx, my, button);
            if (target.isFocusable()) setFocused(target);
            else setFocused(null);
            return consumed;
        }

        setFocused(null);
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        // Same pattern as mouseClicked
        for (int i = portals.size() - 1; i >= 0; i--) {
            WidgetNode portal = portals.get(i);
            if (!portal.isVisible()) continue;
            WidgetNode target = hitTest(portal, mx, my);
            if (target != null) return bubbleMouseReleased(target, mx, my, button);
            if (portal.isModal()) return true;
        }
        WidgetNode target = hitTest(root, mx, my);
        if (target != null && target != root) return bubbleMouseReleased(target, mx, my, button);
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        for (int i = portals.size() - 1; i >= 0; i--) {
            WidgetNode portal = portals.get(i);
            if (!portal.isVisible()) continue;
            WidgetNode target = hitTest(portal, mx, my);
            if (target != null) return bubbleMouseDragged(target, mx, my, button, dx, dy);
            if (portal.isModal()) return true;
        }
        WidgetNode target = hitTest(root, mx, my);
        if (target != null && target != root) return bubbleMouseDragged(target, mx, my, button, dx, dy);
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        for (int i = portals.size() - 1; i >= 0; i--) {
            WidgetNode portal = portals.get(i);
            if (!portal.isVisible()) continue;
            WidgetNode target = hitTest(portal, mx, my);
            if (target != null) return bubbleMouseScrolled(target, mx, my, sx, sy);
            if (portal.isModal()) return true;
        }
        WidgetNode target = hitTest(root, mx, my);
        if (target != null && target != root) return bubbleMouseScrolled(target, mx, my, sx, sy);
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (focusedNode != null) {
            return bubbleKeyPressed(focusedNode, keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (focusedNode != null) {
            return bubbleCharTyped(focusedNode, codePoint, modifiers);
        }
        return false;
    }

    // --- Hit testing ---

    /** Find the deepest visible widget containing (mx, my). Checks children in reverse order (last = top). */
    @Nullable
    WidgetNode hitTest(WidgetNode node, double mx, double my) {
        if (!node.isVisible()) return null;
        if (!node.containsPoint(mx, my)) return null;

        // Nodes that clip children (e.g. ScrollPane) handle their own event dispatch
        // with coordinate adjustments — don't recurse into their children here.
        if (node.isClipChildren()) return node;

        // Check children in reverse order (last child = rendered on top = tested first)
        List<WidgetNode> children = node.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            WidgetNode result = hitTest(children.get(i), mx, my);
            if (result != null) return result;
        }

        return node;
    }

    // --- Bubble helpers ---

    private boolean bubbleMouseClicked(WidgetNode node, double mx, double my, int button) {
        if (node.onMouseClicked(mx, my, button)) return true;
        return node.getParent() != null && bubbleMouseClicked(node.getParent(), mx, my, button);
    }

    private boolean bubbleMouseReleased(WidgetNode node, double mx, double my, int button) {
        if (node.onMouseReleased(mx, my, button)) return true;
        return node.getParent() != null && bubbleMouseReleased(node.getParent(), mx, my, button);
    }

    private boolean bubbleMouseDragged(WidgetNode node, double mx, double my, int button, double dx, double dy) {
        if (node.onMouseDragged(mx, my, button, dx, dy)) return true;
        return node.getParent() != null && bubbleMouseDragged(node.getParent(), mx, my, button, dx, dy);
    }

    private boolean bubbleMouseScrolled(WidgetNode node, double mx, double my, double sx, double sy) {
        if (node.onMouseScrolled(mx, my, sx, sy)) return true;
        return node.getParent() != null && bubbleMouseScrolled(node.getParent(), mx, my, sx, sy);
    }

    private boolean bubbleKeyPressed(WidgetNode node, int keyCode, int scanCode, int modifiers) {
        if (node.onKeyPressed(keyCode, scanCode, modifiers)) return true;
        return node.getParent() != null && bubbleKeyPressed(node.getParent(), keyCode, scanCode, modifiers);
    }

    private boolean bubbleCharTyped(WidgetNode node, char codePoint, int modifiers) {
        if (node.onCharTyped(codePoint, modifiers)) return true;
        return node.getParent() != null && bubbleCharTyped(node.getParent(), codePoint, modifiers);
    }
}
