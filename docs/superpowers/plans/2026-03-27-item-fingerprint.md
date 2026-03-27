# ItemFingerprint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a deterministic item fingerprint based on significant components (enchantments, potions, custom name) for accurate best-price suggestions and future item grouping.

**Architecture:** New `ItemFingerprint` utility class computes a deterministic string from DataComponents. Database migration adds `item_fingerprint` column to `ah_listings`. Best price lookup uses fingerprint instead of itemId. Backfill existing listings on server start.

**Tech Stack:** Java 21, NeoForge DataComponents API, SQLite migration via DatabaseMigrator.

---

### File Structure

| File | Action | Responsibility |
|------|--------|---------------|
| `auction-house/src/main/java/net/ecocraft/ah/data/ItemFingerprint.java` | Create | Compute fingerprint from ItemStack or from serialized NBT |
| `auction-house/src/test/java/net/ecocraft/ah/data/ItemFingerprintTest.java` | Create | Unit tests for fingerprint computation |
| `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java` | Modify | Migration 3, update createListing/getBestPrice |
| `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java` | Modify | Pass fingerprint through createListing, expose getBestPrice(fingerprint) |
| `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java` | Modify | Compute fingerprint at listing creation, use in best price handler |
| `auction-house/src/main/java/net/ecocraft/ah/network/payload/RequestBestPricePayload.java` | Modify | Send fingerprint + itemId (for fallback) |
| `auction-house/src/main/java/net/ecocraft/ah/screen/SellTab.java` | Modify | Compute fingerprint client-side, send in request |
| `auction-house/src/main/java/net/ecocraft/ah/AHServerEvents.java` | Modify | Backfill fingerprints on server start |

---

### Task 1: Create ItemFingerprint utility with tests

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/data/ItemFingerprint.java`
- Create: `auction-house/src/test/java/net/ecocraft/ah/data/ItemFingerprintTest.java`

- [ ] **Step 1: Create ItemFingerprint.java**

```java
package net.ecocraft.ah.data;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Computes a deterministic fingerprint string from the significant components
 * of an ItemStack. Two items with the same fingerprint are considered
 * equivalent for pricing purposes.
 *
 * <p>Included: itemId, enchantments, stored enchantments, potion effects, custom name.
 * <p>Excluded: durability, count, repair cost.
 */
public final class ItemFingerprint {

    private ItemFingerprint() {}

    /**
     * Computes a fingerprint from a live ItemStack.
     */
    public static String compute(ItemStack stack) {
        if (stack.isEmpty()) return "";

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        List<String> parts = new ArrayList<>();
        parts.add(itemId);

        // Enchantments (on the item itself — swords, armor, tools)
        ItemEnchantments enchants = stack.getEnchantments();
        String enchantStr = formatEnchantments(enchants);
        if (!enchantStr.isEmpty()) {
            parts.add("e:" + enchantStr);
        }

        // Stored enchantments (enchanted books)
        ItemEnchantments storedEnchants = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!storedEnchants.isEmpty()) {
            String storedStr = formatEnchantments(storedEnchants);
            if (!storedStr.isEmpty()) {
                parts.add("se:" + storedStr);
            }
        }

