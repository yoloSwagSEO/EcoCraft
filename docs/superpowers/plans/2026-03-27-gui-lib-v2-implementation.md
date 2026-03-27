# gui-lib v2 — Implementation Plan (Part A: Library + Part B: Migration)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor gui-lib from economy-specific widgets to a generic Minecraft UI framework with flexbox layout, configurable theming, auto-sizing, NumberFormatter, Dialogs, and built-in tooltips. Then migrate all consumer modules.

**Architecture:** Delete all old Eco-prefixed classes. Create new generic classes in the same package structure. The layout system (Row/Column) manages positioning; components auto-size and handle overflow. Theme is a configurable object. Migration updates all imports and replaces manual pixel math with layout containers.

**Tech Stack:** Java 21, NeoForge 1.21.1, Minecraft GUI API (AbstractWidget, GuiGraphics, Screen)

---

## Part A: gui-lib v2 Components

### Task 1: Theme + DrawUtils + NumberFormatter (foundations)

**Files:**
- Delete: `gui-lib/src/main/java/net/ecocraft/gui/theme/EcoColors.java`
- Delete: `gui-lib/src/main/java/net/ecocraft/gui/theme/EcoTheme.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/theme/Theme.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/theme/DrawUtils.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/util/NumberFormatter.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/util/NumberFormat.java`
- Create: `gui-lib/src/test/java/net/ecocraft/gui/util/NumberFormatterTest.java`

- [ ] **Step 1: Write NumberFormatter test**

Create `gui-lib/src/test/java/net/ecocraft/gui/util/NumberFormatterTest.java`:
```java
package net.ecocraft.gui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberFormatterTest {

    @Test
    void compactBelow1000() {
        assertEquals("0", NumberFormatter.format(0, NumberFormat.COMPACT));
        assertEquals("500", NumberFormatter.format(500, NumberFormat.COMPACT));
        assertEquals("999", NumberFormatter.format(999, NumberFormat.COMPACT));
    }

    @Test
    void compactThousands() {
        assertEquals("1k", NumberFormatter.format(1000, NumberFormat.COMPACT));
        assertEquals("1.5k", NumberFormatter.format(1500, NumberFormat.COMPACT));
        assertEquals("35.5k", NumberFormatter.format(35500, NumberFormat.COMPACT));
        assertEquals("999.9k", NumberFormatter.format(999900, NumberFormat.COMPACT));
    }

    @Test
    void compactMillions() {
        assertEquals("1M", NumberFormatter.format(1_000_000, NumberFormat.COMPACT));
        assertEquals("2.5M", NumberFormatter.format(2_500_000, NumberFormat.COMPACT));
    }

    @Test
    void compactBillions() {
        assertEquals("1G", NumberFormatter.format(1_000_000_000L, NumberFormat.COMPACT));
        assertEquals("3.4G", NumberFormatter.format(3_400_000_000L, NumberFormat.COMPACT));
    }

    @Test
    void compactNoTrailingZero() {
        assertEquals("2k", NumberFormatter.format(2000, NumberFormat.COMPACT));
        assertEquals("1M", NumberFormatter.format(1_000_000, NumberFormat.COMPACT));
    }

    @Test
    void fullFormat() {
        assertEquals("0", NumberFormatter.format(0, NumberFormat.FULL));
        assertEquals("500", NumberFormatter.format(500, NumberFormat.FULL));
        assertEquals("1 000", NumberFormatter.format(1000, NumberFormat.FULL));
        assertEquals("35 500", NumberFormatter.format(35500, NumberFormat.FULL));
        assertEquals("1 000 000", NumberFormatter.format(1_000_000, NumberFormat.FULL));
    }

    @Test
    void exactFormat() {
        assertEquals("0", NumberFormatter.format(0, NumberFormat.EXACT));
        assertEquals("500", NumberFormatter.format(500, NumberFormat.EXACT));
        assertEquals("35500", NumberFormatter.format(35500, NumberFormat.EXACT));
        assertEquals("1000000", NumberFormatter.format(1_000_000, NumberFormat.EXACT));
    }

    @Test
    void negativeNumbers() {
        assertEquals("-500", NumberFormatter.format(-500, NumberFormat.COMPACT));
        assertEquals("-1.5k", NumberFormatter.format(-1500, NumberFormat.COMPACT));
        assertEquals("-35 500", NumberFormatter.format(-35500, NumberFormat.FULL));
    }
}
```

