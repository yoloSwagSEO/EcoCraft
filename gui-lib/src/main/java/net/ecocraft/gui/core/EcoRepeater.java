package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * V2 dynamic list widget extending BaseWidget. Row widgets are children
 * in the widget tree (via addChild). Built-in scrolling when content overflows.
 *
 * <p>Delete buttons (x) and Add button ("+ Ajouter") are drawn manually,
 * not as child widgets.</p>
 *
 * @param <T> the value type per row
 */
public class EcoRepeater<T> extends BaseWidget {

    private static final int DELETE_BTN_SIZE = 16;
    private static final int ADD_BTN_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int SCROLL_LINE_HEIGHT = 20;

    private final Theme theme;
    private final Font font;

    // Config
    private Supplier<T> itemFactory;
    private int rowHeight = 24;
    private BiConsumer<T, RowContext<T>> rowRenderer;
    private int maxItems = Integer.MAX_VALUE;
    private Consumer<List<T>> responder;

    // Scroll state
    private int scrollOffset = 0;
    private boolean draggingScrollbar = false;
    private double dragStartY;
    private int dragStartOffset;

    // Data source
    private final List<T> data = new ArrayList<>();

    public EcoRepeater(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height);
        this.theme = theme;
        this.font = Minecraft.getInstance().font;
        // We handle scissor/clipping ourselves in render() — don't let the tree clip or re-render children
        setClipChildren(false);
    }

    // --- Configuration API (fluent) ---

    public EcoRepeater<T> itemFactory(Supplier<T> factory) { this.itemFactory = factory; return this; }
    public EcoRepeater<T> rowHeight(int h) { this.rowHeight = h; return this; }
    public EcoRepeater<T> rowRenderer(BiConsumer<T, RowContext<T>> renderer) { this.rowRenderer = renderer; return this; }
    public EcoRepeater<T> maxItems(int max) { this.maxItems = max; return this; }
    public EcoRepeater<T> responder(Consumer<List<T>> responder) { this.responder = responder; return this; }

    /** Set initial data and build widgets. */
    public EcoRepeater<T> values(List<T> values) {
        this.data.clear();
        this.data.addAll(values);
        rebuildAllWidgets();
        return this;
    }

    public List<T> getValues() {
        return new ArrayList<>(data);
    }

    // --- Widget lifecycle ---

    private void rebuildAllWidgets() {
        // Remove all current children
        for (WidgetNode child : new ArrayList<>(getChildren())) {
            removeChild(child);
        }

        if (rowRenderer == null) return;

        int contentW = getContentWidth();
        for (int i = 0; i < data.size(); i++) {
            int rowY = getY() + 1 + i * (rowHeight + ROW_GAP) - scrollOffset;
            int rowX = getX() + 2;
            final int index = i;

            RowContext<T> ctx = new RowContext<>(rowX, rowY, contentW, index, font, theme,
                    newVal -> { data.set(index, newVal); fireResponder(); },
                    this);

            rowRenderer.accept(data.get(i), ctx);
        }

        clampScroll();
    }

    /** Reposition children Y based on current scrollOffset (no rebuild). */
    private void repositionChildren() {
        int i = 0;
        for (WidgetNode child : getChildren()) {
            int rowY = getY() + 1 + i * (rowHeight + ROW_GAP) - scrollOffset;
            child.setPosition(child.getX(), rowY);
            i++;
        }
    }

    private int getContentWidth() {
        boolean scrollbarNeeded = needsScrollbar();
        int sbWidth = scrollbarNeeded ? EcoScrollbar.SCROLLBAR_WIDTH : 0;
        return getWidth() - DELETE_BTN_SIZE - 10 - sbWidth;
    }

    // --- Add / Remove ---

    private void addRow() {
        if (itemFactory == null || data.size() >= maxItems) return;
        data.add(itemFactory.get());
        rebuildAllWidgets();
        fireResponder();
    }

    private void removeRow(int index) {
        if (index < 0 || index >= data.size()) return;
        data.remove(index);
        rebuildAllWidgets();
        fireResponder();
    }

    private void fireResponder() {
        if (responder != null) responder.accept(getValues());
    }

    // --- Scroll geometry ---

    private int getContentHeight() {
        if (data.isEmpty()) return 0;
        return data.size() * (rowHeight + ROW_GAP) - ROW_GAP;
    }

    private int getListHeight() {
        return getHeight() - 2 - ADD_BTN_HEIGHT - ROW_GAP;
    }

    private boolean needsScrollbar() {
        return getContentHeight() > getListHeight();
    }

    private int getMaxScroll() {
        return Math.max(0, getContentHeight() - getListHeight());
    }

    private void clampScroll() {
        int max = getMaxScroll();
        if (scrollOffset < 0) scrollOffset = 0;
        if (scrollOffset > max) scrollOffset = max;
    }

    private static final int MIN_THUMB_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = EcoScrollbar.SCROLLBAR_WIDTH;

    private int getThumbHeight() {
        int listH = getListHeight();
        int contentH = getContentHeight();
        if (contentH <= 0) return listH;
        int thumbH = (int) ((float) listH / contentH * listH);
        return Math.max(MIN_THUMB_HEIGHT, thumbH);
    }

    private int getThumbPosition() {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return 0;
        int trackSpace = getListHeight() - getThumbHeight();
        return (int) ((float) scrollOffset / maxScroll * trackSpace);
    }

    private boolean isInScrollbar(double mx, double my) {
        if (!needsScrollbar()) return false;
        int sbX = getX() + getWidth() - SCROLLBAR_WIDTH;
        int listY = getY() + 1;
        int listH = getListHeight();
        return mx >= sbX && mx < getX() + getWidth()
                && my >= listY && my < listY + listH;
    }

    private boolean isOnThumb(double mx, double my) {
        if (!isInScrollbar(mx, my)) return false;
        int listY = getY() + 1;
        int thumbY = listY + getThumbPosition();
        int thumbH = getThumbHeight();
        return my >= thumbY && my < thumbY + thumbH;
    }

    // --- Rendering ---

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Panel background
        DrawUtils.drawPanel(graphics, x, y, w, h, theme.bgDark, theme.border);

        int listX = x + 1;
        int listY = y + 1;
        int listH = getListHeight();
        int contentW = w - 2;

        // Scissor for the list area (adjusted for ScrollPane render offset)
        int scrollY = ScrollPane.getRenderOffsetY();
        graphics.enableScissor(listX, listY + scrollY, listX + contentW, listY + listH + scrollY);

        if (data.isEmpty()) {
            String emptyText = "Aucun \u00e9l\u00e9ment";
            int tw = font.width(emptyText);
            graphics.drawString(font, emptyText, listX + (contentW - tw) / 2,
                    listY + (listH - font.lineHeight) / 2, theme.textDim, false);
        } else {
            // Reposition children to account for scroll offset
            repositionChildren();

            for (int i = 0; i < data.size(); i++) {
                int rowY = listY + i * (rowHeight + ROW_GAP) - scrollOffset;

                // Row background
                if (i % 2 == 1) {
                    graphics.fill(listX, rowY, listX + contentW, rowY + rowHeight, theme.bgRowAlt);
                }

                // Separator
                if (i < data.size() - 1) {
                    graphics.fill(listX, rowY + rowHeight, listX + contentW,
                            rowY + rowHeight + 1, theme.bgMedium);
                }

                // Delete button
                int delX = listX + contentW - DELETE_BTN_SIZE - 4
                        - (needsScrollbar() ? SCROLLBAR_WIDTH : 0);
                int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;

                boolean delHovered = mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                        && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE
                        && mouseY >= listY && mouseY < listY + listH;

                DrawUtils.drawPanel(graphics, delX, delY, DELETE_BTN_SIZE, DELETE_BTN_SIZE,
                        delHovered ? theme.dangerBg : theme.bgMedium,
                        delHovered ? theme.danger : theme.borderLight);
                String xChar = "\u00d7";
                graphics.drawString(font, xChar,
                        delX + (DELETE_BTN_SIZE - font.width(xChar)) / 2,
                        delY + (DELETE_BTN_SIZE - font.lineHeight) / 2,
                        delHovered ? theme.danger : theme.textDim, false);
            }

            // Render children (already positioned with scroll offset)
            renderChildrenInternal(graphics, mouseX, mouseY, partialTick);
        }

        graphics.disableScissor();

        // Scrollbar
        if (needsScrollbar()) {
            renderScrollbar(graphics, mouseX, mouseY);
        }

        // Add button
        if (data.size() < maxItems) {
            int addY = y + h - 1 - ADD_BTN_HEIGHT;
            int addX = listX;
            int addW = w - 2;
            boolean addHovered = mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT;

            DrawUtils.drawPanel(graphics, addX, addY, addW, ADD_BTN_HEIGHT,
                    addHovered ? 0xFF2A4A2A : theme.successBg, theme.success);
            String addText = "+ Ajouter";
            graphics.drawString(font, addText,
                    addX + (addW - font.width(addText)) / 2,
                    addY + (ADD_BTN_HEIGHT - font.lineHeight) / 2,
                    theme.success, false);
        }
    }

    @Override
    public void renderChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: children are already rendered inside render() with the scroll translate.
        // This prevents the WidgetTree from rendering children a second time without translate.
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
        int sbX = getX() + getWidth() - SCROLLBAR_WIDTH;
        int listY = getY() + 1;
        int listH = getListHeight();

        // Track
        graphics.fill(sbX, listY, sbX + SCROLLBAR_WIDTH, listY + listH, theme.bgMedium);

        // Thumb
        int thumbHeight = getThumbHeight();
        int thumbY = listY + getThumbPosition();
        int thumbColor = draggingScrollbar ? theme.accent : theme.bgLight;
        graphics.fill(sbX + 1, thumbY, sbX + SCROLLBAR_WIDTH - 1, thumbY + thumbHeight, thumbColor);
    }

    // --- Mouse events ---

    @Override
    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (!containsPoint(mouseX, mouseY) || button != 0) return false;

        int listX = getX() + 1;
        int listY = getY() + 1;
        int listH = getListHeight();
        int contentW = getWidth() - 2;

        // Scrollbar
        if (isInScrollbar(mouseX, mouseY)) {
            if (isOnThumb(mouseX, mouseY)) {
                draggingScrollbar = true;
                dragStartY = mouseY;
                dragStartOffset = scrollOffset;
            } else {
                int trackSpace = listH - getThumbHeight();
                double relativeY = mouseY - listY - (double) getThumbHeight() / 2;
                double ratio = relativeY / trackSpace;
                ratio = Math.max(0, Math.min(1, ratio));
                scrollOffset = (int) (ratio * getMaxScroll());
                clampScroll();
            }
            return true;
        }

        // Add button
        if (data.size() < maxItems) {
            int addY = getY() + getHeight() - 1 - ADD_BTN_HEIGHT;
            int addX = listX;
            int addW = getWidth() - 2;
            if (mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT) {
                addRow();
                return true;
            }
        }

        // Delete buttons (positions already include scroll offset)
        if (mouseY >= listY && mouseY < listY + listH) {
            for (int i = 0; i < data.size(); i++) {
                int rowY = listY + i * (rowHeight + ROW_GAP) - scrollOffset;
                int delX = listX + contentW - DELETE_BTN_SIZE - 4
                        - (needsScrollbar() ? SCROLLBAR_WIDTH : 0);
                int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
                if (mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                        && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE) {
                    removeRow(i);
                    return true;
                }
            }

            // Forward to children (already positioned with scroll offset)
            for (int i = getChildren().size() - 1; i >= 0; i--) {
                WidgetNode child = getChildren().get(i);
                if (child.isVisible() && child.containsPoint(mouseX, mouseY)) {
                    if (child.onMouseClicked(mouseX, mouseY, button)) return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingScrollbar) {
            int trackSpace = getListHeight() - getThumbHeight();
            if (trackSpace <= 0) return true;
            double dragDelta = mouseY - dragStartY;
            double ratio = dragDelta / trackSpace;
            scrollOffset = dragStartOffset + (int) (ratio * getMaxScroll());
            clampScroll();
            return true;
        }

        // Forward to children (already positioned with scroll offset)
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            WidgetNode child = getChildren().get(i);
            if (child.isVisible() && child.containsPoint(mouseX, mouseY)) {
                if (child.onMouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }

        // Forward to children (already positioned with scroll offset)
        for (int i = getChildren().size() - 1; i >= 0; i--) {
            WidgetNode child = getChildren().get(i);
            if (child.isVisible() && child.containsPoint(mouseX, mouseY)) {
                if (child.onMouseReleased(mouseX, mouseY, button)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!containsPoint(mouseX, mouseY)) return false;
        if (!needsScrollbar()) return false;
        scrollOffset -= (int) (scrollY * SCROLL_LINE_HEIGHT);
        clampScroll();
        return true;
    }

    // --- Row Context ---

    /**
     * Context passed to the row renderer. Provides position info and
     * a way to add widgets as children of the repeater.
     */
    public static class RowContext<T> {
        private final int x, y, width, index;
        private final Font font;
        private final Theme theme;
        private final Consumer<T> valueSetter;
        private final EcoRepeater<?> repeater;

        RowContext(int x, int y, int width, int index, Font font, Theme theme,
                   Consumer<T> valueSetter, EcoRepeater<?> repeater) {
            this.x = x; this.y = y; this.width = width; this.index = index;
            this.font = font; this.theme = theme;
            this.valueSetter = valueSetter; this.repeater = repeater;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int index() { return index; }
        public Font font() { return font; }
        public Theme theme() { return theme; }
        public void setValue(T value) { valueSetter.accept(value); }

        /** Add a widget as a child of the repeater (in the widget tree). */
        public void addWidget(WidgetNode widget) { repeater.addChild(widget); }
    }
}
