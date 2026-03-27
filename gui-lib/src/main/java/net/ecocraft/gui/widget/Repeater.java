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
 * Dynamic list backed by a data source. Each row is rendered from a template
 * that creates real Screen-level widgets. Add/remove controls modify the data
 * source and rebuild only the Repeater's widgets.
 *
 * <p>Widgets are registered/unregistered with the Screen via callbacks,
 * so Minecraft handles focus and input naturally.</p>
 *
 * @param <T> the value type per row
 */
public class Repeater<T> extends AbstractWidget {

    private static final int DELETE_BTN_SIZE = 16;
    private static final int ADD_BTN_HEIGHT = 18;
    private static final int ROW_GAP = 2;

    private final Theme theme;
    private final Font font;

    // Config
    private Supplier<T> itemFactory;
    private int rowHeight = 24;
    private BiConsumer<T, RowContext<T>> rowRenderer;
    private int maxItems = Integer.MAX_VALUE;
    private Consumer<List<T>> responder;

    // Screen integration — widgets are added/removed from the Screen
    private Consumer<AbstractWidget> widgetAdder;
    private Consumer<AbstractWidget> widgetRemover;

    // Data source + row tracking
    private final List<T> data = new ArrayList<>();
    private final List<List<AbstractWidget>> rowWidgets = new ArrayList<>();

    public Repeater(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.font = Minecraft.getInstance().font;
    }

    // --- Configuration API ---

    public Repeater<T> itemFactory(Supplier<T> factory) { this.itemFactory = factory; return this; }
    public Repeater<T> rowHeight(int h) { this.rowHeight = h; return this; }
    public Repeater<T> rowRenderer(BiConsumer<T, RowContext<T>> renderer) { this.rowRenderer = renderer; return this; }
    public Repeater<T> maxItems(int max) { this.maxItems = max; return this; }
    public Repeater<T> responder(Consumer<List<T>> responder) { this.responder = responder; return this; }
    public Repeater<T> widgetAdder(Consumer<AbstractWidget> adder) { this.widgetAdder = adder; return this; }
    public Repeater<T> widgetRemover(Consumer<AbstractWidget> remover) { this.widgetRemover = remover; return this; }

    /** Set initial data and build widgets. Call AFTER widgetAdder/widgetRemover are set. */
    public Repeater<T> values(List<T> values) {
        removeAllWidgets();
        this.data.clear();
        this.data.addAll(values);
        this.rowWidgets.clear();
        rebuildAllWidgets();
        return this;
    }

    public List<T> getValues() {
        return new ArrayList<>(data);
    }

    // --- Widget lifecycle ---

    private void rebuildAllWidgets() {
        for (int i = 0; i < data.size(); i++) {
            List<AbstractWidget> widgets = createRowWidgets(i);
            rowWidgets.add(widgets);
        }
    }

    private List<AbstractWidget> createRowWidgets(int index) {
        List<AbstractWidget> widgets = new ArrayList<>();
        if (rowRenderer == null) return widgets;

        int contentW = width - DELETE_BTN_SIZE - 10;
        int rowY = getY() + 1 + index * (rowHeight + ROW_GAP);
        int rowX = getX() + 2;

        RowContext<T> ctx = new RowContext<>(rowX, rowY, contentW, index, font, theme,
                newVal -> { data.set(index, newVal); fireResponder(); });

        rowRenderer.accept(data.get(index), ctx);
        widgets.addAll(ctx.createdWidgets);

        // Register with Screen
        if (widgetAdder != null) {
            for (AbstractWidget w : widgets) {
                widgetAdder.accept(w);
            }
        }

        return widgets;
    }

    private void removeRowWidgets(int index) {
        if (index < 0 || index >= rowWidgets.size()) return;
        List<AbstractWidget> widgets = rowWidgets.get(index);
        if (widgetRemover != null) {
            for (AbstractWidget w : widgets) {
                widgetRemover.accept(w);
            }
        }
        widgets.clear();
    }

    private void removeAllWidgets() {
        for (int i = 0; i < rowWidgets.size(); i++) {
            if (widgetRemover != null) {
                for (AbstractWidget w : rowWidgets.get(i)) {
                    widgetRemover.accept(w);
                }
            }
        }
        rowWidgets.clear();
    }

    private void rebuildFromScratch() {
        removeAllWidgets();
        rowWidgets.clear();
        rebuildAllWidgets();
    }

