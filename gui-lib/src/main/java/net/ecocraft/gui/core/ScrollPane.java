package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A scrollable container that clips children to its bounds and scrolls them vertically.
 * Children use absolute Y positions as if no scrolling occurs; the ScrollPane applies
 * a Y translation during rendering and adjusts mouse coordinates for hit testing.
 *
 * <p>Includes a built-in scrollbar on the right side, rendered when content exceeds
 * the visible height.</p>
 */
public class ScrollPane extends BaseWidget {

    private final Theme theme;
    private int contentHeight = 0;
    private int scrollOffset = 0;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLL_LINE_HEIGHT = 20;
    private static final int MIN_THUMB_HEIGHT = 16;

    private boolean draggingScrollbar = false;
    private double dragStartY;
    private int dragStartOffset;

    /**
     * Thread-local render offset tracking. Widgets that use enableScissor
     * should call ScrollPane.getRenderOffsetY() to adjust their scissor coordinates.
     */
    private static int renderOffsetY = 0;

    /** Returns the current cumulative Y render offset from enclosing ScrollPanes. */
    public static int getRenderOffsetY() {
        return renderOffsetY;
    }

    public ScrollPane(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        setClipChildren(true);
    }

    // --- API ---

    /** Set the total height of the scrollable content area. */
    public void setContentHeight(int contentHeight) {
        this.contentHeight = contentHeight;
        clampScroll();
    }

