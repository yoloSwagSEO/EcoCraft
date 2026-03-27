package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Dynamic list widget where each row contains user-defined widgets and a delete button.
 * An "Add" button at the bottom allows adding new rows.
 *
 * @param <T> the value type per row
 */
public class Repeater<T> extends AbstractWidget {

    private static final int DELETE_BTN_SIZE = 16;
    private static final int ADD_BTN_HEIGHT = 18;
    private static final int ROW_GAP = 2;
    private static final int SCROLLBAR_WIDTH = 8;

    private final Theme theme;
    private final Font font;
    private final Scrollbar scrollbar;

    private Supplier<T> itemFactory;
    private int rowHeight = 24;
    private BiConsumer<T, RowContext<T>> rowRenderer;
    private int maxItems = Integer.MAX_VALUE;
    private Consumer<List<T>> responder;

    private final List<RowEntry<T>> rows = new ArrayList<>();

    public Repeater(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.font = Minecraft.getInstance().font;
        this.scrollbar = new Scrollbar(x + width - SCROLLBAR_WIDTH - 1, y + 1, height - 2 - ADD_BTN_HEIGHT - ROW_GAP, theme);
    }

    // --- Configuration API ---

    public Repeater<T> itemFactory(Supplier<T> factory) {
        this.itemFactory = factory;
        return this;
    }

    public Repeater<T> rowHeight(int height) {
        this.rowHeight = height;
        return this;
    }

    public Repeater<T> rowRenderer(BiConsumer<T, RowContext<T>> renderer) {
        this.rowRenderer = renderer;
        return this;
    }

    public Repeater<T> maxItems(int max) {
        this.maxItems = max;
        return this;
    }

    public Repeater<T> responder(Consumer<List<T>> responder) {
        this.responder = responder;
        return this;
    }

    public Repeater<T> values(List<T> values) {
        this.rows.clear();
        for (T value : values) {
            this.rows.add(new RowEntry<>(value));
        }
        rebuildAllRows();
        updateScrollbar();
        return this;
    }

    public List<T> values() {
        List<T> result = new ArrayList<>();
        for (RowEntry<T> row : rows) {
            result.add(row.value);
        }
        return result;
    }

    // --- Internal ---

    private void rebuildAllRows() {
        for (int i = 0; i < rows.size(); i++) {
            rebuildRow(i);
        }
    }

    private void rebuildRow(int index) {
        RowEntry<T> entry = rows.get(index);
        entry.widgets.clear();

        if (rowRenderer == null) return;

        int contentWidth = getContentWidth();
        // Row widgets area: leave space for delete button
        int widgetAreaWidth = contentWidth - DELETE_BTN_SIZE - 4;

        RowContext<T> ctx = new RowContext<>(
                0, 0, // x/y are placeholders, set during render
                widgetAreaWidth, index, font, theme,
                newVal -> {
                    entry.value = newVal;
                    fireResponder();
                },
                entry.widgets
        );

        rowRenderer.accept(entry.value, ctx);
    }

    private int getContentWidth() {
        boolean needsScroll = needsScrollbar();
        return width - 2 - (needsScroll ? SCROLLBAR_WIDTH : 0); // 2 for border
    }

    private int getTotalContentHeight() {
        if (rows.isEmpty()) return rowHeight; // empty state height
        return rows.size() * (rowHeight + ROW_GAP) - ROW_GAP;
    }

    private int getListAreaHeight() {
        return height - 2 - ADD_BTN_HEIGHT - ROW_GAP; // borders + add button
    }

    private boolean needsScrollbar() {
        return getTotalContentHeight() > getListAreaHeight();
    }

    private int getScrollOffset() {
        if (!needsScrollbar()) return 0;
        int maxScroll = getTotalContentHeight() - getListAreaHeight();
        return (int) (scrollbar.getScrollValue() * maxScroll);
    }

    private void updateScrollbar() {
        int listHeight = getListAreaHeight();
        int contentHeight = getTotalContentHeight();
        scrollbar.setContentRatio(contentHeight > 0 ? (float) listHeight / contentHeight : 1f);
        scrollbar.setPosition(getX() + width - SCROLLBAR_WIDTH - 1, getY() + 1);
        scrollbar.setHeight(listHeight);
    }

    private boolean isAddButtonVisible() {
        return rows.size() < maxItems;
    }

