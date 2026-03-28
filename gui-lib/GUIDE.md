# EcoCraft GUI Library V2 — Developer Guide

## Overview

A widget tree-based GUI library for NeoForge 1.21.1 Minecraft mods. All widgets extend `BaseWidget` and live in a `WidgetTree` managed by `EcoScreen`. The tree handles rendering (depth-first), events (bubble up), focus (single global), and portals (for overlays).

**Package:** `net.ecocraft.gui.core`

---

## Core Architecture

### Widget Tree

```java
// EcoScreen creates and owns the tree
public class MyScreen extends EcoScreen {
    @Override
    protected void init() {
        super.init(); // clears tree

        EcoButton btn = EcoButton.success(THEME, Component.translatable("my.button"), () -> { ... });
        btn.setPosition(10, 10);
        btn.setSize(100, 20);
        getTree().addChild(btn); // add to root of tree
    }
}
```

### Rendering Order
- Tree renders depth-first: parent first, then children in order
- Last child added = rendered on top = receives events first
- Portals render after everything (always on top)

### Event Bubbling
- Click → hit test finds deepest widget under cursor → `onMouseClicked()` called
- If returns `false` → bubbles to parent → parent's `onMouseClicked()` called
- If returns `true` → event consumed, bubbling stops
- Key events go directly to the focused widget, then bubble up

### Focus
- Single global focus in the tree
- Clicking a focusable widget auto-focuses it
- `widget.isFocusable()` returns `true` for text inputs
- Key/char events only reach the focused widget

### Portals
- For overlays (dialogs, dropdown lists) that must render above everything
- `getTree().addPortal(dialog)` — renders and receives events on top
- Modal portals block all events to widgets below

### Widget ID & Data Attributes
```java
widget.setId("my-table");
widget.setData("page", 1);
widget.setData("source", someObject);

// Find by ID
EcoTable table = getTree().findById("my-table");
int page = widget.getData("page");
```

---

## Components Reference

### EcoButton
Styled button with builder pattern and theme presets.

```java
// Factory methods (quick)
EcoButton btn = EcoButton.success(THEME, Component.literal("Save"), () -> onSave());
EcoButton btn = EcoButton.danger(THEME, Component.literal("Delete"), () -> onDelete());
EcoButton btn = EcoButton.primary(THEME, Component.literal("OK"), () -> onOK());
EcoButton btn = EcoButton.warning(THEME, Component.literal("Bid"), () -> onBid());
EcoButton btn = EcoButton.ghost(THEME, Component.literal("Cancel"), () -> onCancel());

// Builder (full control)
EcoButton btn = EcoButton.builder(Component.literal("Custom"), () -> onClick())
    .theme(THEME)
    .bounds(x, y, width, height)
    .bgColor(0xFF1A1A2E)
    .borderColor(0xFF444444)
    .textColor(0xFFFFFFFF)
    .hoverBg(0xFF2A2A3E)
    .build();

// After creation
btn.setPosition(x, y);
btn.setSize(w, h);
btn.setEnabled(false);
btn.setLabel(Component.literal("New label")); // dynamic label update
addChild(btn);
```

### EcoToggle
Pill-shaped ON/OFF toggle switch.

```java
EcoToggle toggle = new EcoToggle(x, y, 40, 16, THEME);
toggle.value(true);
toggle.showLabels(true);              // shows "ON"/"OFF" inside
toggle.onColor(THEME.success);        // border color when ON
toggle.onBg(THEME.successBg);         // bg color when ON
toggle.responder(val -> setting = val);
addChild(toggle);
```

### EcoCheckbox
Square checkbox with optional label.

```java
EcoCheckbox cb = new EcoCheckbox(font, x, y, Component.translatable("my.setting"), THEME);
cb.checked(true);
cb.responder(val -> enabled = val);
addChild(cb);
```

### EcoCycleButton
Cycles through options on click.

```java
EcoCycleButton cycle = new EcoCycleButton(font, x, y, 120, 18, THEME);
cycle.options(List.of("Option A", "Option B", "Option C"));
cycle.selectedIndex(0);
cycle.responder(idx -> selectedOption = idx);
addChild(cycle);
// Left click = next, Right click = previous
```

