# Multi Auction House — Design Spec

## Overview

Support multiple independent Auction Houses on a single server. Each AH has its own identity, configuration, and listings. NPCs and terminals are linked to a specific AH. Players interact with one AH at a time (Buy/Sell tabs), but their history (Mes enchères, Ledger) aggregates all AHs.

## Data Model

### ah_instances table (new)

| Column | Type | Description |
|--------|------|-------------|
| `id` | TEXT PK | UUID, unique identifier |
| `slug` | TEXT UNIQUE | URL-friendly name for commands (auto-generated from name) |
| `name` | TEXT | Display name (e.g. "Hôtel des Ventes", "Faction Rouge") |
| `sale_rate` | INTEGER | Sale tax rate 0-100 (percentage) |
| `deposit_rate` | INTEGER | Deposit rate 0-100 (percentage) |
| `durations` | TEXT | JSON array of available durations in hours (e.g. "[12,24,48]") |

A "default" AH is created automatically on first startup with the values from AHConfig defaults.

### Existing tables — add ah_id

- `ah_listings`: add `ah_id TEXT NOT NULL DEFAULT 'default'` (FK → ah_instances.id)
- `ah_parcels`: add `ah_id TEXT` (nullable — legacy parcels and cross-AH parcels like listing fees)
- `ah_price_history`: add `ah_id TEXT NOT NULL DEFAULT 'default'`

Migration backfills existing rows with the default AH's UUID.

### Slug generation

Auto-generated from the display name: lowercase, spaces → hyphens, accents stripped, special chars removed. Regenerated on rename. Must be unique.

Example: "Faction Rouge" → "faction-rouge", "Hôtel des Ventes" → "hotel-des-ventes"

## Settings Screen

### Layout

Two-column layout replacing the current single-column settings:

**Left sidebar (accordion-style):**
- **Général** — always first, general/NPC config
- **[AH Name 1]** — one entry per AH (e.g. "Default")
- **[AH Name 2]** — additional AHs
- **[+ Créer un AH]** — button at bottom of sidebar

**Right panel:** content of the selected sidebar entry

### Onglet "Général"

- **Pseudo du skin** (TextInput) — only if opened via NPC, otherwise hidden
- **Hôtel des ventes** (Dropdown) — select which AH this NPC/terminal is linked to

### Onglet per-AH

- **Nom de l'AH** (TextInput) — editable, updates sidebar label in real-time
- **Taxe sur les ventes** (Slider 0-100%)
- **Dépôt de mise en vente** (Slider 0-100%)
- **Durées de listing** (Repeater of NumberInput, hours)
- **Supprimer cet AH** (Button, red, danger) — hidden for default AH

### Create AH

Click "+ Créer un AH" → new AH created with default mod values (not copied from default AH). New sidebar entry appears. Config values: sale_rate=5, deposit_rate=2, durations=[12,24,48], name="Nouvel AH".

### Delete AH

Click "Supprimer" → confirmation Dialog with 3 options:
- **"Rendre les items aux joueurs"** — create parcels for all active listings
- **"Supprimer toutes les listings"** — delete active listings (no parcels)
- **"Transférer à l'AH par défaut"** — move all listings to default AH

Cannot delete the default AH. NPCs/terminals linked to deleted AH revert to default.

### Rename AH

Changing the name in the TextInput updates:
- The sidebar label in real-time
- The slug (regenerated)
- The `ah_instances` row

## New GUI Components (gui-lib)

### Dropdown (Select)

A clickable field that opens a list of options below it. Single selection. Shows current value.

- API: `Dropdown(font, x, y, width, height, theme)`
- `.options(List<String>)` — set available options
- `.selectedIndex(int)` / `.getSelectedIndex()`
- `.responder(Consumer<Integer>)` — callback on selection change
- Click → opens option list below, click option → selects, click outside → closes
- Uses scissor clipping if list is longer than available space

### CycleButton

A button that cycles through values on click. Shows current value.

- API: `CycleButton(font, x, y, width, height, theme)`
- `.options(List<String>)`
- `.selectedIndex(int)` / `.getSelectedIndex()`
- `.responder(Consumer<Integer>)`
- Left click → next, right click → previous

## Service Layer

`AuctionService` methods become AH-aware:

**Scoped to one AH (receives ahId):**
- `createListing(..., ahId)` — listing belongs to this AH
- `searchListings(ahId, query, category, page, pageSize)` — browse one AH
- `getListingDetail(ahId, itemId)` — detail within one AH
- `buyListing(ahId, buyerUuid, listingId, qty)` — buy from this AH
- `getBestPrice(ahId, fingerprint, itemId)` — price suggestion within this AH