    public int getContentHeight() {
        return contentHeight;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int offset) {
        this.scrollOffset = offset;
        clampScroll();
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // 1. Draw panel background
        graphics.fill(x, y, x + w, y + h, theme.bgDark);

        // 2. Scissor for content area (excluding scrollbar)
        int contentWidth = w - (needsScrollbar() ? SCROLLBAR_WIDTH : 0);
        graphics.enableScissor(x, y, x + contentWidth, y + h);

        // 3. Translate and render children (adjust mouseY so hover/focus states work)
        graphics.pose().pushPose();
        graphics.pose().translate(0, -scrollOffset, 0);
        int prevOffset = renderOffsetY;
        renderOffsetY = prevOffset - scrollOffset;
        renderChildrenInternal(graphics, mouseX, mouseY + scrollOffset, partialTick);
        renderOffsetY = prevOffset;
        graphics.pose().popPose();

        // 4. Disable scissor
        graphics.disableScissor();

        // 5. Draw scrollbar if needed
        if (needsScrollbar()) {
            renderScrollbar(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void renderChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: children are rendered inside render() with scroll translate
    }

    /** Internal: render children inside the scroll translate. Called from render(). */
    private void renderChildrenInternal(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (WidgetNode child : getChildren()) {
            if (child.isVisible()) {
                child.render(graphics, mouseX, mouseY, partialTick);
                child.renderChildren(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = getX() + getWidth() - SCROLLBAR_WIDTH;
        int y = getY();
        int h = getHeight();

        // Track
        graphics.fill(x, y, x + SCROLLBAR_WIDTH, y + h, theme.bgMedium);

        // Thumb
        int thumbHeight = getThumbHeight();
        int thumbY = y + getThumbPosition();
        int thumbColor = draggingScrollbar ? theme.accent : theme.bgLight;
        graphics.fill(x, thumbY, x + SCROLLBAR_WIDTH, thumbY + thumbHeight, thumbColor);
    }

    // --- Scrollbar geometry ---

    private boolean needsScrollbar() {
        return contentHeight > getHeight();
    }

    private int getMaxScroll() {
        return Math.max(0, contentHeight - getHeight());
    }

    private int getThumbHeight() {
        if (!needsScrollbar()) return getHeight();
        int visibleHeight = getHeight();
        int thumbH = (int) ((float) visibleHeight / contentHeight * visibleHeight);
        return Math.max(MIN_THUMB_HEIGHT, thumbH);
    }

    private int getThumbPosition() {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return 0;
        int trackSpace = getHeight() - getThumbHeight();
        return (int) ((float) scrollOffset / maxScroll * trackSpace);
    }

    private boolean isInScrollbar(double mx, double my) {
        if (!needsScrollbar()) return false;
        int sbX = getX() + getWidth() - SCROLLBAR_WIDTH;
        return mx >= sbX && mx < getX() + getWidth()
                && my >= getY() && my < getY() + getHeight();
    }

    private boolean isOnThumb(double mx, double my) {
        if (!isInScrollbar(mx, my)) return false;
        int thumbY = getY() + getThumbPosition();
        int thumbH = getThumbHeight();
        return my >= thumbY && my < thumbY + thumbH;
    }

    private void clampScroll() {
        int max = getMaxScroll();
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > max) scrollOffset = max;
    }

    /** Recursively find the deepest visible widget containing (mx, my). */
    private WidgetNode deepHitTest(WidgetNode node, double mx, double my) {
        if (!node.isVisible()) return null;
        if (!node.containsPoint(mx, my)) return null;

        // Don't recurse into nested ScrollPanes — they handle their own dispatch
        if (node != this && node.isClipChildren()) return node;

        java.util.List<WidgetNode> children = node.getChildren();
        for (int i = children.size() - 1; i >= 0; i--) {
            WidgetNode result = deepHitTest(children.get(i), mx, my);
            if (result != null) return result;
        }
        return node;
    }

    // --- Mouse events ---

    @Override
    public boolean onMouseScrolled(double mx, double my, double scrollX, double scrollY) {
        // Let children handle scroll first (e.g., nested EcoRepeater with its own scroll)
        double adjustedY = my + scrollOffset;
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            WidgetNode child = getChildren().get(i);
            if (child.isVisible() && child.containsPoint(mx, adjustedY)) {
                if (child.onMouseScrolled(mx, adjustedY, scrollX, scrollY)) return true;
            }
        }
        // No child consumed it — scroll ourselves
        if (!needsScrollbar()) return false;
        scrollOffset -= (int) (scrollY * SCROLL_LINE_HEIGHT);
        clampScroll();
        return true;
    }

    @Override
    public boolean onMouseClicked(double mx, double my, int button) {
        // Check scrollbar first
        if (isInScrollbar(mx, my)) {
            if (isOnThumb(mx, my)) {
                // Start dragging
                draggingScrollbar = true;
                dragStartY = my;
                dragStartOffset = scrollOffset;
            } else {
                // Jump to position on track
                int trackHeight = getHeight();
                int thumbH = getThumbHeight();
                int trackSpace = trackHeight - thumbH;
                // Center thumb on click position
                double relativeY = my - getY() - (double) thumbH / 2;
                double ratio = relativeY / trackSpace;
                ratio = Math.max(0, Math.min(1, ratio));
                scrollOffset = (int) (ratio * getMaxScroll());
                clampScroll();
            }
            return true;
        }

        // Adjust Y for scroll offset and forward to deepest matching child
        double adjustedY = my + scrollOffset;
        WidgetNode deepest = deepHitTest(this, mx, adjustedY);
        if (deepest != null && deepest != this) {
            // Bubble up from deepest widget to this ScrollPane
            WidgetNode node = deepest;
            while (node != null && node != this) {
                if (node.onMouseClicked(mx, adjustedY, button)) return true;
                node = node.getParent();
            }
        }
        return false;
    }

    @Override
    public boolean onMouseDragged(double mx, double my, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            int trackSpace = getHeight() - getThumbHeight();
            if (trackSpace <= 0) return true;
            double dragDelta = my - dragStartY;
            double ratio = dragDelta / trackSpace;
            scrollOffset = dragStartOffset + (int) (ratio * getMaxScroll());
            clampScroll();
            return true;
        }

        // Forward to children with adjusted Y
        double adjustedY = my + scrollOffset;
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            WidgetNode child = getChildren().get(i);
            if (child.isVisible() && child.containsPoint(mx, adjustedY)) {
                if (child.onMouseDragged(mx, adjustedY, button, deltaX, deltaY)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mx, double my, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }

        // Forward to children with adjusted Y
        double adjustedY = my + scrollOffset;
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            WidgetNode child = getChildren().get(i);
            if (child.isVisible() && child.containsPoint(mx, adjustedY)) {
                if (child.onMouseReleased(mx, adjustedY, button)) return true;
            }
        }
        return false;
    }
}