- [ ] **Step 2: Create NumberFormat enum**

Create `gui-lib/src/main/java/net/ecocraft/gui/util/NumberFormat.java`:
```java
package net.ecocraft.gui.util;

public enum NumberFormat {
    COMPACT,
    FULL,
    EXACT
}
```

- [ ] **Step 3: Create NumberFormatter**

Create `gui-lib/src/main/java/net/ecocraft/gui/util/NumberFormatter.java`:
```java
package net.ecocraft.gui.util;

public final class NumberFormatter {
    private NumberFormatter() {}

    public static String format(long value, NumberFormat mode) {
        if (value == 0) return "0";

        boolean negative = value < 0;
        long abs = Math.abs(value);
        String prefix = negative ? "-" : "";

        return switch (mode) {
            case COMPACT -> prefix + formatCompact(abs);
            case FULL -> prefix + formatFull(abs);
            case EXACT -> prefix + String.valueOf(abs);
        };
    }

    public static String format(double value, NumberFormat mode) {
        return format(Math.round(value), mode);
    }

    private static String formatCompact(long value) {
        if (value >= 1_000_000_000L) {
            return formatWithSuffix(value, 1_000_000_000L, "G");
        } else if (value >= 1_000_000L) {
            return formatWithSuffix(value, 1_000_000L, "M");
        } else if (value >= 1_000L) {
            return formatWithSuffix(value, 1_000L, "k");
        }
        return String.valueOf(value);
    }

    private static String formatWithSuffix(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long remainder = (value % divisor) * 10 / divisor;
        if (remainder == 0) {
            return whole + suffix;
        }
        return whole + "." + remainder + suffix;
    }

    private static String formatFull(long value) {
        String raw = String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        int start = raw.length() % 3;
        if (start > 0) {
            sb.append(raw, 0, start);
        }
        for (int i = start; i < raw.length(); i += 3) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(raw, i, i + 3);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run NumberFormatter tests**

Run: `./gradlew :gui-lib:test`
Expected: All tests pass.

- [ ] **Step 5: Create Theme**

Create `gui-lib/src/main/java/net/ecocraft/gui/theme/Theme.java`:
```java
package net.ecocraft.gui.theme;

/**
 * Configurable color palette for all UI components.
 * Use Theme.dark() for the default WoW-inspired dark theme.
 */
public class Theme {
    // Backgrounds
    public final int bgDarkest, bgDark, bgMedium, bgLight, bgRowAlt;
    // Borders
    public final int border, borderLight, borderAccent;
    // Accent
    public final int accent, accentBg, accentBgDim;
    // Text
    public final int textWhite, textLight, textGrey, textDim, textDark;
    // Functional
    public final int success, successBg, warning, warningBg, danger, dangerBg, info, infoBg;
    // Rarity
    public final int rarityCommon, rarityUncommon, rarityRare, rarityEpic, rarityLegendary;
    // Disabled state
    public final int disabledBg, disabledText, disabledBorder;