**Cross-AH (no ahId, returns all):**
- `getMyListings(playerUuid, ...)` — all AHs, result includes ah_id
- `getMyPurchases(playerUuid, ...)` — all AHs
- `getLedger(playerUuid, ...)` — all AHs, result includes ah_id
- `getPlayerStats(playerUuid, ...)` — aggregated across all AHs

**AH management:**
- `createAH(name)` → AH instance with generated UUID + slug
- `deleteAH(ahId, DeleteMode)` — enum: RETURN_ITEMS, DELETE_LISTINGS, TRANSFER_TO_DEFAULT
- `updateAH(ahId, name, saleRate, depositRate, durations)`
- `getAH(ahId)` / `getAllAHs()` / `getAHBySlug(slug)`

## Network

### New payloads

- `AHContextPayload(String ahId, String ahName)` (S→C) — sent on AH open, tells client which AH they're viewing
- `AHInstancesPayload(List<AHInstance>)` (S→C) — sent to admin, list of all AHs for settings screen
- `CreateAHPayload(String name)` (C→S) — admin creates AH
- `DeleteAHPayload(String ahId, String deleteMode)` (C→S) — admin deletes AH
- `UpdateAHPayload(String ahId, String name, int saleRate, int depositRate, List<Integer> durations)` (C→S) — replaces current UpdateAHSettingsPayload

### Modified payloads

All scoped payloads add `String ahId`:
- `RequestListingsPayload(ahId, search, category, page, pageSize)`
- `RequestListingDetailPayload(ahId, itemId, enchantFilters)`
- `CreateListingPayload(ahId, listingType, price, durationHours, slotIndex)`
- `BuyListingPayload(ahId, listingId, quantity)`
- `PlaceBidPayload(ahId, listingId, amount)`
- `RequestBestPricePayload(ahId, fingerprint, itemId)`

Cross-AH payloads stay unchanged:
- `RequestMyListingsPayload(subTab)` — returns all AHs
- `RequestLedgerPayload(period, typeFilter, page)` — returns all AHs

### Modified response payloads

- `ListingsResponsePayload` — no change (already scoped by request)
- `MyListingsResponsePayload.MyListingEntry` — add `String ahId, String ahName`
- `LedgerResponsePayload.LedgerEntry` — add `String ahId, String ahName`

## UI Adaptations

### Buy/Sell tabs

Scoped to the current AH. The AH name is displayed in the header (next to the tab bar or below it). All requests include `ahId`.

### Mes enchères tab

Shows entries from ALL AHs. If the player has entries on more than one AH, an extra column "AH" appears showing the AH name, and a FilterTags row allows filtering by AH. If only one AH has entries, no extra column or filter.

### Ledger tab

Same behavior as Mes enchères — extra column + filter only when multi-AH entries exist.

## Entity/Block Binding

### AuctioneerEntity

- New NBT field `linkedAhId` (String, UUID of the AH, default = default AH UUID)
- When interacting, the server reads `linkedAhId` and sends `AHContextPayload`
- Configurable in the "Général" tab of settings via Dropdown

### AuctionTerminalBlock

- Block entity with `linkedAhId` field
- Same binding mechanism as NPC

### Fallback

If `linkedAhId` points to a deleted AH, falls back to default AH.

## Commands

- `/ah` — open default AH GUI
- `/ah browse <slug>` — open specific AH GUI
- `/ah sell <price>` — sell on default AH (or last used?)
- `/ah admin create <name>` — create new AH
- `/ah admin delete <slug>` — delete AH (with confirmation in chat)
- `/ah admin list` — list all AHs with slug, name, listing count
- `/ah admin rename <slug> <new name>` — rename AH

## Migration Strategy

Database migration (version 4):
1. Create `ah_instances` table
2. Insert default AH with UUID, slug="default", name="Hôtel des Ventes", current config values
3. Add `ah_id` column to `ah_listings`, `ah_parcels`, `ah_price_history` with DEFAULT = default AH UUID
4. Create indexes on `ah_id`

AHConfig TOML is replaced by per-AH config in the database. The TOML file becomes minimal (just the default AH UUID reference).

## Decomposition into implementation specs

This is too large for a single plan. Implementation order:

1. **Phase A: Data + Storage** — ah_instances table, migration, AuctionStorageProvider multi-AH queries
2. **Phase B: gui-lib components** — Dropdown, CycleButton
3. **Phase C: Service + Network** — AuctionService multi-AH, payload changes, handlers
4. **Phase D: Settings screen refactor** — sidebar accordion, per-AH config, create/delete/rename
5. **Phase E: UI adaptations** — AH context in Buy/Sell, conditional columns in Mes enchères/Ledger
6. **Phase F: Commands** — /ah admin create/delete/list/rename, /ah browse

Each phase produces working software and can be tested independently.