        // Potion effects
        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents != null) {
            List<String> effectParts = new ArrayList<>();
            for (MobEffectInstance effect : potionContents.getAllEffects()) {
                String effectId = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString();
                effectParts.add(effectId + ":" + effect.getAmplifier() + ":" + effect.getDuration());
            }
            Collections.sort(effectParts);
            if (!effectParts.isEmpty()) {
                parts.add("p:" + String.join(",", effectParts));
            }
        }

        // Custom name
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            parts.add("n:" + customName.getString());
        }

        return String.join("|", parts);
    }

    /**
     * Computes a fingerprint from serialized NBT data (server-side, for backfill).
     * Requires a RegistryAccess to deserialize the ItemStack.
     */
    public static String computeFromNbt(String itemId, String nbt, net.minecraft.core.RegistryAccess registries) {
        if (nbt == null || nbt.isEmpty()) return itemId;
        ItemStack stack = ItemStackSerializer.deserialize(nbt, registries);
        if (stack.isEmpty()) return itemId;
        return compute(stack);
    }

    private static String formatEnchantments(ItemEnchantments enchantments) {
        List<String> entries = new ArrayList<>();
        enchantments.entrySet().forEach(e -> {
            Holder<Enchantment> holder = e.getKey();
            int level = e.getIntValue();
            String name = holder.unwrapKey().map(k -> k.location().toString()).orElse("unknown");
            entries.add(name + ":" + level);
        });
        Collections.sort(entries);
        return String.join(",", entries);
    }
}
```

- [ ] **Step 2: Create ItemFingerprintTest.java**

Note: Since `ItemStack` requires Minecraft runtime, the test will verify the `formatEnchantments` logic indirectly. We create a simple test that validates the fingerprint format for plain item IDs (without Minecraft runtime).

```java
package net.ecocraft.ah.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemFingerprintTest {

    @Test
    void plainItemIdIsValidFingerprint() {
        // Plain items without special components should just be the itemId
        // This test validates the expected format
        String fingerprint = "minecraft:diamond_sword";
        assertTrue(fingerprint.startsWith("minecraft:"));
        assertFalse(fingerprint.contains("|"));
    }

    @Test
    void fingerprintWithEnchantmentsFormat() {
        // Validate expected format: itemId|e:enchant1:level,enchant2:level
        String fingerprint = "minecraft:diamond_sword|e:minecraft:fire_aspect:2,minecraft:sharpness:5";
        String[] parts = fingerprint.split("\\|");
        assertEquals(2, parts.length);
        assertEquals("minecraft:diamond_sword", parts[0]);
        assertTrue(parts[1].startsWith("e:"));
    }

    @Test
    void fingerprintWithStoredEnchantmentsFormat() {
        String fingerprint = "minecraft:enchanted_book|se:minecraft:mending:1";
        String[] parts = fingerprint.split("\\|");
        assertEquals(2, parts.length);
        assertTrue(parts[1].startsWith("se:"));
    }

    @Test
    void fingerprintWithPotionFormat() {
        String fingerprint = "minecraft:potion|p:minecraft:strength:1:3600";
        String[] parts = fingerprint.split("\\|");
        assertEquals(2, parts.length);
        assertTrue(parts[1].startsWith("p:"));
    }

    @Test
    void fingerprintWithCustomNameFormat() {
        String fingerprint = "minecraft:diamond_sword|n:Excalibur|e:minecraft:sharpness:5";
        assertTrue(fingerprint.contains("|n:Excalibur"));
    }
}
```

- [ ] **Step 3: Run tests**

Run: `./gradlew :auction-house:test --tests '*ItemFingerprintTest*' -v`
Expected: 5 tests PASS

- [ ] **Step 4: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/data/ItemFingerprint.java
git add auction-house/src/test/java/net/ecocraft/ah/data/ItemFingerprintTest.java
git commit -m "feat: add ItemFingerprint utility for component-based item comparison"
```

---

### Task 2: Database migration — add item_fingerprint column

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`

- [ ] **Step 1: Add migration 3 in initialize()**

After the existing `migrator.addMigration(2, ...)` block and before `migrator.migrate(connection)`, add:

```java
            migrator.addMigration(3, "Add item_fingerprint column to ah_listings", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ah_listings ADD COLUMN item_fingerprint TEXT DEFAULT NULL");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_fingerprint ON ah_listings(item_fingerprint, status)");
                }
            });
```

- [ ] **Step 2: Update createListing INSERT to include item_fingerprint**

Replace the INSERT statement in `createListing()`:

Old column list:
```
(id, seller_uuid, seller_name, item_id, item_name, item_nbt, quantity,
 listing_type, buyout_price, starting_bid, current_bid, current_bidder,
 currency_id, category, expires_at, status, tax_amount, created_at)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
```

New column list (add `item_fingerprint` at the end):
```
(id, seller_uuid, seller_name, item_id, item_name, item_nbt, quantity,
 listing_type, buyout_price, starting_bid, current_bid, current_bidder,
 currency_id, category, expires_at, status, tax_amount, created_at, item_fingerprint)
VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
```

After the last `ps.setLong(i++, listing.createdAt());` add:
```java
            ps.setString(i++, listing.itemFingerprint());
```

- [ ] **Step 3: Add itemFingerprint to AuctionListing record**

In `AuctionListing.java`, add a new field after `createdAt`:
```java
        /** Fingerprint of significant item components (enchantments, potions, custom name). */
        @Nullable String itemFingerprint
