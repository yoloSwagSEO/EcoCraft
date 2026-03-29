package net.ecocraft.ah.storage;

import net.ecocraft.ah.data.*;
import net.ecocraft.core.storage.DatabaseMigrator;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JDBC-backed storage provider for the auction house.
 *
 * <p>Uses a dedicated SQLite database file separate from economy-core's database.
 * All monetary values are stored and returned as {@code long} (smallest currency unit).</p>
 */
public class AuctionStorageProvider {

    private final Path dbPath;
    private Connection connection;

    public AuctionStorageProvider(Path dbPath) {
        this.dbPath = dbPath;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            connection.setAutoCommit(true);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            DatabaseMigrator migrator = new DatabaseMigrator("ecocraft-auction-house");

            migrator.addMigration(1, "Initial schema - listings, bids, parcels, price_history", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    // --- Listings ---
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_listings (
                            id             TEXT PRIMARY KEY,
                            seller_uuid    TEXT NOT NULL,
                            seller_name    TEXT NOT NULL,
                            item_id        TEXT NOT NULL,
                            item_name      TEXT NOT NULL,
                            item_nbt       TEXT,
                            quantity       INTEGER NOT NULL DEFAULT 1,
                            listing_type   TEXT NOT NULL,
                            buyout_price   INTEGER NOT NULL DEFAULT 0,
                            starting_bid   INTEGER NOT NULL DEFAULT 0,
                            current_bid    INTEGER NOT NULL DEFAULT 0,
                            current_bidder TEXT,
                            currency_id    TEXT NOT NULL,
                            category       TEXT NOT NULL,
                            expires_at     INTEGER NOT NULL,
                            status         TEXT NOT NULL DEFAULT 'ACTIVE',
                            tax_amount     INTEGER NOT NULL DEFAULT 0,
                            created_at     INTEGER NOT NULL
                        )
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_listings_status_item
                            ON ah_listings(status, item_id)
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_listings_seller
                            ON ah_listings(seller_uuid, status)
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_listings_category
                            ON ah_listings(category, status)
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_listings_expires
                            ON ah_listings(expires_at, status)
                    """);

                    // --- Bids ---
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_bids (
                            id          TEXT PRIMARY KEY,
                            listing_id  TEXT NOT NULL,
                            bidder_uuid TEXT NOT NULL,
                            bidder_name TEXT NOT NULL,
                            amount      INTEGER NOT NULL,
                            timestamp   INTEGER NOT NULL,
                            FOREIGN KEY (listing_id) REFERENCES ah_listings(id)
                        )
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_bids_listing
                            ON ah_bids(listing_id, amount DESC)
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_bids_bidder
                            ON ah_bids(bidder_uuid)
                    """);

                    // --- Parcels ---
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_parcels (
                            id             TEXT PRIMARY KEY,
                            recipient_uuid TEXT NOT NULL,
                            item_id        TEXT,
                            item_name      TEXT,
                            item_nbt       TEXT,
                            quantity       INTEGER NOT NULL DEFAULT 0,
                            amount         INTEGER NOT NULL DEFAULT 0,
                            currency_id    TEXT,
                            source         TEXT NOT NULL,
                            created_at     INTEGER NOT NULL,
                            collected      INTEGER NOT NULL DEFAULT 0
                        )
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_parcels_recipient
                            ON ah_parcels(recipient_uuid, collected)
                    """);

                    // --- Price History ---
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_price_history (
                            id          TEXT PRIMARY KEY,
                            item_id     TEXT NOT NULL,
                            currency_id TEXT NOT NULL,
                            sale_price  INTEGER NOT NULL,
                            quantity    INTEGER NOT NULL DEFAULT 1,
                            sold_at     INTEGER NOT NULL
                        )
                    """);
                    stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_price_history_item
                            ON ah_price_history(item_id, currency_id, sold_at DESC)
                    """);
                }
            });

            migrator.addMigration(2, "Add enchantment index table", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_listing_enchantments (
                            listing_id       TEXT NOT NULL,
                            enchantment_name TEXT NOT NULL,
                            enchantment_level INTEGER NOT NULL,
                            display_name     TEXT NOT NULL,
                            PRIMARY KEY (listing_id, enchantment_name),
                            FOREIGN KEY (listing_id) REFERENCES ah_listings(id)
                        )
                    """);
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_le_enchant ON ah_listing_enchantments(enchantment_name)");
                }
            });

            migrator.addMigration(3, "Add item_fingerprint column to ah_listings", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ah_listings ADD COLUMN item_fingerprint TEXT DEFAULT NULL");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_fingerprint ON ah_listings(item_fingerprint, status)");
                }
            });

            migrator.addMigration(4, "Multi-AH: add ah_instances table and ah_id columns", conn -> {
                try (Statement stmt = conn.createStatement()) {
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

                    String defaultId = "00000000-0000-0000-0000-000000000001";
                    stmt.execute("INSERT OR IGNORE INTO ah_instances (id, slug, name, sale_rate, deposit_rate, durations) " +
                            "VALUES ('" + defaultId + "', 'default', 'Hôtel des Ventes', 5, 2, '[12,24,48]')");

                    stmt.execute("ALTER TABLE ah_listings ADD COLUMN ah_id TEXT NOT NULL DEFAULT '" + defaultId + "'");
                    stmt.execute("ALTER TABLE ah_parcels ADD COLUMN ah_id TEXT DEFAULT '" + defaultId + "'");
                    stmt.execute("ALTER TABLE ah_price_history ADD COLUMN ah_id TEXT NOT NULL DEFAULT '" + defaultId + "'");

                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_listings_ah ON ah_listings(ah_id, status)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_parcels_ah ON ah_parcels(ah_id)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_price_history_ah ON ah_price_history(ah_id)");
                }
            });

            migrator.addMigration(5, "Add allow_buyout and allow_auction columns to ah_instances", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN allow_buyout INTEGER NOT NULL DEFAULT 1");
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN allow_auction INTEGER NOT NULL DEFAULT 1");
                }
            });

            migrator.addMigration(6, "Add tax_recipient column to ah_instances", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN tax_recipient TEXT NOT NULL DEFAULT ''");
                }
            });

            migrator.addMigration(7, "Add pending_notifications table for offline players", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS ah_pending_notifications (
                            id          TEXT PRIMARY KEY,
                            player_uuid TEXT NOT NULL,
                            event_type  TEXT NOT NULL,
                            item_name   TEXT NOT NULL DEFAULT '',
                            player_name TEXT NOT NULL DEFAULT '',
                            amount      INTEGER NOT NULL DEFAULT 0,
                            currency_id TEXT NOT NULL DEFAULT '',
                            created_at  INTEGER NOT NULL
                        )
                    """);
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_pending_notif_player ON ah_pending_notifications(player_uuid)");
                }
            });

            migrator.addMigration(8, "Add override_perm_tax column to ah_instances", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN override_perm_tax INTEGER NOT NULL DEFAULT 0");
                }
            });

            migrator.addMigration(9, "Add delivery config columns to ah_instances", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN delivery_mode TEXT NOT NULL DEFAULT 'DIRECT'");
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN delivery_delay_purchase INTEGER NOT NULL DEFAULT 0");
                    stmt.execute("ALTER TABLE ah_instances ADD COLUMN delivery_delay_expired INTEGER NOT NULL DEFAULT 60");
                }
            });

            migrator.migrate(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize auction-house database", e);
        }
    }

    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close auction-house database", e);
        }
    }

    // -------------------------------------------------------------------------
    // AH Instances
    // -------------------------------------------------------------------------

    public List<AHInstance> getAllAHInstances() {
        List<AHInstance> results = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM ah_instances ORDER BY slug ASC")) {
            while (rs.next()) results.add(mapAHInstance(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get AH instances", e);
        }
        return results;
    }

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

    public AHInstance getDefaultAHInstance() {
        return getAHInstance(AHInstance.DEFAULT_ID);
    }

    public void createAHInstance(AHInstance ah) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ah_instances (id, slug, name, sale_rate, deposit_rate, durations, allow_buyout, allow_auction, tax_recipient, override_perm_tax, delivery_mode, delivery_delay_purchase, delivery_delay_expired) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, ah.id());
            ps.setString(2, ah.slug());
            ps.setString(3, ah.name());
            ps.setInt(4, ah.saleRate());
            ps.setInt(5, ah.depositRate());
            ps.setString(6, durationsToJson(ah.durations()));
            ps.setInt(7, ah.allowBuyout() ? 1 : 0);
            ps.setInt(8, ah.allowAuction() ? 1 : 0);
            ps.setString(9, ah.taxRecipient() != null ? ah.taxRecipient() : "");
            ps.setInt(10, ah.overridePermTax() ? 1 : 0);
            ps.setString(11, ah.deliveryMode() != null ? ah.deliveryMode() : "DIRECT");
            ps.setInt(12, ah.deliveryDelayPurchase());
            ps.setInt(13, ah.deliveryDelayExpired());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create AH instance", e);
        }
    }

    public void updateAHInstance(AHInstance ah) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_instances SET slug = ?, name = ?, sale_rate = ?, deposit_rate = ?, durations = ?, allow_buyout = ?, allow_auction = ?, tax_recipient = ?, override_perm_tax = ?, delivery_mode = ?, delivery_delay_purchase = ?, delivery_delay_expired = ? WHERE id = ?")) {
            ps.setString(1, ah.slug());
            ps.setString(2, ah.name());
            ps.setInt(3, ah.saleRate());
            ps.setInt(4, ah.depositRate());
            ps.setString(5, durationsToJson(ah.durations()));
            ps.setInt(6, ah.allowBuyout() ? 1 : 0);
            ps.setInt(7, ah.allowAuction() ? 1 : 0);
            ps.setString(8, ah.taxRecipient() != null ? ah.taxRecipient() : "");
            ps.setInt(9, ah.overridePermTax() ? 1 : 0);
            ps.setString(10, ah.deliveryMode() != null ? ah.deliveryMode() : "DIRECT");
            ps.setInt(11, ah.deliveryDelayPurchase());
            ps.setInt(12, ah.deliveryDelayExpired());
            ps.setString(13, ah.id());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update AH instance", e);
        }
    }

    public void deleteAHInstance(String ahId) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ah_instances WHERE id = ?")) {
            ps.setString(1, ahId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete AH instance", e);
        }
    }

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

    public int deleteActiveListings(String ahId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ah_listings WHERE ah_id = ? AND status = 'ACTIVE'")) {
            ps.setString(1, ahId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete listings for AH", e);
        }
    }

    public int countActiveListings(String ahId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM ah_listings WHERE ah_id = ? AND status = 'ACTIVE'")) {
            ps.setString(1, ahId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count listings", e);
        }
    }

    public List<AuctionListing> getActiveListingsForAH(String ahId) {
        List<AuctionListing> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_listings WHERE ah_id = ? AND status = 'ACTIVE'")) {
            ps.setString(1, ahId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get active listings for AH", e);
        }
        return results;
    }

    private AHInstance mapAHInstance(ResultSet rs) throws SQLException {
        boolean allowBuyout = true;
        boolean allowAuction = true;
        try { allowBuyout = rs.getInt("allow_buyout") != 0; } catch (SQLException ignored) {}
        try { allowAuction = rs.getInt("allow_auction") != 0; } catch (SQLException ignored) {}
        String taxRecipient = "";
        try { taxRecipient = rs.getString("tax_recipient"); if (taxRecipient == null) taxRecipient = ""; } catch (SQLException ignored) {}
        boolean overridePermTax = false;
        try { overridePermTax = rs.getInt("override_perm_tax") != 0; } catch (SQLException ignored) {}
        String deliveryMode = AHInstance.DEFAULT_DELIVERY_MODE;
        try { String dm = rs.getString("delivery_mode"); if (dm != null && !dm.isEmpty()) deliveryMode = dm; } catch (SQLException ignored) {}
        int deliveryDelayPurchase = AHInstance.DEFAULT_DELIVERY_DELAY_PURCHASE;
        try { deliveryDelayPurchase = rs.getInt("delivery_delay_purchase"); } catch (SQLException ignored) {}
        int deliveryDelayExpired = AHInstance.DEFAULT_DELIVERY_DELAY_EXPIRED;
        try { deliveryDelayExpired = rs.getInt("delivery_delay_expired"); } catch (SQLException ignored) {}
        return new AHInstance(
                rs.getString("id"), rs.getString("slug"), rs.getString("name"),
                rs.getInt("sale_rate"), rs.getInt("deposit_rate"),
                parseDurationsJson(rs.getString("durations")),
                allowBuyout, allowAuction, taxRecipient, overridePermTax,
                deliveryMode, deliveryDelayPurchase, deliveryDelayExpired);
    }

    private String durationsToJson(List<Integer> durations) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < durations.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(durations.get(i));
        }
        return sb.append("]").toString();
    }

    private List<Integer> parseDurationsJson(String json) {
        List<Integer> result = new ArrayList<>();
        if (json == null || json.isEmpty()) return AHInstance.DEFAULT_DURATIONS;
        String inner = json.replaceAll("[\\[\\]\\s]", "");
        if (inner.isEmpty()) return AHInstance.DEFAULT_DURATIONS;
        for (String s : inner.split(",")) {
            try { result.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
        }
        return result.isEmpty() ? AHInstance.DEFAULT_DURATIONS : result;
    }

    // -------------------------------------------------------------------------
    // Listings — write
    // -------------------------------------------------------------------------

    public void createListing(AuctionListing listing) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ah_listings
                (id, seller_uuid, seller_name, item_id, item_name, item_nbt, quantity,
                 listing_type, buyout_price, starting_bid, current_bid, current_bidder,
                 currency_id, category, expires_at, status, tax_amount, created_at, item_fingerprint, ah_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            int i = 1;
            ps.setString(i++, listing.id());
            ps.setString(i++, listing.sellerUuid().toString());
            ps.setString(i++, listing.sellerName());
            ps.setString(i++, listing.itemId());
            ps.setString(i++, listing.itemName());
            ps.setString(i++, listing.itemNbt());
            ps.setInt(i++, listing.quantity());
            ps.setString(i++, listing.listingType().name());
            ps.setLong(i++, listing.buyoutPrice());
            ps.setLong(i++, listing.startingBid());
            ps.setLong(i++, listing.currentBid());
            ps.setString(i++, listing.currentBidderUuid() != null ? listing.currentBidderUuid().toString() : null);
            ps.setString(i++, listing.currencyId());
            ps.setString(i++, listing.category().name());
            ps.setLong(i++, listing.expiresAt());
            ps.setString(i++, listing.status().name());
            ps.setLong(i++, listing.taxAmount());
            ps.setLong(i++, listing.createdAt());
            ps.setString(i++, listing.itemFingerprint());
            ps.setString(i, listing.ahId() != null ? listing.ahId() : AHInstance.DEFAULT_ID);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create listing", e);
        }
    }

    public void updateListingStatus(String listingId, ListingStatus status) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_listings SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, listingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update listing status", e);
        }
    }

    public void updateListingBid(String listingId, long newBid, UUID bidderUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_listings SET current_bid = ?, current_bidder = ? WHERE id = ?")) {
            ps.setLong(1, newBid);
            ps.setString(2, bidderUuid.toString());
            ps.setString(3, listingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update listing bid", e);
        }
    }

    /**
     * Marks a listing as SOLD.
     */
    public void completeSale(String listingId) {
        updateListingStatus(listingId, ListingStatus.SOLD);
    }

    /**
     * Marks a listing as CANCELLED.
     */
    public void cancelListing(String listingId) {
        updateListingStatus(listingId, ListingStatus.CANCELLED);
    }

    /**
     * Updates the quantity of a listing (for partial purchases).
     */
    public void updateListingQuantity(String listingId, int newQuantity) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_listings SET quantity = ? WHERE id = ?")) {
            ps.setInt(1, newQuantity);
            ps.setString(2, listingId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update listing quantity", e);
        }
    }

    /**
     * Returns the lowest buyout price for ACTIVE listings matching the fingerprint,
     * falling back to itemId if no fingerprint match is found. Returns -1 if none found.
     */
    public long getBestPrice(String ahId, String fingerprint, String itemId) {
        // Try exact fingerprint match first
        long price = queryMinPrice("SELECT MIN(buyout_price) FROM ah_listings WHERE ah_id = ? AND item_fingerprint = ? AND status = 'ACTIVE' AND buyout_price > 0", ahId, fingerprint);
        if (price > 0) return price;
        // Fallback: match by itemId only
        return queryMinPrice("SELECT MIN(buyout_price) FROM ah_listings WHERE ah_id = ? AND item_id = ? AND status = 'ACTIVE' AND buyout_price > 0", ahId, itemId);
    }

    private long queryMinPrice(String sql, String ahId, String param) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, ahId);
            ps.setString(2, param);
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

    /**
     * Bulk-expires ACTIVE listings whose expiry timestamp is in the past.
     *
     * @return the IDs of listings that were expired
     */
    public List<String> expireOldListings(long nowMs) {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM ah_listings WHERE status = 'ACTIVE' AND expires_at <= ?")) {
            select.setLong(1, nowMs);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) ids.add(rs.getString("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query expirable listings", e);
        }

        if (!ids.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE ah_listings SET status = 'EXPIRED' WHERE id IN (" + placeholders + ")")) {
                for (int i = 0; i < ids.size(); i++) update.setString(i + 1, ids.get(i));
                update.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to expire listings", e);
            }
        }
        return ids;
    }

    // -------------------------------------------------------------------------
    // Listings — read
    // -------------------------------------------------------------------------

    @Nullable
    public AuctionListing getListingById(String listingId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_listings WHERE id = ?")) {
            ps.setString(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapListing(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get listing by id", e);
        }
        return null;
    }

    /**
     * Returns ACTIVE listings, optionally filtered by search text and/or category,
     * ordered by buyout_price ASC (best deal first), with pagination.
     */
    public List<AuctionListing> getActiveListings(
            @Nullable String search,
            @Nullable ItemCategory category,
            int page,
            int pageSize) {

        var sql = new StringBuilder("SELECT * FROM ah_listings WHERE status = 'ACTIVE'");
        var params = new ArrayList<Object>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (item_name LIKE ? OR item_id LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category.name());
        }

        sql.append(" ORDER BY buyout_price ASC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((long) page * pageSize);

        return queryListings(sql.toString(), params);
    }

    /**
     * Returns the count of ACTIVE listings matching the filter (for pagination).
     */
    public long countActiveListings(@Nullable String search, @Nullable ItemCategory category) {
        var sql = new StringBuilder("SELECT COUNT(*) FROM ah_listings WHERE status = 'ACTIVE'");
        var params = new ArrayList<Object>();

        if (search != null && !search.isBlank()) {
            sql.append(" AND (item_name LIKE ? OR item_id LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category.name());
        }

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) setParam(ps, i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count active listings", e);
        }
    }

    /**
     * Returns ACTIVE listings grouped by item_id for the browse view.
     * Each row contains: item_id, item_name, best_price, listing_count, total_quantity.
     */
    public List<ListingGroupSummary> getListingsGroupedByItem(
            String ahId,
            @Nullable String search,
            @Nullable ItemCategory category,
            int page,
            int pageSize) {

        var sql = new StringBuilder("""
                SELECT item_id, item_name, item_nbt,
                       MIN(buyout_price) AS best_price,
                       COUNT(*)         AS listing_count,
                       SUM(quantity)    AS total_quantity,
                       category
                FROM ah_listings
                WHERE status = 'ACTIVE' AND buyout_price > 0 AND ah_id = ?
            """);
        var params = new ArrayList<Object>();
        params.add(ahId);

        if (search != null && !search.isBlank()) {
            sql.append(" AND (item_name LIKE ? OR item_id LIKE ?)");
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category.name());
        }

        sql.append(" GROUP BY item_id, item_name ORDER BY best_price ASC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((long) page * pageSize);

        List<ListingGroupSummary> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) setParam(ps, i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new ListingGroupSummary(
                            rs.getString("item_id"),
                            rs.getString("item_name"),
                            rs.getString("item_nbt"),
                            rs.getLong("best_price"),
                            rs.getInt("listing_count"),
                            rs.getInt("total_quantity"),
                            ItemCategory.valueOf(rs.getString("category"))
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get grouped listings", e);
        }
        return results;
    }

    /**
     * Returns all ACTIVE listings for a specific item (detail view).
     */
    public List<AuctionListing> getListingsForItem(String ahId, String itemId) {
        String sql = "SELECT * FROM ah_listings WHERE status = 'ACTIVE' AND ah_id = ? AND item_id = ? ORDER BY buyout_price ASC";
        return queryListings(sql, List.of(ahId, itemId));
    }

    /**
     * Returns all listings (any status) created by a given player, newest first.
     */
    public List<AuctionListing> getPlayerListings(UUID sellerUuid, int page, int pageSize) {
        String sql = "SELECT * FROM ah_listings WHERE seller_uuid = ? ORDER BY created_at DESC LIMIT ? OFFSET ?";
        return queryListings(sql, List.of(sellerUuid.toString(), pageSize, (long) page * pageSize));
    }

    /**
     * Returns listings where the given player was the buyer (status = SOLD and current_bidder = player
     * for auctions, or listings bought via buyout — tracked via parcels with HDV_PURCHASE source).
     * For simplicity at storage level, returns listings where current_bidder = player and status = SOLD.
     */
    public List<AuctionListing> getPlayerPurchases(UUID buyerUuid, int page, int pageSize) {
        String sql = """
                SELECT * FROM ah_listings
                WHERE status = 'SOLD' AND current_bidder = ?
                ORDER BY created_at DESC LIMIT ? OFFSET ?
            """;
        return queryListings(sql, List.of(buyerUuid.toString(), pageSize, (long) page * pageSize));
    }

    /**
     * Returns ACTIVE auction listings that the player has bid on.
     */
    public List<AuctionListing> getPlayerBids(UUID bidderUuid) {
        String sql = """
                SELECT DISTINCT l.* FROM ah_listings l
                JOIN ah_bids b ON b.listing_id = l.id
                WHERE b.bidder_uuid = ? AND l.status = 'ACTIVE'
                ORDER BY l.expires_at ASC
            """;
        return queryListings(sql, List.of(bidderUuid.toString()));
    }

    // -------------------------------------------------------------------------
    // Bids
    // -------------------------------------------------------------------------

    public void placeBid(AuctionBid bid) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ah_bids (id, listing_id, bidder_uuid, bidder_name, amount, timestamp)
                VALUES (?,?,?,?,?,?)
            """)) {
            ps.setString(1, bid.id());
            ps.setString(2, bid.listingId());
            ps.setString(3, bid.bidderUuid().toString());
            ps.setString(4, bid.bidderName());
            ps.setLong(5, bid.amount());
            ps.setLong(6, bid.timestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to place bid", e);
        }
    }

    @Nullable
    public AuctionBid getHighestBid(String listingId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_bids WHERE listing_id = ? ORDER BY amount DESC LIMIT 1")) {
            ps.setString(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapBid(rs);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get highest bid", e);
        }
        return null;
    }

    public List<AuctionBid> getBidsForListing(String listingId) {
        List<AuctionBid> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_bids WHERE listing_id = ? ORDER BY amount DESC")) {
            ps.setString(1, listingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapBid(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get bids for listing", e);
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Parcels
    // -------------------------------------------------------------------------

    public void createParcel(AuctionParcel parcel) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ah_parcels
                (id, recipient_uuid, item_id, item_name, item_nbt, quantity, amount,
                 currency_id, source, created_at, collected, ah_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """)) {
            int i = 1;
            ps.setString(i++, parcel.id());
            ps.setString(i++, parcel.recipientUuid().toString());
            ps.setString(i++, parcel.itemId());
            ps.setString(i++, parcel.itemName());
            ps.setString(i++, parcel.itemNbt());
            ps.setInt(i++, parcel.quantity());
            ps.setLong(i++, parcel.amount());
            ps.setString(i++, parcel.currencyId());
            ps.setString(i++, parcel.source().name());
            ps.setLong(i++, parcel.createdAt());
            ps.setInt(i++, parcel.collected() ? 1 : 0);
            ps.setString(i, parcel.ahId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create parcel", e);
        }
    }

    public List<AuctionParcel> getUncollectedParcels(UUID recipientUuid) {
        List<AuctionParcel> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_parcels WHERE recipient_uuid = ? AND collected = 0 ORDER BY created_at ASC")) {
            ps.setString(1, recipientUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapParcel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get uncollected parcels", e);
        }
        return results;
    }

    public int countUncollectedParcels(UUID recipientUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM ah_parcels WHERE recipient_uuid = ? AND collected = 0")) {
            ps.setString(1, recipientUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count uncollected parcels", e);
        }
    }

    public void markParcelCollected(String parcelId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ah_parcels SET collected = 1 WHERE id = ?")) {
            ps.setString(1, parcelId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to mark parcel collected", e);
        }
    }

    // -------------------------------------------------------------------------
    // Price History
    // -------------------------------------------------------------------------

    public void logPriceHistory(String ahId, String id, String itemId, String currencyId, long salePrice, int quantity, long soldAt) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ah_price_history (id, item_id, currency_id, sale_price, quantity, sold_at, ah_id)
                VALUES (?,?,?,?,?,?,?)
            """)) {
            ps.setString(1, id);
            ps.setString(2, itemId);
            ps.setString(3, currencyId);
            ps.setLong(4, salePrice);
            ps.setInt(5, quantity);
            ps.setLong(6, soldAt);
            ps.setString(7, ahId != null ? ahId : AHInstance.DEFAULT_ID);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log price history", e);
        }
    }

    /**
     * Returns price statistics for an item over the past {@code windowMs} milliseconds.
     */
    @Nullable
    public PriceStats getPriceHistory(String itemId, String currencyId, long windowMs) {
        long since = System.currentTimeMillis() - windowMs;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT AVG(sale_price) AS avg_price,
                       MIN(sale_price) AS min_price,
                       MAX(sale_price) AS max_price,
                       SUM(quantity)   AS volume
                FROM ah_price_history
                WHERE item_id = ? AND currency_id = ? AND sold_at >= ?
            """)) {
            ps.setString(1, itemId);
            ps.setString(2, currencyId);
            ps.setLong(3, since);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong("volume") > 0) {
                    return new PriceStats(
                            rs.getLong("avg_price"),
                            rs.getLong("min_price"),
                            rs.getLong("max_price"),
                            rs.getInt("volume")
                    );
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get price history", e);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Player stats & ledger
    // -------------------------------------------------------------------------

    /**
     * Returns aggregate sales/purchase/tax statistics for a player within a time window.
     */
    public PlayerStats getPlayerStats(UUID playerUuid, long sinceMs) {
        // Total sales revenue (parcels of type HDV_SALE sent to this player)
        long totalSales = sumParcelAmounts(playerUuid, ParcelSource.HDV_SALE, sinceMs);
        // Total purchases (parcels of type HDV_PURCHASE sent to this player — currency value)
        long totalPurchases = sumParcelAmounts(playerUuid, ParcelSource.HDV_PURCHASE, sinceMs);

        // Tax paid is tracked via tax_amount on the seller's listings that completed
        long taxesPaid = 0;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(SUM(tax_amount), 0)
                FROM ah_listings
                WHERE seller_uuid = ? AND status = 'SOLD' AND created_at >= ?
            """)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, sinceMs);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) taxesPaid = rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get taxes paid", e);
        }

        // Also include deposit fees (HDV_LISTING_FEE) in taxes total
        long depositFees = sumParcelAmounts(playerUuid, ParcelSource.HDV_LISTING_FEE, sinceMs);
        taxesPaid += depositFees;

        return new PlayerStats(totalSales, totalPurchases, taxesPaid);
    }

    private long sumParcelAmounts(UUID playerUuid, ParcelSource source, long sinceMs) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT COALESCE(SUM(amount), 0) FROM ah_parcels
                WHERE recipient_uuid = ? AND source = ? AND created_at >= ?
            """)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, source.name());
            ps.setLong(3, sinceMs);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to sum parcel amounts", e);
        }
    }

    /**
     * Returns a paginated ledger of parcels for a player (most recent first).
     */
    public List<AuctionParcel> getPlayerLedger(UUID playerUuid, @Nullable ParcelSource source,
                                                long sinceMs, int page, int pageSize) {
        var sql = new StringBuilder(
                "SELECT * FROM ah_parcels WHERE recipient_uuid = ? AND created_at >= ?");
        var params = new ArrayList<Object>();
        params.add(playerUuid.toString());
        params.add(sinceMs);

        if (source != null) {
            sql.append(" AND source = ?");
            params.add(source.name());
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        params.add(pageSize);
        params.add((long) page * pageSize);

        List<AuctionParcel> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) setParam(ps, i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapParcel(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get player ledger", e);
        }
        return results;
    }

    // -------------------------------------------------------------------------
    // Enchantment Index
    // -------------------------------------------------------------------------

    /**
     * Indexes the enchantments of a listing for server-side filtering.
     */
    public void indexEnchantments(String listingId, List<EnchantmentEntry> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return;
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR IGNORE INTO ah_listing_enchantments
                (listing_id, enchantment_name, enchantment_level, display_name)
                VALUES (?,?,?,?)
            """)) {
            for (EnchantmentEntry e : enchantments) {
                ps.setString(1, listingId);
                ps.setString(2, e.name());
                ps.setInt(3, e.level());
                ps.setString(4, e.displayName());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to index enchantments for listing " + listingId, e);
        }
    }

    /**
     * Updates the item_fingerprint for a single listing.
     */
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

    /**
     * Returns all ACTIVE listings that have no item_fingerprint yet.
     * Used during server startup to backfill fingerprints for listings created before migration v3.
     */
    public List<AuctionListing> getListingsWithoutFingerprint() {
        String sql = """
                SELECT * FROM ah_listings
                WHERE status = 'ACTIVE'
                AND item_fingerprint IS NULL
            """;
        return queryListings(sql, List.of());
    }

    /**
     * Returns all ACTIVE listings that have item_nbt but no entries in ah_listing_enchantments.
     * Used during server startup to reindex enchantments for listings created before migration v2.
     */
    public List<AuctionListing> getListingsWithoutEnchantmentIndex() {
        String sql = """
                SELECT l.* FROM ah_listings l
                WHERE l.status = 'ACTIVE'
                AND l.item_nbt IS NOT NULL
                AND l.item_nbt != ''
                AND l.id NOT IN (SELECT DISTINCT listing_id FROM ah_listing_enchantments)
            """;
        return queryListings(sql, List.of());
    }

    /**
     * Returns all unique enchantment display names for ACTIVE listings of the given item type.
     */
    public List<String> getAvailableEnchantments(String itemId) {
        List<String> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DISTINCT le.display_name
                FROM ah_listing_enchantments le
                JOIN ah_listings l ON l.id = le.listing_id
                WHERE l.item_id = ? AND l.status = 'ACTIVE'
                ORDER BY le.display_name
            """)) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(rs.getString("display_name"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get available enchantments for item " + itemId, e);
        }
        return results;
    }

    /**
     * Returns ACTIVE listings for a specific item, optionally filtered by enchantment display names.
     * If enchantmentFilters is empty, returns all listings (same as {@link #getListingsForItem(String)}).
     * Uses OR logic: listings that have ANY of the specified enchantments are included.
     */
    public List<AuctionListing> getListingsForItemFiltered(String ahId, String itemId, Set<String> enchantmentFilters, int page, int pageSize) {
        if (enchantmentFilters == null || enchantmentFilters.isEmpty()) {
            return getListingsForItem(ahId, itemId);
        }

        String placeholders = enchantmentFilters.stream()
                .map(f -> "?")
                .collect(Collectors.joining(","));

        String sql = """
                SELECT DISTINCT l.* FROM ah_listings l
                JOIN ah_listing_enchantments le ON le.listing_id = l.id
                WHERE l.status = 'ACTIVE' AND l.ah_id = ? AND l.item_id = ?
                  AND le.display_name IN (%s)
                ORDER BY l.buyout_price ASC
                LIMIT ? OFFSET ?
            """.formatted(placeholders);

        List<Object> params = new ArrayList<>();
        params.add(ahId);
        params.add(itemId);
        params.addAll(enchantmentFilters);
        params.add(pageSize);
        params.add((long) page * pageSize);

        return queryListings(sql, params);
    }

    // -------------------------------------------------------------------------
    // Pending Notifications (for offline players)
    // -------------------------------------------------------------------------

    public void createPendingNotification(UUID playerUuid, String eventType, String itemName,
                                           String playerName, long amount, String currencyId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO ah_pending_notifications (id, player_uuid, event_type, item_name, player_name, amount, currency_id, created_at) VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, playerUuid.toString());
            ps.setString(3, eventType);
            ps.setString(4, itemName != null ? itemName : "");
            ps.setString(5, playerName != null ? playerName : "");
            ps.setLong(6, amount);
            ps.setString(7, currencyId != null ? currencyId : "");
            ps.setLong(8, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create pending notification", e);
        }
    }

    public List<PendingNotification> getPendingNotifications(UUID playerUuid) {
        List<PendingNotification> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ah_pending_notifications WHERE player_uuid = ? ORDER BY created_at ASC")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(new PendingNotification(
                            rs.getString("id"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("event_type"),
                            rs.getString("item_name"),
                            rs.getString("player_name"),
                            rs.getLong("amount"),
                            rs.getString("currency_id"),
                            rs.getLong("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get pending notifications", e);
        }
        return results;
    }

    public void deletePendingNotifications(UUID playerUuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ah_pending_notifications WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete pending notifications", e);
        }
    }

    public record PendingNotification(
            String id, UUID playerUuid, String eventType, String itemName,
            String playerName, long amount, String currencyId, long createdAt) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<AuctionListing> queryListings(String sql, List<Object> params) {
        List<AuctionListing> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) setParam(ps, i + 1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(mapListing(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query listings", e);
        }
        return results;
    }

    private void setParam(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof String s) ps.setString(index, s);
        else if (value instanceof Long l) ps.setLong(index, l);
        else if (value instanceof Integer i) ps.setInt(index, i);
        else ps.setObject(index, value);
    }

    private AuctionListing mapListing(ResultSet rs) throws SQLException {
        String bidderStr = rs.getString("current_bidder");
        String ahId = null;
        try { ahId = rs.getString("ah_id"); } catch (SQLException ignored) {}
        return new AuctionListing(
                rs.getString("id"),
                UUID.fromString(rs.getString("seller_uuid")),
                rs.getString("seller_name"),
                rs.getString("item_id"),
                rs.getString("item_name"),
                rs.getString("item_nbt"),
                rs.getInt("quantity"),
                ListingType.valueOf(rs.getString("listing_type")),
                rs.getLong("buyout_price"),
                rs.getLong("starting_bid"),
                rs.getLong("current_bid"),
                bidderStr != null ? UUID.fromString(bidderStr) : null,
                rs.getString("currency_id"),
                ItemCategory.valueOf(rs.getString("category")),
                rs.getLong("expires_at"),
                ListingStatus.valueOf(rs.getString("status")),
                rs.getLong("tax_amount"),
                rs.getLong("created_at"),
                rs.getString("item_fingerprint"),
                ahId
        );
    }

    private AuctionBid mapBid(ResultSet rs) throws SQLException {
        return new AuctionBid(
                rs.getString("id"),
                rs.getString("listing_id"),
                UUID.fromString(rs.getString("bidder_uuid")),
                rs.getString("bidder_name"),
                rs.getLong("amount"),
                rs.getLong("timestamp")
        );
    }

    private AuctionParcel mapParcel(ResultSet rs) throws SQLException {
        String sourceStr = rs.getString("source");
        String ahId = null;
        try { ahId = rs.getString("ah_id"); } catch (SQLException ignored) {}
        return new AuctionParcel(
                rs.getString("id"),
                UUID.fromString(rs.getString("recipient_uuid")),
                rs.getString("item_id"),
                rs.getString("item_name"),
                rs.getString("item_nbt"),
                rs.getInt("quantity"),
                rs.getLong("amount"),
                rs.getString("currency_id"),
                ParcelSource.valueOf(sourceStr),
                rs.getLong("created_at"),
                rs.getInt("collected") == 1,
                ahId
        );
    }

    // -------------------------------------------------------------------------
    // Value objects returned by queries
    // -------------------------------------------------------------------------

    public record ListingGroupSummary(
            String itemId,
            String itemName,
            @Nullable String itemNbt,
            long bestPrice,
            int listingCount,
            int totalQuantity,
            ItemCategory category
    ) {}

    public record PriceStats(
            long avgPrice,
            long minPrice,
            long maxPrice,
            int volume
    ) {}

    public record PlayerStats(
            long totalSalesRevenue,
            long totalPurchases,
            long taxesPaid
    ) {}
}