    public Theme(Builder builder) {
        this.bgDarkest = builder.bgDarkest;
        this.bgDark = builder.bgDark;
        this.bgMedium = builder.bgMedium;
        this.bgLight = builder.bgLight;
        this.bgRowAlt = builder.bgRowAlt;
        this.border = builder.border;
        this.borderLight = builder.borderLight;
        this.borderAccent = builder.borderAccent;
        this.accent = builder.accent;
        this.accentBg = builder.accentBg;
        this.accentBgDim = builder.accentBgDim;
        this.textWhite = builder.textWhite;
        this.textLight = builder.textLight;
        this.textGrey = builder.textGrey;
        this.textDim = builder.textDim;
        this.textDark = builder.textDark;
        this.success = builder.success;
        this.successBg = builder.successBg;
        this.warning = builder.warning;
        this.warningBg = builder.warningBg;
        this.danger = builder.danger;
        this.dangerBg = builder.dangerBg;
        this.info = builder.info;
        this.infoBg = builder.infoBg;
        this.rarityCommon = builder.rarityCommon;
        this.rarityUncommon = builder.rarityUncommon;
        this.rarityRare = builder.rarityRare;
        this.rarityEpic = builder.rarityEpic;
        this.rarityLegendary = builder.rarityLegendary;
        this.disabledBg = builder.disabledBg;
        this.disabledText = builder.disabledText;
        this.disabledBorder = builder.disabledBorder;
    }

    /** Default WoW-inspired dark theme. */
    public static Theme dark() {
        return new Builder()
            .bgDarkest(0xFF0D0D1A).bgDark(0xFF12122A).bgMedium(0xFF1A1A2E)
            .bgLight(0xFF2A2A3E).bgRowAlt(0xFF0A0A18)
            .border(0xFF333333).borderLight(0xFF444444).borderAccent(0xFFFFD700)
            .accent(0xFFFFD700).accentBg(0xFF4A3A1A).accentBgDim(0xFF3A2A1A)
            .textWhite(0xFFFFFFFF).textLight(0xFFCCCCCC).textGrey(0xFFAAAAAA)
            .textDim(0xFF888888).textDark(0xFF666666)
            .success(0xFF4CAF50).successBg(0xFF1A3A1A)
            .warning(0xFFFF9800).warningBg(0xFF2A1A0A)
            .danger(0xFFFF6B6B).dangerBg(0xFF2A0A0A)
            .info(0xFF64B5F6).infoBg(0xFF0A1A2A)
            .rarityCommon(0xFFFFFFFF).rarityUncommon(0xFF1EFF00)
            .rarityRare(0xFF0070DD).rarityEpic(0xFFA335EE).rarityLegendary(0xFFFF8000)
            .disabledBg(0xFF1A1A1A).disabledText(0xFF555555).disabledBorder(0xFF2A2A2A)
            .build();
    }

    public static class Builder {
        int bgDarkest, bgDark, bgMedium, bgLight, bgRowAlt;
        int border, borderLight, borderAccent;
        int accent, accentBg, accentBgDim;
        int textWhite, textLight, textGrey, textDim, textDark;
        int success, successBg, warning, warningBg, danger, dangerBg, info, infoBg;
        int rarityCommon, rarityUncommon, rarityRare, rarityEpic, rarityLegendary;
        int disabledBg, disabledText, disabledBorder;

