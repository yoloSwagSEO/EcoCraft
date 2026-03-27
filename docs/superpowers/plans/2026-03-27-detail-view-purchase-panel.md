# Detail View Purchase Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the Buy tab detail view to a two-column layout with a contextual purchase/bid panel on the right.

**Architecture:** First add row selection support to gui-lib's Table component (reusable feature). Then refactor BuyTab's `initDetail()` to split into left table (65%) + right panel (35%), and add panel rendering with ItemSlot, NumberInput, and Button composition.

**Tech Stack:** Java 21, NeoForge, gui-lib Table/ItemSlot/NumberInput/Button, existing AH payloads.

---

### Task 1: Add row selection support to gui-lib Table

The Table currently has hover highlighting but no persistent selection. We need: a `selectedRow` index, visual highlight for it, a `selectionListener` callback, and a `setSelectedRow()` method.

**Files:**
- Modify: `gui-lib/src/main/java/net/ecocraft/gui/table/Table.java`

- [ ] **Step 1: Add selection state fields**

In `Table.java`, after the `sortDirty` field block, add:

```java
// Selection state
private int selectedRow = -1;
private java.util.function.IntConsumer selectionListener;
```

- [ ] **Step 2: Add selection API methods**

After the `getHoveredIcon()` method, add:

```java
/** Returns the currently selected row index, or -1 if none. */
public int getSelectedRow() { return selectedRow; }

/** Programmatically selects a row. Pass -1 to clear selection. */
public void setSelectedRow(int index) {
    this.selectedRow = index;
}

/** Sets a listener called when the user clicks a row (receives row index in display order). */
public void setSelectionListener(java.util.function.IntConsumer listener) {
    this.selectionListener = listener;
}
```

- [ ] **Step 3: Add selection highlight in renderWidget**

In the row rendering loop, after the hover highlight block (`if (isHovered) { ... } else if (alt) { ... }`), modify to also check selection:

Replace:
```java
if (isHovered) {
    hoveredRow = i;
    graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.bgMedium);
} else if (alt) {
    graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.bgRowAlt);
}
```

With:
```java
boolean isSelected = (i == selectedRow);
if (isHovered) {
    hoveredRow = i;
    graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.bgMedium);
} else if (isSelected) {
    graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.accentBg);
} else if (alt) {
    graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, theme.bgRowAlt);
}
```

- [ ] **Step 4: Fire selection on click**

In `mouseClicked()`, modify the row click handler. Replace:

```java
List<TableRow> display = getDisplayRows();
if (button != 0 || hoveredRow < 0 || hoveredRow >= display.size()) return false;
var row = display.get(hoveredRow);
if (row.onClick() != null) {
    row.onClick().run();
    return true;
}
return false;
```

With:
```java
List<TableRow> display = getDisplayRows();
if (button != 0 || hoveredRow < 0 || hoveredRow >= display.size()) return false;
selectedRow = hoveredRow;
if (selectionListener != null) {
    selectionListener.accept(hoveredRow);
}
var row = display.get(hoveredRow);
if (row.onClick() != null) {
    row.onClick().run();
    return true;
}
return true;
```

- [ ] **Step 5: Verify build**

Run: `./gradlew :gui-lib:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/table/Table.java
git commit -m "feat(gui-lib): add row selection support to Table"
```

---

### Task 2: Refactor BuyTab detail view layout to two columns

Split the detail view into left table (~65%) and right panel area (~35%). Remove the "Action" column from the table. Add selection state tracking.

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/BuyTab.java`

- [ ] **Step 1: Add detail panel state fields**

After the existing detail mode widget fields (`private Table detailTable;`), add:

```java
// Detail panel state
private int selectedEntryIndex = 0; // index into filtered detailEntries
private ItemSlot panelItemSlot;
private NumberInput panelQuantityInput;
private NumberInput panelBidInput;
private Button panelActionButton;
private static final int PANEL_WIDTH_RATIO = 35; // percentage of width
```

Add imports at the top:
```java
import net.ecocraft.gui.widget.ItemSlot;
import net.ecocraft.gui.widget.NumberInput;
```

- [ ] **Step 2: Refactor initDetail — split layout**

Replace the table section of `initDetail()` (from `// Detail table` comment to `addWidget.accept(detailTable)`). The new code computes two-column layout and removes the Action column:

```java
        // Two-column layout
        int panelW = (int) (w * PANEL_WIDTH_RATIO / 100.0);
        int tableW = w - panelW - 6; // 6px gap

        // Detail table (left column) — no "Action" column
        int tableY = filterY;
        int tableH = y + h - 24 - tableY;
        List<TableColumn> columns = List.of(
                TableColumn.sortableLeft(Component.literal("Vendeur"), 2f),
                TableColumn.sortableCenter(Component.literal("Qté"), 1f),
                TableColumn.sortableRight(Component.literal("Prix unit."), 2f),
                TableColumn.center(Component.literal("Type"), 1f),
                TableColumn.sortableCenter(Component.literal("Expire"), 1.5f)
        );
        detailTable = Table.builder()
                .columns(columns)
                .theme(THEME)
                .navigation(Table.Navigation.SCROLL)
                .showScrollbar(true)
                .scrollLines(1)
                .build(x, tableY, tableW, tableH);
        detailTable.setSelectionListener(this::onDetailRowSelected);
        addWidget.accept(detailTable);

        // Right panel widgets
        int panelX = x + tableW + 6;
        int panelY = y + 16;
        initPurchasePanel(addWidget, panelX, panelY, panelW);
```

- [ ] **Step 3: Add initPurchasePanel method**

After `initDetail()`, add:

```java
    private void initPurchasePanel(Consumer<AbstractWidget> addWidget, int px, int py, int pw) {
        Font font = Minecraft.getInstance().font;

        // Item slot (centered)
        int slotSize = 32;
        int slotX = px + (pw - slotSize) / 2;
        panelItemSlot = new ItemSlot(slotX, py + 4, slotSize, THEME);
        addWidget.accept(panelItemSlot);

        // Quantity input (for BUYOUT)
        int inputY = py + 100;
        panelQuantityInput = new NumberInput(font, px + 4, inputY, pw - 8, 16, THEME);
        panelQuantityInput.min(1).max(1).step(1);
        panelQuantityInput.setValue(1);
        panelQuantityInput.responder(val -> updatePanelTotal());
        addWidget.accept(panelQuantityInput);

        // Bid input (for AUCTION — overlaps quantity, only one visible at a time)
        panelBidInput = new NumberInput(font, px + 4, inputY, pw - 8, 16, THEME);
        panelBidInput.min(1).max(Long.MAX_VALUE).step(1);
        panelBidInput.setValue(1);
        panelBidInput.visible = false;
        addWidget.accept(panelBidInput);

        // Action button
        int btnY = inputY + 40;
        panelActionButton = Button.success(THEME, Component.literal("Acheter"), () -> onPanelAction());
        panelActionButton.setX(px + 4);
        panelActionButton.setY(btnY);
        panelActionButton.setWidth(pw - 8);
        panelActionButton.setHeight(20);
        addWidget.accept(panelActionButton);

        // Apply initial selection
        updatePurchasePanel();
    }
```

- [ ] **Step 4: Add onDetailRowSelected handler**

```java
    private void onDetailRowSelected(int displayIndex) {
        this.selectedEntryIndex = displayIndex;
        updatePurchasePanel();
    }
```

- [ ] **Step 5: Add updatePurchasePanel method**

This updates all panel widgets based on the selected entry:

```java
    private void updatePurchasePanel() {
        var filtered = getFilteredEntries();
        if (selectedEntryIndex < 0 || selectedEntryIndex >= filtered.size()) {
            selectedEntryIndex = 0;
        }
        if (filtered.isEmpty()) return;

        var entry = filtered.get(selectedEntryIndex);
        boolean isAuction = "AUCTION".equals(entry.type());

        // Update item slot
        ItemStack icon;
        int stackIdx = detailEntries.indexOf(entry);
        if (stackIdx >= 0 && stackIdx < detailStacks.size() && !detailStacks.get(stackIdx).isEmpty()) {
            icon = detailStacks.get(stackIdx);
        } else {
            icon = AuctionHouseScreen.itemFromId(detailItemId);
        }
        panelItemSlot.setItem(icon, detailRarityColor);

        // Quantity vs Bid
        if (isAuction) {
            panelQuantityInput.visible = false;
            panelBidInput.visible = true;
            long minBid = entry.unitPrice() > 0 ? entry.unitPrice() + 1 : 1;
            panelBidInput.min(minBid).setValue(minBid);
            panelActionButton.setMessage(Component.literal("Enchérir"));
            // Orange style for auction button
        } else {
            panelQuantityInput.visible = true;
            panelBidInput.visible = false;
            panelQuantityInput.max(entry.quantity()).setValue(entry.quantity());
            panelQuantityInput.setEnabled(entry.quantity() > 1);
            panelActionButton.setMessage(Component.literal("Acheter"));
        }
    }

    private void updatePanelTotal() {
        // Total is recalculated and drawn in renderDetailForeground
    }
```

- [ ] **Step 6: Add getFilteredEntries helper**

