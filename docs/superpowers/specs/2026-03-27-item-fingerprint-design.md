# ItemFingerprint — Design Spec

## Overview

A utility that generates a deterministic fingerprint string from the significant components of an `ItemStack`. Two items with the same fingerprint are considered "identical" for pricing and grouping purposes. Used by the best price suggestion in the Sell tab and potentially by browse grouping.

## Fingerprint Components

**Included (affect item identity/value):**
- `itemId` — registry name (e.g. `minecraft:diamond_sword`)
- Enchantments — sorted alphabetically by enchantment name, with level (e.g. `minecraft:sharpness:5`)
- Stored enchantments — for enchanted books, same format as enchantments
- Potion effects — type + amplifier + duration
- Custom name — if the item was renamed at an anvil

**Excluded (cosmetic or variable):**
- Durability (damage value)
- Stack count
- Repair cost

## Fingerprint Format

Deterministic string built from sorted components:

```
minecraft:diamond_sword|e:minecraft:fire_aspect:2,minecraft:sharpness:5
minecraft:enchanted_book|se:minecraft:mending:1
minecraft:potion|p:minecraft:strength:1:3600
minecraft:diamond_sword|n:Custom Name|e:minecraft:sharpness:5
minecraft:bread
```

Components are separated by `|`, sub-components by `,`. If no special components, the fingerprint is just the itemId.

## Implementation

### ItemFingerprint.java

Location: `auction-house/src/main/java/net/ecocraft/ah/data/ItemFingerprint.java`

Static utility class:
- `String compute(ItemStack stack)` — generates the fingerprint from a live ItemStack
- `String computeFromNbt(String itemId, String nbt, RegistryAccess registries)` — generates fingerprint from serialized NBT (for server-side recomputation)

### Database Migration

Add column `item_fingerprint TEXT` to `ah_listings` table via `DatabaseMigrator`. Add an index on `(item_fingerprint, status)` for fast lookups.

Migration steps:
1. `ALTER TABLE ah_listings ADD COLUMN item_fingerprint TEXT DEFAULT NULL`
2. `CREATE INDEX idx_listings_fingerprint ON ah_listings(item_fingerprint, status)`
3. Backfill existing listings: iterate all listings with `item_nbt`, compute fingerprint, update. Listings without NBT get `item_fingerprint = item_id`.

### Storage Changes

- `AuctionStorageProvider.getBestPrice(String fingerprint)` — query changes from `WHERE item_id = ?` to `WHERE item_fingerprint = ?`
- `AuctionStorageProvider.createListing(...)` — store the fingerprint at creation time

### Network Changes

- `RequestBestPricePayload` — send `fingerprint` instead of `itemId`
- `BestPriceResponsePayload` — no change (still returns bestPrice)
- Client computes fingerprint from the selected ItemStack before sending

### Sell Tab Changes

- `onInventorySlotClicked` — compute fingerprint from selected ItemStack, send in `RequestBestPricePayload`

### Fallback

If `getBestPrice(fingerprint)` returns -1 (no exact match), fallback to `getBestPrice(itemId only)` to suggest a base price for items of the same type but different components.

## Testing

Unit test `ItemFingerprint.compute()`:
- Plain item → just itemId
- Enchanted sword → itemId + sorted enchantments
- Enchanted book → itemId + sorted stored enchantments
- Potion → itemId + effects
- Renamed item → itemId + custom name
- Same enchantments in different order → same fingerprint
- Different durability → same fingerprint