### EcoEditBox
Custom single-line text input. Full cursor, selection, clipboard support. **Replaces Minecraft's EditBox.**

```java
EcoEditBox editBox = new EcoEditBox(font, x, y, 200, 18, THEME);
editBox.setHint("Type here...");
editBox.setValue("initial text");
editBox.setMaxLength(100);
editBox.setFilter(s -> s.matches("[0-9]*")); // numbers only
editBox.setResponder(text -> onTextChanged(text));
addChild(editBox);
// isFocusable() = true — handles keyboard via tree focus
```

### EcoTextInput
Thin wrapper around EcoEditBox with themed border. Use this instead of EcoEditBox directly.

```java
EcoTextInput input = new EcoTextInput(font, x, y, 200, 18,
    Component.translatable("my.placeholder"), THEME);
input.setValue("value");
input.responder(text -> name = text);
input.setMaxLength(50);
addChild(input);
```

### EcoNumberInput
Numeric input with optional +/- buttons.

```java
EcoNumberInput numInput = new EcoNumberInput(font, x, y, 120, 18, THEME);
numInput.min(1).max(64).step(1);
numInput.showButtons(true);  // +/- buttons on sides
numInput.setValue(10);
numInput.responder(val -> quantity = val);
addChild(numInput);
```

### EcoTextArea
Multi-line text input with scrolling.

```java
EcoTextArea area = new EcoTextArea(font, x, y, 300, 150, THEME);
area.setValue("Line 1\nLine 2\nLine 3");
area.setMaxLength(1000);
area.setResponder(text -> description = text);
addChild(area);
// Supports: Enter for newlines, Up/Down arrows, scroll, selection, clipboard
```

### EcoSlider
Range slider (horizontal or vertical).

```java
EcoSlider slider = new EcoSlider(font, x, y, 200, 16, THEME);
slider.min(0).max(100).step(1).value(50);
slider.suffix("%");
slider.orientation(EcoSlider.Orientation.HORIZONTAL);
slider.labelPosition(EcoSlider.LabelPosition.AFTER); // BEFORE, CENTER, AFTER
slider.trackColor(THEME.success);
slider.responder(val -> taxRate = val.intValue());
addChild(slider);
// Also supports mouse wheel and drag
```

### EcoDropdown
Select dropdown with option list.

```java
EcoDropdown dropdown = new EcoDropdown(font, x, y, 150, 18, THEME);
dropdown.options(List.of("Option A", "Option B", "Option C"));
dropdown.selectedIndex(0);
dropdown.responder(idx -> selected = idx);
addChild(dropdown);
// Click opens list below, click option selects, click outside closes
```

### EcoRadioGroup
Vertical radio button group.

```java
EcoRadioGroup radio = new EcoRadioGroup(font, x, y, THEME);
radio.options(List.of("Small", "Medium", "Large"));
radio.selectedIndex(1);
radio.spacing(4);
radio.responder(idx -> size = idx);
addChild(radio);
```

### EcoFilterTags
Clickable tag pills for filtering. Supports single-select (radio) and multi-select modes.

```java
// Single-select (radio mode)
List<Component> labels = List.of(
    Component.translatable("filter.all"),
    Component.translatable("filter.weapons"),
    Component.translatable("filter.armor")
);
EcoFilterTags tags = new EcoFilterTags(x, y, labels, idx -> onFilterChanged(idx), THEME);
tags.setActiveTag(0);
addChild(tags);

// Multi-select mode
EcoFilterTags multiTags = new EcoFilterTags(x, y, labels, (Set<Integer> selected) -> {
    onMultiFilterChanged(selected);
}, THEME, true);
addChild(multiTags);
```

### EcoTabBar
Horizontal tab navigation.

```java
List<Component> tabLabels = List.of(
    Component.literal("Tab 1"),
    Component.literal("Tab 2"),
    Component.literal("Tab 3")
);
EcoTabBar tabBar = new EcoTabBar(x, y, tabLabels, idx -> onTabChanged(idx));
addChild(tabBar);
tabBar.setActiveTab(0);
```

### EcoTable
Data table with sorting, selection, pagination, and scrolling.

