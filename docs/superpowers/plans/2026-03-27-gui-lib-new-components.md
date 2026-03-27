# GUI Lib New Components (Toggle, Slider, Repeater) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add three new reusable GUI components to gui-lib: Toggle switch, Slider range input, and Repeater dynamic list.

**Architecture:** Each component extends `AbstractWidget`, uses the existing `Theme` color system, and follows the fluent builder pattern established by other gui-lib widgets. Components are independent with no dependencies between them.

**Tech Stack:** Java 21, NeoForge 1.21.1, gui-lib Theme/DrawUtils.

---

### Task 1: Toggle Switch

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/Toggle.java`

- [ ] **Step 1: Create Toggle.java**

```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Pill-shaped toggle switch with optional ON/OFF labels.
 * Click to toggle between states.
 */
public class Toggle extends AbstractWidget {

    private final Theme theme;
    private boolean value;
    private boolean showLabels;
    private int onColor;
    private int onBg;
    private @Nullable Consumer<Boolean> responder;

    public Toggle(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.value = false;
        this.showLabels = false;
        this.onColor = theme.success;
        this.onBg = theme.successBg;
    }

    public Toggle value(boolean value) { this.value = value; return this; }
    public boolean getValue() { return value; }
    public Toggle showLabels(boolean show) { this.showLabels = show; return this; }
    public Toggle onColor(int color) { this.onColor = color; return this; }
    public Toggle onBg(int bg) { this.onBg = bg; return this; }
    public Toggle responder(Consumer<Boolean> responder) { this.responder = responder; return this; }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = width, h = height;
        int radius = h / 2;

