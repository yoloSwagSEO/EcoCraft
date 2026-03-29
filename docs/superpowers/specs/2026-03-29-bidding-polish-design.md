# Bidding Polish — Design Spec

## Overview

Polish the auction bidding experience with three features: a reusable toast notification system in gui-lib, a configurable notification framework in auction-house, and bid history UI in the detail view.

---

## Part 1: gui-lib — EcoToast + EcoToastManager

### EcoToast

A standalone overlay widget (not in the WidgetTree). Built via fluent builder:

```java
EcoToast.builder(theme)
    .title("Surenchéri !")
    .message("Épée en diamant — 150 Or")
    .icon(itemStack)              // optional, nullable
    .level(ToastLevel.WARNING)    // INFO, SUCCESS, WARNING, ERROR
    .animation(ToastAnimation.SLIDE_RIGHT)  // SLIDE_UP/DOWN/LEFT/RIGHT, FADE
    .duration(5000)               // ms, 0 = permanent (manual dismiss only)
    .dismissOnClick(true)         // click anywhere on toast to close
    .build();
```

**ToastLevel** determines the accent color (left bar or border):
- `INFO` — gold (theme.accent)
- `SUCCESS` — green (theme.success)
- `WARNING` — orange (theme.warning)
- `ERROR` — red (theme.danger)

**Layout** (approximate):
```
┌──┬──────────────────────┐
│▌ │ [icon]  Title         │
│▌ │         Message text   │
└──┴──────────────────────┘
```
- Left color bar (4px) = ToastLevel color
- Icon: 16×16, optional. Can be an ItemStack or null.
- Title: bold (theme.textWhite)
- Message: theme.textLight, max 2 lines, trimmed with ellipsis

### ToastAnimation

Animations controlled by the developer when creating a toast (not user-configurable):
- `SLIDE_RIGHT` — enters from right edge, exits to right
- `SLIDE_LEFT` — enters from left edge, exits to left
- `SLIDE_UP` — enters from top edge, exits to top
- `SLIDE_DOWN` — enters from bottom, exits to bottom
- `FADE` — alpha fade in/out

Animation duration: ~300ms, ease-out curve.

### EcoToastManager

Client-side singleton managing toast display:
- `show(EcoToast)` — enqueue a toast
- FIFO queue, max 3 visible simultaneously, rest waits
- Renders via NeoForge `RenderGuiLayerEvent.Post` — on top of everything (HUD, open GUIs)
- Position: top-right corner, stacked vertically with 4px gap
- Each toast lifecycle: animation in → display (duration) → animation out → removed
- Click on toast dismisses it (if `dismissOnClick` is true)

---

## Part 2: auction-house — Notification System

### Settings Screen Access Changes

- Settings screen becomes accessible to **all players** (not just OP)
- Existing tabs (Général, etc.) are hidden for non-OP players
- New **"Notifications"** tab is visible to all players

### Notification Config — Client-Side File

Stored locally at `config/ecocraft_ah_notifications.json`:

```json
{
  "outbid":           "BOTH",
  "auction_won":      "BOTH",
  "auction_lost":     "TOAST",
  "sale_completed":   "BOTH",
  "listing_expired":  "CHAT"
}
```

Values per event: `CHAT`, `TOAST`, `BOTH`, `NONE`.
Default: all `BOTH` on first use.

### Notification Event Types

| Event Type | When Triggered | Toast Level | Default |
|---|---|---|---|
| `outbid` | Another player outbids you | WARNING | BOTH |
| `auction_won` | You win an auction (highest bidder at expiry) | SUCCESS | BOTH |
| `auction_lost` | Auction you bid on ends, you're not the winner | ERROR | BOTH |
| `sale_completed` | Your listing (buyout or auction) is sold | SUCCESS | BOTH |
| `listing_expired` | Your listing expires unsold | WARNING | BOTH |

### Notifications Tab UI

One row per event type, using EcoGrid layout:

| Label (col-6) | Dropdown (col-4) |
|---|---|
| Surenchéri | `Chat / Toast / Les deux / Désactivé` |
| Enchère remportée | `Chat / Toast / Les deux / Désactivé` |
| Enchère perdue | `Chat / Toast / Les deux / Désactivé` |
| Vente réalisée | `Chat / Toast / Les deux / Désactivé` |
| Annonce expirée | `Chat / Toast / Les deux / Désactivé` |

Auto-save on each dropdown change (no save button needed).

### NotificationManager (Client-Side)

- Receives `AHNotificationPayload` from server
- Reads local config for the event type
- Dispatches to:
  - Chat: `player.displayClientMessage(Component)`
  - Toast: `EcoToastManager.show(toast)` with appropriate level, icon, and text
  - Both or none depending on config

### Network Payload

Single generic payload from server to client:

```java
record AHNotificationPayload(
    String eventType,     // "outbid", "auction_won", etc.
    String itemName,
    String playerName,    // the outbidder, the buyer, etc.
    long amount,
    String currencyId
) implements CustomPacketPayload
```

### Server-Side Trigger Points

Notifications are sent from `AuctionService`:
- `placeBid()` → send `outbid` to previous bidder (if online)
- `completedAuctionSale()` → send `auction_won` to winner, `sale_completed` to seller
- `expireListings()` with no bids → send `listing_expired` to seller
- `expireListings()` with bids → send `auction_won` to winner, `auction_lost` to other bidders, `sale_completed` to seller

For offline players: notifications are lost (they'll see the parcels/ledger entries when they log in). No queuing.

### Scope: Only Passive Events

Action confirmations (bid placed, listing created, etc.) remain as direct `AHActionResultPayload` chat responses. The notification system handles only passive events that happen asynchronously.

---

## Part 3: Bid History UI

### Detail View — Right Panel Addition

When a selected listing is type AUCTION, add below existing info:

**"Dernières enchères" section:**
- Shows the 3 most recent bids directly in the panel
- Format per line: `PlayerName — 150 Or` + relative time ("il y a 2h")
- Sorted by amount descending (highest first)
- If no bids: "Aucune enchère" in textDim
- **"Voir tout (X)" button** — opens EcoDialog with full history
  - Hidden if ≤ 3 bids (already fully visible)

### EcoDialog — Full Bid History

- Title: "Historique des enchères — ItemName"
- EcoRepeater listing all bids
- Format per row: `#1 PlayerName — 150 Or — 29/03/2026 14:30`
- Highest bid highlighted with accent color (gold)

### Network

**Enriched existing payload:** `ListingDetailResponsePayload` gains a `List<BidEntry> recentBids` field (top 3 bids), sent automatically when loading detail view for an auction listing.

**New payload for full history:**
```java
record RequestBidHistoryPayload(String listingId) implements CustomPacketPayload
record BidHistoryResponsePayload(String listingId, List<BidEntry> bids) implements CustomPacketPayload
record BidEntry(String bidderName, long amount, long timestamp)
```

`RequestBidHistoryPayload` is sent when clicking "Voir tout". Server responds with all bids for that listing.

---

## Cross-Cutting: Internationalization

All user-facing strings MUST use the existing i18n system (`Component.translatable()` with keys in FR/EN/ES lang files). This includes:
- Toast titles and messages (e.g., `ecocraft_ah.notification.outbid.title`)
- Chat notification messages
- Notification tab labels and dropdown options
- Bid history UI labels ("Dernières enchères", "Voir tout", "Aucune enchère", etc.)
- Dialog titles and row formatting

No hardcoded French strings anywhere — everything goes through lang files.
