# gui-lib v2 — Generic Minecraft UI Framework — Design Spec

## Overview

Refactor the gui-lib from an economy-specific widget collection into a generic, reusable Minecraft UI framework. Inspired by web UI frameworks (flexbox layout, configurable components, theming). All economy-specific logic moves to the consumer modules.

## Principles

1. **Layout-first**: Row/Column containers handle positioning. No more manual pixel math in consumer code.
2. **Auto-sizing**: Components calculate their own minimum size. Containers respect it. Overflow is handled (truncate, scroll).
3. **Manual override always available**: Any component can bypass layout with `setPosition(x, y)` and `setSize(w, h)`.
4. **Generic, not economy-specific**: No "Eco" prefix. No hardcoded currency logic. Consumer modules configure behavior.
5. **Enrich the lib, not the consumer**: When a GUI pattern repeats across modules, it becomes a lib component.
6. **Tooltips built-in**: PaginatedTable and ItemSlot render tooltips automatically on hover.

## Layout System — Flexbox Simplified

Two container widgets: `Row` (horizontal) and `Column` (vertical).

### Row
Distributes children horizontally. Each child has either:
- `weight` (float) — proportional share of remaining space
- Fixed width (int) — exact pixel width

Properties:
- `gap` (int) — pixels between children (default 0)
- `padding` (int) — inner padding on all sides (default 0)
- Height: either fixed or determined by parent

### Column
Same as Row but vertical. Children have `weight` or fixed height.

### Layout Example
```
Column(padding=4, gap=4) {
    Row(height=20, gap=4) {
        TextInput(weight=1)          // takes remaining space
        Button(width=60)             // fixed 60px
    }
    Row(weight=1, gap=4) {           // fills remaining vertical space
        Column(width=80) {           // sidebar
            Button(height=16)
            Button(height=16)
            ...
        }
        PaginatedTable(weight=1)     // fills remaining space
    }
    Row(height=38, gap=4) {
        StatCard(weight=1)
        StatCard(weight=1)
        StatCard(weight=1)
    }
}
```

### Manual Override
Any widget can be positioned manually instead of using layout:
```java
var button = new Button(Component.literal("Click"), style, onClick);
button.setManualBounds(100, 200, 80, 20); // x, y, width, height
```
When manual bounds are set, the layout system skips this widget.

## Components

### Theme
Replaces `EcoColors`. A configurable color palette object, not static constants.

```java
public class Theme {
    // Factory for the default WoW-inspired dark theme
    public static Theme dark();

    // Colors
    int bgDarkest, bgDark, bgMedium, bgLight, bgRowAlt;
    int border, borderLight, borderGold;
    int gold, goldBg, goldBgDim;
    int textWhite, textLight, textGrey, textDim, textDark;
    int success, successBg, warning, warningBg, danger, dangerBg, info, infoBg;

    // Rarity colors
    int rarityCommon, rarityUncommon, rarityRare, rarityEpic, rarityLegendary;
}
```

All components accept a `Theme` parameter (or use `Theme.dark()` as default).

### DrawUtils
Replaces `EcoTheme`. Static helpers for common draw operations:
- `drawPanel(GuiGraphics, x, y, w, h, bgColor, borderColor)`
- `drawPanel(GuiGraphics, x, y, w, h, Theme)` — uses theme defaults
- `drawSeparator(GuiGraphics, x, y, w, color)`
- `drawGoldSeparator(GuiGraphics, x, y, w, Theme)`
- `drawLeftAccent(GuiGraphics, x, y, h, color)`

### NumberFormatter
Utility for formatting numbers in different modes.

```java
public enum NumberFormat {
    COMPACT,  // 35.5k, 1.2M, 3.4G
    FULL,     // 35 500 (space-separated thousands)
    EXACT     // 35500 (raw number, for inputs)
}

public class NumberFormatter {
    static String format(long value, NumberFormat mode);
    static String format(double value, NumberFormat mode);
}
```

Rules for COMPACT mode:
- < 1 000 → raw number: `500`
- ≥ 1 000 → `1.5k` (1 decimal max, no trailing zero: `1k` not `1.0k`)
- ≥ 1 000 000 → `1.5M`
- ≥ 1 000 000 000 → `1.5G`

### StatCard
Replaces `EcoStatCard`. Configurable card displaying a metric.

Options:
- `label` (Component) — header text
- `value` (Component) — main value text
- `valueColor` (int) — color of the value
- `subtitle` (Component, optional) — trend or secondary info
- `subtitleColor` (int)
- `icon` (ItemStack, optional) — icon displayed left of the value
- `numberFormat` (NumberFormat, optional) — if set, auto-formats the numeric value
- Auto-height: calculates minimum height from content (label + value + optional subtitle)

