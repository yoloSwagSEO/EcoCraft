# Phase 2: Economy Core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the economy-core module — the actual server-side mod that provides currency management, persistent storage, commands, permissions, and the vault block.

**Architecture:** Server-side NeoForge mod implementing all economy-api interfaces. Uses SavedData for simple persistence with a database abstraction layer for SQLite/MySQL. Config via NeoForge TOML. Commands via Brigadier. Vault block as an EntityBlock with a synchronized Menu+Screen GUI.

**Tech Stack:** Java 21, NeoForge 1.21.1, SQLite (via JDBC bundled in Java), HikariCP for connection pooling (MySQL/PostgreSQL), Brigadier commands, NeoForge config system.

---

## File Structure

```
economy-core/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/net/ecocraft/core/
    │   │   ├── EcoCraftCoreMod.java              # @Mod entry point, registers everything
    │   │   ├── config/
    │   │   │   └── EcoConfig.java                 # NeoForge TOML config
    │   │   ├── registry/
    │   │   │   └── EcoRegistries.java             # Deferred registers (blocks, items, menus, block entities)
    │   │   ├── storage/
    │   │   │   ├── DatabaseProvider.java           # Interface for DB operations
    │   │   │   ├── SqliteDatabaseProvider.java     # SQLite implementation
    │   │   │   ├── DatabaseSchema.java             # Table creation / migration
    │   │   │   └── StorageManager.java             # Factory: picks provider based on config
    │   │   ├── impl/
    │   │   │   ├── CurrencyRegistryImpl.java       # CurrencyRegistry implementation
    │   │   │   ├── EconomyProviderImpl.java        # EconomyProvider implementation (with payment priority)
    │   │   │   ├── ExchangeServiceImpl.java        # ExchangeService implementation
    │   │   │   └── TransactionLogImpl.java         # TransactionLog implementation
    │   │   ├── permission/
    │   │   │   ├── PermissionChecker.java          # Interface
    │   │   │   └── DefaultPermissionChecker.java   # Built-in op-level based
    │   │   ├── command/
    │   │   │   ├── EcoCommands.java                # Registers all commands
    │   │   │   ├── BalanceCommand.java             # /balance
    │   │   │   ├── PayCommand.java                 # /pay
    │   │   │   ├── CurrencyCommand.java            # /currency list|convert
    │   │   │   └── EcoAdminCommand.java            # /eco give|take|set
    │   │   └── vault/
    │   │       ├── VaultBlock.java                 # The vault block
    │   │       ├── VaultBlockEntity.java           # Block entity with item storage
    │   │       ├── VaultMenu.java                  # Container menu
    │   │       └── VaultScreen.java                # Client-side GUI (uses gui-lib)
    │   ├── resources/
    │   │   └── assets/ecocraft_core/
    │   │       ├── blockstates/vault_block.json
    │   │       ├── models/block/vault_block.json
    │   │       ├── models/item/vault_block.json
    │   │       └── lang/
    │   │           ├── en_us.json
    │   │           └── fr_fr.json
    │   └── templates/
    │       └── META-INF/
    │           └── neoforge.mods.toml
    └── test/java/net/ecocraft/core/
        ├── impl/
        │   ├── CurrencyRegistryImplTest.java
        │   └── EconomyProviderImplTest.java
        └── storage/
            └── SqliteDatabaseProviderTest.java
```

---

### Task 1: Economy-Core Module Scaffolding

**Files:**
- Create: `economy-core/build.gradle`
- Create: `economy-core/src/main/templates/META-INF/neoforge.mods.toml`
- Create: `economy-core/src/main/java/net/ecocraft/core/EcoCraftCoreMod.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/config/EcoConfig.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/registry/EcoRegistries.java`
- Modify: `settings.gradle` — add `:economy-core`
- Modify: `gradle.properties` — add economy_core properties

- [ ] **Step 1: Add economy-core to settings.gradle**

In `settings.gradle`, add after the existing includes:
```groovy
include ':economy-core'
```

- [ ] **Step 2: Add properties to gradle.properties**

Add to `gradle.properties`:
```properties
# economy-core
economy_core_mod_id=ecocraft_core
economy_core_mod_name=EcoCraft Economy Core
```

- [ ] **Step 3: Create economy-core/build.gradle**

```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

base {
    archivesName = 'ecocraft-economy-core'
}

neoForge {
    version = project.neo_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }

    runs {
        client {
            client()
        }
        server {
            server()
            programArgument '--nogui'
        }
    }

    mods {
        "${project.economy_core_mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    implementation project(':economy-api')
    implementation project(':gui-lib')
}

var generateModMetadata = tasks.register("generateModMetadata", ProcessResources) {
    var replaceProperties = [
        minecraft_version      : project.minecraft_version,
        minecraft_version_range: project.minecraft_version_range,
        neo_version            : project.neo_version,
        loader_version_range   : project.loader_version_range,
        mod_id                 : project.economy_core_mod_id,
        mod_name               : project.economy_core_mod_name,
        mod_version            : project.mod_version,
    ]
    inputs.properties replaceProperties
    expand replaceProperties
    from "src/main/templates"
    into "build/generated/sources/modMetadata"
}

sourceSets.main.resources.srcDir generateModMetadata
neoForge.ideSyncTask generateModMetadata
```

- [ ] **Step 4: Create neoforge.mods.toml**

Create `economy-core/src/main/templates/META-INF/neoforge.mods.toml`:
```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="MIT"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
description='''
Default economy implementation with currency management, storage, commands, and vault block.
'''

[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="[${neo_version},)"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="BOTH"

[[dependencies.${mod_id}]]
modId="ecocraft_api"
type="required"
versionRange="[${mod_version},)"
ordering="AFTER"
side="BOTH"
```

- [ ] **Step 5: Create EcoConfig**