        public Builder bgDarkest(int c) { bgDarkest = c; return this; }
        public Builder bgDark(int c) { bgDark = c; return this; }
        public Builder bgMedium(int c) { bgMedium = c; return this; }
        public Builder bgLight(int c) { bgLight = c; return this; }
        public Builder bgRowAlt(int c) { bgRowAlt = c; return this; }
        public Builder border(int c) { border = c; return this; }
        public Builder borderLight(int c) { borderLight = c; return this; }
        public Builder borderAccent(int c) { borderAccent = c; return this; }
        public Builder accent(int c) { accent = c; return this; }
        public Builder accentBg(int c) { accentBg = c; return this; }
        public Builder accentBgDim(int c) { accentBgDim = c; return this; }
        public Builder textWhite(int c) { textWhite = c; return this; }
        public Builder textLight(int c) { textLight = c; return this; }
        public Builder textGrey(int c) { textGrey = c; return this; }
        public Builder textDim(int c) { textDim = c; return this; }
        public Builder textDark(int c) { textDark = c; return this; }
        public Builder success(int c) { success = c; return this; }
        public Builder successBg(int c) { successBg = c; return this; }
        public Builder warning(int c) { warning = c; return this; }
        public Builder warningBg(int c) { warningBg = c; return this; }
        public Builder danger(int c) { danger = c; return this; }
        public Builder dangerBg(int c) { dangerBg = c; return this; }
        public Builder info(int c) { info = c; return this; }
        public Builder infoBg(int c) { infoBg = c; return this; }
        public Builder rarityCommon(int c) { rarityCommon = c; return this; }
        public Builder rarityUncommon(int c) { rarityUncommon = c; return this; }
        public Builder rarityRare(int c) { rarityRare = c; return this; }
        public Builder rarityEpic(int c) { rarityEpic = c; return this; }
        public Builder rarityLegendary(int c) { rarityLegendary = c; return this; }
        public Builder disabledBg(int c) { disabledBg = c; return this; }
        public Builder disabledText(int c) { disabledText = c; return this; }
        public Builder disabledBorder(int c) { disabledBorder = c; return this; }
        public Theme build() { return new Theme(this); }
    }
}
```

- [ ] **Step 6: Create DrawUtils**

Create `gui-lib/src/main/java/net/ecocraft/gui/theme/DrawUtils.java`:
```java
package net.ecocraft.gui.theme;

import net.minecraft.client.gui.GuiGraphics;