### PaginatedTable
Replaces `EcoPaginatedTable`. Sortable, paginated table with icons and tooltips.

Changes from current:
- **Auto-truncate**: cell text is automatically truncated with "..." if it exceeds column width. No manual truncation needed by consumers.
- **Built-in tooltip**: when hovering a row that has an icon, renders the vanilla ItemStack tooltip automatically.
- `getHoveredIcon()` public method for consumers who want custom tooltip behavior.
- Column weights properly distribute available width.

### TabBar
Replaces `EcoTabBar`. Mostly unchanged — already generic enough. Remove "Eco" prefix.

### Button
Replaces `EcoButton`. Style via builder instead of enum.

```java
var btn = Button.builder(Component.literal("Buy"), onClick)
    .bgColor(0xFF4CAF50)
    .borderColor(0xFF4CAF50)
    .textColor(0xFFFFFFFF)
    .hoverBg(0xFF2A4A2A)
    .build();

// Or use theme presets
var btn = Button.success(theme, Component.literal("Buy"), onClick);
var btn = Button.danger(theme, Component.literal("Cancel"), onClick);
var btn = Button.primary(theme, Component.literal("Search"), onClick);
```

Preset factory methods: `primary`, `success`, `danger`, `warning`, `ghost` — all take a `Theme`.

### Scrollbar
Replaces `EcoScrollbar`. Unchanged — already generic.

### TextInput
Replaces `EcoSearchBar`. Generic text input.

Options:
- `placeholder` (Component) — hint text when empty
- `icon` (optional) — small icon rendered inside the input (e.g., magnifying glass for search)
- `maxLength` (int)
- `filter` (Predicate<String>) — input validation
- `responder` (Consumer<String>) — onChange callback
- Themed styling (border gold when focused, dark background)

### NumberInput
New component. Numeric input with optional +/- buttons.

Options:
- `min`, `max` (long) — range constraints
- `step` (long) — increment/decrement step for +/- buttons (default 1)
- `showButtons` (boolean) — show +/- buttons (default true)
- `numberFormat` (NumberFormat) — display format
- `responder` (Consumer<Long>) — onChange callback
- Validation: rejects non-numeric input, clamps to min/max

### FilterTags
Replaces `EcoFilterTags`. Unchanged — already generic.

### ItemSlot
Replaces `EcoItemSlot`. Unchanged except:
- Built-in tooltip on hover (always renders vanilla tooltip)
- Rarity color auto-detected from ItemStack

### TableColumn / TableRow
Mostly unchanged. `TableRow` adds optional tooltip data (Component list) for custom tooltip content beyond the item tooltip.

## Package Structure

```
gui-lib/src/main/java/net/ecocraft/gui/
├── EcoCraftGuiMod.java          # @Mod entry (unchanged)
├── theme/
│   ├── Theme.java               # Configurable color palette
│   └── DrawUtils.java           # Static draw helpers
├── layout/
│   ├── Row.java                  # Horizontal flex container
│   └── Column.java              # Vertical flex container
├── widget/
│   ├── Button.java              # Styled button with builder
│   ├── Scrollbar.java           # Vertical scrollbar
│   ├── TextInput.java           # Generic text input
│   ├── NumberInput.java         # Numeric input with +/-
│   ├── TabBar.java              # Tab navigation
│   ├── FilterTags.java          # Clickable filter pills
│   ├── StatCard.java            # Metric display card
│   └── ItemSlot.java            # Item display with rarity border
├── table/
│   ├── TableColumn.java         # Column definition
│   ├── TableRow.java            # Row data
│   └── PaginatedTable.java      # Paginated sortable table
└── util/
    └── NumberFormatter.java     # Number formatting utility
```

## Migration Impact

### economy-core
- `VaultScreen.java` — update imports, use `Theme.dark()`, `DrawUtils`

### auction-house
- All 5 screen files — update imports, use new component names
- Replace manual pixel calculations with `Row`/`Column` layout where beneficial
- Remove manual `truncateText()` calls (PaginatedTable handles it)
- Remove manual tooltip rendering (PaginatedTable handles it)
- `SellTab` — use `Row`/`Column` for the two-column layout, `NumberInput` for price

## Non-Goals (for now)
- Theming system with multiple swappable themes (future C refactor)
- Composable containers (Card wrapping arbitrary widgets)
- Animation system
- Responsive breakpoints