    private void updateAllPositions() {
        for (int i = 0; i < data.size() && i < rowWidgets.size(); i++) {
            int rowY = getY() + 1 + i * (rowHeight + ROW_GAP);
            int rowX = getX() + 2;
            for (AbstractWidget w : rowWidgets.get(i)) {
                w.setX(rowX);
                w.setY(rowY);
            }
        }
    }

    // --- Add / Remove ---

    private void addRow() {
        if (itemFactory == null || data.size() >= maxItems) return;
        data.add(itemFactory.get());
        List<AbstractWidget> widgets = createRowWidgets(data.size() - 1);
        rowWidgets.add(widgets);
        fireResponder();
    }

    private void removeRow(int index) {
        if (index < 0 || index >= data.size()) return;
        removeRowWidgets(index);
        data.remove(index);
        rowWidgets.remove(index);
        // Rebuild all to fix indices and positions
        rebuildFromScratch();
        fireResponder();
    }

    private void fireResponder() {
        if (responder != null) responder.accept(getValues());
    }

    // --- Rendering ---

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Panel background
        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, theme.bgDark, theme.border);

        int listX = getX() + 1;
        int listY = getY() + 1;
        int listH = height - 2 - ADD_BTN_HEIGHT - ROW_GAP;
        int contentW = width - 2;

        if (data.isEmpty()) {
            String emptyText = "Aucun \u00e9l\u00e9ment";
            int tw = font.width(emptyText);
            graphics.drawString(font, emptyText, listX + (contentW - tw) / 2,
                    listY + (listH - font.lineHeight) / 2, theme.textDim, false);
        } else {
            for (int i = 0; i < data.size(); i++) {
                int rowY = listY + i * (rowHeight + ROW_GAP);

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
                int delX = listX + contentW - DELETE_BTN_SIZE - 4;
                int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
                boolean delHovered = mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                        && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE;

                DrawUtils.drawPanel(graphics, delX, delY, DELETE_BTN_SIZE, DELETE_BTN_SIZE,
                        delHovered ? theme.dangerBg : theme.bgMedium,
                        delHovered ? theme.danger : theme.borderLight);
                String xChar = "\u00d7";
                graphics.drawString(font, xChar,
                        delX + (DELETE_BTN_SIZE - font.width(xChar)) / 2,
                        delY + (DELETE_BTN_SIZE - font.lineHeight) / 2,
                        delHovered ? theme.danger : theme.textDim, false);
            }
            // Note: row widgets are rendered by the Screen (they are Screen children)
        }

        // Add button
        if (data.size() < maxItems) {
            int addY = getY() + height - 1 - ADD_BTN_HEIGHT;
            int addX = listX;
            int addW = width - 2;
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

    // --- Input: only handle add/remove buttons ---

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || button != 0) return false;

        int listX = getX() + 1;
        int listY = getY() + 1;
        int contentW = width - 2;

        // Add button
        if (data.size() < maxItems) {
            int addY = getY() + height - 1 - ADD_BTN_HEIGHT;
            int addX = listX;
            int addW = width - 2;
            if (mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT) {
                addRow();
                return true;
            }
        }

        // Delete buttons
        for (int i = 0; i < data.size(); i++) {
            int rowY = listY + i * (rowHeight + ROW_GAP);
            int delX = listX + contentW - DELETE_BTN_SIZE - 4;
            int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
            if (mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                    && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE) {
                removeRow(i);
                return true;
            }
        }

        // Don't consume — let the click fall through to the row widgets
        // (they are Screen children and will handle focus naturally)
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }

    // --- Row Context ---

    public static class RowContext<T> {
        private final int x, y, width, index;
        private final Font font;
        private final Theme theme;
        private final Consumer<T> valueSetter;
        private final List<AbstractWidget> createdWidgets = new ArrayList<>();

        RowContext(int x, int y, int width, int index, Font font, Theme theme, Consumer<T> valueSetter) {
            this.x = x; this.y = y; this.width = width; this.index = index;
            this.font = font; this.theme = theme; this.valueSetter = valueSetter;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int index() { return index; }
        public Font font() { return font; }
        public Theme theme() { return theme; }
        public void setValue(T value) { valueSetter.accept(value); }
        public void addWidget(AbstractWidget widget) { createdWidgets.add(widget); }
    }
}
