# gui-lib V2 Core: Widget Tree + Event System — Design Spec

## Overview

Replace gui-lib's flat widget list architecture with a proper widget tree. Widgets form a parent→children hierarchy. Rendering follows the tree (depth-first). Events bubble up from target to root. Portals allow widgets (dropdowns, dialogs) to render/receive events at the root level. A single global focus model. Scissor clipping is opt-in per widget.

This is the foundation for all gui-lib improvements. Built incrementally — existing widgets are migrated one by one.

## Architecture

### WidgetNode Interface

The contract for any widget in the tree:

```java
public interface WidgetNode {
    // Tree structure
    @Nullable WidgetNode getParent();
    List<WidgetNode> getChildren();
    void addChild(WidgetNode child);
    void removeChild(WidgetNode child);
    void setParent(@Nullable WidgetNode parent);

    // Bounds (absolute screen coordinates)
    int getX();
    int getY();
    int getWidth();
    int getHeight();
    void setPosition(int x, int y);
    void setSize(int width, int height);
    boolean containsPoint(double mx, double my);

    // Rendering
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);
    void renderChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    // Mouse events — return true to consume (stops bubbling)
    boolean onMouseClicked(double mouseX, double mouseY, int button);
    boolean onMouseReleased(double mouseX, double mouseY, int button);
    boolean onMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY);
    boolean onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);

    // Key events — sent to focused widget, bubble up
    boolean onKeyPressed(int keyCode, int scanCode, int modifiers);
    boolean onCharTyped(char codePoint, int modifiers);

    // Focus
    boolean isFocusable();
    void onFocusGained();
    void onFocusLost();

    // Options
    boolean isClipChildren();   // scissor opt-in
    boolean isModal();          // blocks events to siblings below
    boolean isVisible();
    void setVisible(boolean visible);
}
```

### BaseWidget Abstract Class

Convenience base class implementing `WidgetNode`. Manages children list, parent reference, bounds, visibility. Subclasses only override what they need.

```java
public abstract class BaseWidget implements WidgetNode {
    private WidgetNode parent;
    private final List<WidgetNode> children = new ArrayList<>();
    private int x, y, width, height;
    private boolean visible = true;
    private boolean clipChildren = false;
    private boolean modal = false;

    // All WidgetNode methods implemented with defaults
    // render() is abstract — subclasses must implement
    // All event handlers return false by default (not consumed)
    // renderChildren() iterates children and renders each, with scissor if clipChildren

    // Convenience methods:
    void setBounds(int x, int y, int w, int h);
    void removeFromParent();
    <T extends WidgetNode> T findParent(Class<T> type);  // walk up tree
}
```

### WidgetTree — The Root Manager

Owns the root node and manages global concerns:

```java
public class WidgetTree {
    private final RootNode root;        // invisible root container
    private WidgetNode focusedNode;     // single global focus
    private final List<WidgetNode> portals = new ArrayList<>();  // portal nodes

    // Tree operations
    void addChild(WidgetNode child);    // add to root
    void removeChild(WidgetNode child); // remove from root
    void clear();                       // remove all children

    // Portals (render/events at root level, above everything)
    void addPortal(WidgetNode portal);
    void removePortal(WidgetNode portal);

    // Focus
    void setFocused(WidgetNode node);
    WidgetNode getFocused();

    // Render — called by EcoScreen
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    // Event dispatch — called by EcoScreen
    boolean mouseClicked(double mouseX, double mouseY, int button);
    boolean mouseReleased(double mouseX, double mouseY, int button);
    boolean mouseDragged(double mouseX, double mouseY, int button, double dX, double dY);
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);
    boolean keyPressed(int keyCode, int scanCode, int modifiers);
    boolean charTyped(char codePoint, int modifiers);
}
```

### Rendering Pipeline

```
WidgetTree.render():
  1. Render root's children (depth-first, in order)
     For each child:
       a. If not visible → skip
       b. Render the widget itself (widget.render())
       c. If clipChildren → enableScissor(widget bounds)
       d. Render children recursively (widget.renderChildren())
       e. If clipChildren → disableScissor()
  2. Render portals (last = on top of everything)
     Same depth-first process for each portal
```

### Event Dispatch (Bubble Model)

