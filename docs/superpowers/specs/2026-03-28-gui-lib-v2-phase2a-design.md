# gui-lib V2 Phase 2A: Foundation Components — Design Spec

## Overview

New components built on the V2 core (BaseWidget/WidgetTree). These are the building blocks that other components depend on.

## 1. EcoEditBox — Custom Text Input

Replaces Minecraft's EditBox. Full text editing with cursor, selection, clipboard.

### Visual
- Background: `theme.bgDark`, border: `theme.border` (unfocused) / `theme.borderAccent` (focused)
- Text color: `theme.textLight`
- Cursor: 1px wide vertical line, blinking (toggle every 500ms)
- Selection: highlighted background `theme.accentBg`
- Placeholder text (hint): `theme.textDim`, shown when empty and unfocused

### Features
- Single-line text input
- Cursor positioning via click
- Arrow keys to move cursor (left/right, home/end)
- Text selection: shift+arrows, shift+home/end, double-click selects word, triple-click selects all
- Clipboard: Ctrl+C copy, Ctrl+V paste, Ctrl+X cut, Ctrl+A select all
- Backspace/Delete to remove characters
- Character filter (optional, e.g. numbers only)
- Max length (optional)
- Horizontal scroll when text exceeds visible width
- Responder callback on text change

### API
```java
EcoEditBox editBox = new EcoEditBox(font, x, y, width, height, theme);
editBox.setHint(Component.literal("Placeholder..."));
editBox.setValue("initial text");
editBox.setFilter(s -> s.matches("[0-9]*")); // optional
editBox.setMaxLength(50); // optional
editBox.setResponder(text -> { ... }); // on change
String value = editBox.getValue();
```

### Focus
- Extends BaseWidget, implements isFocusable() → true
- onFocusGained() → show cursor, start blink
- onFocusLost() → hide cursor, clear selection
- No dependency on Minecraft's focus system

## 2. Label — Text Display

Simple text rendering widget. For placing text in layouts and trees.

### API
```java
Label label = new Label(font, x, y, Component.literal("Hello"), theme);
label.setColor(theme.accent); // optional, default theme.textLight
label.setAlignment(Label.Align.LEFT); // LEFT, CENTER, RIGHT
```

### Features
- Renders a single Component
- Configurable color and alignment
- Width auto-calculated from text, or fixed width with truncation
- Not focusable, no events

## 3. Panel — Container with Background

A container that draws a themed background and border. Children are positioned inside.

### API
```java
Panel panel = new Panel(x, y, width, height, theme);
panel.addChild(label);
panel.addChild(button);
panel.setClipChildren(true); // optional scissor
```

### Features
- Draws `DrawUtils.drawPanel()` background before children
- Optional title rendered at top
- Children positioned relative to panel bounds (panel handles offset? or absolute?)
- For V1: absolute positioning. Relative positioning is a Phase 3 layout concern.

## 4. ScrollPane — Scrollable Container

A container that scrolls its children vertically. Shows a scrollbar when content overflows.

### API
```java
ScrollPane scrollPane = new ScrollPane(x, y, width, height, theme);
scrollPane.addChild(widget1);
scrollPane.addChild(widget2);
scrollPane.setContentHeight(500); // total height of content
```

### Features
- Clips children to its bounds (clipChildren = true)
- Vertical scroll via mouse wheel
- Built-in scrollbar (rendered inside, right side)
- Content offset: children are rendered with a Y offset based on scroll position
- Hit test adjusts for scroll offset
- Mouse wheel scrolls when hovered

### Scroll Mechanics
- Children have absolute Y positions (as if no scroll)
- ScrollPane applies a translate before rendering children
- Hit test adds scroll offset to mouse Y before checking children
- Scrollbar thumb size proportional to visible/total ratio

## Files

| File | Package | Description |
|------|---------|-------------|
| `gui-lib/.../core/EcoEditBox.java` | `core` | Custom text input |
| `gui-lib/.../core/Label.java` | `core` | Text display |
| `gui-lib/.../core/Panel.java` | `core` | Container with background |
| `gui-lib/.../core/ScrollPane.java` | `core` | Scrollable container |

All in the `core` package since they use the new BaseWidget system.
