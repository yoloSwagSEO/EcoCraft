# AH Screen Migration to EcoScreen — Design Spec

## Overview

Migrate all Auction House screens from Minecraft's `Screen` + old `widget/` package to the V2 system (`EcoScreen` + `core/` package). After migration, delete the old `widget/` package.

## Screens to Migrate

### AuctionHouseScreen → extends EcoScreen
- `EcoTabBar` as tree child for tab navigation
- Each tab (BuyTab, SellTab, MyAuctionsTab, LedgerTab) is a `BaseWidget` child
- Tab switch: `setVisible(false/true)` — no rebuild, tabs persist their state
- Balance: `Label` in the tree, updated on `receiveBalanceUpdate`
- Gear icon: `EcoButton` in the tree, visible only if admin
- Dialog: `tree.addPortal(dialog)` — no more `activeDialog` hack or `mouseX=-1`
- All network receivers update existing widgets (no `rebuildCurrentTab()`)

### BuyTab extends BaseWidget
- Browse mode: EcoTable + EcoFilterTags (categories) + EcoTextInput (search) + pagination EcoButtons
- Detail mode: EcoTable (left) + purchase Panel (right) with EcoItemSlot, EcoNumberInput, Labels, EcoButtons
- Mode switch: two Panel children, toggle visibility
- Network handlers update table rows and panel content

### SellTab extends BaseWidget
- EcoItemSlot + EcoTextInput (price) + EcoFilterTags (durations) + Labels + EcoButton
- Right column: InventoryGrid (keep as-is for now, it needs special Minecraft ItemStack handling)

### MyAuctionsTab extends BaseWidget
- EcoFilterTags (sub-tabs, status filter, AH filter)
- EcoTable with conditional AH column
- EcoStatCards in footer
- EcoButton for collect

### LedgerTab extends BaseWidget
- EcoFilterTags (period, type, AH filter)
- EcoTable with conditional AH column
- EcoStatCards

### AHSettingsScreen → extends EcoScreen
- Left Panel with EcoButtons (sidebar tabs)
- Right Panel with dynamic content (EcoTextInput, EcoSlider, EcoRepeater, EcoDropdown)
- Delete confirmation via EcoDialog portal

## Key Pattern: Tab as BaseWidget

```java
public class BuyTab extends BaseWidget {
    private final AuctionHouseScreen parent;

    public BuyTab(AuctionHouseScreen parent, int x, int y, int w, int h) {
        super(x, y, w, h);
        this.parent = parent;
        initWidgets(); // create all children once
    }

    private void initWidgets() {
        // Create child widgets via addChild()
        // They persist across tab switches
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float pt) {
        // Draw backgrounds, labels
        // Children rendered automatically by tree
    }

    // Network handlers update existing widgets
    public void onReceiveListings(ListingsResponsePayload p) {
        table.setRows(...); // update existing table
    }
}
```

## Key Pattern: Dialog as Portal

```java
// Instead of:
activeDialog = dialog;

// V2:
EcoDialog dialog = EcoDialog.confirm(...);
getTree().addPortal(dialog);
// Dialog is modal → blocks all events below
// Dialog closes itself → removes from portal
```

## InventoryGrid

Keep `InventoryGrid` as-is (extends AbstractWidget) for now. It has deep Minecraft ItemStack integration. Wrap it or handle it specially in SellTab. This is the one exception to the full migration.

Actually — create a simple bridge: SellTab can add InventoryGrid as a non-tree widget rendered manually. Or better: port InventoryGrid to BaseWidget too since it's not that complex.

Decision: port InventoryGrid to `EcoInventoryGrid extends BaseWidget` as part of this migration.

## French Translations

Replace all English messages with French:
- "No parcels to collect." → "Aucun colis à récupérer."
- "Collected N parcel(s)." → "N colis récupéré(s)."
- "Purchase successful!" → "Achat réussi !"
- "[Auction House] You have N uncollected parcel(s)." → "[HDV] Vous avez N colis en attente."
- All other English strings in ServerPayloadHandler, AHCommand, AHServerEvents

## Cleanup After Migration

Delete the entire old `widget/` package:
- `gui-lib/src/main/java/net/ecocraft/gui/widget/*.java` (all files)
- `gui-lib/src/main/java/net/ecocraft/gui/dialog/Dialog.java`

Keep:
- `gui-lib/src/main/java/net/ecocraft/gui/theme/` (Theme, DrawUtils — used by both)
- `gui-lib/src/main/java/net/ecocraft/gui/table/` (TableColumn, TableRow — reused by EcoTable)
- `gui-lib/src/main/java/net/ecocraft/gui/layout/` (Row, Column — may be useful)
- `gui-lib/src/main/java/net/ecocraft/gui/util/` (NumberFormat, NumberFormatter)

## Migration Order

1. **EcoInventoryGrid** — port InventoryGrid to V2 (needed by SellTab)
2. **AuctionHouseScreen** — shell: EcoScreen + EcoTabBar + balance Label + gear EcoButton
3. **BuyTab** — as BaseWidget with browse/detail modes
4. **SellTab** — as BaseWidget with EcoInventoryGrid
5. **MyAuctionsTab** — as BaseWidget
6. **LedgerTab** — as BaseWidget
7. **AHSettingsScreen** — as EcoScreen with sidebar
8. **French translations** — all messages
9. **Cleanup** — delete old widget/ package

## Files Changed

| File | Action |
|------|--------|
| `auction-house/.../screen/AuctionHouseScreen.java` | Rewrite (extends EcoScreen) |
| `auction-house/.../screen/BuyTab.java` | Rewrite (extends BaseWidget) |
| `auction-house/.../screen/SellTab.java` | Rewrite (extends BaseWidget) |
| `auction-house/.../screen/MyAuctionsTab.java` | Rewrite (extends BaseWidget) |
| `auction-house/.../screen/LedgerTab.java` | Rewrite (extends BaseWidget) |
| `auction-house/.../screen/AHSettingsScreen.java` | Rewrite (extends EcoScreen) |
| `gui-lib/.../core/EcoInventoryGrid.java` | Create |
| `gui-lib/.../widget/*.java` | Delete (after migration) |
| `gui-lib/.../dialog/Dialog.java` | Delete (after migration) |
| `auction-house/.../network/ServerPayloadHandler.java` | FR translations |
| `auction-house/.../AHServerEvents.java` | FR translations |
| `auction-house/.../command/AHCommand.java` | FR translations |
