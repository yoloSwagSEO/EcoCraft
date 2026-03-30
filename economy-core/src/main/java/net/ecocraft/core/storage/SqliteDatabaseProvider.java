package net.ecocraft.core.storage;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
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
    public synchronized void initialize() {
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
            migrator.addMigration(2, "Exchange rates and daily limits tables", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS exchange_rates (
                            from_currency TEXT NOT NULL,
                            to_currency TEXT NOT NULL,
                            rate TEXT NOT NULL,
                            fee_rate TEXT NOT NULL DEFAULT '0',
                            PRIMARY KEY (from_currency, to_currency)
                        )
                    """);
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS exchange_daily (
                            player_uuid TEXT NOT NULL,
                            from_currency TEXT NOT NULL,
                            to_currency TEXT NOT NULL,
                            day TEXT NOT NULL,
                            total_amount INTEGER NOT NULL DEFAULT 0,
                            PRIMARY KEY (player_uuid, from_currency, to_currency, day)
                        )
                    """);
                }
            });
            migrator.addMigration(3, "Currencies table for persistence", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("""
                        CREATE TABLE IF NOT EXISTS currencies (
                            id TEXT PRIMARY KEY,
                            name TEXT NOT NULL,
                            symbol TEXT NOT NULL,
                            decimals INTEGER NOT NULL DEFAULT 0,
                            physical INTEGER NOT NULL DEFAULT 0,
                            item_id TEXT,
                            exchangeable INTEGER NOT NULL DEFAULT 1,
                            reference_rate TEXT NOT NULL DEFAULT '1.0'
                        )
                    """);
                }
            });
            migrator.addMigration(4, "Decimal migration - multiply all amounts by 100 for decimals=2", conn -> {
                try (Statement stmt = conn.createStatement()) {
                    // balances.amount is TEXT (BigDecimal string) — read, multiply, write back
                    try (ResultSet rs = stmt.executeQuery("SELECT player_uuid, currency_id, amount FROM balances")) {
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE balances SET amount = ? WHERE player_uuid = ? AND currency_id = ?")) {
                            while (rs.next()) {
                                BigDecimal old = new BigDecimal(rs.getString("amount"));
                                BigDecimal migrated = old.multiply(BigDecimal.valueOf(100));
                                update.setString(1, migrated.toPlainString());
                                update.setString(2, rs.getString("player_uuid"));
                                update.setString(3, rs.getString("currency_id"));
                                update.addBatch();
                            }
                            update.executeBatch();
                        }
                    }
                    // transactions.amount is TEXT (BigDecimal string) — same approach
                    try (ResultSet rs = stmt.executeQuery("SELECT id, amount FROM transactions")) {
                        try (PreparedStatement update = conn.prepareStatement(
                                "UPDATE transactions SET amount = ? WHERE id = ?")) {
                            while (rs.next()) {
                                BigDecimal old = new BigDecimal(rs.getString("amount"));
                                BigDecimal migrated = old.multiply(BigDecimal.valueOf(100));
                                update.setString(1, migrated.toPlainString());
                                update.setString(2, rs.getString("id"));
                                update.addBatch();
                            }
                            update.executeBatch();
                        }
                    }
                }
            });
            migrator.migrate(connection);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database", e);
        }
    }

    @Override
    public synchronized void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close database", e);
        }
    }

    @Override
    public synchronized BigDecimal getVirtualBalance(UUID player, String currencyId) {
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
    public synchronized List<BalanceEntry> getAllBalances(String currencyId) {
        List<BalanceEntry> results = new java.util.ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT player_uuid, currency_id, amount FROM balances WHERE currency_id = ? ORDER BY CAST(amount AS REAL) DESC")) {
            ps.setString(1, currencyId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new BalanceEntry(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("currency_id"),
                        new BigDecimal(rs.getString("amount"))));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all balances", e);
        }
        return results;
    }

    @Override
    public synchronized boolean hasAccount(UUID player, String currencyId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM balances WHERE player_uuid = ? AND currency_id = ?")) {
            ps.setString(1, player.toString());
            ps.setString(2, currencyId);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check account existence", e);
        }
    }

    @Override
    public synchronized void setVirtualBalance(UUID player, String currencyId, BigDecimal amount) {
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
    public synchronized void logTransaction(UUID txId, @Nullable UUID from, @Nullable UUID to,
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
    public synchronized List<TransactionRecord> getTransactionHistory(@Nullable UUID player, @Nullable String type,
                                                          @Nullable Instant from, @Nullable Instant to,
                                                          int offset, int limit) {
        var sql = new StringBuilder("SELECT * FROM transactions WHERE 1=1");
        var params = new ArrayList<Object>();
        if (player != null) {
            sql.append(" AND (from_uuid = ? OR to_uuid = ?)");
            params.add(player.toString());
            params.add(player.toString());
        }

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
    public synchronized long getTransactionCount(@Nullable UUID player, @Nullable String type,
                                     @Nullable Instant from, @Nullable Instant to) {
        var sql = new StringBuilder("SELECT COUNT(*) FROM transactions WHERE 1=1");
        var params = new ArrayList<Object>();
        if (player != null) {
            sql.append(" AND (from_uuid = ? OR to_uuid = ?)");
            params.add(player.toString());
            params.add(player.toString());
        }

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

    // --- Exchange rates ---

    @Override
    public synchronized void saveExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate, BigDecimal feeRate) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO exchange_rates (from_currency, to_currency, rate, fee_rate)
                VALUES (?, ?, ?, ?)
            """)) {
            ps.setString(1, fromCurrency);
            ps.setString(2, toCurrency);
            ps.setString(3, rate.toPlainString());
            ps.setString(4, feeRate.toPlainString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save exchange rate", e);
        }
    }

    @Override
    public synchronized @Nullable StoredExchangeRate getExchangeRate(String fromCurrency, String toCurrency) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT rate, fee_rate FROM exchange_rates WHERE from_currency = ? AND to_currency = ?")) {
            ps.setString(1, fromCurrency);
            ps.setString(2, toCurrency);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new StoredExchangeRate(fromCurrency, toCurrency,
                        new BigDecimal(rs.getString("rate")),
                        new BigDecimal(rs.getString("fee_rate")));
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get exchange rate", e);
        }
    }

    @Override
    public synchronized void deleteExchangeRate(String fromCurrency, String toCurrency) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM exchange_rates WHERE from_currency = ? AND to_currency = ?")) {
            ps.setString(1, fromCurrency);
            ps.setString(2, toCurrency);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete exchange rate", e);
        }
    }

    @Override
    public synchronized List<StoredExchangeRate> getAllExchangeRates() {
        List<StoredExchangeRate> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT from_currency, to_currency, rate, fee_rate FROM exchange_rates")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new StoredExchangeRate(
                        rs.getString("from_currency"),
                        rs.getString("to_currency"),
                        new BigDecimal(rs.getString("rate")),
                        new BigDecimal(rs.getString("fee_rate"))));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all exchange rates", e);
        }
        return results;
    }

    // --- Daily exchange limits ---

    @Override
    public synchronized void recordDailyExchange(String playerUuid, String fromCurrency, String toCurrency, long amount) {
        String day = LocalDate.now().toString();
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO exchange_daily (player_uuid, from_currency, to_currency, day, total_amount)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, from_currency, to_currency, day)
                DO UPDATE SET total_amount = total_amount + excluded.total_amount
            """)) {
            ps.setString(1, playerUuid);
            ps.setString(2, fromCurrency);
            ps.setString(3, toCurrency);
            ps.setString(4, day);
            ps.setLong(5, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to record daily exchange", e);
        }
    }

    @Override
    public synchronized long getDailyExchangeTotal(String playerUuid, String fromCurrency, String toCurrency) {
        String day = LocalDate.now().toString();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT total_amount FROM exchange_daily WHERE player_uuid = ? AND from_currency = ? AND to_currency = ? AND day = ?")) {
            ps.setString(1, playerUuid);
            ps.setString(2, fromCurrency);
            ps.setString(3, toCurrency);
            ps.setString(4, day);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("total_amount");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get daily exchange total", e);
        }
    }

    // --- Currencies ---

    @Override
    public synchronized void saveCurrency(String id, String name, String symbol, int decimals,
                                          boolean physical, @Nullable String itemId,
                                          boolean exchangeable, BigDecimal referenceRate) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO currencies (id, name, symbol, decimals, physical, item_id, exchangeable, reference_rate)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            ps.setString(1, id);
            ps.setString(2, name);
            ps.setString(3, symbol);
            ps.setInt(4, decimals);
            ps.setInt(5, physical ? 1 : 0);
            ps.setString(6, itemId);
            ps.setInt(7, exchangeable ? 1 : 0);
            ps.setString(8, referenceRate.toPlainString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save currency", e);
        }
    }

    @Override
    public synchronized void deleteCurrency(String id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM currencies WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete currency", e);
        }
    }

    @Override
    public synchronized List<StoredCurrency> getAllCurrencies() {
        List<StoredCurrency> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, symbol, decimals, physical, item_id, exchangeable, reference_rate FROM currencies")) {
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                results.add(new StoredCurrency(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("symbol"),
                        rs.getInt("decimals"),
                        rs.getInt("physical") == 1,
                        rs.getString("item_id"),
                        rs.getInt("exchangeable") == 1,
                        new BigDecimal(rs.getString("reference_rate"))));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get all currencies", e);
        }
        return results;
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