```java
// Uses TableColumn and TableRow from net.ecocraft.gui.table package
List<TableColumn> columns = List.of(
    TableColumn.sortableLeft(Component.literal("Name"), 3f),
    TableColumn.sortableRight(Component.literal("Price"), 2f),
    TableColumn.center(Component.literal("Type"), 1f)
);

EcoTable table = EcoTable.builder()
    .columns(columns)
    .theme(THEME)
    .navigation(EcoTable.Navigation.SCROLL) // NONE, SCROLL, PAGINATED
    .showScrollbar(true)
    .scrollLines(1)
    .tooltips(true)
    .build(x, y, width, height);
addChild(table);

// Set data
List<TableRow> rows = new ArrayList<>();
rows.add(TableRow.withIcon(itemStack, rarityColor, List.of(
    TableRow.Cell.of(Component.literal("Diamond Sword"), color, "Diamond Sword"), // sortValue
    TableRow.Cell.of(Component.literal("500 G"), THEME.accent, 500L),
    TableRow.Cell.of(Component.literal("Buyout"), THEME.success)
), () -> onRowClicked()));
table.setRows(rows);

// Selection
table.setSelectionListener(idx -> onRowSelected(idx));
table.setSelectedRow(0);

// Sorting: click column header (ASC → DESC → none)
```

### EcoRepeater
Dynamic list with add/remove. Row widgets are children in the tree.

```java
EcoRepeater<Integer> repeater = new EcoRepeater<>(x, y, width, height, THEME);
repeater.itemFactory(() -> 24);          // default value for new rows
repeater.rowHeight(22);
repeater.maxItems(10);
repeater.rowRenderer((value, ctx) -> {
    EcoNumberInput input = new EcoNumberInput(ctx.font(), ctx.x(), ctx.y(), ctx.width(), 18, ctx.theme());
    input.min(1).max(168).step(1);
    input.setValue(value);
    input.responder(newVal -> ctx.setValue(newVal.intValue()));
    ctx.addWidget(input); // adds as child of repeater
});
repeater.values(List.of(12, 24, 48));
repeater.responder(vals -> durations = vals);
addChild(repeater);
```

### EcoDialog
Modal dialog overlay. Use as a portal.

```java
// Confirm dialog
EcoDialog dialog = EcoDialog.confirm(
    THEME,
    Component.translatable("dialog.title"),
    Component.translatable("dialog.message"),
    Component.translatable("dialog.confirm"),
    Component.translatable("dialog.cancel"),
    () -> onConfirm(),
    () -> onCancel()
);
getTree().addPortal(dialog); // renders on top, blocks events below

// Alert dialog
EcoDialog alert = EcoDialog.alert(THEME, title, body, () -> onClose());
getTree().addPortal(alert);

// Input dialog (with number input)
EcoDialog input = EcoDialog.input(THEME, title, body, null, submitLabel, cancelLabel,
    value -> onSubmit(value), () -> onCancel());
getTree().addPortal(input);
```

### Label
Simple text display.

```java
Label label = new Label(font, x, y, Component.literal("Hello"), THEME);
label.setColor(THEME.accent);
label.setAlignment(Label.Align.CENTER); // LEFT, CENTER, RIGHT
addChild(label);

// Fixed width with truncation
Label fixedLabel = new Label(font, x, y, 100, Component.literal("Long text..."), THEME);

// Update text
label.setText(Component.literal("Updated"));
```

### Panel
Container with themed background.

```java
Panel panel = new Panel(x, y, width, height, THEME);
panel.bgColor(THEME.bgDark);
panel.borderColor(THEME.borderAccent);
panel.title(Component.literal("Section"), font); // optional title
panel.setClipChildren(true); // optional scissor
panel.addChild(someWidget);
addChild(panel);
```

### ScrollPane
Scrollable container with built-in scrollbar.

```java
ScrollPane scroll = new ScrollPane(x, y, width, height, THEME);
scroll.setContentHeight(500); // total scrollable height
scroll.addChild(widget1);
scroll.addChild(widget2);
addChild(scroll);
// Mouse wheel scrolls, scrollbar draggable
```

### EcoProgressBar
Progress bar with optional percentage text.