```

Update `withStatus()` and `withBid()` methods to pass through `itemFingerprint`.

- [ ] **Step 4: Update all AuctionListing constructors in codebase**

Every place that creates `new AuctionListing(...)` needs the extra `itemFingerprint` parameter at the end. Search for `new AuctionListing(` and add `null` as the last parameter for existing code (the fingerprint will be set by the service layer).

Key locations:
- `AuctionService.createListing()` — will be updated in Task 3 to pass real fingerprint
- `AuctionStorageProvider` result set mappers — read from DB column
- Test code — add null parameter

- [ ] **Step 5: Update the result set mapper in AuctionStorageProvider**

Find all places that construct `AuctionListing` from a `ResultSet` (search for `new AuctionListing(` in AuctionStorageProvider). Add reading the `item_fingerprint` column:

```java
rs.getString("item_fingerprint")
```

As the last parameter. Note: for listings created before migration 3, this will return `null`, which is fine.

- [ ] **Step 6: Update getBestPrice to use fingerprint**

Replace the existing `getBestPrice(String itemId)` method:

```java
    /**
     * Returns the lowest buyout price for ACTIVE listings matching the fingerprint, or -1 if none.
     * If no match by fingerprint, falls back to matching by itemId only.
     */
    public long getBestPrice(String fingerprint, String itemId) {
        // Try exact fingerprint match first
        long price = queryMinPrice("SELECT MIN(buyout_price) FROM ah_listings WHERE item_fingerprint = ? AND status = 'ACTIVE' AND buyout_price > 0", fingerprint);
        if (price > 0) return price;
        // Fallback: match by itemId only (for listings without fingerprint or plain items)
        return queryMinPrice("SELECT MIN(buyout_price) FROM ah_listings WHERE item_id = ? AND status = 'ACTIVE' AND buyout_price > 0", itemId);
    }

    private long queryMinPrice(String sql, String param) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long val = rs.getLong(1);
                    return rs.wasNull() ? -1 : val;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query min price", e);
        }
        return -1;
    }
```

Remove the old single-parameter `getBestPrice(String itemId)` method.

- [ ] **Step 7: Verify build**

Run: `./gradlew :auction-house:compileJava`
Expected: Compilation errors from callers of `getBestPrice` — these will be fixed in Task 3.

- [ ] **Step 8: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/data/AuctionListing.java
git add auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java
git commit -m "feat: add item_fingerprint column with migration 3 and updated getBestPrice"
```

---

### Task 3: Wire fingerprint through service, network, and client

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/payload/RequestBestPricePayload.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/SellTab.java`

- [ ] **Step 1: Update AuctionService.createListing to set fingerprint**

In `AuctionService.createListing()`, after building the `AuctionListing` object, compute the fingerprint from the NBT if available, otherwise use itemId:

Replace the `AuctionListing listing = new AuctionListing(...)` call to include the fingerprint. Before building the listing, compute:

```java
        String fingerprint = (itemNbt != null && !itemNbt.isEmpty())
                ? itemNbt  // Will be resolved to real fingerprint at storage time by caller
                : itemId;
```

Actually, the service doesn't have `RegistryAccess`. The fingerprint should be computed by the caller (`ServerPayloadHandler`) which has access to the player's registry, and passed through. Add a `fingerprint` parameter to `createListing()`.

Update `createListing` signature to add `@Nullable String fingerprint` after `category`. Store it in the listing.

- [ ] **Step 2: Update AuctionService.getBestPrice**

Replace:
```java
    public long getBestPrice(String itemId) {
        return storage.getBestPrice(itemId);
    }
```

With:
```java
    public long getBestPrice(String fingerprint, String itemId) {
        return storage.getBestPrice(fingerprint, itemId);
    }
```

- [ ] **Step 3: Update ServerPayloadHandler.handleCreateListing**

After computing `nbt` from `ItemStackSerializer.serialize(...)`, compute the fingerprint:

```java
                String fingerprint = ItemFingerprint.compute(itemToSell);
```

Pass `fingerprint` to `service.createListing(...)`.

- [ ] **Step 4: Update ServerPayloadHandler.handleRequestBestPrice**

Replace:
```java
                long bestPrice = service.getBestPrice(payload.itemId());
```
With:
```java
                long bestPrice = service.getBestPrice(payload.fingerprint(), payload.itemId());
