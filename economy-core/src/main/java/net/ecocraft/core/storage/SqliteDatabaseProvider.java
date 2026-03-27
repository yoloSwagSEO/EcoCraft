package net.ecocraft.core.storage;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SqliteDatabaseProvider implements DatabaseProvider {

    private final Path dbPath;
    private Connection connection;

    public SqliteDatabaseProvider(Path dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void initialize() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            connection.setAutoCommit(true);
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            DatabaseMigrator migrator = new DatabaseMigrator("ecocraft-economy");
            migrator.addMigration(1, "Initial schema - balances and transactions tables", conn -> {
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
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_from ON transactions(from_uuid, timestamp DESC)");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_to ON transactions(to_uuid, timestamp DESC)");
                }
            });
            migrator.migrate(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    @Override
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close database", e);
        }
    }

    @Override
    public BigDecimal getVirtualBalance(UUID player, String currencyId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT amount FROM balances WHERE player_uuid = ? AND currency_id = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, currencyId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new BigDecimal(rs.getString("amount"));
            }
            return BigDecimal.ZERO;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get balance", e);
        }
    }

    @Override
    public void setVirtualBalance(UUID player, String currencyId, BigDecimal amount) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO balances (player_uuid, currency_id, amount) VALUES (?, ?, ?)
                ON CONFLICT(player_uuid, currency_id) DO UPDATE SET amount = excluded.amount
            """)) {
            ps.setString(1, player.toString());
            ps.setString(2, currencyId);
            ps.setString(3, amount.toPlainString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set balance", e);
        }
    }

    @Override
    public void logTransaction(UUID txId, @Nullable UUID from, @Nullable UUID to,
                               BigDecimal amount, String currencyId, String type, Instant timestamp) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO transactions (id, from_uuid, to_uuid, amount, currency_id, type, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, txId.toString());
            ps.setString(2, from != null ? from.toString() : null);
            ps.setString(3, to != null ? to.toString() : null);
            ps.setString(4, amount.toPlainString());
            ps.setString(5, currencyId);
            ps.setString(6, type);
            ps.setLong(7, timestamp.toEpochMilli());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to log transaction", e);
        }
    }

    @Override
    public List<TransactionRecord> getTransactionHistory(UUID player, @Nullable String type,
                                                          @Nullable Instant from, @Nullable Instant to,
                                                          int offset, int limit) {
        var sql = new StringBuilder(
            "SELECT * FROM transactions WHERE (from_uuid = ? OR to_uuid = ?)");
        var params = new ArrayList<Object>();
        params.add(player.toString());
        params.add(player.toString());

        appendFilters(sql, params, type, from, to);
        sql.append(" ORDER BY timestamp DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                setParam(ps, i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            var results = new ArrayList<TransactionRecord>();
            while (rs.next()) {
                results.add(mapRow(rs));
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query transactions", e);
        }
    }

    @Override
    public long getTransactionCount(UUID player, @Nullable String type,
                                     @Nullable Instant from, @Nullable Instant to) {
        var sql = new StringBuilder(
            "SELECT COUNT(*) FROM transactions WHERE (from_uuid = ? OR to_uuid = ?)");
        var params = new ArrayList<Object>();
        params.add(player.toString());
        params.add(player.toString());

        appendFilters(sql, params, type, from, to);

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                setParam(ps, i + 1, params.get(i));
            }
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count transactions", e);
        }
    }

    private void appendFilters(StringBuilder sql, List<Object> params,
                                @Nullable String type, @Nullable Instant from, @Nullable Instant to) {
        if (type != null) {
            sql.append(" AND type = ?");
            params.add(type);
        }
        if (from != null) {
            sql.append(" AND timestamp >= ?");
            params.add(from.toEpochMilli());
        }
        if (to != null) {
            sql.append(" AND timestamp <= ?");
            params.add(to.toEpochMilli());
        }
    }

    private void setParam(PreparedStatement ps, int index, Object value) throws SQLException {
        if (value instanceof String s) ps.setString(index, s);
        else if (value instanceof Long l) ps.setLong(index, l);
        else if (value instanceof Integer i) ps.setInt(index, i);
        else ps.setObject(index, value);
    }

    private TransactionRecord mapRow(ResultSet rs) throws SQLException {
        String fromStr = rs.getString("from_uuid");
        String toStr = rs.getString("to_uuid");
        return new TransactionRecord(
            UUID.fromString(rs.getString("id")),
            fromStr != null ? UUID.fromString(fromStr) : null,
            toStr != null ? UUID.fromString(toStr) : null,
            new BigDecimal(rs.getString("amount")),
            rs.getString("currency_id"),
            rs.getString("type"),
            Instant.ofEpochMilli(rs.getLong("timestamp"))
        );
    }
}