    private void addRow() {
        if (itemFactory == null || rows.size() >= maxItems) return;
        T newValue = itemFactory.get();
        RowEntry<T> entry = new RowEntry<>(newValue);
        rows.add(entry);
        rebuildRow(rows.size() - 1);
        updateScrollbar();
        // Scroll to bottom to show new row
        if (needsScrollbar()) {
            scrollbar.setScrollValue(1f);
        }
        fireResponder();
    }

    private void removeRow(int index) {
        if (index < 0 || index >= rows.size()) return;
        rows.remove(index);
        // Rebuild all rows after removal to update indices
        rebuildAllRows();
        updateScrollbar();
        fireResponder();
    }

    private void fireResponder() {
        if (responder != null) {
            responder.accept(values());
        }
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateScrollbar();

        // Panel background
        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, theme.bgDark, theme.border);

        int listX = getX() + 1;
        int listY = getY() + 1;
        int listHeight = getListAreaHeight();
        int contentWidth = getContentWidth();

        // Scissor clipping for list area
        graphics.enableScissor(listX, listY, listX + contentWidth, listY + listHeight);

        if (rows.isEmpty()) {
            // Empty state
            String emptyText = "Aucun \u00e9l\u00e9ment";
            int textWidth = font.width(emptyText);
            int textX = listX + (contentWidth - textWidth) / 2;
            int textY = listY + (listHeight - 8) / 2;
            graphics.drawString(font, emptyText, textX, textY, theme.textDim, false);
        } else {
            int scrollOffset = getScrollOffset();

            for (int i = 0; i < rows.size(); i++) {
                int rowY = listY + i * (rowHeight + ROW_GAP) - scrollOffset;

                // Skip rows outside visible area
                if (rowY + rowHeight < listY || rowY > listY + listHeight) continue;

                RowEntry<T> entry = rows.get(i);

                // Row background
                int rowBg = (i % 2 == 0) ? theme.bgDark : theme.bgRowAlt;
                graphics.fill(listX, rowY, listX + contentWidth, rowY + rowHeight, rowBg);

                // Render row widgets at correct position
                int widgetX = listX + 2;
                int widgetY = rowY + (rowHeight - getMaxWidgetHeight(entry)) / 2;
                updateWidgetPositions(entry, widgetX, widgetY);

                for (AbstractWidget widget : entry.widgets) {
                    widget.render(graphics, mouseX, mouseY, partialTick);
                }

                // Delete button (red x)
                int delX = listX + contentWidth - DELETE_BTN_SIZE - 2;
                int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
                boolean delHovered = mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                        && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE
                        && mouseY >= listY && mouseY < listY + listHeight;

                int delBg = delHovered ? theme.dangerBg : theme.bgMedium;
                int delBorder = delHovered ? theme.danger : theme.borderLight;
                DrawUtils.drawPanel(graphics, delX, delY, DELETE_BTN_SIZE, DELETE_BTN_SIZE, delBg, delBorder);

                // Draw x character
                int xTextX = delX + (DELETE_BTN_SIZE - font.width("\u00d7")) / 2;
                int xTextY = delY + (DELETE_BTN_SIZE - 8) / 2;
                int xColor = delHovered ? theme.danger : theme.textDim;
                graphics.drawString(font, "\u00d7", xTextX, xTextY, xColor, false);

                // Separator line below row (except last)
                if (i < rows.size() - 1) {
                    int sepY = rowY + rowHeight;
                    graphics.fill(listX, sepY, listX + contentWidth, sepY + 1, theme.bgMedium);
                }
            }
        }

        graphics.disableScissor();

        // Scrollbar
        if (needsScrollbar()) {
            scrollbar.render(graphics, mouseX, mouseY, partialTick);
        }

