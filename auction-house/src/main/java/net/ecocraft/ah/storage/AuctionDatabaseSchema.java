package net.ecocraft.ah.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and maintains the auction-house database schema.
 *
 * <p>All monetary amounts are stored as INTEGER (long) in the smallest currency unit.
 * This avoids floating-point precision issues and matches the service layer contract.</p>
 */
public class AuctionDatabaseSchema {

    public static void createTables(Connection conn) throws SQLException {
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
    }
}