Create `economy-core/src/main/java/net/ecocraft/core/config/EcoConfig.java`:
```java
package net.ecocraft.core.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class EcoConfig {
    public static final EcoConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<EcoConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(EcoConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    // Storage
    public final ModConfigSpec.ConfigValue<String> storageType;
    public final ModConfigSpec.ConfigValue<String> mysqlHost;
    public final ModConfigSpec.IntValue mysqlPort;
    public final ModConfigSpec.ConfigValue<String> mysqlDatabase;
    public final ModConfigSpec.ConfigValue<String> mysqlUsername;
    public final ModConfigSpec.ConfigValue<String> mysqlPassword;

    // Economy
    public final ModConfigSpec.ConfigValue<String> defaultCurrencyId;
    public final ModConfigSpec.ConfigValue<String> defaultCurrencyName;
    public final ModConfigSpec.ConfigValue<String> defaultCurrencySymbol;
    public final ModConfigSpec.IntValue defaultCurrencyDecimals;
    public final ModConfigSpec.DoubleValue startingBalance;

    // Vault
    public final ModConfigSpec.BooleanValue vaultEnabled;

    private EcoConfig(ModConfigSpec.Builder builder) {
        builder.push("storage");
        storageType = builder
            .comment("Storage type: 'sqlite' or 'mysql'")
            .define("type", "sqlite");
        mysqlHost = builder.define("mysql.host", "localhost");
        mysqlPort = builder.defineInRange("mysql.port", 3306, 1, 65535);
        mysqlDatabase = builder.define("mysql.database", "ecocraft");
        mysqlUsername = builder.define("mysql.username", "root");
        mysqlPassword = builder.define("mysql.password", "");
        builder.pop();

        builder.push("economy");
        defaultCurrencyId = builder
            .comment("ID of the default server currency")
            .define("defaultCurrency.id", "gold");
        defaultCurrencyName = builder.define("defaultCurrency.name", "Gold");
        defaultCurrencySymbol = builder.define("defaultCurrency.symbol", "⛁");
        defaultCurrencyDecimals = builder.defineInRange("defaultCurrency.decimals", 2, 0, 4);
        startingBalance = builder
            .comment("Starting balance for new players")
            .defineInRange("startingBalance", 100.0, 0.0, Double.MAX_VALUE);
        builder.pop();

        builder.push("vault");
        vaultEnabled = builder
            .comment("Enable the vault block for physical/virtual currency sync")
            .define("enabled", true);
        builder.pop();
    }
}
```

- [ ] **Step 6: Create EcoRegistries**

Create `economy-core/src/main/java/net/ecocraft/core/registry/EcoRegistries.java`:
```java
package net.ecocraft.core.registry;

import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class EcoRegistries {
    public static final DeferredRegister.Blocks BLOCKS =
        DeferredRegister.createBlocks(EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS =
        DeferredRegister.createItems(EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, EcoCraftCoreMod.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, EcoCraftCoreMod.MOD_ID);

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }
}
```

- [ ] **Step 7: Create EcoCraftCoreMod entry point**

Create `economy-core/src/main/java/net/ecocraft/core/EcoCraftCoreMod.java`:
```java
package net.ecocraft.core;

import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.registry.EcoRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(EcoCraftCoreMod.MOD_ID)
public class EcoCraftCoreMod {
    public static final String MOD_ID = "ecocraft_core";

    public EcoCraftCoreMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, EcoConfig.CONFIG_SPEC);
        EcoRegistries.register(modBus);
    }
}
```

- [ ] **Step 8: Verify build**

Run: `./gradlew :economy-core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add -A
git commit -m "feat(economy-core): scaffold module with config, registries, and mod entry point"
```

---

### Task 2: Storage Layer — Database Abstraction + SQLite

**Files:**
- Create: `economy-core/src/main/java/net/ecocraft/core/storage/DatabaseProvider.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/storage/DatabaseSchema.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/storage/SqliteDatabaseProvider.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/storage/StorageManager.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/storage/SqliteDatabaseProviderTest.java`

- [ ] **Step 1: Write the failing test**

Create `economy-core/src/test/java/net/ecocraft/core/storage/SqliteDatabaseProviderTest.java`:
```java
package net.ecocraft.core.storage;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SqliteDatabaseProviderTest {

    private SqliteDatabaseProvider db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void getBalanceReturnsZeroForNewPlayer() {
        var player = UUID.randomUUID();
        assertEquals(BigDecimal.ZERO, db.getVirtualBalance(player, "gold"));
    }

    @Test
    void setAndGetBalance() {
        var player = UUID.randomUUID();
        db.setVirtualBalance(player, "gold", new BigDecimal("150.50"));
        assertEquals(new BigDecimal("150.50"), db.getVirtualBalance(player, "gold"));
    }

    @Test
    void multiplePlayersIndependent() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        db.setVirtualBalance(p1, "gold", new BigDecimal("100"));
        db.setVirtualBalance(p2, "gold", new BigDecimal("200"));

        assertEquals(new BigDecimal("100"), db.getVirtualBalance(p1, "gold"));
        assertEquals(new BigDecimal("200"), db.getVirtualBalance(p2, "gold"));
    }

    @Test
    void multipleCurrenciesIndependent() {
        var player = UUID.randomUUID();
        db.setVirtualBalance(player, "gold", new BigDecimal("100"));
        db.setVirtualBalance(player, "silver", new BigDecimal("5000"));

        assertEquals(new BigDecimal("100"), db.getVirtualBalance(player, "gold"));
        assertEquals(new BigDecimal("5000"), db.getVirtualBalance(player, "silver"));
    }

    @Test
    void logAndQueryTransactions() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        var txId = UUID.randomUUID();

        db.logTransaction(txId, p1, p2, new BigDecimal("50"), "gold", "PAYMENT", Instant.now());

        var history = db.getTransactionHistory(p1, null, null, null, 0, 10);
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("50"), history.get(0).amount());
        assertEquals("PAYMENT", history.get(0).type());
    }

    @Test
    void transactionCountForPagination() {
        var player = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            db.logTransaction(UUID.randomUUID(), player, null,
                new BigDecimal("10"), "gold", "PAYMENT", Instant.now());
        }

        long count = db.getTransactionCount(player, null, null, null);
        assertEquals(5, count);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :economy-core:test`