public final class DrawUtils {
    private DrawUtils() {}

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, int bg, int border) {
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);
    }

    public static void drawPanel(GuiGraphics g, int x, int y, int w, int h, Theme theme) {
        drawPanel(g, x, y, w, h, theme.bgDark, theme.border);
    }

    public static void drawSeparator(GuiGraphics g, int x, int y, int w, int color) {
        g.fill(x, y, x + w, y + 1, color);
    }

    public static void drawAccentSeparator(GuiGraphics g, int x, int y, int w, Theme theme) {
        g.fill(x, y, x + w, y + 2, theme.borderAccent);
    }

    public static void drawLeftAccent(GuiGraphics g, int x, int y, int h, int color) {
        g.fill(x, y, x + 3, y + h, color);
    }

    public static String truncateText(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "...";
        int ellipsisW = font.width(ellipsis);
        while (text.length() > 1 && font.width(text) + ellipsisW > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
```

- [ ] **Step 7: Verify build compiles**

Run: `./gradlew :gui-lib:build`
Expected: BUILD SUCCESSFUL (old Eco classes still exist alongside new ones temporarily).

- [ ] **Step 8: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add Theme, DrawUtils, NumberFormatter foundations for v2"
```

---

### Task 2: Layout System — Row and Column

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/layout/Row.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/layout/Column.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/layout/LayoutEntry.java`

- [ ] **Step 1: Create LayoutEntry**

A wrapper that associates a widget with its layout properties (weight or fixed size).

Create `gui-lib/src/main/java/net/ecocraft/gui/layout/LayoutEntry.java`:
```java
package net.ecocraft.gui.layout;

import net.minecraft.client.gui.components.AbstractWidget;

/**
 * Associates a widget with its layout properties in a Row or Column.
 */
public record LayoutEntry(
    AbstractWidget widget,
    float weight,      // > 0 means proportional; -1 means fixed size
    int fixedSize      // used when weight == -1
) {
    /** Widget takes proportional space based on weight. */
    public static LayoutEntry weighted(AbstractWidget widget, float weight) {
        return new LayoutEntry(widget, weight, -1);
    }

    /** Widget takes exactly fixedSize pixels. */
    public static LayoutEntry fixed(AbstractWidget widget, int fixedSize) {
        return new LayoutEntry(widget, -1f, fixedSize);
    }

    public boolean isWeighted() {
        return weight > 0;
    }
}
```

- [ ] **Step 2: Create Row**

Create `gui-lib/src/main/java/net/ecocraft/gui/layout/Row.java`:
```java
package net.ecocraft.gui.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Horizontal flex container. Distributes children by weight or fixed width.
 */
public class Row extends AbstractWidget {

    private int gap = 0;
    private int padding = 0;
    private final List<LayoutEntry> entries = new ArrayList<>();

    public Row(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public Row gap(int gap) { this.gap = gap; return this; }
    public Row padding(int padding) { this.padding = padding; return this; }

    public Row add(AbstractWidget widget, float weight) {
        entries.add(LayoutEntry.weighted(widget, weight));
        return this;
    }

    public Row addFixed(AbstractWidget widget, int fixedWidth) {
        entries.add(LayoutEntry.fixed(widget, fixedWidth));
        return this;
    }

    /** Recalculate all children positions based on layout rules. */
    public void layout() {
        if (entries.isEmpty()) return;

        int availableWidth = width - padding * 2 - gap * (entries.size() - 1);
        int innerY = getY() + padding;
        int innerH = height - padding * 2;

        // Subtract fixed sizes
        float totalWeight = 0;
        for (var e : entries) {
            if (e.isWeighted()) {
                totalWeight += e.weight();
            } else {
                availableWidth -= e.fixedSize();
            }
        }

        // Position children
        int currentX = getX() + padding;
        for (var e : entries) {
            int childWidth;
            if (e.isWeighted()) {
                childWidth = totalWeight > 0 ? (int) (availableWidth * e.weight() / totalWeight) : 0;
            } else {
                childWidth = e.fixedSize();
            }

            AbstractWidget w = e.widget();
            w.setX(currentX);
            w.setY(innerY);
            w.setWidth(childWidth);
            w.setHeight(innerH);

            // Recursively layout children if they are containers
            if (w instanceof Row row) row.layout();
            if (w instanceof Column col) col.layout();

            currentX += childWidth + gap;
        }
    }

    public List<AbstractWidget> getChildren() {
        return entries.stream().map(LayoutEntry::widget).toList();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Row itself is invisible — children render themselves via Screen
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
```

- [ ] **Step 3: Create Column**

Create `gui-lib/src/main/java/net/ecocraft/gui/layout/Column.java`:
```java
package net.ecocraft.gui.layout;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical flex container. Distributes children by weight or fixed height.
 */
public class Column extends AbstractWidget {

    private int gap = 0;
    private int padding = 0;
    private final List<LayoutEntry> entries = new ArrayList<>();

    public Column(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public Column gap(int gap) { this.gap = gap; return this; }
    public Column padding(int padding) { this.padding = padding; return this; }

    public Column add(AbstractWidget widget, float weight) {
        entries.add(LayoutEntry.weighted(widget, weight));
        return this;
    }

    public Column addFixed(AbstractWidget widget, int fixedHeight) {
        entries.add(LayoutEntry.fixed(widget, fixedHeight));
        return this;
    }

    /** Recalculate all children positions based on layout rules. */
    public void layout() {
        if (entries.isEmpty()) return;

        int availableHeight = height - padding * 2 - gap * (entries.size() - 1);
        int innerX = getX() + padding;
        int innerW = width - padding * 2;

        // Subtract fixed sizes
        float totalWeight = 0;
        for (var e : entries) {
            if (e.isWeighted()) {
                totalWeight += e.weight();
            } else {
                availableHeight -= e.fixedSize();
            }
        }

        // Position children
        int currentY = getY() + padding;
        for (var e : entries) {
            int childHeight;
            if (e.isWeighted()) {
                childHeight = totalWeight > 0 ? (int) (availableHeight * e.weight() / totalWeight) : 0;
            } else {
                childHeight = e.fixedSize();
            }

            AbstractWidget w = e.widget();
            w.setX(innerX);
            w.setY(currentY);
            w.setWidth(innerW);
            w.setHeight(childHeight);

            if (w instanceof Row row) row.layout();
            if (w instanceof Column col) col.layout();

            currentY += childHeight + gap;
        }
    }

    public List<AbstractWidget> getChildren() {
        return entries.stream().map(LayoutEntry::widget).toList();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Column itself is invisible — children render themselves via Screen
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
```

- [ ] **Step 4: Verify build**

Run: `./gradlew :gui-lib:build`

- [ ] **Step 5: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add Row/Column flexbox layout system"
```

---

### Task 3: Refactor existing widgets — rename + add disabled state + tooltips

**Files:**
- Delete: all `Eco*.java` files in `gui-lib/src/main/java/net/ecocraft/gui/widget/`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/Button.java` (with builder, disabled)
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/Scrollbar.java` (renamed from EcoScrollbar)
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/TextInput.java` (from EcoSearchBar, generic)
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/TabBar.java` (renamed)
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/FilterTags.java` (renamed, disabled support)
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/StatCard.java` (auto-height, icon, NumberFormat)
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/ItemSlot.java` (built-in tooltip)
- Modify: `gui-lib/src/main/java/net/ecocraft/gui/table/PaginatedTable.java` (auto-truncate, tooltip)
- Modify: `gui-lib/src/main/java/net/ecocraft/gui/table/TableRow.java` (tooltip data)

All components should:
- Accept a `Theme` (use `Theme.dark()` as default)
- Support `setEnabled(boolean)` / `isEnabled()` for interactive ones
- Use `DrawUtils` for drawing panels/borders

This is a large task. The implementer should:
1. Create each new file based on the old Eco-prefixed version
2. Rename the class, remove "Eco" prefix
3. Replace hardcoded EcoColors references with Theme fields
4. Add disabled state to interactive components (Button, TextInput, FilterTags)
5. Add auto-truncate to PaginatedTable (use DrawUtils.truncateText in renderWidget)
6. Add built-in tooltip rendering to PaginatedTable (getHoveredIcon + renderTooltip)
7. Add built-in tooltip to ItemSlot
8. StatCard: add optional icon rendering, auto-height calculation, NumberFormat support
9. Button: replace Style enum with builder pattern + theme presets
10. TextInput: generalize from SearchBar (remove search-specific code)
11. Delete all old Eco*.java files

After all files are created, verify with `./gradlew :gui-lib:build`.

Note: This will BREAK economy-core and auction-house (they still import old names). That's expected — Task 6 (migration) fixes them.

- [ ] **Step 1: Create all new widget files (code provided in spec)**

Create each file following the spec. Key changes per component:

**Button.java**: Builder pattern with `Button.builder(label, onClick).bgColor(...).build()`. Factory methods `primary(theme, label, onClick)`, `success(...)`, `danger(...)`, `warning(...)`, `ghost(...)`. Disabled state: if `!isEnabled()`, use `theme.disabledBg/Text/Border` and ignore clicks.

**StatCard.java**: Constructor takes `Theme`. Auto-height: `getMinHeight()` returns 28 (label+value) or 42 (with subtitle). Optional `ItemStack icon` rendered left of value. Optional `NumberFormat` for auto-formatting long values.

**PaginatedTable.java**: In `renderWidget`, when drawing cell text, call `DrawUtils.truncateText(font, text, colWidth)` before rendering. After all rows are rendered, if `hoveredRow >= 0` and row has an icon, call `graphics.renderTooltip(font, icon, mouseX, mouseY)`. Add `getHoveredIcon()` public method.

**TextInput.java**: Extends EditBox. Themed background. Optional placeholder icon. Disabled state.

**ItemSlot.java**: Always renders tooltip on hover. Uses `Theme` for border colors.

- [ ] **Step 2: Delete old Eco-prefixed files**

Delete: `EcoButton.java`, `EcoScrollbar.java`, `EcoSearchBar.java`, `EcoTabBar.java`, `EcoFilterTags.java`, `EcoStatCard.java`, `EcoItemSlot.java`, `EcoColors.java`, `EcoTheme.java`.

Rename `EcoPaginatedTable.java` → `PaginatedTable.java`.

- [ ] **Step 3: Verify gui-lib builds** (economy-core and auction-house will fail, that's OK)

Run: `./gradlew :gui-lib:build`

- [ ] **Step 4: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): refactor all widgets — generic names, Theme, disabled state, auto-truncate, tooltips"
```

---

### Task 4: New components — NumberInput + Dialog

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/NumberInput.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/dialog/Dialog.java`

- [ ] **Step 1: Create NumberInput**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/NumberInput.java`:

A composite widget with an EditBox in the center and optional +/- buttons on each side. Properties: `min`, `max`, `step`, `showButtons`, `numberFormat`, `responder`. Validates input is numeric, clamps to range. Disabled state supported.

- [ ] **Step 2: Create Dialog**

Create `gui-lib/src/main/java/net/ecocraft/gui/dialog/Dialog.java`:

A modal overlay widget. Renders a darkened background (50% black fill over entire screen), centered panel with title, body text, optional input, and action buttons. Three factory methods: `alert(title, body, onClose)`, `confirm(title, body, confirmLabel, cancelLabel, onConfirm, onCancel)`, `input(title, body, format, submitLabel, cancelLabel, onSubmit, onCancel)`. Handles Escape key to close. Blocks mouse events to widgets behind it.

- [ ] **Step 3: Verify build**

Run: `./gradlew :gui-lib:build`

- [ ] **Step 4: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add NumberInput and Dialog (alert, confirm, input)"
```

---

## Part B: Migration

### Task 5: Migrate economy-core

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/vault/VaultScreen.java`

- [ ] **Step 1: Update imports and references**

Replace all `EcoColors` references with `Theme.dark()` field access. Replace `EcoTheme` with `DrawUtils`. Update import paths.

- [ ] **Step 2: Verify build**

Run: `./gradlew :economy-core:build`

- [ ] **Step 3: Commit**

```bash
git commit -m "refactor(economy-core): migrate VaultScreen to gui-lib v2"
```

---

### Task 6: Migrate auction-house

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/BuyTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/SellTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/MyAuctionsTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/LedgerTab.java`

- [ ] **Step 1: Update all imports**

Replace all `EcoColors` → `Theme`, `EcoTheme` → `DrawUtils`, `EcoButton` → `Button`, `EcoTabBar` → `TabBar`, `EcoSearchBar` → `TextInput`, `EcoFilterTags` → `FilterTags`, `EcoStatCard` → `StatCard`, `EcoItemSlot` → `ItemSlot`, `EcoPaginatedTable` → `PaginatedTable`, `EcoScrollbar` → `Scrollbar`.

- [ ] **Step 2: Replace EcoColors constants with Theme fields**

Create a `private static final Theme THEME = Theme.dark();` in AuctionHouseScreen. Replace all `EcoColors.GOLD` with `THEME.accent`, `EcoColors.BG_DARKEST` with `THEME.bgDarkest`, etc. Pass THEME to components that accept it.

- [ ] **Step 3: Remove manual truncateText calls**

PaginatedTable now auto-truncates. Remove all `AuctionHouseScreen.truncateText()` calls from table population code. The table handles it internally.

- [ ] **Step 4: Remove manual tooltip rendering**

PaginatedTable now renders tooltips. Remove all `table.getHoveredIcon()` + `renderTooltip()` calls from `renderForeground()` methods.

- [ ] **Step 5: SellTab — use NumberInput for price**

Replace the EditBox + manual filter with `NumberInput`. Set `min=1`, `max=Long.MAX_VALUE`, `step=1`, `showButtons=false`. Use the `responder` callback.

- [ ] **Step 6: Add ConfirmDialog to cancel listing**

In MyAuctionsTab, when "Annuler" is clicked, show a ConfirmDialog before actually cancelling.

- [ ] **Step 7: Verify full build**

Run: `./gradlew clean build`
Expected: ALL modules compile. All tests pass.

- [ ] **Step 8: Deploy and test**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "refactor(auction-house): migrate to gui-lib v2 — Theme, generic widgets, NumberInput, ConfirmDialog"
```
