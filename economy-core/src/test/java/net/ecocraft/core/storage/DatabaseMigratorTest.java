package net.ecocraft.core.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigratorTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        // In-memory SQLite database, isolated per test
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        conn.setAutoCommit(true);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private int getSchemaVersion() throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private boolean tableExists(String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private DatabaseMigrator buildTwoMigrationMigrator() {
        return new DatabaseMigrator("test-db")
                .addMigration(1, "Create table_a", c -> {
                    try (Statement s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS table_a (id INTEGER PRIMARY KEY)");
                    }
                })
                .addMigration(2, "Create table_b", c -> {
                    try (Statement s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS table_b (id INTEGER PRIMARY KEY)");
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void freshDatabase_runsAllMigrations() throws SQLException {
        buildTwoMigrationMigrator().migrate(conn);

        assertEquals(2, getSchemaVersion());
        assertTrue(tableExists("table_a"), "table_a should exist after migration 1");
        assertTrue(tableExists("table_b"), "table_b should exist after migration 2");
    }

    @Test
    void databaseAtVersion1_onlyRunsMigration2() throws SQLException {
        // Simulate a DB already at version 1 (table_a exists, schema_version records it)
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS table_a (id INTEGER PRIMARY KEY)");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER NOT NULL,
                    applied_at INTEGER NOT NULL
                )
            """);
            stmt.execute("INSERT INTO schema_version (version, applied_at) VALUES (0, 0)");
            stmt.execute("INSERT INTO schema_version (version, applied_at) VALUES (1, 1000)");
        }

        List<Integer> ranMigrations = new ArrayList<>();
        new DatabaseMigrator("test-db")
                .addMigration(1, "Create table_a", c -> ranMigrations.add(1))
                .addMigration(2, "Create table_b", c -> {
                    ranMigrations.add(2);
                    try (Statement s = c.createStatement()) {
                        s.execute("CREATE TABLE IF NOT EXISTS table_b (id INTEGER PRIMARY KEY)");
                    }
                })
                .migrate(conn);

        assertFalse(ranMigrations.contains(1), "Migration 1 must NOT run again");
        assertTrue(ranMigrations.contains(2), "Migration 2 must run");
        assertEquals(2, getSchemaVersion());
        assertTrue(tableExists("table_b"));
    }

    @Test
    void databaseAtLatestVersion_runsNothing() throws SQLException {
        // Run migrations once to reach latest
        buildTwoMigrationMigrator().migrate(conn);
        assertEquals(2, getSchemaVersion());

        // Run again — nothing should change
        List<Integer> ranMigrations = new ArrayList<>();
        new DatabaseMigrator("test-db")
                .addMigration(1, "Create table_a", c -> ranMigrations.add(1))
                .addMigration(2, "Create table_b", c -> ranMigrations.add(2))
                .migrate(conn);

        assertTrue(ranMigrations.isEmpty(), "No migrations should run on an up-to-date database");
        assertEquals(2, getSchemaVersion());
    }

    @Test
    void migrationFailure_doesNotUpdateVersion() throws SQLException {
        DatabaseMigrator migrator = new DatabaseMigrator("test-db")
                .addMigration(1, "Intentionally failing migration", c -> {
                    throw new SQLException("Simulated migration failure");
                });

        assertThrows(SQLException.class, () -> migrator.migrate(conn));

        // schema_version table was created but version must still be 0
        assertEquals(0, getSchemaVersion());
    }
}