Expected: FAIL — classes don't exist.

- [ ] **Step 3: Write DatabaseProvider interface**

Create `economy-core/src/main/java/net/ecocraft/core/storage/DatabaseProvider.java`:
```java
package net.ecocraft.core.storage;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DatabaseProvider {

    void initialize();
    void shutdown();

    // Balances
    BigDecimal getVirtualBalance(UUID player, String currencyId);
    void setVirtualBalance(UUID player, String currencyId, BigDecimal amount);

    // Transactions
    void logTransaction(UUID txId, @Nullable UUID from, @Nullable UUID to,
                        BigDecimal amount, String currencyId, String type, Instant timestamp);

    List<TransactionRecord> getTransactionHistory(UUID player, @Nullable String type,
                                                   @Nullable Instant from, @Nullable Instant to,
                                                   int offset, int limit);

    long getTransactionCount(UUID player, @Nullable String type,
                             @Nullable Instant from, @Nullable Instant to);

    record TransactionRecord(
        UUID id, @Nullable UUID from, @Nullable UUID to,
        BigDecimal amount, String currencyId, String type, Instant timestamp
    ) {}
}
```

- [ ] **Step 4: Write DatabaseSchema**

Create `economy-core/src/main/java/net/ecocraft/core/storage/DatabaseSchema.java`:
```java
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
```

- [ ] **Step 5: Write SqliteDatabaseProvider**

Create `economy-core/src/main/java/net/ecocraft/core/storage/SqliteDatabaseProvider.java`:
```java
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
            DatabaseSchema.createTables(connection);
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
```

- [ ] **Step 6: Write StorageManager**

Create `economy-core/src/main/java/net/ecocraft/core/storage/StorageManager.java`:
```java
package net.ecocraft.core.storage;

import net.ecocraft.core.config.EcoConfig;

import java.nio.file.Path;

public class StorageManager {

    private DatabaseProvider provider;

    public void initialize(Path worldDir) {
        String type = EcoConfig.CONFIG.storageType.get();
        if ("sqlite".equalsIgnoreCase(type)) {
            provider = new SqliteDatabaseProvider(worldDir.resolve("ecocraft.db"));
        } else {
            throw new IllegalArgumentException("Unsupported storage type: " + type + ". Use 'sqlite'.");
        }
        provider.initialize();
    }

    public void shutdown() {
        if (provider != null) {
            provider.shutdown();
        }
    }

    public DatabaseProvider getProvider() {
        if (provider == null) {
            throw new IllegalStateException("StorageManager not initialized");
        }
        return provider;
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :economy-core:test`
Expected: PASS — all 6 tests green.

- [ ] **Step 8: Commit**

```bash
git add economy-core/src/
git commit -m "feat(economy-core): add SQLite storage layer with database abstraction"
```

---

### Task 3: Economy Implementations

**Files:**
- Create: `economy-core/src/main/java/net/ecocraft/core/impl/CurrencyRegistryImpl.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/impl/EconomyProviderImpl.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/impl/ExchangeServiceImpl.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/impl/TransactionLogImpl.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/impl/CurrencyRegistryImplTest.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/impl/EconomyProviderImplTest.java`

- [ ] **Step 1: Write CurrencyRegistryImpl test**

Create `economy-core/src/test/java/net/ecocraft/core/impl/CurrencyRegistryImplTest.java`:
```java
package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyRegistryImplTest {

    private CurrencyRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new CurrencyRegistryImpl();
    }

    @Test
    void registerAndRetrieve() {
        var gold = Currency.virtual("gold", "Gold", "⛁", 2);
        registry.register(gold);
        assertEquals(gold, registry.getById("gold"));
        assertTrue(registry.exists("gold"));
    }

    @Test
    void firstRegisteredIsDefault() {
        var gold = Currency.virtual("gold", "Gold", "⛁", 2);
        var silver = Currency.virtual("silver", "Silver", "∘", 0);
        registry.register(gold);
        registry.register(silver);
        assertEquals(gold, registry.getDefault());
    }

    @Test
    void setExplicitDefault() {
        var gold = Currency.virtual("gold", "Gold", "⛁", 2);
        var silver = Currency.virtual("silver", "Silver", "∘", 0);
        registry.register(gold);
        registry.register(silver);
        registry.setDefault("silver");
        assertEquals(silver, registry.getDefault());
    }

    @Test
    void listAllCurrencies() {
        registry.register(Currency.virtual("gold", "Gold", "⛁", 2));
        registry.register(Currency.virtual("silver", "Silver", "∘", 0));
        assertEquals(2, registry.listAll().size());
    }

    @Test
    void unknownCurrencyReturnsNull() {
        assertNull(registry.getById("nonexistent"));
        assertFalse(registry.exists("nonexistent"));
    }
}
```

- [ ] **Step 2: Write CurrencyRegistryImpl**

Create `economy-core/src/main/java/net/ecocraft/core/impl/CurrencyRegistryImpl.java`:
```java
package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyRegistryImpl implements CurrencyRegistry {

    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
    private String defaultId;

    @Override
    public void register(Currency currency) {
        currencies.put(currency.id(), currency);
        if (defaultId == null) {
            defaultId = currency.id();
        }
    }

    @Override
    public @Nullable Currency getById(String id) {
        return currencies.get(id);
    }

    @Override
    public Currency getDefault() {
        if (defaultId == null) {
            throw new IllegalStateException("No currencies registered");
        }
        return currencies.get(defaultId);
    }

    @Override
    public List<Currency> listAll() {
        return List.copyOf(currencies.values());
    }

    @Override
    public boolean exists(String id) {
        return currencies.containsKey(id);
    }

    public void setDefault(String id) {
        if (!currencies.containsKey(id)) {
            throw new IllegalArgumentException("Currency not registered: " + id);
        }
        this.defaultId = id;
    }
}
```

