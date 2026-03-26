package net.ecocraft.core.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseSchema {

    public static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS balances (
                    player_uuid TEXT NOT NULL,
                    currency_id TEXT NOT NULL,
                    amount TEXT NOT NULL DEFAULT '0',
                    PRIMARY KEY (player_uuid, currency_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id TEXT PRIMARY KEY,
                    from_uuid TEXT,
                    to_uuid TEXT,
                    amount TEXT NOT NULL,
                    currency_id TEXT NOT NULL,
                    type TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_tx_from ON transactions(from_uuid, timestamp DESC)
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_tx_to ON transactions(to_uuid, timestamp DESC)
            """);
        }
    }
}