```java
    private List<ListingDetailResponsePayload.ListingEntry> getFilteredEntries() {
        List<ListingDetailResponsePayload.ListingEntry> filtered = new ArrayList<>();
        for (int i = 0; i < detailEntries.size(); i++) {
            ItemStack stack = i < detailStacks.size() ? detailStacks.get(i) : ItemStack.EMPTY;
            if (matchesDurabilityFilter(stack)) {
                filtered.add(detailEntries.get(i));
            }
        }
        return filtered;
    }
```

- [ ] **Step 7: Add onPanelAction method**

```java
    private void onPanelAction() {
        var filtered = getFilteredEntries();
        if (selectedEntryIndex < 0 || selectedEntryIndex >= filtered.size()) return;
        var entry = filtered.get(selectedEntryIndex);
        boolean isAuction = "AUCTION".equals(entry.type());

        if (isAuction) {
            long bidAmount = panelBidInput.getValue();
            PacketDistributor.sendToServer(new PlaceBidPayload(entry.listingId(), bidAmount));
        } else {
            PacketDistributor.sendToServer(new BuyListingPayload(entry.listingId()));
        }
    }
```

- [ ] **Step 8: Update updateDetailTable — remove Action column, use row onClick for selection**

Replace the `updateDetailTable()` method. The rows no longer have an "Action" cell and the onClick sets selection:

```java
    private void updateDetailTable() {
        if (detailTable == null) return;

        var filtered = getFilteredEntries();
        List<TableRow> rows = new ArrayList<>();
        for (int i = 0; i < filtered.size(); i++) {
            var entry = filtered.get(i);
            int stackIdx = detailEntries.indexOf(entry);
            ItemStack stack = stackIdx >= 0 && stackIdx < detailStacks.size() ? detailStacks.get(stackIdx) : ItemStack.EMPTY;

            boolean isAuction = "AUCTION".equals(entry.type());
            ItemStack icon = stack.isEmpty() ? AuctionHouseScreen.itemFromId(detailItemId) : stack;

            final int rowIdx = i;
            rows.add(TableRow.withIcon(icon, detailRarityColor, List.of(
                    TableRow.Cell.of(Component.literal(entry.sellerName()), THEME.textLight, entry.sellerName()),
                    TableRow.Cell.of(Component.literal(String.valueOf(entry.quantity())), THEME.textLight, entry.quantity()),
                    TableRow.Cell.of(Component.literal(formatPrice(entry.unitPrice())), THEME.accent, entry.unitPrice()),
                    TableRow.Cell.of(Component.literal(isAuction ? "Enchère" : "Achat"),
                            isAuction ? THEME.warning : THEME.success),
                    TableRow.Cell.of(Component.literal(formatTimeRemaining(entry.expiresInMs())), THEME.textGrey, entry.expiresInMs())
            ), null)); // no per-row onClick — selection handled by Table.selectionListener
        }
        detailTable.setRows(rows);

        // Pre-select first row (best price)
        if (!rows.isEmpty()) {
            selectedEntryIndex = 0;
            detailTable.setSelectedRow(0);
            updatePurchasePanel();
        }
    }
```

- [ ] **Step 9: Update renderDetailForeground — draw panel labels and total**

Replace `renderDetailForeground()`:

```java
    private void renderDetailForeground(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        // Item header (left aligned)
        int headerY = y + 18;
        int tableW = w - (int)(w * PANEL_WIDTH_RATIO / 100.0) - 6;
        String truncatedName = DrawUtils.truncateText(font, detailItemName, tableW - 70);
        graphics.drawString(font, Component.literal(truncatedName), x + 64, headerY, detailRarityColor, false);

        // Right panel background
        int panelW = (int) (w * PANEL_WIDTH_RATIO / 100.0);
        int panelX = x + tableW + 6;
        int panelY = y + 16;
        int panelH = h - 40;
        DrawUtils.drawPanel(graphics, panelX, panelY, panelW, panelH, THEME);

        // Panel title
        String title = "Offre sélectionnée";
        int titleW = font.width(title);
        graphics.drawString(font, title, panelX + (panelW - titleW) / 2, panelY + 2, THEME.accent, false);
        DrawUtils.drawAccentSeparator(graphics, panelX + 4, panelY + 12, panelW - 8, THEME);

        // Panel content (based on selected entry)
        var filtered = getFilteredEntries();
        if (selectedEntryIndex >= 0 && selectedEntryIndex < filtered.size()) {
            var entry = filtered.get(selectedEntryIndex);
            boolean isAuction = "AUCTION".equals(entry.type());
            int labelX = panelX + 8;
            int valueX = panelX + panelW - 8;
            int lineY = panelY + 42; // below item slot

            // Item name
            String itemName = DrawUtils.truncateText(font, detailItemName, panelW - 16);
            int nameW = font.width(itemName);
            graphics.drawString(font, itemName, panelX + (panelW - nameW) / 2, panelY + 38, detailRarityColor, false);

            // Seller
            lineY += 14;
            graphics.drawString(font, "Vendeur:", labelX, lineY, THEME.textGrey, false);
            String seller = DrawUtils.truncateText(font, entry.sellerName(), panelW / 2);
            graphics.drawString(font, seller, valueX - font.width(seller), lineY, THEME.textLight, false);

            if (isAuction) {
                // Current bid
                lineY += 12;
                graphics.drawString(font, "Enchère actuelle:", labelX, lineY, THEME.textGrey, false);
                String bid = entry.unitPrice() > 0 ? formatPrice(entry.unitPrice()) : "Aucune";
                graphics.drawString(font, bid, valueX - font.width(bid), lineY, THEME.warning, false);

                // Min bid
                lineY += 12;
                long minBid = entry.unitPrice() > 0 ? entry.unitPrice() + 1 : 1;
                graphics.drawString(font, "Enchère min:", labelX, lineY, THEME.textGrey, false);
                String minStr = formatPrice(minBid);
                graphics.drawString(font, minStr, valueX - font.width(minStr), lineY, THEME.textLight, false);

                // Label for bid input
                lineY += 14;
                graphics.drawString(font, "Montant:", labelX, lineY + 4, THEME.textGrey, false);

                // Time remaining
                lineY += 22;
                graphics.drawString(font, "Expire:", labelX, lineY, THEME.textGrey, false);
                String expire = formatTimeRemaining(entry.expiresInMs());
                graphics.drawString(font, expire, valueX - font.width(expire), lineY, THEME.textGrey, false);
            } else {
                // Unit price
                lineY += 12;
                graphics.drawString(font, "Prix unitaire:", labelX, lineY, THEME.textGrey, false);
                String price = formatPrice(entry.unitPrice());
                graphics.drawString(font, price, valueX - font.width(price), lineY, THEME.accent, false);

                // Label for quantity input
                lineY += 14;
                graphics.drawString(font, "Quantité:", labelX, lineY + 4, THEME.textGrey, false);

                // Total price
                lineY += 22;
                long qty = panelQuantityInput != null ? panelQuantityInput.getValue() : entry.quantity();
                long total = entry.unitPrice() * qty;
                graphics.drawString(font, "Prix total:", labelX, lineY, THEME.textLight, false);
                String totalStr = formatPrice(total);
                graphics.drawString(font, totalStr, valueX - font.width(totalStr), lineY, THEME.accent, false);
            }
        }

        // Price history summary below the table
        int historyY = y + h - 18;
        if (detailPriceInfo != null) {
            String historyLine = "Moy: " + formatPrice(detailPriceInfo.avgPrice())
                    + " | Min: " + formatPrice(detailPriceInfo.minPrice())
                    + " | Max: " + formatPrice(detailPriceInfo.maxPrice())
                    + " | Ventes 7j: " + detailPriceInfo.volume7d();
            int historyW = font.width(historyLine);
            graphics.drawString(font, historyLine, x + (w - historyW) / 2, historyY, THEME.textGrey, false);
        } else {
            String noData = "Aucune donnée de prix disponible";
            int noDataW = font.width(noData);
            graphics.drawString(font, noData, x + (w - noDataW) / 2, historyY, THEME.textDim, false);
        }

        // Price info top-right (on the table side, not panel)
        if (detailPriceInfo != null) {
            int infoX = x + tableW - 120;
            graphics.drawString(font, "Moy: " + formatPrice(detailPriceInfo.avgPrice()),
                    infoX, y + 2, THEME.textGrey, false);
            graphics.drawString(font, "Min: " + formatPrice(detailPriceInfo.minPrice()),
                    infoX, y + 12, THEME.success, false);
            graphics.drawString(font, "Max: " + formatPrice(detailPriceInfo.maxPrice()),
                    infoX, y + 22, THEME.danger, false);
        }
    }
```

- [ ] **Step 10: Verify build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 11: Build and deploy**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 12: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/screen/BuyTab.java
git commit -m "feat: refactor detail view with purchase/bid panel"
```

---

### Testing Instructions

1. `/ah populate 50` — generate test data
2. Open HDV → Buy tab → click an item to enter detail view
3. **Table:** Verify 5 columns (no "Action"), rows are clickable, selected row highlighted
4. **Panel (Buyout):** Verify item icon, seller name, unit price, quantity input, total price, "Acheter" button
5. **Panel (Auction):** If an auction listing exists, verify current bid, min bid, bid input, "Enchérir" button
6. **First row pre-selected** on load
7. Click different rows → panel updates
8. Click "Acheter" → verify purchase flow works (balance changes, parcel created)
9. `/ah simulate buy 3` → verify your sold items show in ledger