- [ ] **Step 3: Write EconomyProviderImpl test**

Create `economy-core/src/test/java/net/ecocraft/core/impl/EconomyProviderImplTest.java`:
```java
package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EconomyProviderImplTest {

    private SqliteDatabaseProvider db;
    private EconomyProviderImpl economy;
    private CurrencyRegistryImpl registry;
    private static final Currency GOLD = Currency.virtual("gold", "Gold", "⛁", 2);
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecocraft-test");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        economy = new EconomyProviderImpl(db, registry);
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void newPlayerHasZeroBalance() {
        var player = UUID.randomUUID();
        assertEquals(BigDecimal.ZERO, economy.getBalance(player, GOLD));
    }

    @Test
    void depositIncreasesBalance() {
        var player = UUID.randomUUID();
        var result = economy.deposit(player, new BigDecimal("100"), GOLD);
        assertTrue(result.successful());
        assertEquals(new BigDecimal("100"), economy.getBalance(player, GOLD));
    }

    @Test
    void withdrawDecreasesBalance() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100"), GOLD);
        var result = economy.withdraw(player, new BigDecimal("30"), GOLD);
        assertTrue(result.successful());
        assertEquals(new BigDecimal("70.00"), economy.getBalance(player, GOLD));
    }

    @Test
    void withdrawFailsIfInsufficientFunds() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("10"), GOLD);
        var result = economy.withdraw(player, new BigDecimal("50"), GOLD);
        assertFalse(result.successful());
        assertEquals("Insufficient funds", result.errorMessage());
        assertEquals(new BigDecimal("10"), economy.getBalance(player, GOLD));
    }

    @Test
    void transferMovesMoney() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        economy.deposit(p1, new BigDecimal("100"), GOLD);

        var result = economy.transfer(p1, p2, new BigDecimal("40"), GOLD);
        assertTrue(result.successful());
        assertEquals(new BigDecimal("60.00"), economy.getBalance(p1, GOLD));
        assertEquals(new BigDecimal("40"), economy.getBalance(p2, GOLD));
    }

    @Test
    void transferFailsIfSenderCantAfford() {
        var p1 = UUID.randomUUID();
        var p2 = UUID.randomUUID();
        economy.deposit(p1, new BigDecimal("10"), GOLD);

        var result = economy.transfer(p1, p2, new BigDecimal("50"), GOLD);
        assertFalse(result.successful());
    }

    @Test
    void canAffordChecksBalance() {
        var player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100"), GOLD);
        assertTrue(economy.canAfford(player, new BigDecimal("100"), GOLD));
        assertFalse(economy.canAfford(player, new BigDecimal("101"), GOLD));
    }
}
```

- [ ] **Step 4: Write EconomyProviderImpl**

Create `economy-core/src/main/java/net/ecocraft/core/impl/EconomyProviderImpl.java`:
```java
package net.ecocraft.core.impl;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.Transaction;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.storage.DatabaseProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class EconomyProviderImpl implements EconomyProvider {

    private final DatabaseProvider db;
    private final CurrencyRegistry registry;

    public EconomyProviderImpl(DatabaseProvider db, CurrencyRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    @Override
    public Account getAccount(UUID player, Currency currency) {
        BigDecimal virtual = db.getVirtualBalance(player, currency.id());
        return new Account(player, currency, virtual, BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getBalance(UUID player, Currency currency) {
        return db.getVirtualBalance(player, currency.id());
    }

    @Override
    public BigDecimal getVirtualBalance(UUID player, Currency currency) {
        return db.getVirtualBalance(player, currency.id());
    }

    @Override
    public BigDecimal getVaultBalance(UUID player, Currency currency) {
        return BigDecimal.ZERO; // vault block will override this
    }

    @Override
    public TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency) {
        BigDecimal current = db.getVirtualBalance(player, currency.id());
        if (current.compareTo(amount) < 0) {
            return TransactionResult.failure("Insufficient funds");
        }
        BigDecimal newBalance = current.subtract(amount);
        db.setVirtualBalance(player, currency.id(), newBalance);

        var tx = new Transaction(UUID.randomUUID(), player, null, amount, currency,
            TransactionType.WITHDRAWAL, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        return TransactionResult.success(tx);
    }

    @Override
    public TransactionResult deposit(UUID player, BigDecimal amount, Currency currency) {
        BigDecimal current = db.getVirtualBalance(player, currency.id());
        BigDecimal newBalance = current.add(amount);
        db.setVirtualBalance(player, currency.id(), newBalance);

        var tx = new Transaction(UUID.randomUUID(), null, player, amount, currency,
            TransactionType.DEPOSIT, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        return TransactionResult.success(tx);
    }

    @Override
    public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency) {
        BigDecimal senderBalance = db.getVirtualBalance(from, currency.id());
        if (senderBalance.compareTo(amount) < 0) {
            return TransactionResult.failure("Insufficient funds");
        }

        db.setVirtualBalance(from, currency.id(), senderBalance.subtract(amount));
        BigDecimal receiverBalance = db.getVirtualBalance(to, currency.id());
        db.setVirtualBalance(to, currency.id(), receiverBalance.add(amount));

        var tx = new Transaction(UUID.randomUUID(), from, to, amount, currency,
            TransactionType.PAYMENT, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        return TransactionResult.success(tx);
    }

    @Override
    public boolean canAfford(UUID player, BigDecimal amount, Currency currency) {
        return db.getVirtualBalance(player, currency.id()).compareTo(amount) >= 0;
    }
}
```

- [ ] **Step 5: Write ExchangeServiceImpl**

