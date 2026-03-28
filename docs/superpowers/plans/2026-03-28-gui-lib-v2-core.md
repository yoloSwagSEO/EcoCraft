# gui-lib V2 Core: Widget Tree + Event System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the core widget tree system with parent→children hierarchy, depth-first rendering, bubble event propagation, portals, modal support, and focus management.

**Architecture:** New `core` package in gui-lib with WidgetNode interface, BaseWidget abstract class, WidgetTree manager, RootNode, and EcoScreen bridge. All testable without Minecraft runtime (except EcoScreen). Existing widgets untouched — this is a parallel system.

**Tech Stack:** Java 21, NeoForge 1.21.1 (GuiGraphics, Screen), JUnit 5 for core tests.

---

### File Structure

| File | Description |
|------|-------------|
| `gui-lib/.../core/WidgetNode.java` | Interface — the contract for all tree widgets |
| `gui-lib/.../core/BaseWidget.java` | Abstract class — convenience implementation |
| `gui-lib/.../core/RootNode.java` | Invisible root container (internal) |
| `gui-lib/.../core/WidgetTree.java` | Tree manager — render, events, focus, portals |
| `gui-lib/.../core/EcoScreen.java` | Minecraft Screen bridge |
| `gui-lib/src/test/.../core/WidgetTreeTest.java` | Unit tests for tree, events, focus, portals |

All files in package `net.ecocraft.gui.core`.

---

### Task 1: WidgetNode interface

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/WidgetNode.java`

- [ ] **Step 1: Create WidgetNode.java**

```java
package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Contract for any widget in the gui-lib widget tree.
 * Widgets form a parent→children hierarchy. Rendering is depth-first.
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gui-lib:compileJava`

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/WidgetNode.java
git commit -m "feat(gui-lib): add WidgetNode interface for widget tree"
```

---

### Task 2: BaseWidget abstract class

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/BaseWidget.java`

- [ ] **Step 1: Create BaseWidget.java**

```java
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gui-lib:compileJava`

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/BaseWidget.java
git commit -m "feat(gui-lib): add BaseWidget abstract class"
```

---

### Task 3: RootNode + WidgetTree

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/RootNode.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/WidgetTree.java`

- [ ] **Step 1: Create RootNode.java**

```java
package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Invisible root container. Never renders itself.
 * All top-level widgets are children of the root.
 */
public class RootNode extends BaseWidget {

