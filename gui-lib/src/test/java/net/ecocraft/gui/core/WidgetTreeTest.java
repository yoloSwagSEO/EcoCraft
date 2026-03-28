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

        tree.mouseClicked(50, 25, 0); // Click A -> focus
        assertEquals(a, tree.getFocused());

        tree.mouseClicked(50, 75, 0); // Click B (not focusable) -> clear
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