Create `economy-core/src/main/java/net/ecocraft/core/impl/ExchangeServiceImpl.java`:
```java
package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.transaction.TransactionResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExchangeServiceImpl implements ExchangeService {

    private final EconomyProvider economy;
    private final Map<String, ExchangeRate> rates = new ConcurrentHashMap<>();

    public ExchangeServiceImpl(EconomyProvider economy) {
        this.economy = economy;
    }

    public void registerRate(ExchangeRate rate) {
        rates.put(rateKey(rate.from(), rate.to()), rate);
    }

    @Override
    public TransactionResult convert(UUID player, BigDecimal amount, Currency from, Currency to) {
        ExchangeRate rate = getRate(from, to);
        if (rate == null) {
            return TransactionResult.failure("No exchange rate found for " + from.id() + " -> " + to.id());
        }

        var withdrawResult = economy.withdraw(player, amount, from);
        if (!withdrawResult.successful()) {
            return withdrawResult;
        }

        BigDecimal converted = rate.convert(amount);
        return economy.deposit(player, converted, to);
    }

    @Override
    public @Nullable ExchangeRate getRate(Currency from, Currency to) {
        return rates.get(rateKey(from, to));
    }

    @Override
    public List<ExchangeRate> listRates() {
        return List.copyOf(rates.values());
    }

    private String rateKey(Currency from, Currency to) {
        return from.id() + "->" + to.id();
    }
}
```

- [ ] **Step 6: Write TransactionLogImpl**

Create `economy-core/src/main/java/net/ecocraft/core/impl/TransactionLogImpl.java`:
```java
package net.ecocraft.core.impl;

import net.ecocraft.api.Page;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.*;
import net.ecocraft.core.storage.DatabaseProvider;

import java.util.List;

public class TransactionLogImpl implements TransactionLog {

    private final DatabaseProvider db;
    private final CurrencyRegistry registry;

    public TransactionLogImpl(DatabaseProvider db, CurrencyRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    @Override
    public Page<Transaction> getHistory(TransactionFilter filter) {
        String typeStr = filter.type() != null ? filter.type().name() : null;

        var records = db.getTransactionHistory(
            filter.player(), typeStr, filter.from(), filter.to(),
            filter.offset(), filter.limit()
        );

        long total = db.getTransactionCount(
            filter.player(), typeStr, filter.from(), filter.to()
        );

        List<Transaction> transactions = records.stream().map(r -> {
            Currency currency = registry.getById(r.currencyId());
            if (currency == null) {
                currency = Currency.virtual(r.currencyId(), r.currencyId(), "?", 0);
            }
            return new Transaction(
                r.id(), r.from(), r.to(), r.amount(), currency,
                TransactionType.valueOf(r.type()), r.timestamp()
            );
        }).toList();

        return new Page<>(transactions, filter.offset(), filter.limit(), total);
    }
}
```

- [ ] **Step 7: Run tests**

Run: `./gradlew :economy-core:test`
Expected: PASS — all tests green.

- [ ] **Step 8: Commit**

```bash
git add economy-core/src/
git commit -m "feat(economy-core): implement CurrencyRegistry, EconomyProvider, ExchangeService, TransactionLog"
```

---

### Task 4: Permission System

**Files:**
- Create: `economy-core/src/main/java/net/ecocraft/core/permission/PermissionChecker.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/permission/DefaultPermissionChecker.java`

- [ ] **Step 1: Write PermissionChecker interface**

Create `economy-core/src/main/java/net/ecocraft/core/permission/PermissionChecker.java`:
```java
package net.ecocraft.core.permission;

import net.minecraft.server.level.ServerPlayer;

public interface PermissionChecker {
    boolean hasPermission(ServerPlayer player, String permission);
}
```

- [ ] **Step 2: Write DefaultPermissionChecker**

Create `economy-core/src/main/java/net/ecocraft/core/permission/DefaultPermissionChecker.java`:
```java
package net.ecocraft.core.permission;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

/**
 * Default permission checker based on vanilla op levels.
 * Maps permission nodes to op levels (0 = everyone, 2 = op, 4 = admin).
 */
public class DefaultPermissionChecker implements PermissionChecker {

    private static final Map<String, Integer> PERMISSION_LEVELS = Map.ofEntries(
        Map.entry("economy.balance", 0),
        Map.entry("economy.balance.others", 1),
        Map.entry("economy.pay", 0),
        Map.entry("economy.exchange", 0),
        Map.entry("economy.bank", 0),
        Map.entry("economy.currency.list", 0),
        Map.entry("economy.admin.give", 2),
        Map.entry("economy.admin.take", 2),
        Map.entry("economy.admin.set", 2),
        Map.entry("ah.use", 0),
        Map.entry("ah.sell", 0),
        Map.entry("ah.stats", 0),
        Map.entry("ah.stats.others", 1),
        Map.entry("ah.admin.reload", 2),
        Map.entry("ah.admin.clear", 3),
        Map.entry("ah.admin.expire", 2)
    );

    @Override
    public boolean hasPermission(ServerPlayer player, String permission) {
        Integer required = PERMISSION_LEVELS.get(permission);
        if (required == null) {
            return player.hasPermissions(2); // unknown perms default to op
        }
        return player.hasPermissions(required);
    }
}
```

- [ ] **Step 3: Verify build**

Run: `./gradlew :economy-core:build`

- [ ] **Step 4: Commit**

```bash
git add economy-core/src/
git commit -m "feat(economy-core): add permission system with op-level defaults"
```

---

### Task 5: Commands

**Files:**
- Create: `economy-core/src/main/java/net/ecocraft/core/command/EcoCommands.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/command/BalanceCommand.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/command/PayCommand.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/command/CurrencyCommand.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/command/EcoAdminCommand.java`

- [ ] **Step 1: Create EcoCommands registry**

