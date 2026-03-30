# Economy V2 Phase 4: Multi-Currency Commands + Exchange Persistence

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all economy commands support multiple currencies, persist exchange rates in SQLite, and route operations through external adapters when applicable.

**Architecture:** Commands gain an optional `[currency]` parameter. `EconomyProviderImpl` checks if a currency is external (via `EcoCraftCurrencyApi`) and delegates to the adapter. `ExchangeServiceImpl` persists rates in SQLite and computes cross-rates via the reference currency. Exchange limits and history logging are added.

**Tech Stack:** Java 21, NeoForge 1.21.1, JUnit 5, SQLite

---

## Scope

- Multi-currency support in all commands (`/balance`, `/pay`, `/eco`, `/currency`)
- `EconomyProviderImpl` routing to external adapters
- Exchange rate persistence in SQLite
- Cross-rate calculation via reference currency
- Exchange limits (min/max/daily)
- Exchange history logging (TransactionType.EXCHANGE)
- New commands: `/currency rate`, `/currency setrate`, `/currency history`

**NOT in scope:** Vault Block, Bureau de change (separate plans).

---

## File Map

### New files

| File | Responsibility |
|------|---------------|
| `economy-core/.../storage/ExchangeRateStore.java` | SQLite persistence for exchange rates + daily limits |
| `economy-core/src/test/.../impl/MultiCurrencyTest.java` | Tests for adapter routing |
| `economy-core/src/test/.../impl/ExchangeRatePersistenceTest.java` | Tests for rate persistence + cross-rate |

### Modified files

| File | Changes |
|------|---------|
| `economy-core/.../impl/EconomyProviderImpl.java` | Route to adapters for external currencies |
| `economy-core/.../impl/ExchangeServiceImpl.java` | Persist rates, cross-rate calc, limits, history |
| `economy-core/.../command/BalanceCommand.java` | Optional `[currency]` parameter |
| `economy-core/.../command/PayCommand.java` | Optional `[currency]` parameter |
| `economy-core/.../command/EcoAdminCommand.java` | Optional `[currency]` parameter |
| `economy-core/.../command/CurrencyCommand.java` | Add rate/setrate/history subcommands |
| `economy-core/.../storage/SqliteDatabaseProvider.java` | Add exchange_rates + exchange_daily_limits tables |
| `economy-core/.../storage/DatabaseProvider.java` | Add exchange rate methods |
| `economy-core/.../config/EcoConfig.java` | Add exchange config (fee, limits) |
| `economy-core/.../EcoServerEvents.java` | Load adapters + rates on startup, clear on stop |
| `economy-core/.../EcoServerContext.java` | Add ExchangeRateStore to context |

---

## Tasks

### Task 1: Exchange rate persistence in SQLite

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/storage/DatabaseProvider.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/storage/SqliteDatabaseProvider.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/storage/ExchangeRatePersistenceTest.java`

- [ ] **Step 1: Write tests for exchange rate persistence**

```java
package net.ecocraft.core.storage;

import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.*;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRatePersistenceTest {

    private SqliteDatabaseProvider db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecotest");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    @Test
    void saveAndLoadExchangeRate() {
        db.saveExchangeRate("gold", "spurs", new BigDecimal("0.1"), new BigDecimal("0.02"));
        var rate = db.getExchangeRate("gold", "spurs");
        assertNotNull(rate);
        assertEquals(new BigDecimal("0.1"), rate.rate());
        assertEquals(new BigDecimal("0.02"), rate.feeRate());
    }

    @Test
    void updateExchangeRate() {
        db.saveExchangeRate("gold", "spurs", new BigDecimal("0.1"), new BigDecimal("0.02"));
        db.saveExchangeRate("gold", "spurs", new BigDecimal("0.2"), new BigDecimal("0.03"));
        var rate = db.getExchangeRate("gold", "spurs");
        assertNotNull(rate);
        assertEquals(new BigDecimal("0.2"), rate.rate());
    }

    @Test
    void deleteExchangeRate() {
        db.saveExchangeRate("gold", "spurs", new BigDecimal("0.1"), new BigDecimal("0.02"));
        db.deleteExchangeRate("gold", "spurs");
        assertNull(db.getExchangeRate("gold", "spurs"));
    }

    @Test
    void listAllRates() {
        db.saveExchangeRate("gold", "spurs", new BigDecimal("0.1"), new BigDecimal("0.02"));
        db.saveExchangeRate("gold", "gems", new BigDecimal("5.0"), new BigDecimal("0.01"));
        var rates = db.getAllExchangeRates();
        assertEquals(2, rates.size());
    }

    @Test
    void nonExistentRateReturnsNull() {
        assertNull(db.getExchangeRate("gold", "nonexistent"));
    }

    @Test
    void dailyExchangeTracking() {
        db.recordDailyExchange("player1", "gold", "spurs", 500);
        long total = db.getDailyExchangeTotal("player1", "gold", "spurs");
        assertEquals(500, total);

        db.recordDailyExchange("player1", "gold", "spurs", 300);
        total = db.getDailyExchangeTotal("player1", "gold", "spurs");
        assertEquals(800, total);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :economy-core:test --tests "*ExchangeRatePersistenceTest*"`
Expected: FAIL — methods do not exist

- [ ] **Step 3: Add exchange rate methods to DatabaseProvider interface**

Add to `economy-core/src/main/java/net/ecocraft/core/storage/DatabaseProvider.java`:

```java
// --- Exchange rates ---

record StoredExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate, BigDecimal feeRate) {}

void saveExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate, BigDecimal feeRate);
StoredExchangeRate getExchangeRate(String fromCurrency, String toCurrency);
void deleteExchangeRate(String fromCurrency, String toCurrency);
List<StoredExchangeRate> getAllExchangeRates();

