# Multi-AH Phase A: Data Model + Storage — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the data layer for multiple auction houses — AHInstance record, ah_instances table, ah_id on existing tables, and updated storage queries.

**Architecture:** New `AHInstance` record represents an AH. Database migration 4 creates `ah_instances` table and adds `ah_id` column to listings/parcels/price_history. `AuctionStorageProvider` gets CRUD methods for AH instances and all existing queries gain an optional `ahId` parameter. A "default" AH is auto-created on first run.

**Tech Stack:** Java 21, SQLite, DatabaseMigrator, existing AuctionStorageProvider patterns.

---

### Task 1: Create AHInstance record

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/data/AHInstance.java`

- [ ] **Step 1: Create AHInstance.java**

```java
package net.ecocraft.ah.data;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

/**
 * Represents an Auction House instance with its own configuration.
 */
public record AHInstance(
        String id,          // UUID
        String slug,        // URL-friendly name for commands
        String name,        // Display name
        int saleRate,       // Sale tax rate 0-100 (percentage)
        int depositRate,    // Deposit rate 0-100 (percentage)
        List<Integer> durations  // Available listing durations in hours
) {
    /** Default AH config values. */
    public static final int DEFAULT_SALE_RATE = 5;
    public static final int DEFAULT_DEPOSIT_RATE = 2;
    public static final List<Integer> DEFAULT_DURATIONS = List.of(12, 24, 48);
    public static final String DEFAULT_NAME = "Hôtel des Ventes";

    /** Creates a new AH instance with a generated UUID and slug. */
    public static AHInstance create(String name) {
        return new AHInstance(
                UUID.randomUUID().toString(),
                slugify(name),
                name,
                DEFAULT_SALE_RATE,
                DEFAULT_DEPOSIT_RATE,
                DEFAULT_DURATIONS
        );
    }

    /** Creates the default AH instance. */
    public static AHInstance createDefault() {
        return new AHInstance(
                UUID.randomUUID().toString(),
                "default",
                DEFAULT_NAME,
                DEFAULT_SALE_RATE,
                DEFAULT_DEPOSIT_RATE,
                DEFAULT_DURATIONS
        );
    }

    /** Returns a copy with updated config. */
    public AHInstance withConfig(String name, int saleRate, int depositRate, List<Integer> durations) {
        return new AHInstance(id, slugify(name), name, saleRate, depositRate, durations);
    }

    /** Generates a URL-friendly slug from a display name. */
    public static String slugify(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        return normalized
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :auction-house:compileJava`

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/data/AHInstance.java
git commit -m "feat: add AHInstance record for multi-AH data model"
```

---

### Task 2: Database migration 4 — ah_instances table + ah_id columns

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`

- [ ] **Step 1: Add migration 4**

After the existing `migrator.addMigration(3, ...)` block, add:

```java
            migrator.addMigration(4, "Multi-AH: add ah_instances table and ah_id columns", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    // Create AH instances table
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_instances (
                            id           TEXT PRIMARY KEY,
                            slug         TEXT NOT NULL UNIQUE,
                            name         TEXT NOT NULL,
                            sale_rate    INTEGER NOT NULL DEFAULT 5,
                            deposit_rate INTEGER NOT NULL DEFAULT 2,
                            durations    TEXT NOT NULL DEFAULT '[12,24,48]'
                        )
                    """);

                    // Insert default AH if not exists
                    // Use a deterministic UUID for the default AH so migration is idempotent
                    String defaultId = "00000000-0000-0000-0000-000000000001";
                    stmt.execute("INSERT OR IGNORE INTO ah_instances (id, slug, name, sale_rate, deposit_rate, durations) " +
                            "VALUES ('" + defaultId + "', 'default', 'Hôtel des Ventes', 5, 2, '[12,24,48]')");

                    // Add ah_id to existing tables
                    stmt.execute("ALTER TABLE ah_listings ADD COLUMN ah_id TEXT NOT NULL DEFAULT '" + defaultId + "'");
                    stmt.execute("ALTER TABLE ah_parcels ADD COLUMN ah_id TEXT DEFAULT '" + defaultId + "'");
                    stmt.execute("ALTER TABLE ah_price_history ADD COLUMN ah_id TEXT NOT NULL DEFAULT '" + defaultId + "'");

                    // Indexes
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_ah ON ah_listings(ah_id, status)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_parcels_ah ON ah_parcels(ah_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_price_history_ah ON ah_price_history(ah_id)");
                }
            });
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :auction-house:compileJava`

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java
git commit -m "feat: add migration 4 for multi-AH tables and columns"
```

---

### Task 3: AH Instance CRUD in AuctionStorageProvider

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`

- [ ] **Step 1: Add AH instance CRUD methods**

Add these methods to `AuctionStorageProvider`. Place them after the `initialize()`/`shutdown()` methods and before the listing methods:

```java
    // -------------------------------------------------------------------------
    // AH Instances
    // -------------------------------------------------------------------------

    /** Returns all AH instances. */
    public List<AHInstance> getAllAHInstances() {
        List<AHInstance> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ah_instances ORDER BY slug ASC")) {
            while (rs.next()) {
                results.add(mapAHInstance(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get AH instances", e);
        }
        return results;
    }

    /** Returns an AH instance by ID, or null. */
    public AHInstance getAHInstance(String ahId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM ah_instances WHERE id = ?")) {
            ps.setString(1, ahId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAHInstance(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get AH instance", e);
        }
        return null;
    }

    /** Returns an AH instance by slug, or null. */
    public AHInstance getAHInstanceBySlug(String slug) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM ah_instances WHERE slug = ?")) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAHInstance(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get AH instance by slug", e);
        }
        return null;
    }

    /** Returns the default AH instance. */
    public AHInstance getDefaultAHInstance() {
        return getAHInstanceBySlug("default");
    }

    /** Creates a new AH instance. */
    public void createAHInstance(AHInstance ah) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ah_instances (id, slug, name, sale_rate, deposit_rate, durations) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, ah.id());
            ps.setString(2, ah.slug());
            ps.setString(3, ah.name());
            ps.setInt(4, ah.saleRate());
            ps.setInt(5, ah.depositRate());
            ps.setString(6, durationsToJson(ah.durations()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create AH instance", e);
        }
    }

    /** Updates an existing AH instance. */
    public void updateAHInstance(AHInstance ah) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_instances SET slug = ?, name = ?, sale_rate = ?, deposit_rate = ?, durations = ? WHERE id = ?")) {
            ps.setString(1, ah.slug());
            ps.setString(2, ah.name());
            ps.setInt(3, ah.saleRate());
            ps.setInt(4, ah.depositRate());
            ps.setString(5, durationsToJson(ah.durations()));
            ps.setString(6, ah.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update AH instance", e);
        }
    }

    /** Deletes an AH instance. Does NOT handle listings — caller must handle that first. */
    public void deleteAHInstance(String ahId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ah_instances WHERE id = ?")) {
            ps.setString(1, ahId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete AH instance", e);
        }
    }

    /** Moves all active listings from one AH to another. */
    public int transferListings(String fromAhId, String toAhId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_listings SET ah_id = ? WHERE ah_id = ? AND status = 'ACTIVE'")) {
            ps.setString(1, toAhId);
            ps.setString(2, fromAhId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to transfer listings", e);
        }
    }

    /** Deletes all active listings for an AH (no parcels created). */
    public int deleteActiveListings(String ahId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ah_listings WHERE ah_id = ? AND status = 'ACTIVE'")) {
            ps.setString(1, ahId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete listings for AH", e);
        }
    }

    private AHInstance mapAHInstance(ResultSet rs) throws SQLException {
        return new AHInstance(
                rs.getString("id"),
                rs.getString("slug"),
                rs.getString("name"),
                rs.getInt("sale_rate"),
                rs.getInt("deposit_rate"),
                parseDurationsJson(rs.getString("durations"))
        );
    }

    private String durationsToJson(List<Integer> durations) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < durations.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(durations.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private List<Integer> parseDurationsJson(String json) {
        List<Integer> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return AHInstance.DEFAULT_DURATIONS;
        String inner = json.replaceAll("[\\[\\]\\s]", "");
        if (inner.isEmpty()) return AHInstance.DEFAULT_DURATIONS;
        for (String s : inner.split(",")) {
            try { result.add(Integer.parseInt(s.trim())); }
            catch (NumberFormatException ignored) {}
        }
        return result.isEmpty() ? AHInstance.DEFAULT_DURATIONS : result;
    }
```

Add import at top: `import net.ecocraft.ah.data.AHInstance;`

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :auction-house:compileJava`

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java
git commit -m "feat: add AH instance CRUD methods to storage provider"
```

---

### Task 4: Add ahId parameter to scoped queries

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`

- [ ] **Step 1: Update getListingsGroupedByItem**

Add `String ahId` as first parameter. Update the SQL WHERE clause to include `AND ah_id = ?`. Add the parameter binding.

Current signature: `public List<ListingGroupSummary> getListingsGroupedByItem(String query, String category, int page, int pageSize)`

New signature: `public List<ListingGroupSummary> getListingsGroupedByItem(String ahId, String query, String category, int page, int pageSize)`

In the SQL builder, after `WHERE status = 'ACTIVE'`, add ` AND ah_id = ?`. Bind `ahId` as the first parameter.

- [ ] **Step 2: Update getListingsForItem and getListingsForItemFiltered**

Add `String ahId` as first parameter. Add `AND ah_id = ?` to WHERE clause.

- [ ] **Step 3: Update createListing**

The INSERT already has `ah_id` column from migration (DEFAULT value). But we need to explicitly set it. The `AuctionListing` record doesn't have `ahId` yet — we'll pass it separately.

Add an overload or modify `createListing` to accept `String ahId` parameter. In the INSERT, add `ah_id` column and bind the value.

- [ ] **Step 4: Update getBestPrice**

Add `String ahId` as first parameter. Add `AND ah_id = ?` to both queries (fingerprint match and itemId fallback).

- [ ] **Step 5: Update logPriceHistory**

Add `String ahId` parameter. Include `ah_id` in the INSERT.

- [ ] **Step 6: Update createParcel**

Add `String ahId` parameter (nullable for cross-AH parcels like listing fees). Include `ah_id` in the INSERT.

- [ ] **Step 7: Keep cross-AH queries unchanged**

These methods stay as-is (no ahId filter) — they return data across all AHs:
- `getPlayerListings`
- `getPlayerPurchases`
- `getPlayerBids`
- `getPlayerLedger`
- `getUncollectedParcels`
- `countUncollectedParcels`
- `getPriceHistory` (for stats)
- `getPlayerStats`

BUT: update the `mapListing` method to read `ah_id` from ResultSet if the column exists. Add `ahId` field to `AuctionListing` record.

- [ ] **Step 8: Add ahId to AuctionListing record**

Add `@Nullable String ahId` field to `AuctionListing` record (after `itemFingerprint`). Update `mapListing` to read it. Update `withStatus`/`withBid` to pass it through. Update all `new AuctionListing(...)` calls in tests and other code to add `null` as the last parameter.

- [ ] **Step 9: Fix all compilation errors**

Search for all callers of the modified methods in `AuctionService`, `ServerPayloadHandler`, `AHTestCommand`, and tests. Update them to pass `ahId` (use the default AH ID for now — `"00000000-0000-0000-0000-000000000001"`).

Add a constant in `AHInstance`:
```java
    public static final String DEFAULT_ID = "00000000-0000-0000-0000-000000000001";
```

- [ ] **Step 10: Verify full build**

Run: `./gradlew clean build`
All tests must pass.

- [ ] **Step 11: Deploy and commit**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
git add -A
git commit -m "feat: add ahId to all scoped storage queries for multi-AH support"
```

---

### Testing Instructions

1. Start Minecraft — migration 4 should run (check logs for "Multi-AH" migration)
2. Open AH — everything should work as before (using default AH)
3. `/ah populate 10` — listings created with default AH ID
4. Check database: `ah_instances` table has one "default" row, all listings have `ah_id` set

Phase B (gui-lib Dropdown/CycleButton) and Phase C (Service + Network) will build on this foundation.