Create `economy-core/src/main/java/net/ecocraft/core/command/EcoCommands.java`:
```java
package net.ecocraft.core.command;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class EcoCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                EconomyProvider economy,
                                CurrencyRegistry currencies,
                                ExchangeService exchange,
                                PermissionChecker permissions) {
        BalanceCommand.register(dispatcher, economy, currencies, permissions);
        PayCommand.register(dispatcher, economy, currencies, permissions);
        CurrencyCommand.register(dispatcher, currencies, exchange, permissions);
        EcoAdminCommand.register(dispatcher, economy, currencies, permissions);
    }
}
```

- [ ] **Step 2: Create BalanceCommand**

Create `economy-core/src/main/java/net/ecocraft/core/command/BalanceCommand.java`:
```java
package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class BalanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                EconomyProvider economy,
                                CurrencyRegistry currencies,
                                PermissionChecker permissions) {
        dispatcher.register(Commands.literal("balance")
            .executes(ctx -> showOwnBalance(ctx.getSource(), economy, currencies))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return showPlayerBalance(ctx.getSource(), target, economy, currencies, permissions);
                })
            )
        );

        // Alias
        dispatcher.register(Commands.literal("bal")
            .executes(ctx -> showOwnBalance(ctx.getSource(), economy, currencies))
        );
    }

    private static int showOwnBalance(CommandSourceStack source, EconomyProvider economy,
                                       CurrencyRegistry currencies) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var balance = economy.getBalance(player.getUUID(), currency);
        source.sendSuccess(() -> Component.literal(
            "Balance: " + balance.toPlainString() + " " + currency.symbol()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showPlayerBalance(CommandSourceStack source, ServerPlayer target,
                                          EconomyProvider economy, CurrencyRegistry currencies,
                                          PermissionChecker permissions) {
        ServerPlayer sender = source.getPlayer();
        if (sender != null && !permissions.hasPermission(sender, "economy.balance.others")) {
            source.sendFailure(Component.literal("You don't have permission to check other players' balance"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var balance = economy.getBalance(target.getUUID(), currency);
        source.sendSuccess(() -> Component.literal(
            target.getName().getString() + "'s balance: " + balance.toPlainString() + " " + currency.symbol()
        ), false);
        return Command.SINGLE_SUCCESS;
    }
}
```

- [ ] **Step 3: Create PayCommand**

Create `economy-core/src/main/java/net/ecocraft/core/command/PayCommand.java`:
```java
package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;

public class PayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                EconomyProvider economy,
                                CurrencyRegistry currencies,
                                PermissionChecker permissions) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        double amount = DoubleArgumentType.getDouble(ctx, "amount");
                        return pay(ctx.getSource(), sender, target, amount, economy, currencies, permissions);
                    })
                )
            )
        );
    }

    private static int pay(CommandSourceStack source, ServerPlayer sender, ServerPlayer target,
                           double amount, EconomyProvider economy, CurrencyRegistry currencies,
                           PermissionChecker permissions) {
        if (!permissions.hasPermission(sender, "economy.pay")) {
            source.sendFailure(Component.literal("You don't have permission to pay"));
            return 0;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.literal("You can't pay yourself"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var result = economy.transfer(sender.getUUID(), target.getUUID(),
            BigDecimal.valueOf(amount), currency);

        if (result.successful()) {
            source.sendSuccess(() -> Component.literal(
                "Paid " + amount + " " + currency.symbol() + " to " + target.getName().getString()
            ), false);
            target.sendSystemMessage(Component.literal(
                "Received " + amount + " " + currency.symbol() + " from " + sender.getName().getString()
            ));
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }
}
```

- [ ] **Step 4: Create CurrencyCommand**

Create `economy-core/src/main/java/net/ecocraft/core/command/CurrencyCommand.java`:
```java
package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;

public class CurrencyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CurrencyRegistry currencies,
                                ExchangeService exchange,
                                PermissionChecker permissions) {
        dispatcher.register(Commands.literal("currency")
            .then(Commands.literal("list")
                .executes(ctx -> listCurrencies(ctx.getSource(), currencies))
            )
            .then(Commands.literal("convert")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .then(Commands.argument("from", StringArgumentType.word())
                        .then(Commands.argument("to", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                String fromId = StringArgumentType.getString(ctx, "from");
                                String toId = StringArgumentType.getString(ctx, "to");
                                return convert(ctx.getSource(), player, amount, fromId, toId,
                                    currencies, exchange, permissions);
                            })
                        )
                    )
                )
            )
        );
    }

    private static int listCurrencies(CommandSourceStack source, CurrencyRegistry currencies) {
        var all = currencies.listAll();
        if (all.isEmpty()) {
            source.sendFailure(Component.literal("No currencies registered"));
            return 0;
        }

        Currency def = currencies.getDefault();
        source.sendSuccess(() -> Component.literal("§6=== Currencies ==="), false);
        for (Currency c : all) {
            String marker = c.id().equals(def.id()) ? " §a(default)" : "";
            source.sendSuccess(() -> Component.literal(
                "§f" + c.symbol() + " " + c.name() + " §7(" + c.id() + ")" + marker
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int convert(CommandSourceStack source, ServerPlayer player, double amount,
                               String fromId, String toId,
                               CurrencyRegistry currencies, ExchangeService exchange,
                               PermissionChecker permissions) {
        if (!permissions.hasPermission(player, "economy.exchange")) {
            source.sendFailure(Component.literal("You don't have permission to exchange currencies"));
            return 0;
        }

        Currency from = currencies.getById(fromId);
        Currency to = currencies.getById(toId);
        if (from == null || to == null) {
            source.sendFailure(Component.literal("Unknown currency"));
            return 0;
        }

        var result = exchange.convert(player.getUUID(), BigDecimal.valueOf(amount), from, to);
        if (result.successful()) {
            source.sendSuccess(() -> Component.literal(
                "Converted " + amount + " " + from.symbol() + " to " + to.symbol()
            ), false);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }
}
```