```
WidgetTree.mouseClicked(mx, my, button):
  1. Check portals first (reverse order — last added = top)
     For each portal (reverse):
       a. Hit test: find deepest child containing (mx, my)
       b. If found → call onMouseClicked, bubble up to portal root
       c. If portal.isModal() → stop here (don't check lower portals or root)
  2. Check root's children (reverse order — last added = top)
     Same hit test + bubble process
  3. If a widget consumed the event (returned true) → set focus to that widget

Hit test (depth-first, reverse child order):
  For widget W:
    If W not visible or not containsPoint(mx, my) → null
    For each child of W (reverse order):
      result = hitTest(child, mx, my)
      if result != null → return result
    return W  (W itself is the deepest match)

Bubble:
  target.onMouseClicked(mx, my, button)
  if consumed → return true
  if target.parent != null → bubble to parent
  return false
```

### Key Event Dispatch

```
WidgetTree.keyPressed(keyCode, scanCode, modifiers):
  1. If focusedNode != null:
     a. focusedNode.onKeyPressed(keyCode, scanCode, modifiers)
     b. If not consumed → bubble to parent
  2. If still not consumed → return false
```

### Focus Management

- Single `focusedNode` in the tree
- `setFocused(node)`: calls `onFocusLost()` on old, `onFocusGained()` on new
- Clicking a focusable widget auto-focuses it
- Clicking a non-focusable widget clears focus
- Tab key navigation: not implemented in V1 (can be added later)

### Portals

A portal is a `WidgetNode` that renders and receives events at the root level, regardless of its logical position.

Use cases:
- **Dropdown option list**: created by Dropdown widget, portal renders on top
- **Dialog**: created by any widget, portal renders as modal overlay
- **Tooltip**: portal renders above everything, no event handling

API:
```java
// In a Dropdown widget:
void open() {
    optionList = new DropdownList(...);
    getTree().addPortal(optionList);
}

void close() {
    getTree().removePortal(optionList);
}
```

When a portal's owner is removed from the tree, its portals are automatically cleaned up.

### Modal Behavior

When a portal has `isModal() = true`:
- The event dispatch stops checking lower portals and root children
- Render: a semi-transparent overlay is drawn before the modal portal (configurable)
- The modal portal and its children are the only widgets that receive events

Multiple modals stack naturally (last added = top, receives events first).

### EcoScreen — Bridge to Minecraft

```java
public class EcoScreen extends Screen {
    private final WidgetTree tree = new WidgetTree();

    @Override
    protected void init() {
        tree.clear();
        // Subclasses add widgets to tree
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float pt) {
        renderBackground(graphics, mouseX, mouseY, pt);
        tree.render(graphics, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return tree.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (tree.keyPressed(kc, sc, mod)) return true;
        return super.keyPressed(kc, sc, mod);  // Escape to close screen
    }

    // ... same for other events

    public WidgetTree getTree() { return tree; }
}
```

Subclasses of `EcoScreen` replace `AuctionHouseScreen` etc. They don't use `addRenderableWidget()` — everything goes through the tree.

## File Structure

| File | Description |
|------|-------------|
| `gui-lib/.../core/WidgetNode.java` | Interface |
| `gui-lib/.../core/BaseWidget.java` | Abstract convenience class |
| `gui-lib/.../core/WidgetTree.java` | Root manager, render + event dispatch |
| `gui-lib/.../core/EcoScreen.java` | Minecraft Screen bridge |
| `gui-lib/.../core/RootNode.java` | Invisible root container (internal) |

All in a new `core` package, separate from existing `widget` package. Existing widgets are not modified in this phase — they continue to work with the old system. Migration happens in Phase 2.

## What This Does NOT Include (Phase 2+)

- EcoEditBox (custom text input replacing Minecraft EditBox)
- Migration of existing widgets from AbstractWidget to BaseWidget
- New components (Label, Checkbox, ProgressBar, ScrollPane)
- Standardized widget API (GuiWidget interface, builder pattern)
- Performance optimizations (cached layouts, cached text widths)
- Bug fixes in existing widgets (those are migrated in Phase 2)

## Testing Strategy

The core system (WidgetTree, BaseWidget, event dispatch) is fully testable without Minecraft runtime:
- Create test widgets that extend BaseWidget
- Build trees, dispatch events, verify bubble order
- Verify hit testing with nested widgets
- Verify portal rendering order
- Verify modal blocking
- Verify focus management

## Migration Path

1. **Phase 1 (this spec)**: Build the core. Existing widgets untouched.
2. **Phase 2**: Create `EcoEditBox`. Migrate widgets one by one to `extends BaseWidget`. Each migrated widget works in both old Screen and new EcoScreen.
3. **Phase 3**: Create `EcoScreen` subclasses for AH screens. Remove old AbstractWidget usage. Full integration.

The key constraint: at no point should the auction-house be broken. Each phase produces a working system.