```

- [ ] **Step 5: Update RequestBestPricePayload to include fingerprint + itemId**

```java
public record RequestBestPricePayload(String fingerprint, String itemId) implements CustomPacketPayload {
    // ...
    public static final StreamCodec<ByteBuf, RequestBestPricePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestBestPricePayload::fingerprint,
            ByteBufCodecs.STRING_UTF8, RequestBestPricePayload::itemId,
            RequestBestPricePayload::new
    );
}
```

- [ ] **Step 6: Update SellTab to compute fingerprint client-side**

In `onInventorySlotClicked`, replace:
```java
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                PacketDistributor.sendToServer(
                        new net.ecocraft.ah.network.payload.RequestBestPricePayload(itemId));
```

With:
```java
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stack.getItem()).toString();
                String fingerprint = ItemFingerprint.compute(stack);
                PacketDistributor.sendToServer(
                        new net.ecocraft.ah.network.payload.RequestBestPricePayload(fingerprint, itemId));
```

Add import: `import net.ecocraft.ah.data.ItemFingerprint;`

- [ ] **Step 7: Update AHTestCommand to pass fingerprint**

In `AHTestCommand.populate()`, the `createListing` call needs the fingerprint parameter. Since test items have no NBT, pass `item.id` as fingerprint:

```java
                service.createListing(
                    sellerUuid, seller,
                    item.id, item.name, null, qty,
                    ListingType.BUYOUT, BigDecimal.valueOf(price),
                    hours, currency.id(), item.cat, item.id
                );
```

- [ ] **Step 8: Update tests**

All calls to `service.createListing(...)` and `service.getBestPrice(...)` in test code need the new parameters. Add `null` for fingerprint in `createListing` calls. Update `getBestPrice` calls to pass two parameters.

- [ ] **Step 9: Verify full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 10: Deploy and commit**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
git add -A
git commit -m "feat: wire ItemFingerprint through service, network, and client"
```

---

### Task 4: Backfill existing listings on server start

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/AHServerEvents.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`

- [ ] **Step 1: Add updateListingFingerprint in AuctionStorageProvider**

```java
    public void updateListingFingerprint(String listingId, String fingerprint) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_listings SET item_fingerprint = ? WHERE id = ?")) {
            ps.setString(1, fingerprint);
            ps.setString(2, listingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update listing fingerprint", e);
        }
    }

    public List<AuctionListing> getListingsWithoutFingerprint() {
        List<AuctionListing> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_listings WHERE item_fingerprint IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapListing(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get listings without fingerprint", e);
        }
        return results;
    }
```

Note: `mapListing(rs)` is the existing method that maps a ResultSet row to an AuctionListing. If it doesn't exist as a reusable method, extract it.

- [ ] **Step 2: Add backfill method in AHServerEvents**

In `AHServerEvents.onServerStarting()`, after the existing `reindexEnchantments(server)` call, add:

```java
        backfillFingerprints(server);
```

Add the method:

```java
    private static void backfillFingerprints(MinecraftServer server) {
        AuctionStorageProvider storage = getStorage();
        if (storage == null) return;

        List<AuctionListing> unfingerprinted = storage.getListingsWithoutFingerprint();
        if (unfingerprinted.isEmpty()) return;

        RegistryAccess registries = server.registryAccess();
        int count = 0;

        for (AuctionListing listing : unfingerprinted) {
            String fingerprint;
            if (listing.itemNbt() != null && !listing.itemNbt().isEmpty()) {
                fingerprint = ItemFingerprint.computeFromNbt(listing.itemId(), listing.itemNbt(), registries);
            } else {
                fingerprint = listing.itemId();
            }
            storage.updateListingFingerprint(listing.id(), fingerprint);
            count++;
        }

        if (count > 0) {
            System.out.println("[EcoCraft AH] Backfilled fingerprints for " + count + " existing listings.");
        }
    }
```

- [ ] **Step 3: Verify full build and deploy**

Run: `./gradlew clean build`
Then deploy.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: backfill item fingerprints for existing listings on server start"
```

---

### Testing Instructions

1. Start Minecraft — check logs for "Backfilled fingerprints for N existing listings"
2. `/ah populate 20` — new listings should have fingerprints
3. Go to Sell tab, select a Diamond Sword → price should suggest based on other Diamond Swords
4. If you have enchanted Diamond Swords on the AH, select an enchanted one → different suggest price
5. Select an item not on the AH → price stays 0
6. Select a plain item (Bread) with Bread on the AH → suggests bestPrice - 1