- [ ] **Step 5: Create EcoAdminCommand**

Create `economy-core/src/main/java/net/ecocraft/core/command/EcoAdminCommand.java`:
```java
package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandDispatcher;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;

public class EcoAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                EconomyProvider economy,
                                CurrencyRegistry currencies,
                                PermissionChecker permissions) {
        dispatcher.register(Commands.literal("eco")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                            return give(ctx.getSource(), target, amount, economy, currencies);
                        })
                    )
                )
            )
            .then(Commands.literal("take")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                            return take(ctx.getSource(), target, amount, economy, currencies);
                        })
                    )
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                            return set(ctx.getSource(), target, amount, economy, currencies);
                        })
                    )
                )
            )
        );
    }

    private static int give(CommandSourceStack source, ServerPlayer target, double amount,
                            EconomyProvider economy, CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        economy.deposit(target.getUUID(), BigDecimal.valueOf(amount), currency);
        source.sendSuccess(() -> Component.literal(
            "Gave " + amount + " " + currency.symbol() + " to " + target.getName().getString()
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int take(CommandSourceStack source, ServerPlayer target, double amount,
                            EconomyProvider economy, CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        var result = economy.withdraw(target.getUUID(), BigDecimal.valueOf(amount), currency);
        if (result.successful()) {
            source.sendSuccess(() -> Component.literal(
                "Took " + amount + " " + currency.symbol() + " from " + target.getName().getString()
            ), true);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }

    private static int set(CommandSourceStack source, ServerPlayer target, double amount,
                           EconomyProvider economy, CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        // Withdraw all, then deposit new amount
        BigDecimal current = economy.getBalance(target.getUUID(), currency);
        if (current.signum() > 0) {
            economy.withdraw(target.getUUID(), current, currency);
        }
        if (BigDecimal.valueOf(amount).signum() > 0) {
            economy.deposit(target.getUUID(), BigDecimal.valueOf(amount), currency);
        }
        source.sendSuccess(() -> Component.literal(
            "Set " + target.getName().getString() + "'s balance to " + amount + " " + currency.symbol()
        ), true);
        return Command.SINGLE_SUCCESS;
    }
}
```

- [ ] **Step 6: Verify build**

Run: `./gradlew :economy-core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add economy-core/src/
git commit -m "feat(economy-core): add /balance, /pay, /currency, /eco admin commands"
```

---

### Task 6: Vault Block

**Files:**
- Create: `economy-core/src/main/java/net/ecocraft/core/vault/VaultBlock.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/vault/VaultBlockEntity.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/vault/VaultMenu.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/vault/VaultScreen.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/registry/EcoRegistries.java` — register vault block, item, entity, menu

- [ ] **Step 1: Register vault block in EcoRegistries**

Add to `EcoRegistries.java`:
```java
import net.ecocraft.core.vault.VaultBlock;
import net.ecocraft.core.vault.VaultBlockEntity;
import net.ecocraft.core.vault.VaultMenu;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import java.util.function.Supplier;

// Inside the class:
public static final DeferredBlock<VaultBlock> VAULT_BLOCK =
    BLOCKS.register("vault_block", () -> new VaultBlock(
        BlockBehaviour.Properties.of().strength(3.0f).requiresCorrectToolForDrops()
    ));

public static final DeferredItem<BlockItem> VAULT_BLOCK_ITEM =
    ITEMS.register("vault_block", () -> new BlockItem(
        VAULT_BLOCK.get(), new Item.Properties()
    ));

public static final Supplier<BlockEntityType<VaultBlockEntity>> VAULT_BLOCK_ENTITY =
    BLOCK_ENTITIES.register("vault_block_entity", () ->
        BlockEntityType.Builder.of(VaultBlockEntity::new, VAULT_BLOCK.get()).build(null)
    );

public static final Supplier<MenuType<VaultMenu>> VAULT_MENU =
    MENUS.register("vault_menu", () ->
        new MenuType<>(VaultMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );
```

- [ ] **Step 2: Create VaultBlock**

Create `economy-core/src/main/java/net/ecocraft/core/vault/VaultBlock.java`:
```java
package net.ecocraft.core.vault;

import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class VaultBlock extends BaseEntityBlock {

    public VaultBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new VaultMenu(id, inv),
                Component.translatable("container.ecocraft_core.vault")
            ));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
```

- [ ] **Step 3: Create VaultBlockEntity**

Create `economy-core/src/main/java/net/ecocraft/core/vault/VaultBlockEntity.java`:
```java
package net.ecocraft.core.vault;

import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class VaultBlockEntity extends BlockEntity {

    private @Nullable UUID ownerUuid;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        super(EcoRegistries.VAULT_BLOCK_ENTITY.get(), pos, state);
    }

    public @Nullable UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
    }
}
```

- [ ] **Step 4: Create VaultMenu**

Create `economy-core/src/main/java/net/ecocraft/core/vault/VaultMenu.java`:
```java
package net.ecocraft.core.vault;

import net.ecocraft.core.registry.EcoRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class VaultMenu extends AbstractContainerMenu {

    public VaultMenu(int containerId, Inventory playerInventory) {
        super(EcoRegistries.VAULT_MENU.get(), containerId);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
```

- [ ] **Step 5: Create VaultScreen (client-side)**

Create `economy-core/src/main/java/net/ecocraft/core/vault/VaultScreen.java`:
```java
package net.ecocraft.core.vault;

import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class VaultScreen extends AbstractContainerScreen<VaultMenu> {

    public VaultScreen(VaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 200;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        EcoTheme.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight,
            EcoColors.BG_DARKEST, EcoColors.BORDER_GOLD);

        // Title
        graphics.drawString(font, title, leftPos + 8, topPos + 8, EcoColors.GOLD, false);
        EcoTheme.drawGoldSeparator(graphics, leftPos + 4, topPos + 20, imageWidth - 8);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }
}
```

