# GUI Lib — New Components (Toggle, Slider, Repeater) — Design Spec

## Overview

Three new reusable components for gui-lib to support configuration screens and forms. All components follow the existing WoW dark theme, use the Builder pattern, and integrate with the Theme color system.

---

## 1. Toggle Switch

Pill-shaped toggle that slides a circle between ON and OFF states.

### Visual

- **OFF** : pill background `bgMedium`, border `borderLight`, circle on the left, circle color `textGrey`
- **ON** : pill background `successBg` (or configurable `accentBg`), border `success`, circle on the right, circle color `textWhite`
- **Labels** : optional "ON"/"OFF" text rendered inside the pill (configurable via `.showLabels(true)`)

### API

```java
Toggle toggle = new Toggle(x, y, width, height, theme);
toggle.value(true);                        // set initial state
toggle.showLabels(true);                   // show ON/OFF text
toggle.onColor(theme.success);             // customize ON color (default: success green)
toggle.responder(val -> { ... });          // callback on change
boolean state = toggle.getValue();         // read state
```

### Interaction

- Click anywhere on the toggle → switch state
- Fires responder callback with new boolean value

### Size

- Width and height configurable
- Circle diameter = `height - 4` (2px padding)
- Minimum width = `height * 2` (to fit the circle travel)

---

## 2. Slider (Range Input)

Visual range selector with configurable min/max/step, orientation, and label placement.

### Visual

- **Rail** : thin line (2px) in `bgMedium`, full width/height of the slider area
- **Filled portion** : from min to current value, colored `accent`
- **Cursor** : small square/circle (8×8 or `cursorSize` configurable) in `accent`, with `borderAccent` border
- **Label** : formatted value with optional suffix, positioned before/center/after the rail

### API

```java
Slider slider = new Slider(font, x, y, width, height, theme);
slider.min(0).max(100).step(1);            // integer range
slider.min(0).max(0.10).step(0.01);        // decimal range
slider.value(5.0);                          // initial value
slider.suffix("%");                         // display "5%"
slider.orientation(Slider.Orientation.HORIZONTAL);  // default
slider.orientation(Slider.Orientation.VERTICAL);
slider.labelPosition(Slider.LabelPosition.AFTER);   // default
slider.labelPosition(Slider.LabelPosition.BEFORE);
slider.labelPosition(Slider.LabelPosition.CENTER);  // on cursor
slider.responder(val -> { ... });           // callback
double val = slider.getValue();             // read value
```

### Label positions

**Horizontal mode:**
- BEFORE = label left of the rail
- CENTER = label rendered on/above the cursor, moves with it
- AFTER = label right of the rail (default)

**Vertical mode:**
- BEFORE = label above the rail
- CENTER = label rendered on/beside the cursor, moves with it
- AFTER = label below the rail (default)

### Interaction

- Click on rail → jump cursor to that position (snapped to step)
- Click + drag cursor → smooth movement (snapped to step on release)
- Mouse wheel when hovered → increment/decrement by step

### Value formatting

- If step is integer (step % 1 == 0) → format as integer: "5"
- If step is decimal → format with matching precision: "0.05"
- Suffix appended: "5%", "0.05", "12h"

### Size

- Width and height fully configurable
- Rail thickness: 2px (horizontal) or 2px (vertical)
- Cursor size: 8px default, configurable
- Label space: automatically reserved based on labelPosition and font width

---

## 3. Repeater

Dynamic list with per-row form content, add/remove functionality, and optional scrolling.

### Visual

- **Container** : panel background (`bgDark`, `border`), scrollable if content exceeds height
- **Each row** : horizontal layout with user-defined widgets on the left + red `×` delete button on the right. Rows separated by 1px `bgMedium` line.
- **Add button** : `+ Ajouter` button at the bottom of the list (green, `Button.success` style)
- **Empty state** : centered text "Aucun élément" in `textDim`

### API

```java
Repeater<Integer> repeater = new Repeater<>(x, y, width, height, theme);
repeater.itemFactory(() -> 24);                        // default value for new items
repeater.rowHeight(24);                                // height per row
repeater.rowRenderer((value, ctx) -> {                 // render each row
    // ctx provides: x, y, width, index, and methods to create/position widgets
    NumberInput input = new NumberInput(font, ctx.x(), ctx.y(), ctx.width() - 24, 18, theme);
    input.setValue(value);
    input.responder(newVal -> ctx.setValue(newVal.intValue()));
    ctx.addWidget(input);
});
repeater.values(List.of(12, 24, 48));                  // initial values
repeater.maxItems(10);                                 // optional limit
repeater.responder(values -> { ... });                 // callback when list changes
List<Integer> values = repeater.getValues();           // read values
```

### RowContext

Provided to the `rowRenderer` for each row:

```java
interface RowContext<T> {
    int x();                    // left x of the content area
    int y();                    // top y of this row
    int width();                // available width (excluding delete button)
    int index();                // row index
    void setValue(T value);     // update the value for this row
    void addWidget(AbstractWidget widget);  // register a widget for input handling
}
```

### Interaction

- Click `×` on a row → remove that row, shift others up, fire responder
- Click `+ Ajouter` → append a new row with default value from `itemFactory`, fire responder
- Scroll wheel → scroll the list if it overflows
- Add button hidden when `maxItems` reached

### Scrolling

- Uses existing gui-lib `Scrollbar` when content exceeds container height
- Row area clipped with scissor

---

## File Structure

| File | Description |
|------|-------------|
| `gui-lib/src/main/java/net/ecocraft/gui/widget/Toggle.java` | Toggle switch component |
| `gui-lib/src/main/java/net/ecocraft/gui/widget/Slider.java` | Slider range input component |
| `gui-lib/src/main/java/net/ecocraft/gui/widget/Repeater.java` | Dynamic list repeater component |

All three extend `AbstractWidget` and follow existing gui-lib patterns (Theme integration, DrawUtils for rendering, narration stubs).

## Testing

Unit testing is limited since these are rendering widgets (require Minecraft runtime). In-game testing via the AH settings screen (next spec) will validate all components. Format/logic tests can be written for value snapping and formatting.