// --- Daily exchange limits ---

void recordDailyExchange(String playerUuid, String fromCurrency, String toCurrency, long amount);
long getDailyExchangeTotal(String playerUuid, String fromCurrency, String toCurrency);
```

- [ ] **Step 4: Implement in SqliteDatabaseProvider**

Add table creation in `initialize()`:
```java
stmt.execute("""
    CREATE TABLE IF NOT EXISTS exchange_rates (
        from_currency TEXT NOT NULL,
        to_currency TEXT NOT NULL,
        rate TEXT NOT NULL,
        fee_rate TEXT NOT NULL DEFAULT '0',
        PRIMARY KEY (from_currency, to_currency)
    )""");

stmt.execute("""
    CREATE TABLE IF NOT EXISTS exchange_daily (
        player_uuid TEXT NOT NULL,
        from_currency TEXT NOT NULL,
        to_currency TEXT NOT NULL,
        day TEXT NOT NULL,
        total_amount INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (player_uuid, from_currency, to_currency, day)
    )""");
```

Implement all 6 methods with prepared statements. Daily exchange uses `LocalDate.now().toString()` as the `day` key.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :economy-core:test --tests "*ExchangeRatePersistenceTest*"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(economy-core): persist exchange rates and daily limits in SQLite"
```

---