    public RootNode() {
        super(0, 0, 0, 0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Root is invisible — only children render
    }

    @Override
    public boolean containsPoint(double mx, double my) {
        return true; // Root covers entire screen
    }
}
```

- [ ] **Step 2: Create WidgetTree.java**

```java
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
        if (focusedNode != null && isDescendantOf(focusedNode, portal)) {
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

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Render root's children
        renderNode(root, graphics, mouseX, mouseY, partialTick);

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

            renderNode(child, graphics, mouseX, mouseY, partialTick);

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
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :gui-lib:compileJava`

- [ ] **Step 4: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/RootNode.java
git add gui-lib/src/main/java/net/ecocraft/gui/core/WidgetTree.java
git commit -m "feat(gui-lib): add RootNode and WidgetTree with render + event dispatch"
```

---

### Task 4: Unit tests for WidgetTree

**Files:**
- Create: `gui-lib/src/test/java/net/ecocraft/gui/core/WidgetTreeTest.java`

- [ ] **Step 1: Create test file**

Note: Since `GuiGraphics` requires Minecraft runtime, tests focus on tree structure, hit testing, event dispatch, and focus management. Create a `TestWidget` that extends `BaseWidget` with a no-op render.

```java
package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WidgetTreeTest {

    /** Minimal test widget — tracks received events. */
    static class TestWidget extends BaseWidget {
        final String name;
        final List<String> events = new ArrayList<>();
        boolean consumeClicks = false;
        boolean consumeKeys = false;
        boolean focusable = false;

        TestWidget(String name, int x, int y, int w, int h) {
            super(x, y, w, h);
            this.name = name;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            // no-op in tests
        }

        @Override
        public boolean onMouseClicked(double mx, double my, int button) {
            events.add("click:" + name);
            return consumeClicks;
        }

        @Override
        public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
            events.add("key:" + name + ":" + keyCode);
            return consumeKeys;
        }

        @Override
        public boolean onCharTyped(char c, int modifiers) {
            events.add("char:" + name + ":" + c);
            return consumeKeys;
        }

        @Override
        public boolean isFocusable() { return focusable; }

        @Override
        public void onFocusGained() { events.add("focus:" + name); }

        @Override
        public void onFocusLost() { events.add("unfocus:" + name); }
    }

    private WidgetTree tree;

    @BeforeEach
    void setup() {
        tree = new WidgetTree();
    }

    // --- Tree structure ---

    @Test
    void addChildSetsParent() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        tree.addChild(a);
        assertEquals(tree.getRoot(), a.getParent());
        assertEquals(1, tree.getRoot().getChildren().size());
    }

    @Test
    void removeChildClearsParent() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        tree.addChild(a);
        tree.removeChild(a);
        assertNull(a.getParent());
        assertEquals(0, tree.getRoot().getChildren().size());
    }

    @Test
    void clearRemovesAll() {
        tree.addChild(new TestWidget("A", 0, 0, 10, 10));
        tree.addChild(new TestWidget("B", 0, 0, 10, 10));
        tree.clear();
        assertEquals(0, tree.getRoot().getChildren().size());
    }

    @Test
    void addChildToOtherParentRemovesFromOld() {
        TestWidget parent1 = new TestWidget("P1", 0, 0, 100, 100);
        TestWidget parent2 = new TestWidget("P2", 0, 0, 100, 100);
        TestWidget child = new TestWidget("C", 0, 0, 50, 50);
        tree.addChild(parent1);
        tree.addChild(parent2);
        parent1.addChild(child);
        assertEquals(1, parent1.getChildren().size());
        parent2.addChild(child);
        assertEquals(0, parent1.getChildren().size());
        assertEquals(1, parent2.getChildren().size());
        assertEquals(parent2, child.getParent());
    }

    // --- Hit testing ---

    @Test
    void hitTestFindsDeepestWidget() {
        TestWidget outer = new TestWidget("outer", 0, 0, 200, 200);
        TestWidget inner = new TestWidget("inner", 50, 50, 100, 100);
        outer.addChild(inner);
        tree.addChild(outer);

        WidgetNode result = tree.hitTest(tree.getRoot(), 75, 75);
        assertEquals(inner, result);
    }

    @Test
    void hitTestReturnsParentWhenOutsideChild() {
        TestWidget outer = new TestWidget("outer", 0, 0, 200, 200);
        TestWidget inner = new TestWidget("inner", 50, 50, 100, 100);
        outer.addChild(inner);
        tree.addChild(outer);

        WidgetNode result = tree.hitTest(tree.getRoot(), 10, 10);
        assertEquals(outer, result);
    }

    @Test
    void hitTestReturnsNullOutsideAll() {
        TestWidget a = new TestWidget("A", 10, 10, 50, 50);
        tree.addChild(a);

        WidgetNode result = tree.hitTest(tree.getRoot(), 0, 0);
        // Root containsPoint returns true, so it returns root
        assertEquals(tree.getRoot(), result);
    }

    @Test
    void hitTestPrefersLastChild() {
        // Two overlapping widgets — last added is "on top"
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        TestWidget b = new TestWidget("B", 0, 0, 100, 100);
        tree.addChild(a);
        tree.addChild(b);

        WidgetNode result = tree.hitTest(tree.getRoot(), 50, 50);
        assertEquals(b, result); // B is last = on top
    }

    @Test
    void hitTestSkipsInvisible() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        TestWidget b = new TestWidget("B", 0, 0, 100, 100);
        b.setVisible(false);
        tree.addChild(a);
        tree.addChild(b);

        WidgetNode result = tree.hitTest(tree.getRoot(), 50, 50);
        assertEquals(a, result);
    }

    // --- Event bubbling ---

    @Test
    void clickBubblesToParentWhenNotConsumed() {
        TestWidget parent = new TestWidget("parent", 0, 0, 200, 200);
        TestWidget child = new TestWidget("child", 50, 50, 100, 100);
        parent.addChild(child);
        tree.addChild(parent);

        tree.mouseClicked(75, 75, 0);

        assertEquals(List.of("click:child"), child.events);
        assertEquals(List.of("click:parent"), parent.events);
    }

    @Test
    void clickStopsBubblingWhenConsumed() {
        TestWidget parent = new TestWidget("parent", 0, 0, 200, 200);
        TestWidget child = new TestWidget("child", 50, 50, 100, 100);
        child.consumeClicks = true;
        parent.addChild(child);
        tree.addChild(parent);

        tree.mouseClicked(75, 75, 0);

        assertEquals(List.of("click:child"), child.events);
        assertTrue(parent.events.isEmpty()); // Parent never got the event
    }

    // --- Focus ---

    @Test
    void clickOnFocusableWidgetSetsFocus() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        a.focusable = true;
        tree.addChild(a);

        tree.mouseClicked(50, 50, 0);

        assertEquals(a, tree.getFocused());
        assertTrue(a.events.contains("focus:A"));
    }

    @Test
    void clickOnNonFocusableClearsFocus() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 50);
        a.focusable = true;
        TestWidget b = new TestWidget("B", 0, 50, 100, 50);
        tree.addChild(a);
        tree.addChild(b);

        tree.mouseClicked(50, 25, 0); // Click A → focus
        assertEquals(a, tree.getFocused());

        tree.mouseClicked(50, 75, 0); // Click B (not focusable) → clear
        assertNull(tree.getFocused());
        assertTrue(a.events.contains("unfocus:A"));
    }

    @Test
    void keyEventsGoToFocusedNode() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        a.focusable = true;
        tree.addChild(a);
        tree.setFocused(a);

        tree.keyPressed(65, 0, 0); // 'A' key
        tree.charTyped('a', 0);

        assertTrue(a.events.contains("key:A:65"));
        assertTrue(a.events.contains("char:A:a"));
    }

    @Test
    void keyEventsIgnoredWhenNoFocus() {
        TestWidget a = new TestWidget("A", 0, 0, 100, 100);
        tree.addChild(a);

        boolean consumed = tree.keyPressed(65, 0, 0);
        assertFalse(consumed);
        assertTrue(a.events.isEmpty());
    }

    // --- Portals ---

    @Test
    void portalReceivesEventsBeforeRoot() {
        TestWidget root = new TestWidget("root", 0, 0, 200, 200);
        root.consumeClicks = true;
        tree.addChild(root);

        TestWidget portal = new TestWidget("portal", 50, 50, 100, 100);
        portal.consumeClicks = true;
        tree.addPortal(portal);

        tree.mouseClicked(75, 75, 0);

        assertTrue(portal.events.contains("click:portal"));
        assertTrue(root.events.isEmpty()); // Root never got it
    }

    @Test
    void modalPortalBlocksEverythingBelow() {
        TestWidget root = new TestWidget("root", 0, 0, 200, 200);
        tree.addChild(root);

        TestWidget modal = new TestWidget("modal", 50, 50, 50, 50);
        modal.setModal(true);
        tree.addPortal(modal);

        // Click outside the modal — should be blocked
        boolean consumed = tree.mouseClicked(10, 10, 0);
        assertTrue(consumed);
        assertTrue(root.events.isEmpty());
    }

    @Test
    void removePortalCleansUp() {
        TestWidget portal = new TestWidget("portal", 0, 0, 100, 100);
        portal.focusable = true;
        tree.addPortal(portal);
        tree.setFocused(portal);

        tree.removePortal(portal);

        assertNull(tree.getFocused());
        assertFalse(tree.mouseClicked(50, 50, 0)); // Portal gone
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :gui-lib:test --tests '*WidgetTreeTest*' -v`
Expected: All tests PASS (render() is no-op in tests, no Minecraft runtime needed)

Note: If `GuiGraphics` import causes issues in test compilation, the `render()` method takes it as parameter but tests never call `tree.render()`. The test should still compile since `GuiGraphics` is on the compile classpath via NeoForge.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/test/java/net/ecocraft/gui/core/WidgetTreeTest.java
git commit -m "test(gui-lib): add WidgetTree unit tests for tree, events, focus, portals"
```

---

### Task 5: EcoScreen bridge

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/EcoScreen.java`

- [ ] **Step 1: Create EcoScreen.java**

```java
package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Base Screen class that uses the WidgetTree instead of Minecraft's flat widget list.
 * Subclasses add widgets to {@link #getTree()} instead of using addRenderableWidget().
 */
public abstract class EcoScreen extends Screen {

    private final WidgetTree tree = new WidgetTree();

    protected EcoScreen(Component title) {
        super(title);
    }

    public WidgetTree getTree() { return tree; }

    @Override
    protected void init() {
        tree.clear();
        // Subclasses override and add widgets to tree
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        tree.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tree.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (tree.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (tree.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tree.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tree.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (tree.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 2: Verify full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Deploy**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 4: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/EcoScreen.java
git commit -m "feat(gui-lib): add EcoScreen bridge for WidgetTree integration"
```

---

### Testing Instructions

The core is a parallel system — it does not affect existing widgets or the auction house. Validation:

1. All unit tests pass (`./gradlew :gui-lib:test`)
2. Full build succeeds
3. Auction house works normally (no regression)

Phase 2 will create EcoEditBox and migrate existing widgets to BaseWidget, then Phase 3 will migrate AH screens to EcoScreen.