- [ ] **Step 6: Create block model and lang files**

Create `economy-core/src/main/resources/assets/ecocraft_core/blockstates/vault_block.json`:
```json
{
  "variants": {
    "": { "model": "ecocraft_core:block/vault_block" }
  }
}
```

Create `economy-core/src/main/resources/assets/ecocraft_core/models/block/vault_block.json`:
```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "minecraft:block/gold_block"
  }
}
```

Create `economy-core/src/main/resources/assets/ecocraft_core/models/item/vault_block.json`:
```json
{
  "parent": "ecocraft_core:block/vault_block"
}
```

Create `economy-core/src/main/resources/assets/ecocraft_core/lang/en_us.json`:
```json
{
  "block.ecocraft_core.vault_block": "Vault",
  "container.ecocraft_core.vault": "Vault",
  "itemGroup.ecocraft_core": "EcoCraft Economy"
}
```

Create `economy-core/src/main/resources/assets/ecocraft_core/lang/fr_fr.json`:
```json
{
  "block.ecocraft_core.vault_block": "Coffre-fort",
  "container.ecocraft_core.vault": "Coffre-fort",
  "itemGroup.ecocraft_core": "EcoCraft Économie"
}
```

- [ ] **Step 7: Verify build**

Run: `./gradlew :economy-core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add economy-core/src/
git commit -m "feat(economy-core): add vault block with entity, menu, screen, and assets"
```

---

### Task 7: Wire Everything Together — Server Lifecycle

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoCraftCoreMod.java` — add event handlers for server start/stop, command registration, player join
- Create: `economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java`

- [ ] **Step 1: Create EcoServerEvents**

Create `economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java`:
```java
package net.ecocraft.core;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.transaction.TransactionLog;
import net.ecocraft.core.command.EcoCommands;
import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.impl.*;
import net.ecocraft.core.permission.DefaultPermissionChecker;
import net.ecocraft.core.permission.PermissionChecker;
import net.ecocraft.core.storage.StorageManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.math.BigDecimal;

@EventBusSubscriber(modid = EcoCraftCoreMod.MOD_ID)
public class EcoServerEvents {

    private static final StorageManager storage = new StorageManager();
    private static CurrencyRegistryImpl currencyRegistry;
    private static EconomyProviderImpl economyProvider;
    private static ExchangeServiceImpl exchangeService;
    private static TransactionLogImpl transactionLog;
    private static PermissionChecker permissions;

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        var server = event.getServer();
        var worldDir = server.overworld().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);

        // Initialize storage
        storage.initialize(worldDir);

        // Initialize currency registry with default currency from config
        currencyRegistry = new CurrencyRegistryImpl();
        var defaultCurrency = Currency.virtual(
            EcoConfig.CONFIG.defaultCurrencyId.get(),
            EcoConfig.CONFIG.defaultCurrencyName.get(),
            EcoConfig.CONFIG.defaultCurrencySymbol.get(),
            EcoConfig.CONFIG.defaultCurrencyDecimals.get()
        );
        currencyRegistry.register(defaultCurrency);

        // Initialize services
        economyProvider = new EconomyProviderImpl(storage.getProvider(), currencyRegistry);
        exchangeService = new ExchangeServiceImpl(economyProvider);
        transactionLog = new TransactionLogImpl(storage.getProvider(), currencyRegistry);
        permissions = new DefaultPermissionChecker();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        storage.shutdown();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (economyProvider != null) {
            EcoCommands.register(event.getDispatcher(), economyProvider,
                currencyRegistry, exchangeService, permissions);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (economyProvider == null) return;
        var player = event.getEntity();
        var currency = currencyRegistry.getDefault();
        var balance = economyProvider.getBalance(player.getUUID(), currency);

        // Give starting balance to new players
        if (balance.compareTo(BigDecimal.ZERO) == 0) {
            double startingBalance = EcoConfig.CONFIG.startingBalance.get();
            if (startingBalance > 0) {
                economyProvider.deposit(player.getUUID(), BigDecimal.valueOf(startingBalance), currency);
            }
        }
    }

    // Accessors for other modules
    public static EconomyProvider getEconomy() { return economyProvider; }
    public static CurrencyRegistry getCurrencyRegistry() { return currencyRegistry; }
    public static ExchangeService getExchangeService() { return exchangeService; }
    public static TransactionLog getTransactionLog() { return transactionLog; }
    public static PermissionChecker getPermissions() { return permissions; }
}
```

- [ ] **Step 2: Register VaultScreen on client side**

Add to `EcoCraftCoreMod.java` or create a client events class. Update `EcoCraftCoreMod.java`:
```java
package net.ecocraft.core;

import net.ecocraft.core.config.EcoConfig;
import net.ecocraft.core.registry.EcoRegistries;
import net.ecocraft.core.vault.VaultScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(EcoCraftCoreMod.MOD_ID)
public class EcoCraftCoreMod {
    public static final String MOD_ID = "ecocraft_core";

    public EcoCraftCoreMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, EcoConfig.CONFIG_SPEC);
        EcoRegistries.register(modBus);
    }

    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(EcoRegistries.VAULT_MENU.get(), VaultScreen::new);
        }
    }
}
```

- [ ] **Step 3: Run full build and tests**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add economy-core/src/ settings.gradle gradle.properties
git commit -m "feat(economy-core): wire server lifecycle, commands, currency init, and vault screen registration"
```

---

### Task 8: Final Build Verification

- [ ] **Step 1: Run clean build with all tests**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Verify all JARs**

Run:
```bash
ls -la economy-api/build/libs/
ls -la gui-lib/build/libs/
ls -la economy-core/build/libs/
```

Expected: all three JARs present.

- [ ] **Step 3: Run all tests**

Run: `./gradlew test`
Expected: All tests pass (economy-api + economy-core).

- [ ] **Step 4: Commit if needed**

If any cleanup was needed, commit it.
