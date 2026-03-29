package net.ecocraft.core.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseMigrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrator.class);

    @FunctionalInterface
    public interface Migration {
        void execute(Connection conn) throws SQLException;
    }

    private record VersionedMigration(int version, String description, Migration migration) {}

    private final List<VersionedMigration> migrations = new ArrayList<>();
    private final String dbName; // for logging

    public DatabaseMigrator(String dbName) {
        this.dbName = dbName;
    }

    /** Register a migration. Versions must be sequential starting from 1. */
    public DatabaseMigrator addMigration(int version, String description, Migration migration) {
        migrations.add(new VersionedMigration(version, description, migration));
        return this;
    }

    /** Run all pending migrations on the given connection. */
    public void migrate(Connection conn) throws SQLException {
        ensureSchemaVersionTable(conn);
        int currentVersion = getCurrentVersion(conn);

        for (var m : migrations) {
            if (m.version() > currentVersion) {
                LOGGER.info("[{}] Running migration v{}: {}", dbName, m.version(), m.description());
                conn.setAutoCommit(false);
                try {
                    m.migration().execute(conn);
                    setVersion(conn, m.version());
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                LOGGER.info("[{}] Migration v{} complete.", dbName, m.version());
            }
        }
    }

    private void ensureSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL,
                    applied_at INTEGER NOT NULL
                )
            """);
            // Insert initial row if empty
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM schema_version");
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
                    ps.setInt(1, 0);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            }
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version");
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }

    private void setVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO schema_version (version, applied_at) VALUES (?, ?)")) {
            ps.setInt(1, version);
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }
}