        // Add button
        if (isAddButtonVisible()) {
            int addY = getY() + height - 1 - ADD_BTN_HEIGHT;
            int addX = listX;
            int addW = width - 2;

            boolean addHovered = mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT;

            int addBg = addHovered ? 0xFF2A4A2A : theme.successBg;
            int addBorder = theme.success;
            DrawUtils.drawPanel(graphics, addX, addY, addW, ADD_BTN_HEIGHT, addBg, addBorder);

            String addText = "+ Ajouter";
            int addTextX = addX + (addW - font.width(addText)) / 2;
            int addTextY = addY + (ADD_BTN_HEIGHT - 8) / 2;
            graphics.drawString(font, addText, addTextX, addTextY, theme.success, false);
        }
    }

    private int getMaxWidgetHeight(RowEntry<T> entry) {
        int maxH = 0;
        for (AbstractWidget w : entry.widgets) {
            maxH = Math.max(maxH, w.getHeight());
        }
        return maxH > 0 ? maxH : rowHeight;
    }

    private void updateWidgetPositions(RowEntry<T> entry, int baseX, int baseY) {
        // Widgets are created with relative x=0, we offset them
        // The rowRenderer creates widgets with ctx.x() and ctx.y()
        // We need to rebuild with correct positions
        if (rowRenderer == null) return;

        int contentWidth = getContentWidth();
        int widgetAreaWidth = contentWidth - DELETE_BTN_SIZE - 4;
        int idx = entry.widgets.isEmpty() ? 0 : findRowIndex(entry);

        entry.widgets.clear();
        RowContext<T> ctx = new RowContext<>(
                baseX, baseY, widgetAreaWidth, idx, font, theme,
                newVal -> {
                    entry.value = newVal;
                    fireResponder();
                },
                entry.widgets
        );
        rowRenderer.accept(entry.value, ctx);
    }

    private int findRowIndex(RowEntry<T> entry) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == entry) return i;
        }
        return 0;
    }

    // --- Input handling ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) return false;

        // Scrollbar
        if (needsScrollbar() && scrollbar.isMouseOver(mouseX, mouseY)) {
            return scrollbar.mouseClicked(mouseX, mouseY, button);
        }

        int listX = getX() + 1;
        int listY = getY() + 1;
        int listHeight = getListAreaHeight();
        int contentWidth = getContentWidth();

        // Add button
        if (isAddButtonVisible()) {
            int addY = getY() + height - 1 - ADD_BTN_HEIGHT;
            int addX = listX;
            int addW = width - 2;
            if (mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT) {
                addRow();
                return true;
            }
        }

        // Row clicks (within clipped area)
        if (mouseY < listY || mouseY >= listY + listHeight) return false;

        int scrollOffset = getScrollOffset();
        for (int i = 0; i < rows.size(); i++) {
            int rowY = listY + i * (rowHeight + ROW_GAP) - scrollOffset;
            if (rowY + rowHeight < listY || rowY > listY + listHeight) continue;

            if (mouseY >= rowY && mouseY < rowY + rowHeight) {
                // Check delete button
                int delX = listX + contentWidth - DELETE_BTN_SIZE - 2;
                int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
                if (mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                        && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE) {
                    removeRow(i);
                    return true;
                }

                // Forward to row widgets
                RowEntry<T> entry = rows.get(i);
                for (AbstractWidget widget : entry.widgets) {
                    if (widget.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (needsScrollbar()) {
            return scrollbar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (needsScrollbar()) {
            scrollbar.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        if (needsScrollbar()) {
            return scrollbar.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (RowEntry<T> entry : rows) {
            for (AbstractWidget widget : entry.widgets) {
                if (widget.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        for (RowEntry<T> entry : rows) {
            for (AbstractWidget widget : entry.widgets) {
                if (widget.charTyped(c, modifiers)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    // --- Row entry ---

    private static class RowEntry<T> {
        T value;
        final List<AbstractWidget> widgets = new ArrayList<>();

        RowEntry(T value) {
            this.value = value;
        }
    }

    // --- Row context ---

    /**
     * Context provided to the row renderer for creating widgets within a row.
     */
    public static class RowContext<T> {
        private final int x;
        private final int y;
        private final int width;
        private final int index;
        private final Font font;
        private final Theme theme;
        private final Consumer<T> valueSetter;
        private final List<AbstractWidget> widgets;

        RowContext(int x, int y, int width, int index, Font font, Theme theme,
                   Consumer<T> valueSetter, List<AbstractWidget> widgets) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.index = index;
            this.font = font;
            this.theme = theme;
            this.valueSetter = valueSetter;
            this.widgets = widgets;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int index() { return index; }
        public Font font() { return font; }
        public Theme theme() { return theme; }

        public void setValue(T value) {
            valueSetter.accept(value);
        }

        public void addWidget(AbstractWidget widget) {
            widgets.add(widget);
        }
    }
}
