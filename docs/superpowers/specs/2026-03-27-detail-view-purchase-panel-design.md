# Detail View Purchase Panel — Design Spec

## Overview

Refactor the Buy tab's detail view to add a purchase/bid panel on the right side. Currently, buying requires clicking an "Acheter" text in the table row — no quantity selection, no recap, no enchère support. The new layout splits the detail view into a table (left) and a contextual action panel (right).

## Layout

**Two-column split:**
- **Left (~65%):** Scrollable table of offers (sellers). Same as current but **without the "Action" column** (replaced by the panel).
- **Right (~35%):** Contextual purchase/bid panel, updates on row selection.

**Above the table:** Filters (enchantments, durability) remain unchanged.
**Below the table:** Price history bar remains unchanged.

## Table Changes

Remove the "Action" column. Keep: Vendeur, Qté, Prix unit., Type, Expire (all sortable except Type).

When a row is clicked, it becomes visually selected (highlighted) and the right panel updates. **On load, the first row (best price) is pre-selected.**

## Right Panel — Buyout Mode

When selected row is `BUYOUT` type:

1. **Item icon** (ItemSlot) + item name + enchantment list (if any)
2. **Vendeur:** seller name
3. **Prix unitaire:** formatted price
4. **Quantité:** NumberInput field (min=1, max=offer quantity, default=1)
5. **Prix total:** unit price × selected quantity, recalculated dynamically
6. **Bouton "Acheter"** (green, Button.success)

On click "Acheter" → sends `BuyListingPayload` to server (existing flow). Quantity field determines how many to buy from this listing.

Note: Current server `buyListing()` buys the entire listing (all quantity). The quantity selector is a UI affordance for when partial purchases are implemented later. For now, if the listing has qty=1 the field is locked to 1. If qty>1, the field shows the full quantity and is read-only (buying means buying the whole stack).

## Right Panel — Auction Mode

When selected row is `AUCTION` type:

1. **Item icon** (ItemSlot) + item name + enchantment list (if any)
2. **Vendeur:** seller name
3. **Enchère actuelle:** current bid amount, or "Aucune enchère" if 0
4. **Enchère minimum:** currentBid + 1 (or starting price if no bids)
5. **Montant:** NumberInput field (pre-filled with minimum bid)
6. **Temps restant:** expiry countdown
7. **Bouton "Enchérir"** (orange, Button.auction or warning-styled)

On click "Enchérir" → sends `PlaceBidPayload` with the entered amount (existing flow).

## Components Used (all from gui-lib)

- `ItemSlot` — item icon display
- `NumberInput` — quantity / bid amount
- `Button` — action button
- `DrawUtils` — panel background, separators, text rendering
- `Theme` — colors

No new gui-lib components needed — this is pure composition in `BuyTab.java`.

## Panel Empty State

No empty state — the first row is always pre-selected on load.

## Text (French)

- "Offre sélectionnée" (panel title)
- "Vendeur:" / "Prix unitaire:" / "Quantité:" / "Prix total:"
- "Enchère actuelle:" / "Enchère minimum:" / "Montant:" / "Temps restant:"
- "Acheter" / "Enchérir"
- "Aucune enchère" (no bids yet)