### Task 2: ExchangeService with cross-rate calculation and limits

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/impl/ExchangeServiceImpl.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/config/EcoConfig.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/impl/ExchangeServiceV2Test.java`

- [ ] **Step 1: Write tests for cross-rate calculation**

```java
package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.core.storage.DatabaseProvider;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeServiceV2Test {

    private SqliteDatabaseProvider db;
    private CurrencyRegistryImpl registry;
    private EconomyProviderImpl economy;
    private ExchangeServiceImpl exchange;
    private Path tempDir;

    static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 0);
    static final Currency SPURS = Currency.builder("spurs", "Spurs", "S").exchangeable(true).referenceRate(0.1).build();
    static final Currency GEMS = Currency.builder("gems", "Gems", "💎").exchangeable(true).referenceRate(5.0).build();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecotest");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        registry.register(SPURS);
        registry.register(GEMS);
        economy = new EconomyProviderImpl(db, registry);
        exchange = new ExchangeServiceImpl(economy, db, registry);
    }

    @AfterEach
    void tearDown() { db.shutdown(); }

    @Test
    void crossRateCalculation() {
        // SPURS rate = 0.1 (1 spur = 0.1 gold), GEMS rate = 5.0 (1 gem = 5 gold)
        // So 100 spurs = 10 gold = 2 gems
        ExchangeRate rate = exchange.getRate(SPURS, GEMS);
        assertNotNull(rate);
        BigDecimal converted = rate.convert(new BigDecimal("100"));
        assertEquals(new BigDecimal("2"), converted);
    }

    @Test
    void convertWithSufficientFunds() {
        UUID player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("1000"), SPURS);

        TransactionResult result = exchange.convert(player, new BigDecimal("100"), SPURS, GEMS);
        assertTrue(result.successful());

        BigDecimal spursBalance = economy.getVirtualBalance(player, SPURS);
        BigDecimal gemsBalance = economy.getVirtualBalance(player, GEMS);
        assertEquals(new BigDecimal("900"), spursBalance);
        assertTrue(gemsBalance.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void convertNonExchangeableFails() {
        UUID player = UUID.randomUUID();
        Currency noExchange = Currency.virtual("nope", "Nope", "N", 0);
        registry.register(noExchange);
        economy.deposit(player, new BigDecimal("1000"), noExchange);

        TransactionResult result = exchange.convert(player, new BigDecimal("100"), noExchange, GOLD);
        assertFalse(result.successful());
    }

    @Test
    void persistedRateOverridesCrossRate() {
        // Set a manual rate that differs from the calculated cross-rate
        db.saveExchangeRate("spurs", "gems", new BigDecimal("0.05"), new BigDecimal("0"));
        exchange.loadRatesFromStorage();

        ExchangeRate rate = exchange.getRate(SPURS, GEMS);
        assertNotNull(rate);
        // Manual rate: 1 spur = 0.05 gems, so 100 spurs = 5 gems
        BigDecimal converted = rate.convert(new BigDecimal("100"));
        assertEquals(new BigDecimal("5"), converted);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :economy-core:test --tests "*ExchangeServiceV2Test*"`
Expected: FAIL — constructor mismatch

- [ ] **Step 3: Update ExchangeServiceImpl**

Modify `economy-core/src/main/java/net/ecocraft/core/impl/ExchangeServiceImpl.java`:

New constructor: `ExchangeServiceImpl(EconomyProvider economy, DatabaseProvider db, CurrencyRegistry registry)`

Key changes:
- Load persisted rates from DB on construction via `loadRatesFromStorage()`
- `getRate()`: check persisted rates first, then compute cross-rate via `referenceRate`
- `convert()`: validate exchangeable flag, check daily limits, log EXCHANGE transaction
- Cross-rate formula: `rate = fromCurrency.referenceRate / toCurrency.referenceRate`
- New methods: `loadRatesFromStorage()`, `saveRate()`, `computeCrossRate()`

- [ ] **Step 4: Add exchange config to EcoConfig**

Add to `economy-core/src/main/java/net/ecocraft/core/config/EcoConfig.java`:

```java
// Exchange
public final DoubleValue exchangeGlobalFeePercent;   // default: 2.0
public final LongValue exchangeMinAmount;              // default: 0 (disabled)
public final LongValue exchangeMaxAmount;              // default: 0 (disabled)
public final LongValue exchangeDailyLimitPerPlayer;    // default: 0 (disabled)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :economy-core:test --tests "*ExchangeServiceV2Test*"`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(economy-core): ExchangeService with cross-rates, persistence, and limits"
```

---

### Task 3: EconomyProvider adapter routing

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/impl/EconomyProviderImpl.java`
- Create: `economy-core/src/test/java/net/ecocraft/core/impl/AdapterRoutingTest.java`

- [ ] **Step 1: Write tests for adapter routing**

```java
package net.ecocraft.core.impl;

import net.ecocraft.api.EcoCraftCurrencyApi;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.ExternalCurrencyAdapter;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.core.storage.SqliteDatabaseProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AdapterRoutingTest {

    private SqliteDatabaseProvider db;
    private CurrencyRegistryImpl registry;
    private EconomyProviderImpl economy;
    private Path tempDir;

    static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 0);
    static final Currency EXTERNAL = Currency.builder("ext", "External", "E").exchangeable(true).build();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ecotest");
        db = new SqliteDatabaseProvider(tempDir.resolve("test.db"));
        db.initialize();
        registry = new CurrencyRegistryImpl();
        registry.register(GOLD);
        registry.register(EXTERNAL);
        economy = new EconomyProviderImpl(db, registry);
        EcoCraftCurrencyApi.clearAdapters();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
        EcoCraftCurrencyApi.clearAdapters();
    }

    @Test
    void nativeCurrencyUsesDatabase() {
        UUID player = UUID.randomUUID();
        economy.deposit(player, new BigDecimal("100"), GOLD);
        assertEquals(new BigDecimal("100"), economy.getVirtualBalance(player, GOLD));
    }

    @Test
    void externalCurrencyRoutesToAdapter() {
        AtomicLong balance = new AtomicLong(500);
        EcoCraftCurrencyApi.registerAdapter(new ExternalCurrencyAdapter() {
            public String modId() { return "test"; }
            public Currency getCurrency() { return EXTERNAL; }
            public long getBalance(UUID p) { return balance.get(); }
            public boolean withdraw(UUID p, long amt) { balance.addAndGet(-amt); return true; }
            public boolean deposit(UUID p, long amt) { balance.addAndGet(amt); return true; }
            public boolean canAfford(UUID p, long amt) { return balance.get() >= amt; }
        });

        UUID player = UUID.randomUUID();
        BigDecimal bal = economy.getVirtualBalance(player, EXTERNAL);
        assertEquals(new BigDecimal("500"), bal);

        TransactionResult result = economy.withdraw(player, new BigDecimal("200"), EXTERNAL);
        assertTrue(result.successful());
        assertEquals(300, balance.get());
    }

    @Test
    void canAffordDelegatesToAdapter() {
        AtomicLong balance = new AtomicLong(50);
        EcoCraftCurrencyApi.registerAdapter(new ExternalCurrencyAdapter() {
            public String modId() { return "test"; }
            public Currency getCurrency() { return EXTERNAL; }
            public long getBalance(UUID p) { return balance.get(); }
            public boolean withdraw(UUID p, long amt) { return false; }
            public boolean deposit(UUID p, long amt) { return false; }
            public boolean canAfford(UUID p, long amt) { return balance.get() >= amt; }
        });

        UUID player = UUID.randomUUID();
        assertTrue(economy.canAfford(player, new BigDecimal("50"), EXTERNAL));
        assertFalse(economy.canAfford(player, new BigDecimal("51"), EXTERNAL));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :economy-core:test --tests "*AdapterRoutingTest*"`
Expected: FAIL — adapter routing not implemented

- [ ] **Step 3: Implement adapter routing in EconomyProviderImpl**

In each method (`getVirtualBalance`, `withdraw`, `deposit`, `canAfford`, `transfer`), add a check at the top:

```java
ExternalCurrencyAdapter adapter = EcoCraftCurrencyApi.getAdapterForCurrency(currency.id());
if (adapter != null) {
    // Delegate to adapter
    // Convert BigDecimal to long for adapter calls
    // Convert long back to BigDecimal for return values
    // Still fire events and log transactions
}
// Otherwise: existing database logic
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :economy-core:test --tests "*AdapterRoutingTest*"`
Expected: ALL PASS

- [ ] **Step 5: Run full test suite**

Run: `./gradlew :economy-core:test`
Expected: ALL PASS (no regression)

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat(economy-core): EconomyProvider routes to external adapters for non-native currencies"
```

---

### Task 4: Multi-currency commands

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/BalanceCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/PayCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/EcoAdminCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/CurrencyCommand.java`

- [ ] **Step 1: Add optional currency argument helper**

Create a shared helper method in `EcoCommands.java` or use inline in each command:

```java
// Argument: .then(argument("currency", StringArgumentType.word()).suggests(currencySuggestions))
// Resolver: String currencyId = StringArgumentType.getString(ctx, "currency"); Currency c = registry.getById(currencyId);
// Suggestion provider: (ctx, builder) -> { for (Currency c : registry.listAll()) builder.suggest(c.id()); return builder.buildFuture(); }
```

- [ ] **Step 2: Update BalanceCommand**

Add optional `[currency]` to `/balance`, `/balance of <name>`, `/balance list`:
- `/balance` → shows default currency balance
- `/balance gold` → shows gold balance
- `/balance of Steve spurs` → shows Steve's spurs balance
- Use `CurrencyFormatter.format()` for display

- [ ] **Step 3: Update PayCommand**

Add optional `[currency]` to `/pay <player> <amount> [currency]`:
- Default: uses default currency
- With currency: uses specified currency

- [ ] **Step 4: Update EcoAdminCommand**

Add optional `[currency]` to `/eco give/take/set <player> <amount> [currency]`

- [ ] **Step 5: Update CurrencyCommand**

Add new subcommands:
- `/currency rate <currency>` — show exchange rate to reference
- `/currency setrate <currency> <rate>` — admin: set exchange rate (requires ADMIN_SET perm)
- `/currency history [player]` — admin: show recent exchange history

- [ ] **Step 6: Build and test**

Run: `./gradlew :economy-core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(economy-core): multi-currency support in all commands + exchange rate admin"
```

---

### Task 5: Wire everything in EcoServerEvents

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoServerContext.java`

- [ ] **Step 1: Update EcoServerContext**

Add `DatabaseProvider` direct reference (for exchange rate storage access):
```java
public DatabaseProvider getDatabaseProvider() { return storage.getProvider(); }
```

- [ ] **Step 2: Update EcoServerEvents.onServerStarting**

After creating the registry and economy provider:
1. Load exchange rates from DB: `exchangeService.loadRatesFromStorage()`
2. Register adapters from `EcoCraftCurrencyApi.getAdapters()` into the `CurrencyRegistry`
3. Pass `DatabaseProvider` to `ExchangeServiceImpl` constructor

- [ ] **Step 3: Update EcoServerEvents.onServerStopped**

Add: `EcoCraftCurrencyApi.clearAdapters()`

- [ ] **Step 4: Full build + deploy**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

Deploy:
```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
cp mail/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 5: Commit and push**

```bash
git add -A
git commit -m "feat(economy-core): wire multi-currency + exchange persistence in server lifecycle"
git push origin master
```

---

## What's Next

| Plan | Content | Depends on |
|------|---------|------------|
| **Phase 5** | Vault Block refonte (multi-currency, item values, withdrawal priority) | This plan |
| **Phase 6** | Bureau de change (block + NPC + UI) | This plan |
| **Phase 7** | Numismatics adapter | This plan |
| **Phase 8** | AH + Mail migration to Currency V2 | Phase 5+6 |