        // Pill background
        int bg = value ? onBg : theme.bgMedium;
        int border = value ? onColor : theme.borderLight;
        graphics.fill(x, y, x + w, y + h, bg);
        // Border (1px)
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);

        // Circle (thumb)
        int circleSize = h - 4;
        int circleY = y + 2;
        int circleX = value ? (x + w - circleSize - 2) : (x + 2);
        int circleColor = value ? theme.textWhite : theme.textGrey;
        graphics.fill(circleX, circleY, circleX + circleSize, circleY + circleSize, circleColor);

        // Labels
        if (showLabels) {
            Font font = Minecraft.getInstance().font;
            int textY = y + (h - font.lineHeight) / 2;
            if (value) {
                String onText = "ON";
                graphics.drawString(font, onText, x + 4, textY, theme.textWhite, false);
            } else {
                String offText = "OFF";
                int offW = font.width(offText);
                graphics.drawString(font, offText, x + w - offW - 4, textY, theme.textDim, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (!isMouseOver(mouseX, mouseY)) return false;
        value = !value;
        if (responder != null) responder.accept(value);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gui-lib:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/widget/Toggle.java
git commit -m "feat(gui-lib): add Toggle switch component"
```

---

### Task 2: Slider (Range Input)

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/Slider.java`

- [ ] **Step 1: Create Slider.java**

```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Range slider with configurable min/max/step, orientation, and label position.
 * Supports horizontal and vertical orientations.
 */
public class Slider extends AbstractWidget {

    public enum Orientation { HORIZONTAL, VERTICAL }
    public enum LabelPosition { BEFORE, CENTER, AFTER }

    private static final int RAIL_THICKNESS = 2;
    private static final int DEFAULT_CURSOR_SIZE = 8;

    private final Theme theme;
    private final Font font;
    private double min = 0, max = 100, step = 1, value = 0;
    private int cursorSize = DEFAULT_CURSOR_SIZE;
    private String suffix = "";
    private Orientation orientation = Orientation.HORIZONTAL;
    private LabelPosition labelPosition = LabelPosition.AFTER;
    private @Nullable Consumer<Double> responder;
    private boolean dragging = false;

    public Slider(Font font, int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.font = font;
    }

    public Slider min(double min) { this.min = min; return this; }
    public Slider max(double max) { this.max = max; return this; }
    public Slider step(double step) { this.step = step; return this; }
    public Slider value(double value) { this.value = snap(value); return this; }
    public double getValue() { return value; }
    public Slider suffix(String suffix) { this.suffix = suffix; return this; }
    public Slider cursorSize(int size) { this.cursorSize = size; return this; }
    public Slider orientation(Orientation o) { this.orientation = o; return this; }
    public Slider labelPosition(LabelPosition p) { this.labelPosition = p; return this; }
    public Slider responder(Consumer<Double> responder) { this.responder = responder; return this; }

    private double snap(double val) {
        val = Math.max(min, Math.min(max, val));
        if (step > 0) {
            val = Math.round((val - min) / step) * step + min;
            val = Math.max(min, Math.min(max, val));
        }
        return val;
    }

    private String formatValue() {
        if (step % 1 == 0 && step >= 1) {
            return String.valueOf((long) value) + suffix;
        }
        // Determine decimal places from step
        String stepStr = String.valueOf(step);
        int dotIdx = stepStr.indexOf('.');
        int decimals = dotIdx >= 0 ? stepStr.length() - dotIdx - 1 : 0;
        return String.format("%." + decimals + "f", value) + suffix;
    }

    // Returns the rail area (excluding label space)
    private int[] getRailBounds() {
        int x = getX(), y = getY(), w = width, h = height;
        String label = formatValue();
        int labelW = font.width(label) + 4;
        int labelH = font.lineHeight + 4;

        if (orientation == Orientation.HORIZONTAL) {
            int railX = x, railW = w;
            if (labelPosition == LabelPosition.BEFORE) { railX += labelW; railW -= labelW; }
            else if (labelPosition == LabelPosition.AFTER) { railW -= labelW; }
            int railY = y + (h - RAIL_THICKNESS) / 2;
            return new int[]{railX, railY, railW, RAIL_THICKNESS};
        } else {
            int railY = y, railH = h;
            if (labelPosition == LabelPosition.BEFORE) { railY += labelH; railH -= labelH; }
            else if (labelPosition == LabelPosition.AFTER) { railH -= labelH; }
            int railX = x + (w - RAIL_THICKNESS) / 2;
            return new int[]{railX, railY, RAIL_THICKNESS, railH};
        }
    }

    private double getProgress() {
        if (max <= min) return 0;
        return (value - min) / (max - min);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int[] rail = getRailBounds();
        int rx = rail[0], ry = rail[1], rw = rail[2], rh = rail[3];
        double progress = getProgress();

        if (orientation == Orientation.HORIZONTAL) {
            // Rail background
            graphics.fill(rx, ry, rx + rw, ry + rh, theme.bgMedium);
            // Filled portion
            int filledW = (int) (rw * progress);
            graphics.fill(rx, ry, rx + filledW, ry + rh, theme.accent);
            // Cursor
            int cx = rx + filledW - cursorSize / 2;
            int cy = getY() + (height - cursorSize) / 2;
            graphics.fill(cx, cy, cx + cursorSize, cy + cursorSize, theme.accent);
            graphics.fill(cx, cy, cx + cursorSize, cy + 1, theme.borderAccent);
            graphics.fill(cx, cy + cursorSize - 1, cx + cursorSize, cy + cursorSize, theme.borderAccent);
            graphics.fill(cx, cy, cx + 1, cy + cursorSize, theme.borderAccent);
            graphics.fill(cx + cursorSize - 1, cy, cx + cursorSize, cy + cursorSize, theme.borderAccent);
        } else {
            // Vertical: rail goes top-to-bottom, value increases upward
            graphics.fill(rx, ry, rx + rw, ry + rh, theme.bgMedium);
            int filledH = (int) (rh * progress);
            graphics.fill(rx, ry + rh - filledH, rx + rw, ry + rh, theme.accent);
            // Cursor
            int cy = ry + rh - filledH - cursorSize / 2;
            int cx = getX() + (width - cursorSize) / 2;
            graphics.fill(cx, cy, cx + cursorSize, cy + cursorSize, theme.accent);
            graphics.fill(cx, cy, cx + cursorSize, cy + 1, theme.borderAccent);
            graphics.fill(cx, cy + cursorSize - 1, cx + cursorSize, cy + cursorSize, theme.borderAccent);
            graphics.fill(cx, cy, cx + 1, cy + cursorSize, theme.borderAccent);
            graphics.fill(cx + cursorSize - 1, cy, cx + cursorSize, cy + cursorSize, theme.borderAccent);
        }

        // Label
        String label = formatValue();
        int labelW = font.width(label);
        if (orientation == Orientation.HORIZONTAL) {
            int textY = getY() + (height - font.lineHeight) / 2;
            switch (labelPosition) {
                case BEFORE -> graphics.drawString(font, label, getX(), textY, theme.textLight, false);
                case AFTER -> graphics.drawString(font, label, getX() + width - labelW, textY, theme.textLight, false);
                case CENTER -> {
                    int cx = rx + (int)(rw * progress);
                    graphics.drawString(font, label, cx - labelW / 2, getY() - font.lineHeight - 2, theme.textLight, false);
                }
            }
        } else {
            int textX = getX() + (width - labelW) / 2;
            switch (labelPosition) {
                case BEFORE -> graphics.drawString(font, label, textX, getY(), theme.textLight, false);
                case AFTER -> graphics.drawString(font, label, textX, getY() + height - font.lineHeight, theme.textLight, false);
                case CENTER -> {
                    int cy = ry + rh - (int)(rh * progress);
                    graphics.drawString(font, label, getX() + width + 2, cy - font.lineHeight / 2, theme.textLight, false);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        dragging = true;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return false;
        updateValueFromMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) return false;
        double newVal = snap(value + (scrollY > 0 ? step : -step));
        if (newVal != value) {
            value = newVal;
            if (responder != null) responder.accept(value);
        }
        return true;
    }

    private void updateValueFromMouse(double mouseX, double mouseY) {
        int[] rail = getRailBounds();
        double progress;
        if (orientation == Orientation.HORIZONTAL) {
            progress = (mouseX - rail[0]) / (double) rail[2];
        } else {
            progress = 1.0 - (mouseY - rail[1]) / (double) rail[3];
        }
        progress = Math.max(0, Math.min(1, progress));
        double newVal = snap(min + progress * (max - min));
        if (newVal != value) {
            value = newVal;
            if (responder != null) responder.accept(value);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gui-lib:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/widget/Slider.java
git commit -m "feat(gui-lib): add Slider range input component"
```

---

### Task 3: Repeater (Dynamic List)

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/Repeater.java`

- [ ] **Step 1: Create Repeater.java**

```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Dynamic list with per-row widgets, add/remove functionality, and scrolling.
 * The row content is defined by the consumer via a RowRenderer callback.
 *
 * @param <T> the type of value each row represents
 */
public class Repeater<T> extends AbstractWidget {

    private static final int DELETE_BTN_SIZE = 16;
    private static final int ADD_BTN_HEIGHT = 18;
    private static final int ROW_GAP = 2;

    private final Theme theme;
    private final Font font;
    private int rowHeight = 24;
    private int maxItems = Integer.MAX_VALUE;
    private @Nullable Supplier<T> itemFactory;
    private @Nullable RowRenderer<T> rowRenderer;
    private @Nullable Consumer<List<T>> responder;

    private final List<T> values = new ArrayList<>();
    private final List<List<AbstractWidget>> rowWidgets = new ArrayList<>();
    private int scrollOffset = 0;
    private @Nullable Scrollbar scrollbar;

    /** Callback to render/create widgets for a single row. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(T value, RowContext<T> context);
    }

    /** Context provided to RowRenderer for each row. */
    public static class RowContext<T> {
        private final int x, y, width, index;
        private final Consumer<T> valueSetter;
        private final List<AbstractWidget> widgets = new ArrayList<>();
        private final Font font;
        private final Theme theme;

        RowContext(int x, int y, int width, int index, Consumer<T> valueSetter, Font font, Theme theme) {
            this.x = x; this.y = y; this.width = width; this.index = index;
            this.valueSetter = valueSetter; this.font = font; this.theme = theme;
        }

        public int x() { return x; }
        public int y() { return y; }
        public int width() { return width; }
        public int index() { return index; }
        public Font font() { return font; }
        public Theme theme() { return theme; }
        public void setValue(T value) { valueSetter.accept(value); }
        public void addWidget(AbstractWidget widget) { widgets.add(widget); }
    }

    public Repeater(int x, int y, int width, int height, Theme theme) {
        super(x, y, width, height, Component.empty());
        this.theme = theme;
        this.font = Minecraft.getInstance().font;
    }

    public Repeater<T> rowHeight(int h) { this.rowHeight = h; return this; }
    public Repeater<T> maxItems(int max) { this.maxItems = max; return this; }
    public Repeater<T> itemFactory(Supplier<T> factory) { this.itemFactory = factory; return this; }
    public Repeater<T> rowRenderer(RowRenderer<T> renderer) { this.rowRenderer = renderer; return this; }
    public Repeater<T> responder(Consumer<List<T>> responder) { this.responder = responder; return this; }

    public Repeater<T> values(List<T> vals) {
        this.values.clear();
        this.values.addAll(vals);
        rebuildWidgets();
        return this;
    }

    public List<T> getValues() { return new ArrayList<>(values); }

    private void rebuildWidgets() {
        rowWidgets.clear();
        if (rowRenderer == null) return;

        for (int i = 0; i < values.size(); i++) {
            final int idx = i;
            T val = values.get(i);
            int rowY = getY() + i * (rowHeight + ROW_GAP);
            int contentW = width - DELETE_BTN_SIZE - 8;

            RowContext<T> ctx = new RowContext<>(
                getX() + 2, rowY, contentW, i,
                newVal -> { values.set(idx, newVal); fireResponder(); },
                font, theme
            );
            rowRenderer.render(val, ctx);
            rowWidgets.add(ctx.widgets);
        }

        // Setup scrollbar if needed
        int contentHeight = values.size() * (rowHeight + ROW_GAP) + ADD_BTN_HEIGHT;
        int availableHeight = height;
        if (contentHeight > availableHeight) {
            if (scrollbar == null) {
                scrollbar = new Scrollbar(getX() + width - 10, getY(), height, theme);
            }
            scrollbar.setContentRatio((float) availableHeight / contentHeight);
        } else {
            scrollbar = null;
            scrollOffset = 0;
        }
    }

    private void fireResponder() {
        if (responder != null) responder.accept(getValues());
    }

    private void addItem() {
        if (values.size() >= maxItems || itemFactory == null) return;
        values.add(itemFactory.get());
        rebuildWidgets();
        fireResponder();
    }

    private void removeItem(int index) {
        if (index < 0 || index >= values.size()) return;
        values.remove(index);
        rebuildWidgets();
        fireResponder();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        DrawUtils.drawPanel(graphics, getX(), getY(), width, height, theme.bgDark, theme.border);

        int contentHeight = values.size() * (rowHeight + ROW_GAP) + ADD_BTN_HEIGHT;
        int scrollPixels = 0;
        if (scrollbar != null) {
            scrollPixels = (int) (scrollbar.getScrollValue() * Math.max(0, contentHeight - height));
        }

        // Scissor clip
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);

        // Empty state
        if (values.isEmpty()) {
            String empty = "Aucun élément";
            int ew = font.width(empty);
            graphics.drawString(font, empty, getX() + (width - ew) / 2, getY() + height / 2 - 4, theme.textDim, false);
        }

        // Rows
        for (int i = 0; i < values.size(); i++) {
            int rowY = getY() + i * (rowHeight + ROW_GAP) - scrollPixels;

            // Row separator
            if (i > 0) {
                graphics.fill(getX() + 2, rowY - 1, getX() + width - 2, rowY, theme.bgMedium);
            }

            // Row widgets
            if (i < rowWidgets.size()) {
                for (var widget : rowWidgets.get(i)) {
                    // Adjust widget Y for scroll
                    int originalY = getY() + i * (rowHeight + ROW_GAP);
                    int dy = rowY - originalY;
                    widget.setY(widget.getY() + dy);
                    widget.renderWidget(graphics, mouseX, mouseY, partialTick);
                    widget.setY(widget.getY() - dy); // restore
                }
            }

            // Delete button
            int delX = getX() + width - DELETE_BTN_SIZE - 4;
            int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
            boolean delHovered = mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                    && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE
                    && mouseY >= getY() && mouseY < getY() + height;
            graphics.fill(delX, delY, delX + DELETE_BTN_SIZE, delY + DELETE_BTN_SIZE,
                    delHovered ? theme.dangerBg : theme.bgMedium);
            int xTextX = delX + (DELETE_BTN_SIZE - font.width("×")) / 2;
            int xTextY = delY + (DELETE_BTN_SIZE - font.lineHeight) / 2;
            graphics.drawString(font, "×", xTextX, xTextY, theme.danger, false);
        }

        // Add button
        if (values.size() < maxItems && itemFactory != null) {
            int addY = getY() + values.size() * (rowHeight + ROW_GAP) - scrollPixels + 2;
            int addW = width - 8;
            int addX = getX() + 4;
            boolean addHovered = mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT
                    && mouseY >= getY() && mouseY < getY() + height;
            graphics.fill(addX, addY, addX + addW, addY + ADD_BTN_HEIGHT,
                    addHovered ? theme.successBg : theme.bgMedium);
            graphics.fill(addX, addY, addX + addW, addY + 1, theme.success);
            graphics.fill(addX, addY + ADD_BTN_HEIGHT - 1, addX + addW, addY + ADD_BTN_HEIGHT, theme.success);
            graphics.fill(addX, addY, addX + 1, addY + ADD_BTN_HEIGHT, theme.success);
            graphics.fill(addX + addW - 1, addY, addX + addW, addY + ADD_BTN_HEIGHT, theme.success);
            String addText = "+ Ajouter";
            int addTextW = font.width(addText);
            graphics.drawString(font, addText, addX + (addW - addTextW) / 2,
                    addY + (ADD_BTN_HEIGHT - font.lineHeight) / 2, theme.success, false);
        }

        graphics.disableScissor();

        // Scrollbar
        if (scrollbar != null) {
            scrollbar.renderWidget(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        if (scrollbar != null && scrollbar.mouseClicked(mouseX, mouseY, button)) return true;

        int contentHeight = values.size() * (rowHeight + ROW_GAP) + ADD_BTN_HEIGHT;
        int scrollPixels = 0;
        if (scrollbar != null) {
            scrollPixels = (int) (scrollbar.getScrollValue() * Math.max(0, contentHeight - height));
        }

        // Check delete buttons
        for (int i = 0; i < values.size(); i++) {
            int rowY = getY() + i * (rowHeight + ROW_GAP) - scrollPixels;
            int delX = getX() + width - DELETE_BTN_SIZE - 4;
            int delY = rowY + (rowHeight - DELETE_BTN_SIZE) / 2;
            if (mouseX >= delX && mouseX < delX + DELETE_BTN_SIZE
                    && mouseY >= delY && mouseY < delY + DELETE_BTN_SIZE) {
                removeItem(i);
                return true;
            }
        }

        // Check add button
        if (values.size() < maxItems && itemFactory != null) {
            int addY = getY() + values.size() * (rowHeight + ROW_GAP) - scrollPixels + 2;
            int addX = getX() + 4;
            int addW = width - 8;
            if (mouseX >= addX && mouseX < addX + addW
                    && mouseY >= addY && mouseY < addY + ADD_BTN_HEIGHT) {
                addItem();
                return true;
            }
        }

        // Forward to row widgets
        for (int i = 0; i < rowWidgets.size(); i++) {
            for (var widget : rowWidgets.get(i)) {
                if (widget.mouseClicked(mouseX, mouseY, button)) return true;
            }
        }

        return true; // consume click to prevent passthrough
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (scrollbar != null && scrollbar.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbar != null) scrollbar.mouseReleased(mouseX, mouseY, button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY) || scrollbar == null) return false;
        float current = scrollbar.getScrollValue();
        float delta = (float) (-scrollY * (rowHeight + ROW_GAP)) /
                (values.size() * (rowHeight + ROW_GAP) + ADD_BTN_HEIGHT);
        scrollbar.setScrollValue(Math.max(0, Math.min(1, current + delta)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        for (var widgets : rowWidgets) {
            for (var widget : widgets) {
                if (widget.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        for (var widgets : rowWidgets) {
            for (var widget : widgets) {
                if (widget.charTyped(codePoint, modifiers)) return true;
            }
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :gui-lib:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/widget/Repeater.java
git commit -m "feat(gui-lib): add Repeater dynamic list component"
```

---

### Task 4: Build, deploy, and final commit

- [ ] **Step 1: Full build with tests**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Deploy**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

---

### Testing Instructions

These components will be tested via the AH settings screen (next spec). For now, verify compilation and that existing tests still pass. In-game validation:

1. Toggle: click toggles state, labels show/hide, colors change
2. Slider: drag cursor, click rail to jump, scroll wheel, verify value snapping
3. Repeater: add/remove rows, scroll when many rows, verify delete button works