```java
EcoProgressBar bar = new EcoProgressBar(x, y, 200, 16, THEME);
bar.progress(0.75);
bar.fillColor(THEME.success);
bar.showPercent(true);        // shows "75%"
bar.label(Component.literal("Loading..."));
addChild(bar);
```

### EcoStatCard
Metric display with label and value.

```java
EcoStatCard card = new EcoStatCard(x, y, width, height,
    Component.translatable("stat.revenue"),
    Component.literal("1 500 G"),
    THEME.success, THEME);
addChild(card);

// Update value
card.setValue(Component.literal("2 000 G"), THEME.success);
```

### EcoItemSlot
Minecraft item display with rarity border and tooltip.

```java
EcoItemSlot slot = new EcoItemSlot(x, y, 32);
slot.setItem(itemStack, rarityColor);
addChild(slot);
// Hover shows item tooltip automatically
```

### EcoInventoryGrid
Player inventory display with sections.

```java
EcoInventoryGrid grid = EcoInventoryGrid.builder()
    .inventory(player.getInventory())
    .columns(9)
    .slotSize(EcoInventoryGrid.SlotSize.MEDIUM)
    .scrollable(true)
    .showMain(true).showHotbar(true)
    .showArmor(false).showOffhand(false)
    .onSlotClicked(slotIndex -> onSlotClicked(slotIndex))
    .theme(THEME)
    .build();
grid.setBounds(x, y, w, h);
addChild(grid);

// Refresh after inventory changes
grid.refresh();
grid.setSelectedSlot(5);
```

### EcoScrollbar
Standalone scrollbar (usually used internally by ScrollPane and EcoTable).

```java
EcoScrollbar scrollbar = new EcoScrollbar(x, y, height, THEME);
scrollbar.setContentRatio(0.5f); // visible = 50% of content
scrollbar.setScrollValue(0.0f);  // 0.0 = top, 1.0 = bottom
addChild(scrollbar);
```

---

## Theme

All widgets use the `Theme` class for consistent colors.

```java
Theme THEME = Theme.dark(); // WoW-inspired dark theme

// Key colors:
THEME.bgDarkest, THEME.bgDark, THEME.bgMedium, THEME.bgLight
THEME.accent, THEME.accentBg        // gold
THEME.success, THEME.successBg      // green
THEME.danger, THEME.dangerBg        // red
THEME.warning, THEME.warningBg      // orange
THEME.textWhite, THEME.textLight, THEME.textGrey, THEME.textDim
THEME.border, THEME.borderLight, THEME.borderAccent
THEME.rarityCommon, THEME.rarityUncommon, THEME.rarityRare, THEME.rarityEpic
```

---

## i18n

All user-facing strings must use `Component.translatable("modid.key")`:

```java
// In Java:
Component.translatable("ecocraft_ah.button.buy")

// In lang files (assets/<modid>/lang/):
// en_us.json: { "ecocraft_ah.button.buy": "Buy" }
// fr_fr.json: { "ecocraft_ah.button.buy": "Acheter" }
// es_es.json: { "ecocraft_ah.button.buy": "Comprar" }
```

---

## Common Patterns

### Tab System
```java
// Create tabs as BaseWidget children, switch with setVisible
BuyTab buyTab = new BuyTab(this, x, y, w, h);
SellTab sellTab = new SellTab(this, x, y, w, h);
getTree().addChild(buyTab);
getTree().addChild(sellTab);

// Switch tab
buyTab.setVisible(tab == 0);
sellTab.setVisible(tab == 1);
```

### Rebuilding a Tab's Content
```java
// Inside a tab (extends BaseWidget):
private void buildWidgets() {
    // Remove old children
    for (WidgetNode child : new ArrayList<>(getChildren())) {
        removeChild(child);
    }
    // Create new widgets
    addChild(new EcoButton(...));
    addChild(new EcoTable(...));
}
```

### Hit Test Order
Last child added = tested first (on top). If a Label overlaps a Button, add the Label BEFORE the Button so the Button receives clicks.

### DrawUtils
```java
DrawUtils.drawPanel(graphics, x, y, w, h, bgColor, borderColor);
DrawUtils.drawAccentSeparator(graphics, x, y, width, theme);
DrawUtils.truncateText(font, text, maxWidth); // returns truncated string with "..."
```
