# Phase 1: Project Setup + Economy API + GUI Library — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Set up the NeoForge multi-module project and implement the economy-api interfaces and gui-lib reusable components.

**Architecture:** Gradle multi-module mono-repo with ModDevGradle 2.0.141 for NeoForge 1.21.1. `economy-api` is a pure-interface module using NeoForm (vanilla mode) since it only needs Minecraft types but no loader. `gui-lib` uses full NeoForge since it needs client-side GUI classes. Both modules produce independent JARs.

**Tech Stack:** Java 21, NeoForge 21.1.221, Minecraft 1.21.1, ModDevGradle 2.0.141, Parchment mappings 2024.11.17, JUnit 5 for unit tests.

---

## File Structure

```
minecraft-economy/
├── settings.gradle
├── build.gradle                          # Root: common config
├── gradle.properties                     # Shared version properties
│
├── economy-api/
│   ├── build.gradle                      # Vanilla mode (neoFormVersion)
│   └── src/
│       ├── main/java/net/ecocraft/api/
│       │   ├── currency/
│       │   │   ├── Currency.java              # Currency record
│       │   │   └── CurrencyRegistry.java      # Registry interface
│       │   ├── account/
│       │   │   └── Account.java               # Account record
│       │   ├── transaction/
│       │   │   ├── Transaction.java           # Transaction record
│       │   │   ├── TransactionType.java       # Enum
│       │   │   ├── TransactionResult.java     # Result record
│       │   │   ├── TransactionFilter.java     # Filter record
│       │   │   └── TransactionLog.java        # History interface
│       │   ├── exchange/
│       │   │   ├── ExchangeRate.java          # Rate record
│       │   │   └── ExchangeService.java       # Exchange interface
│       │   ├── EconomyProvider.java           # Core economy interface
│       │   └── Page.java                      # Pagination helper
│       └── test/java/net/ecocraft/api/
│           ├── currency/CurrencyTest.java
│           ├── account/AccountTest.java
│           ├── transaction/TransactionTest.java
│           └── exchange/ExchangeRateTest.java
│
├── gui-lib/
│   ├── build.gradle                      # Full NeoForge
│   └── src/
│       ├── main/java/net/ecocraft/gui/
│       │   ├── EcoCraftGuiMod.java            # @Mod entry (client dist)
│       │   ├── theme/
│       │   │   ├── EcoColors.java             # Color palette constants
│       │   │   └── EcoTheme.java              # Theme utilities (draw helpers)
│       │   ├── widget/
│       │   │   ├── EcoScrollbar.java          # Scrollbar widget
│       │   │   ├── EcoButton.java             # Styled button variants
│       │   │   ├── EcoTabBar.java             # Tab navigation
│       │   │   ├── EcoSearchBar.java          # Search input
│       │   │   ├── EcoFilterTags.java         # Clickable filter pills
│       │   │   ├── EcoStatCard.java           # Stat display card
│       │   │   └── EcoItemSlot.java           # Item display with rarity border
│       │   └── table/
│       │       ├── TableColumn.java           # Column definition
│       │       ├── TableRow.java              # Row data holder
│       │       └── EcoPaginatedTable.java     # Paginated sortable table
│       ├── main/resources/
│       │   └── assets/ecocraft_gui/
│       │       └── textures/gui/
│       │           └── widgets.png            # Sprite sheet for scrollbar etc.
│       └── main/templates/
│           └── META-INF/
│               └── neoforge.mods.toml         # gui-lib mod metadata
```

---

### Task 1: Gradle Multi-Module Scaffolding

**Files:**
- Create: `settings.gradle`
- Create: `build.gradle`
- Create: `gradle.properties`
- Create: `economy-api/build.gradle`
- Create: `gui-lib/build.gradle`
- Create: `.gitignore`

- [ ] **Step 1: Install Gradle wrapper**

Run:
```bash
cd /home/florian/perso/minecraft
gradle wrapper --gradle-version 8.10
```

If `gradle` is not installed globally, download the wrapper JAR manually:
```bash
mkdir -p gradle/wrapper
curl -L -o gradle/wrapper/gradle-wrapper.jar https://github.com/gradle/gradle/raw/v8.10.0/gradle/wrapper/gradle-wrapper.jar
cat > gradle/wrapper/gradle-wrapper.properties << 'PROPS'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
PROPS
cat > gradlew << 'SCRIPT'
#!/bin/sh
exec java -jar "$0/../gradle/wrapper/gradle-wrapper.jar" "$@"
SCRIPT
chmod +x gradlew
```

- [ ] **Step 2: Create .gitignore**

Create `.gitignore`:
```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.vscode/
.settings/
.classpath
.project
.factorypath
bin/

# NeoForge
run/
runs/
src/generated/
logs/

# OS
.DS_Store
Thumbs.db

# Superpowers
.superpowers/
```

- [ ] **Step 3: Create gradle.properties**

Create `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.daemon=true
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true

# Minecraft / NeoForge
minecraft_version=1.21.1
minecraft_version_range=[1.21.1]
neo_version=21.1.221
neoform_version=1.21.1-20240808.144430
loader_version_range=[1,)

# Parchment mappings (human-readable parameter names)
parchment_minecraft_version=1.21.1
parchment_mappings_version=2024.11.17

# Project
group_id=net.ecocraft
mod_version=0.1.0

# economy-api
economy_api_mod_id=ecocraft_api
economy_api_mod_name=EcoCraft Economy API

# gui-lib
gui_lib_mod_id=ecocraft_gui
gui_lib_mod_name=EcoCraft GUI Library
```

- [ ] **Step 4: Create settings.gradle**

Create `settings.gradle`:
```groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

rootProject.name = 'minecraft-economy'

include ':economy-api'
include ':gui-lib'
```

- [ ] **Step 5: Create root build.gradle**

Create `build.gradle`:
```groovy
subprojects {
    apply plugin: 'java-library'

    group = project.group_id
    version = project.mod_version

    java.toolchain.languageVersion = JavaLanguageVersion.of(21)

    repositories {
        mavenCentral()
    }

    dependencies {
        testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    }

    tasks.withType(JavaCompile).configureEach {
        options.encoding = 'UTF-8'
    }

    tasks.withType(Test).configureEach {
        useJUnitPlatform()
    }
}
```

- [ ] **Step 6: Create economy-api/build.gradle**

Create `economy-api/build.gradle`:
```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

base {
    archivesName = 'ecocraft-economy-api'
}

neoForge {
    neoFormVersion = project.neoform_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }
}
```

- [ ] **Step 7: Create gui-lib/build.gradle**

Create `gui-lib/build.gradle`:
```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

base {
    archivesName = 'ecocraft-gui-lib'
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
    }

    mods {
        "${project.gui_lib_mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    implementation project(':economy-api')
}

var generateModMetadata = tasks.register("generateModMetadata", ProcessResources) {
    var replaceProperties = [
        minecraft_version      : project.minecraft_version,
        minecraft_version_range: project.minecraft_version_range,
        neo_version            : project.neo_version,
        loader_version_range   : project.loader_version_range,
        mod_id                 : project.gui_lib_mod_id,
        mod_name               : project.gui_lib_mod_name,
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

- [ ] **Step 8: Create gui-lib mod metadata template**

Create `gui-lib/src/main/templates/META-INF/neoforge.mods.toml`:
```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="MIT"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
description='''
Reusable GUI component library with WoW-inspired dark theme for Minecraft mods.
'''

[[dependencies.${mod_id}]]
modId="neoforge"
type="required"
versionRange="[${neo_version},)"
ordering="NONE"
side="CLIENT"

[[dependencies.${mod_id}]]
modId="minecraft"
type="required"
versionRange="${minecraft_version_range}"
ordering="NONE"
side="CLIENT"
```

- [ ] **Step 9: Create gui-lib mod entry point**

Create `gui-lib/src/main/java/net/ecocraft/gui/EcoCraftGuiMod.java`:
```java
package net.ecocraft.gui;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EcoCraftGuiMod.MOD_ID)
public class EcoCraftGuiMod {
    public static final String MOD_ID = "ecocraft_gui";

    public EcoCraftGuiMod(IEventBus modBus) {
    }
}
```

- [ ] **Step 10: Verify the build compiles**

Run:
```bash
cd /home/florian/perso/minecraft
./gradlew build
```

Expected: BUILD SUCCESSFUL (both modules compile, no tests yet).

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: scaffold NeoForge multi-module project

Sets up Gradle multi-module structure with ModDevGradle 2.0.141
for NeoForge 1.21.1 (Minecraft 1.21.1, Java 21).
Modules: economy-api (vanilla mode), gui-lib (full NeoForge)."
```

---

### Task 2: Economy API — Currency Model

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/currency/Currency.java`
- Create: `economy-api/src/test/java/net/ecocraft/api/currency/CurrencyTest.java`

- [ ] **Step 1: Write the failing test**

Create `economy-api/src/test/java/net/ecocraft/api/currency/CurrencyTest.java`:
```java
package net.ecocraft.api.currency;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {

    @Test
    void virtualCurrencyHasCorrectDefaults() {
        var gold = Currency.virtual("gold", "Or", "⛁", 2);

        assertEquals("gold", gold.id());
        assertEquals("Or", gold.name());
        assertEquals("⛁", gold.symbol());
        assertEquals(2, gold.decimals());
        assertFalse(gold.physical());
        assertNull(gold.itemId());
    }

    @Test
    void physicalCurrencyLinksToItem() {
        var coins = Currency.physical("coins", "Pièces", "$", 0, "lightmanscurrency:coin_gold");

        assertEquals("coins", coins.id());
        assertTrue(coins.physical());
        assertEquals("lightmanscurrency:coin_gold", coins.itemId());
    }

    @Test
    void decimalsCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            Currency.virtual("bad", "Bad", "X", -1)
        );
    }

    @Test
    void idCannotBeBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            Currency.virtual("", "Empty", "X", 0)
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :economy-api:test
```

Expected: FAIL — `Currency` class does not exist.

- [ ] **Step 3: Write the Currency record**

Create `economy-api/src/main/java/net/ecocraft/api/currency/Currency.java`:
```java
package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;

/**
 * Represents a currency in the economy system.
 *
 * @param id       Unique identifier (e.g., "gold", "diamonds")
 * @param name     Display name (e.g., "Or", "Diamants")
 * @param symbol   Short symbol (e.g., "⛁", "$")
 * @param decimals Number of decimal places (0 = whole coins)
 * @param physical Whether this currency is backed by an in-game item
 * @param itemId   If physical, the item's registry name (e.g., "minecraft:gold_ingot")
 */
public record Currency(
        String id,
        String name,
        String symbol,
        int decimals,
        boolean physical,
        @Nullable String itemId
) {
    public Currency {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Currency id cannot be blank");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("Decimals cannot be negative");
        }
        if (physical && (itemId == null || itemId.isBlank())) {
            throw new IllegalArgumentException("Physical currency must have an itemId");
        }
    }

    public static Currency virtual(String id, String name, String symbol, int decimals) {
        return new Currency(id, name, symbol, decimals, false, null);
    }

    public static Currency physical(String id, String name, String symbol, int decimals, String itemId) {
        return new Currency(id, name, symbol, decimals, true, itemId);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew :economy-api:test
```

Expected: PASS — all 4 tests green.

- [ ] **Step 5: Commit**

```bash
git add economy-api/src/
git commit -m "feat(economy-api): add Currency record with virtual/physical factory methods"
```

---

### Task 3: Economy API — Account Model

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/account/Account.java`
- Create: `economy-api/src/test/java/net/ecocraft/api/account/AccountTest.java`

- [ ] **Step 1: Write the failing test**

Create `economy-api/src/test/java/net/ecocraft/api/account/AccountTest.java`:
```java
package net.ecocraft.api.account;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountTest {

    private static final Currency GOLD = Currency.virtual("gold", "Or", "⛁", 2);
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void totalBalanceIsSumOfVirtualAndVault() {
        var account = new Account(PLAYER, GOLD, new BigDecimal("100.50"), new BigDecimal("49.50"));

        assertEquals(new BigDecimal("150.00"), account.totalBalance());
    }

    @Test
    void newAccountStartsAtZero() {
        var account = Account.empty(PLAYER, GOLD);

        assertEquals(BigDecimal.ZERO, account.virtualBalance());
        assertEquals(BigDecimal.ZERO, account.vaultBalance());
        assertEquals(BigDecimal.ZERO, account.totalBalance());
    }

    @Test
    void balancesCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            new Account(PLAYER, GOLD, new BigDecimal("-1"), BigDecimal.ZERO)
        );
        assertThrows(IllegalArgumentException.class, () ->
            new Account(PLAYER, GOLD, BigDecimal.ZERO, new BigDecimal("-1"))
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :economy-api:test
```

Expected: FAIL — `Account` class does not exist.

- [ ] **Step 3: Write the Account record**

Create `economy-api/src/main/java/net/ecocraft/api/account/Account.java`:
```java
package net.ecocraft.api.account;

import net.ecocraft.api.currency.Currency;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A player's account for a specific currency.
 * Tracks both virtual balance (pure digital) and vault balance (physical items stored).
 *
 * @param owner          Player UUID
 * @param currency       The currency this account holds
 * @param virtualBalance Pure virtual balance (earned via sales, /pay, etc.)
 * @param vaultBalance   Value of physical items stored in the vault block
 */
public record Account(
        UUID owner,
        Currency currency,
        BigDecimal virtualBalance,
        BigDecimal vaultBalance
) {
    public Account {
        if (virtualBalance.signum() < 0) {
            throw new IllegalArgumentException("Virtual balance cannot be negative");
        }
        if (vaultBalance.signum() < 0) {
            throw new IllegalArgumentException("Vault balance cannot be negative");
        }
    }

    public BigDecimal totalBalance() {
        return virtualBalance.add(vaultBalance);
    }

    public static Account empty(UUID owner, Currency currency) {
        return new Account(owner, currency, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew :economy-api:test
```

Expected: PASS — all tests green.

- [ ] **Step 5: Commit**

```bash
git add economy-api/src/
git commit -m "feat(economy-api): add Account record with virtual/vault balance split"
```

---

### Task 4: Economy API — Transaction Model

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionType.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionResult.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/transaction/Transaction.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionFilter.java`
- Create: `economy-api/src/test/java/net/ecocraft/api/transaction/TransactionTest.java`

- [ ] **Step 1: Write the failing test**

Create `economy-api/src/test/java/net/ecocraft/api/transaction/TransactionTest.java`:
```java
package net.ecocraft.api.transaction;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransactionTest {

    private static final Currency GOLD = Currency.virtual("gold", "Or", "⛁", 2);

    @Test
    void transactionStoresAllFields() {
        var id = UUID.randomUUID();
        var from = UUID.randomUUID();
        var to = UUID.randomUUID();
        var now = Instant.now();

        var tx = new Transaction(id, from, to, new BigDecimal("50.00"), GOLD, TransactionType.PAYMENT, now);

        assertEquals(id, tx.id());
        assertEquals(from, tx.from());
        assertEquals(to, tx.to());
        assertEquals(new BigDecimal("50.00"), tx.amount());
        assertEquals(GOLD, tx.currency());
        assertEquals(TransactionType.PAYMENT, tx.type());
        assertEquals(now, tx.timestamp());
    }

    @Test
    void amountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
            new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ZERO, GOLD, TransactionType.PAYMENT, Instant.now())
        );
    }

    @Test
    void successResultCarriesTransaction() {
        var tx = new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            new BigDecimal("10"), GOLD, TransactionType.PAYMENT, Instant.now());
        var result = TransactionResult.success(tx);

        assertTrue(result.successful());
        assertEquals(tx, result.transaction());
        assertNull(result.errorMessage());
    }

    @Test
    void failureResultCarriesMessage() {
        var result = TransactionResult.failure("Insufficient funds");

        assertFalse(result.successful());
        assertNull(result.transaction());
        assertEquals("Insufficient funds", result.errorMessage());
    }

    @Test
    void filterBuilderSetsFields() {
        var player = UUID.randomUUID();
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-03-01T00:00:00Z");

        var filter = new TransactionFilter(player, TransactionType.HDV_SALE, from, to, 0, 20);

        assertEquals(player, filter.player());
        assertEquals(TransactionType.HDV_SALE, filter.type());
        assertEquals(0, filter.offset());
        assertEquals(20, filter.limit());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :economy-api:test
```

Expected: FAIL — classes do not exist.

- [ ] **Step 3: Write TransactionType enum**

Create `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionType.java`:
```java
package net.ecocraft.api.transaction;

public enum TransactionType {
    PAYMENT,
    EXCHANGE,
    TAX,
    DEPOSIT,
    WITHDRAWAL,
    HDV_SALE,
    HDV_PURCHASE,
    HDV_LISTING_FEE,
    HDV_EXPIRED_REFUND
}
```

- [ ] **Step 4: Write Transaction record**

Create `economy-api/src/main/java/net/ecocraft/api/transaction/Transaction.java`:
```java
package net.ecocraft.api.transaction;

import net.ecocraft.api.currency.Currency;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of a financial transaction.
 *
 * @param id        Unique transaction identifier
 * @param from      Sender UUID (null for system-generated, e.g., admin give)
 * @param to        Recipient UUID (null for system-consumed, e.g., tax)
 * @param amount    Transaction amount (must be positive)
 * @param currency  Currency used
 * @param type      Type of transaction
 * @param timestamp When the transaction occurred
 */
public record Transaction(
        UUID id,
        @Nullable UUID from,
        @Nullable UUID to,
        BigDecimal amount,
        Currency currency,
        TransactionType type,
        Instant timestamp
) {
    public Transaction {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
    }
}
```

- [ ] **Step 5: Write TransactionResult record**

Create `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionResult.java`:
```java
package net.ecocraft.api.transaction;

import org.jetbrains.annotations.Nullable;

/**
 * Result of an economy operation — either success with a transaction, or failure with a message.
 */
public record TransactionResult(
        boolean successful,
        @Nullable Transaction transaction,
        @Nullable String errorMessage
) {
    public static TransactionResult success(Transaction transaction) {
        return new TransactionResult(true, transaction, null);
    }

    public static TransactionResult failure(String errorMessage) {
        return new TransactionResult(false, null, errorMessage);
    }
}
```

- [ ] **Step 6: Write TransactionFilter record**

Create `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionFilter.java`:
```java
package net.ecocraft.api.transaction;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter criteria for querying transaction history.
 *
 * @param player UUID of the player (required)
 * @param type   Filter by transaction type (null = all types)
 * @param from   Start of time range (null = no lower bound)
 * @param to     End of time range (null = no upper bound)
 * @param offset Pagination offset
 * @param limit  Pagination page size
 */
public record TransactionFilter(
        UUID player,
        @Nullable TransactionType type,
        @Nullable Instant from,
        @Nullable Instant to,
        int offset,
        int limit
) {
}
```

- [ ] **Step 7: Run test to verify it passes**

Run:
```bash
./gradlew :economy-api:test
```

Expected: PASS — all tests green.

- [ ] **Step 8: Commit**

```bash
git add economy-api/src/
git commit -m "feat(economy-api): add Transaction, TransactionResult, TransactionFilter, TransactionType"
```

---

### Task 5: Economy API — Exchange Rate Model

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/exchange/ExchangeRate.java`
- Create: `economy-api/src/test/java/net/ecocraft/api/exchange/ExchangeRateTest.java`

- [ ] **Step 1: Write the failing test**

Create `economy-api/src/test/java/net/ecocraft/api/exchange/ExchangeRateTest.java`:
```java
package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRateTest {

    private static final Currency GOLD = Currency.virtual("gold", "Or", "⛁", 2);
    private static final Currency SILVER = Currency.virtual("silver", "Argent", "∘", 0);

    @Test
    void convertWithoutFee() {
        var rate = new ExchangeRate(GOLD, SILVER, new BigDecimal("100"), BigDecimal.ZERO);

        // 5 gold = 500 silver
        assertEquals(new BigDecimal("500"), rate.convert(new BigDecimal("5")));
    }

    @Test
    void convertWithFee() {
        // 5% fee
        var rate = new ExchangeRate(GOLD, SILVER, new BigDecimal("100"), new BigDecimal("0.05"));

        // 10 gold = 1000 silver - 5% = 950 silver
        assertEquals(new BigDecimal("950.00"), rate.convert(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void rateMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
            new ExchangeRate(GOLD, SILVER, BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    @Test
    void feeCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            new ExchangeRate(GOLD, SILVER, BigDecimal.ONE, new BigDecimal("-0.1"))
        );
    }

    @Test
    void cannotExchangeSameCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
            new ExchangeRate(GOLD, GOLD, BigDecimal.ONE, BigDecimal.ZERO)
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```bash
./gradlew :economy-api:test
```

Expected: FAIL — `ExchangeRate` does not exist.

- [ ] **Step 3: Write ExchangeRate record**

Create `economy-api/src/main/java/net/ecocraft/api/exchange/ExchangeRate.java`:
```java
package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;

import java.math.BigDecimal;

/**
 * Defines a conversion rate between two currencies.
 *
 * @param from Source currency
 * @param to   Target currency
 * @param rate Conversion rate (1 unit of 'from' = 'rate' units of 'to')
 * @param fee  Fee as a fraction (0.05 = 5%). Applied after conversion.
 */
public record ExchangeRate(
        Currency from,
        Currency to,
        BigDecimal rate,
        BigDecimal fee
) {
    public ExchangeRate {
        if (from.id().equals(to.id())) {
            throw new IllegalArgumentException("Cannot exchange same currency");
        }
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("Fee cannot be negative");
        }
    }

    /**
     * Convert an amount from the source currency to the target currency, applying the fee.
     *
     * @param amount Amount in the source currency
     * @return Amount in the target currency after fee
     */
    public BigDecimal convert(BigDecimal amount) {
        var converted = amount.multiply(rate);
        if (fee.signum() > 0) {
            var feeAmount = converted.multiply(fee);
            converted = converted.subtract(feeAmount);
        }
        return converted;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:
```bash
./gradlew :economy-api:test
```

Expected: PASS — all tests green.

- [ ] **Step 5: Commit**

```bash
git add economy-api/src/
git commit -m "feat(economy-api): add ExchangeRate with conversion and fee calculation"
```

---

### Task 6: Economy API — Service Interfaces & Page

**Files:**
- Create: `economy-api/src/main/java/net/ecocraft/api/Page.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/EconomyProvider.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/currency/CurrencyRegistry.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/exchange/ExchangeService.java`
- Create: `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionLog.java`

- [ ] **Step 1: Write Page record**

Create `economy-api/src/main/java/net/ecocraft/api/Page.java`:
```java
package net.ecocraft.api;

import java.util.List;

/**
 * A page of results for paginated queries.
 *
 * @param items      The items on this page
 * @param offset     Offset from the start of the full result set
 * @param limit      Maximum items per page
 * @param totalCount Total number of items across all pages
 * @param <T>        Type of items
 */
public record Page<T>(
        List<T> items,
        int offset,
        int limit,
        long totalCount
) {
    public int totalPages() {
        if (limit <= 0) return 0;
        return (int) Math.ceil((double) totalCount / limit);
    }

    public int currentPage() {
        if (limit <= 0) return 0;
        return offset / limit;
    }

    public boolean hasNext() {
        return offset + limit < totalCount;
    }

    public boolean hasPrevious() {
        return offset > 0;
    }
}
```

- [ ] **Step 2: Write EconomyProvider interface**

Create `economy-api/src/main/java/net/ecocraft/api/EconomyProvider.java`:
```java
package net.ecocraft.api;

import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core economy interface. Provides balance queries and money operations.
 * Implementations handle the payment priority: virtual balance first, then vault.
 */
public interface EconomyProvider {

    /**
     * Get the full account for a player and currency.
     */
    Account getAccount(UUID player, Currency currency);

    /**
     * Get total balance (virtual + vault).
     */
    BigDecimal getBalance(UUID player, Currency currency);

    /**
     * Get only the virtual (non-physical) balance.
     */
    BigDecimal getVirtualBalance(UUID player, Currency currency);

    /**
     * Get only the vault (physical items stored) balance.
     */
    BigDecimal getVaultBalance(UUID player, Currency currency);

    /**
     * Withdraw money from a player. Follows payment priority:
     * 1. Virtual balance first
     * 2. Vault balance if virtual insufficient
     */
    TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency);

    /**
     * Deposit money to a player's virtual balance.
     */
    TransactionResult deposit(UUID player, BigDecimal amount, Currency currency);

    /**
     * Transfer money between two players.
     */
    TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency);

    /**
     * Check if a player can afford the given amount (virtual + vault).
     */
    boolean canAfford(UUID player, BigDecimal amount, Currency currency);
}
```

- [ ] **Step 3: Write CurrencyRegistry interface**

Create `economy-api/src/main/java/net/ecocraft/api/currency/CurrencyRegistry.java`:
```java
package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Registry for server currencies. One currency is marked as the default (official) currency.
 */
public interface CurrencyRegistry {

    void register(Currency currency);

    @Nullable
    Currency getById(String id);

    Currency getDefault();

    List<Currency> listAll();

    boolean exists(String id);
}
```

- [ ] **Step 4: Write ExchangeService interface**

Create `economy-api/src/main/java/net/ecocraft/api/exchange/ExchangeService.java`:
```java
package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Currency exchange service. Handles conversion between currencies.
 */
public interface ExchangeService {

    /**
     * Convert an amount from one currency to another for a player.
     * Withdraws from source, deposits converted amount to target.
     */
    TransactionResult convert(UUID player, BigDecimal amount, Currency from, Currency to);

    /**
     * Get the exchange rate between two currencies, or null if no rate exists.
     */
    @Nullable
    ExchangeRate getRate(Currency from, Currency to);

    /**
     * List all registered exchange rates.
     */
    List<ExchangeRate> listRates();
}
```

- [ ] **Step 5: Write TransactionLog interface**

Create `economy-api/src/main/java/net/ecocraft/api/transaction/TransactionLog.java`:
```java
package net.ecocraft.api.transaction;

import net.ecocraft.api.Page;

/**
 * Read-only access to transaction history.
 */
public interface TransactionLog {

    /**
     * Query transaction history with filters and pagination.
     */
    Page<Transaction> getHistory(TransactionFilter filter);
}
```

- [ ] **Step 6: Run all tests to verify nothing is broken**

Run:
```bash
./gradlew :economy-api:test
```

Expected: PASS — all existing tests still green. Interfaces have no tests (they're contracts).

- [ ] **Step 7: Commit**

```bash
git add economy-api/src/
git commit -m "feat(economy-api): add EconomyProvider, CurrencyRegistry, ExchangeService, TransactionLog interfaces"
```

---

### Task 7: GUI Library — Theme and Colors

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/theme/EcoColors.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/theme/EcoTheme.java`

- [ ] **Step 1: Write EcoColors constants**

Create `gui-lib/src/main/java/net/ecocraft/gui/theme/EcoColors.java`:
```java
package net.ecocraft.gui.theme;

/**
 * WoW-inspired color palette for all EcoCraft GUIs.
 * Colors are stored as ARGB integers for use with GuiGraphics.
 */
public final class EcoColors {
    private EcoColors() {}

    // Backgrounds
    public static final int BG_DARKEST   = 0xFF0D0D1A;
    public static final int BG_DARK      = 0xFF12122A;
    public static final int BG_MEDIUM    = 0xFF1A1A2E;
    public static final int BG_LIGHT     = 0xFF2A2A3E;
    public static final int BG_ROW_ALT   = 0xFF0A0A18;

    // Borders
    public static final int BORDER       = 0xFF333333;
    public static final int BORDER_LIGHT = 0xFF444444;
    public static final int BORDER_GOLD  = 0xFFFFD700;

    // Accent
    public static final int GOLD         = 0xFFFFD700;
    public static final int GOLD_BG      = 0xFF4A3A1A;
    public static final int GOLD_BG_DIM  = 0xFF3A2A1A;

    // Text
    public static final int TEXT_WHITE   = 0xFFFFFFFF;
    public static final int TEXT_LIGHT   = 0xFFCCCCCC;
    public static final int TEXT_GREY    = 0xFFAAAAAA;
    public static final int TEXT_DIM     = 0xFF888888;
    public static final int TEXT_DARK    = 0xFF666666;

    // Rarity colors (WoW standard)
    public static final int RARITY_COMMON    = 0xFFFFFFFF;
    public static final int RARITY_UNCOMMON  = 0xFF1EFF00;
    public static final int RARITY_RARE      = 0xFF0070DD;
    public static final int RARITY_EPIC      = 0xFFA335EE;
    public static final int RARITY_LEGENDARY = 0xFFFF8000;

    // Functional
    public static final int SUCCESS      = 0xFF4CAF50;
    public static final int SUCCESS_BG   = 0xFF1A3A1A;
    public static final int WARNING      = 0xFFFF9800;
    public static final int WARNING_BG   = 0xFF2A1A0A;
    public static final int DANGER       = 0xFFFF6B6B;
    public static final int DANGER_BG    = 0xFF2A0A0A;
    public static final int INFO         = 0xFF64B5F6;
    public static final int INFO_BG      = 0xFF0A1A2A;
}
```

- [ ] **Step 2: Write EcoTheme draw helpers**

Create `gui-lib/src/main/java/net/ecocraft/gui/theme/EcoTheme.java`:
```java
package net.ecocraft.gui.theme;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Theme utility class providing common draw operations for EcoCraft GUIs.
 */
public final class EcoTheme {
    private EcoTheme() {}

    /**
     * Draw a filled rectangle with a 1px border.
     */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                 int bgColor, int borderColor) {
        // Background
        graphics.fill(x, y, x + width, y + height, bgColor);
        // Border: top, bottom, left, right
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y, x + 1, y + height, borderColor);
        graphics.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    /**
     * Draw a panel using default dark theme colors.
     */
    public static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        drawPanel(graphics, x, y, width, height, EcoColors.BG_DARK, EcoColors.BORDER);
    }

    /**
     * Draw a horizontal separator line.
     */
    public static void drawSeparator(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
    }

    /**
     * Draw a gold-accented header separator (2px).
     */
    public static void drawGoldSeparator(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 2, EcoColors.BORDER_GOLD);
    }

    /**
     * Draw a highlighted left border (for selected items).
     */
    public static void drawLeftAccent(GuiGraphics graphics, int x, int y, int height, int color) {
        graphics.fill(x, y, x + 3, y + height, color);
    }

    /**
     * Draw a status badge background (rounded-ish small rect).
     */
    public static void drawBadge(GuiGraphics graphics, int x, int y, int width, int height,
                                 int bgColor) {
        graphics.fill(x, y, x + width, y + height, bgColor);
    }
}
```

- [ ] **Step 3: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoColors palette and EcoTheme draw helpers"
```

---

### Task 8: GUI Library — Scrollbar Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoScrollbar.java`

- [ ] **Step 1: Write the scrollbar widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoScrollbar.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A WoW-style vertical scrollbar. Tracks scroll position as a float [0.0, 1.0].
 * Parent widget should call {@link #setContentRatio(float)} to set thumb size
 * and {@link #getScrollValue()} to get current position.
 */
public class EcoScrollbar extends AbstractWidget {

    private static final int SCROLLBAR_WIDTH = 8;
    private static final int MIN_THUMB_HEIGHT = 16;

    private float scrollValue = 0f;
    private float contentRatio = 1f;
    private boolean dragging = false;
    private double dragOffset = 0;

    public EcoScrollbar(int x, int y, int height) {
        super(x, y, SCROLLBAR_WIDTH, height, Component.empty());
    }

    /**
     * Set the ratio of visible content to total content.
     * E.g., if 10 items visible out of 50 total, ratio = 0.2
     */
    public void setContentRatio(float ratio) {
        this.contentRatio = Math.max(0f, Math.min(1f, ratio));
    }

    /**
     * Get current scroll position as a value between 0.0 (top) and 1.0 (bottom).
     */
    public float getScrollValue() {
        return scrollValue;
    }

    public void setScrollValue(float value) {
        this.scrollValue = Math.max(0f, Math.min(1f, value));
    }

    /**
     * Whether scrolling is needed (content is larger than visible area).
     */
    public boolean needsScrollbar() {
        return contentRatio < 1f;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!needsScrollbar()) return;

        // Track background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, EcoColors.BG_DARKEST);
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, EcoColors.BORDER);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, EcoColors.BORDER);

        // Thumb
        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * contentRatio));
        int trackSpace = height - thumbHeight;
        int thumbY = getY() + (int) (trackSpace * scrollValue);

        boolean hovered = isMouseOver(mouseX, mouseY);
        int thumbColor = (hovered || dragging) ? EcoColors.GOLD : EcoColors.GOLD_BG_DIM;
        int thumbBorder = (hovered || dragging) ? EcoColors.BORDER_GOLD : EcoColors.BORDER_LIGHT;

        graphics.fill(getX() + 1, thumbY, getX() + width - 1, thumbY + thumbHeight, thumbColor);
        graphics.fill(getX() + 1, thumbY, getX() + width - 1, thumbY + 1, thumbBorder);
        graphics.fill(getX() + 1, thumbY + thumbHeight - 1, getX() + width - 1, thumbY + thumbHeight, thumbBorder);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!needsScrollbar() || button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * contentRatio));
        int trackSpace = height - thumbHeight;
        int thumbY = getY() + (int) (trackSpace * scrollValue);

        if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
            dragging = true;
            dragOffset = mouseY - thumbY;
        } else {
            float clickRatio = (float) (mouseY - getY() - thumbHeight / 2.0) / (height - thumbHeight);
            setScrollValue(clickRatio);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!dragging) return false;

        int thumbHeight = Math.max(MIN_THUMB_HEIGHT, (int) (height * contentRatio));
        int trackSpace = height - thumbHeight;
        if (trackSpace <= 0) return false;

        float newValue = (float) (mouseY - getY() - dragOffset) / trackSpace;
        setScrollValue(newValue);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!needsScrollbar()) return false;
        setScrollValue(scrollValue - (float) (scrollY * 0.05));
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoScrollbar widget with drag, click, and mouse wheel support"
```

---

### Task 9: GUI Library — Button Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoButton.java`

- [ ] **Step 1: Write the button widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoButton.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * A styled button with multiple variants matching the WoW-inspired theme.
 */
public class EcoButton extends AbstractWidget {

    public enum Style {
        PRIMARY(EcoColors.GOLD_BG, EcoColors.BORDER_GOLD, EcoColors.GOLD, EcoColors.GOLD_BG_DIM),
        SUCCESS(EcoColors.SUCCESS_BG, EcoColors.SUCCESS, EcoColors.TEXT_WHITE, 0xFF2A4A2A),
        AUCTION(EcoColors.WARNING_BG, EcoColors.WARNING, EcoColors.TEXT_WHITE, 0xFF3A2A1A),
        DANGER(EcoColors.DANGER_BG, EcoColors.DANGER, EcoColors.DANGER, 0xFF3A1A1A),
        GHOST(EcoColors.BG_MEDIUM, EcoColors.BORDER_LIGHT, EcoColors.TEXT_GREY, EcoColors.BG_LIGHT);

        final int bgColor;
        final int borderColor;
        final int textColor;
        final int hoverBg;

        Style(int bgColor, int borderColor, int textColor, int hoverBg) {
            this.bgColor = bgColor;
            this.borderColor = borderColor;
            this.textColor = textColor;
            this.hoverBg = hoverBg;
        }
    }

    private final Style style;
    private final Runnable onPress;

    public EcoButton(int x, int y, int width, int height, Component label, Style style, Runnable onPress) {
        super(x, y, width, height, label);
        this.style = style;
        this.onPress = onPress;
    }

    public static EcoButton primary(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.PRIMARY, onPress);
    }

    public static EcoButton success(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.SUCCESS, onPress);
    }

    public static EcoButton auction(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.AUCTION, onPress);
    }

    public static EcoButton danger(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new EcoButton(x, y, width, height, label, Style.DANGER, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean hovered = isHovered();
        int bg = hovered ? style.hoverBg : style.bgColor;

        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, bg);

        // Border
        graphics.fill(getX(), getY(), getX() + width, getY() + 1, style.borderColor);
        graphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, style.borderColor);
        graphics.fill(getX(), getY(), getX() + 1, getY() + height, style.borderColor);
        graphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, style.borderColor);

        // Label centered
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        int textWidth = font.width(getMessage());
        int textX = getX() + (width - textWidth) / 2;
        int textY = getY() + (height - 8) / 2;
        graphics.drawString(font, getMessage(), textX, textY, style.textColor, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onPress.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoButton with primary, success, auction, danger, ghost styles"
```

---

### Task 10: GUI Library — Tab Bar Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoTabBar.java`

- [ ] **Step 1: Write the tab bar widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoTabBar.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A horizontal tab bar with WoW-style gold active tab.
 */
public class EcoTabBar extends AbstractWidget {

    private static final int TAB_HEIGHT = 22;
    private static final int TAB_GAP = 4;
    private static final int TAB_PADDING_H = 16;

    private final List<Component> tabs;
    private int activeTab = 0;
    private final IntConsumer onTabChanged;

    public EcoTabBar(int x, int y, List<Component> tabs, IntConsumer onTabChanged) {
        super(x, y, 0, TAB_HEIGHT, Component.empty());
        this.tabs = tabs;
        this.onTabChanged = onTabChanged;
        // Width is calculated from tab labels
        this.width = calculateTotalWidth();
    }

    private int calculateTotalWidth() {
        Font font = Minecraft.getInstance().font;
        int total = 0;
        for (Component tab : tabs) {
            total += font.width(tab) + TAB_PADDING_H * 2 + TAB_GAP;
        }
        return total - TAB_GAP;
    }

    public int getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(int index) {
        if (index >= 0 && index < tabs.size()) {
            this.activeTab = index;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tabs.size(); i++) {
            Component label = tabs.get(i);
            int tabWidth = font.width(label) + TAB_PADDING_H * 2;
            boolean isActive = i == activeTab;
            boolean isHovered = mouseX >= currentX && mouseX < currentX + tabWidth
                    && mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;

            // Background
            int bg = isActive ? EcoColors.GOLD_BG : (isHovered ? EcoColors.BG_LIGHT : EcoColors.BG_MEDIUM);
            int border = isActive ? EcoColors.BORDER_GOLD : EcoColors.BORDER;
            int textColor = isActive ? EcoColors.GOLD : EcoColors.TEXT_DARK;

            graphics.fill(currentX, getY(), currentX + tabWidth, getY() + TAB_HEIGHT, bg);

            // Border
            graphics.fill(currentX, getY(), currentX + tabWidth, getY() + 1, border);
            graphics.fill(currentX, getY() + TAB_HEIGHT - 1, currentX + tabWidth, getY() + TAB_HEIGHT, border);
            graphics.fill(currentX, getY(), currentX + 1, getY() + TAB_HEIGHT, border);
            graphics.fill(currentX + tabWidth - 1, getY(), currentX + tabWidth, getY() + TAB_HEIGHT, border);

            // Label
            int textX = currentX + TAB_PADDING_H;
            int textY = getY() + (TAB_HEIGHT - 8) / 2;
            graphics.drawString(font, label, textX, textY, textColor, false);

            currentX += tabWidth + TAB_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tabs.size(); i++) {
            int tabWidth = font.width(tabs.get(i)) + TAB_PADDING_H * 2;
            if (mouseX >= currentX && mouseX < currentX + tabWidth) {
                if (i != activeTab) {
                    activeTab = i;
                    onTabChanged.accept(i);
                }
                return true;
            }
            currentX += tabWidth + TAB_GAP;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + TAB_HEIGHT;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoTabBar widget with gold active tab style"
```

---

### Task 11: GUI Library — Search Bar Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoSearchBar.java`

- [ ] **Step 1: Write the search bar widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoSearchBar.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * A themed search input with placeholder text and search icon.
 */
public class EcoSearchBar extends EditBox {

    private final Consumer<String> onSearch;

    public EcoSearchBar(Font font, int x, int y, int width, int height,
                        Component placeholder, Consumer<String> onSearch) {
        super(font, x, y, width, height, placeholder);
        this.onSearch = onSearch;
        this.setHint(placeholder);
        this.setBordered(false);
        this.setTextColor(EcoColors.TEXT_LIGHT & 0x00FFFFFF); // strip alpha for EditBox
        this.setResponder(onSearch);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Custom background
        int bg = isFocused() ? EcoColors.BG_MEDIUM : EcoColors.BG_DARK;
        int border = isFocused() ? EcoColors.BORDER_GOLD : EcoColors.BORDER;

        graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() + height + 2, bg);
        graphics.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() - 1, border);
        graphics.fill(getX() - 2, getY() + height + 1, getX() + width + 2, getY() + height + 2, border);
        graphics.fill(getX() - 2, getY() - 2, getX() - 1, getY() + height + 2, border);
        graphics.fill(getX() + width + 1, getY() - 2, getX() + width + 2, getY() + height + 2, border);

        // Search icon
        Font font = net.minecraft.client.Minecraft.getInstance().font;
        if (getValue().isEmpty() && !isFocused()) {
            graphics.drawString(font, "🔍", getX(), getY() + 1, EcoColors.TEXT_DARK, false);
        }

        super.renderWidget(graphics, mouseX, mouseY, partialTick);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoSearchBar with themed styling and placeholder"
```

---

### Task 12: GUI Library — Filter Tags Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoFilterTags.java`

- [ ] **Step 1: Write the filter tags widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoFilterTags.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * A horizontal row of clickable filter pills/tags.
 */
public class EcoFilterTags extends AbstractWidget {

    private static final int TAG_HEIGHT = 20;
    private static final int TAG_GAP = 6;
    private static final int TAG_PADDING_H = 12;

    private final List<Component> tags;
    private int activeTag = 0;
    private final IntConsumer onTagChanged;

    public EcoFilterTags(int x, int y, List<Component> tags, IntConsumer onTagChanged) {
        super(x, y, 0, TAG_HEIGHT, Component.empty());
        this.tags = tags;
        this.onTagChanged = onTagChanged;
        this.width = calculateTotalWidth();
    }

    private int calculateTotalWidth() {
        Font font = Minecraft.getInstance().font;
        int total = 0;
        for (Component tag : tags) {
            total += font.width(tag) + TAG_PADDING_H * 2 + TAG_GAP;
        }
        return total - TAG_GAP;
    }

    public int getActiveTag() {
        return activeTag;
    }

    public void setActiveTag(int index) {
        if (index >= 0 && index < tags.size()) {
            this.activeTag = index;
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            Component label = tags.get(i);
            int tagWidth = font.width(label) + TAG_PADDING_H * 2;
            boolean isActive = i == activeTag;
            boolean isHovered = mouseX >= currentX && mouseX < currentX + tagWidth
                    && mouseY >= getY() && mouseY < getY() + TAG_HEIGHT;

            int bg = isActive ? EcoColors.GOLD_BG_DIM : (isHovered ? EcoColors.BG_LIGHT : EcoColors.BG_MEDIUM);
            int border = isActive ? EcoColors.BORDER_GOLD : EcoColors.BORDER_LIGHT;
            int textColor = isActive ? EcoColors.GOLD : EcoColors.TEXT_GREY;

            // Pill shape (rounded via full rect)
            graphics.fill(currentX, getY(), currentX + tagWidth, getY() + TAG_HEIGHT, bg);
            graphics.fill(currentX, getY(), currentX + tagWidth, getY() + 1, border);
            graphics.fill(currentX, getY() + TAG_HEIGHT - 1, currentX + tagWidth, getY() + TAG_HEIGHT, border);
            graphics.fill(currentX, getY(), currentX + 1, getY() + TAG_HEIGHT, border);
            graphics.fill(currentX + tagWidth - 1, getY(), currentX + tagWidth, getY() + TAG_HEIGHT, border);

            int textX = currentX + TAG_PADDING_H;
            int textY = getY() + (TAG_HEIGHT - 8) / 2;
            graphics.drawString(font, label, textX, textY, textColor, false);

            currentX += tagWidth + TAG_GAP;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        Font font = Minecraft.getInstance().font;
        int currentX = getX();

        for (int i = 0; i < tags.size(); i++) {
            int tagWidth = font.width(tags.get(i)) + TAG_PADDING_H * 2;
            if (mouseX >= currentX && mouseX < currentX + tagWidth) {
                if (i != activeTag) {
                    activeTag = i;
                    onTagChanged.accept(i);
                }
                return true;
            }
            currentX += tagWidth + TAG_GAP;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + width
                && mouseY >= getY() && mouseY < getY() + TAG_HEIGHT;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoFilterTags clickable pill/tag widget"
```

---

### Task 13: GUI Library — Stat Card Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoStatCard.java`

- [ ] **Step 1: Write the stat card widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoStatCard.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * A stat card displaying a label, value, and optional subtitle (trend indicator).
 */
public class EcoStatCard extends AbstractWidget {

    private final Component label;
    private Component value;
    private int valueColor;
    private @Nullable Component subtitle;
    private int subtitleColor;

    public EcoStatCard(int x, int y, int width, int height,
                       Component label, Component value, int valueColor) {
        super(x, y, width, height, label);
        this.label = label;
        this.value = value;
        this.valueColor = valueColor;
        this.subtitleColor = EcoColors.TEXT_GREY;
    }

    public void setValue(Component value, int color) {
        this.value = value;
        this.valueColor = color;
    }

    public void setSubtitle(@Nullable Component subtitle, int color) {
        this.subtitle = subtitle;
        this.subtitleColor = color;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Panel background
        EcoTheme.drawPanel(graphics, getX(), getY(), width, height);

        int padding = 10;
        int innerX = getX() + padding;

        // Label (uppercase small)
        int labelY = getY() + padding;
        graphics.drawString(font, label, innerX, labelY, EcoColors.TEXT_DIM, false);

        // Value (large - using scale would be complex, just draw normally with bold color)
        int valueY = labelY + 14;
        graphics.drawString(font, value, innerX, valueY, valueColor, false);

        // Subtitle
        if (subtitle != null) {
            int subtitleY = valueY + 14;
            graphics.drawString(font, subtitle, innerX, subtitleY, subtitleColor, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoStatCard widget for metric display"
```

---

### Task 14: GUI Library — Item Slot Widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoItemSlot.java`

- [ ] **Step 1: Write the item slot widget**

Create `gui-lib/src/main/java/net/ecocraft/gui/widget/EcoItemSlot.java`:
```java
package net.ecocraft.gui.widget;

import net.ecocraft.gui.theme.EcoColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * An item display slot with a rarity-colored border.
 */
public class EcoItemSlot extends AbstractWidget {

    private @Nullable ItemStack itemStack;
    private int rarityColor;

    public EcoItemSlot(int x, int y, int size) {
        super(x, y, size, size, Component.empty());
        this.rarityColor = EcoColors.RARITY_COMMON;
    }

    public void setItem(@Nullable ItemStack stack, int rarityColor) {
        this.itemStack = stack;
        this.rarityColor = rarityColor;
    }

    public void setItem(@Nullable ItemStack stack) {
        this.itemStack = stack;
        if (stack != null && !stack.isEmpty()) {
            this.rarityColor = getRarityColor(stack);
        }
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Background
        graphics.fill(getX(), getY(), getX() + width, getY() + height, EcoColors.BG_LIGHT);

        // Rarity border (2px)
        graphics.fill(getX(), getY(), getX() + width, getY() + 2, rarityColor);
        graphics.fill(getX(), getY() + height - 2, getX() + width, getY() + height, rarityColor);
        graphics.fill(getX(), getY(), getX() + 2, getY() + height, rarityColor);
        graphics.fill(getX() + width - 2, getY(), getX() + width, getY() + height, rarityColor);

        // Item rendering
        if (itemStack != null && !itemStack.isEmpty()) {
            int itemX = getX() + (width - 16) / 2;
            int itemY = getY() + (height - 16) / 2;
            graphics.renderItem(itemStack, itemX, itemY);
            graphics.renderItemDecorations(Minecraft.getInstance().font, itemStack, itemX, itemY);
        }

        // Tooltip on hover
        if (isHovered() && itemStack != null && !itemStack.isEmpty()) {
            graphics.renderTooltip(Minecraft.getInstance().font, itemStack, mouseX, mouseY);
        }
    }

    private static int getRarityColor(ItemStack stack) {
        return switch (stack.getRarity()) {
            case COMMON -> EcoColors.RARITY_COMMON;
            case UNCOMMON -> EcoColors.RARITY_UNCOMMON;
            case RARE -> EcoColors.RARITY_RARE;
            case EPIC -> EcoColors.RARITY_EPIC;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 2: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoItemSlot widget with rarity border and tooltip"
```

---

### Task 15: GUI Library — Paginated Table

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/table/TableColumn.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/table/TableRow.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/table/EcoPaginatedTable.java`

- [ ] **Step 1: Write TableColumn record**

Create `gui-lib/src/main/java/net/ecocraft/gui/table/TableColumn.java`:
```java
package net.ecocraft.gui.table;

import net.minecraft.network.chat.Component;

/**
 * Defines a column in a paginated table.
 *
 * @param header   Column header text
 * @param weight   Relative width weight (columns share space proportionally)
 * @param align    Text alignment within the column
 * @param sortable Whether clicking the header sorts by this column
 */
public record TableColumn(
        Component header,
        float weight,
        Align align,
        boolean sortable
) {
    public enum Align { LEFT, CENTER, RIGHT }

    public static TableColumn left(Component header, float weight) {
        return new TableColumn(header, weight, Align.LEFT, false);
    }

    public static TableColumn center(Component header, float weight) {
        return new TableColumn(header, weight, Align.CENTER, false);
    }

    public static TableColumn right(Component header, float weight) {
        return new TableColumn(header, weight, Align.RIGHT, false);
    }

    public static TableColumn sortableLeft(Component header, float weight) {
        return new TableColumn(header, weight, Align.LEFT, true);
    }

    public static TableColumn sortableCenter(Component header, float weight) {
        return new TableColumn(header, weight, Align.CENTER, true);
    }
}
```

- [ ] **Step 2: Write TableRow**

Create `gui-lib/src/main/java/net/ecocraft/gui/table/TableRow.java`:
```java
package net.ecocraft.gui.table;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A row of data in a paginated table.
 * Each cell holds text + optional color. First cell can optionally have an item icon.
 */
public record TableRow(
        @Nullable ItemStack icon,
        int iconRarityColor,
        List<Cell> cells,
        @Nullable Runnable onClick
) {
    public record Cell(Component text, int color) {
        public static Cell of(Component text, int color) {
            return new Cell(text, color);
        }
    }

    public static TableRow of(List<Cell> cells, @Nullable Runnable onClick) {
        return new TableRow(null, 0xFFFFFFFF, cells, onClick);
    }

    public static TableRow withIcon(ItemStack icon, int rarityColor, List<Cell> cells, @Nullable Runnable onClick) {
        return new TableRow(icon, rarityColor, cells, onClick);
    }
}
```

- [ ] **Step 3: Write EcoPaginatedTable**

Create `gui-lib/src/main/java/net/ecocraft/gui/table/EcoPaginatedTable.java`:
```java
package net.ecocraft.gui.table;

import net.ecocraft.gui.theme.EcoColors;
import net.ecocraft.gui.theme.EcoTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A paginated, sortable table with WoW-style gold headers.
 */
public class EcoPaginatedTable extends AbstractWidget {

    private static final int HEADER_HEIGHT = 22;
    private static final int ROW_HEIGHT = 24;
    private static final int ICON_SIZE = 18;
    private static final int ICON_MARGIN = 8;

    private final List<TableColumn> columns;
    private final List<TableRow> rows = new ArrayList<>();
    private int page = 0;
    private int rowsPerPage;
    private int hoveredRow = -1;

    public EcoPaginatedTable(int x, int y, int width, int height, List<TableColumn> columns) {
        super(x, y, width, height, Component.empty());
        this.columns = columns;
        this.rowsPerPage = Math.max(1, (height - HEADER_HEIGHT) / ROW_HEIGHT);
    }

    public void setRows(List<TableRow> rows) {
        this.rows.clear();
        this.rows.addAll(rows);
        this.page = 0;
    }

    public int getPage() { return page; }
    public int getTotalPages() { return Math.max(1, (int) Math.ceil((double) rows.size() / rowsPerPage)); }
    public int getTotalRows() { return rows.size(); }

    public void nextPage() {
        if (page < getTotalPages() - 1) page++;
    }

    public void previousPage() {
        if (page > 0) page--;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;

        // Table background
        EcoTheme.drawPanel(graphics, getX(), getY(), width, height);

        // Calculate column positions
        float totalWeight = 0;
        boolean hasIcon = rows.stream().anyMatch(r -> r.icon() != null);
        int iconSpace = hasIcon ? ICON_SIZE + ICON_MARGIN : 0;
        for (var col : columns) totalWeight += col.weight();

        int contentWidth = width - 2 - iconSpace; // -2 for borders
        int[] colX = new int[columns.size()];
        int[] colW = new int[columns.size()];
        int cx = getX() + 1 + iconSpace;
        for (int i = 0; i < columns.size(); i++) {
            colX[i] = cx;
            colW[i] = (int) (contentWidth * columns.get(i).weight() / totalWeight);
            cx += colW[i];
        }

        // Header
        int headerY = getY() + 1;
        graphics.fill(getX() + 1, headerY, getX() + width - 1, headerY + HEADER_HEIGHT, EcoColors.BG_MEDIUM);
        EcoTheme.drawGoldSeparator(graphics, getX() + 1, headerY + HEADER_HEIGHT - 2, width - 2);

        for (int i = 0; i < columns.size(); i++) {
            var col = columns.get(i);
            int textX = getAlignedX(font, col.header(), colX[i], colW[i], col.align());
            int textY = headerY + (HEADER_HEIGHT - 8) / 2;
            graphics.drawString(font, col.header(), textX, textY, EcoColors.GOLD, false);
        }

        // Rows
        int startIdx = page * rowsPerPage;
        int endIdx = Math.min(startIdx + rowsPerPage, rows.size());
        hoveredRow = -1;

        for (int i = startIdx; i < endIdx; i++) {
            var row = rows.get(i);
            int rowIndex = i - startIdx;
            int rowY = headerY + HEADER_HEIGHT + rowIndex * ROW_HEIGHT;

            // Alternating background
            boolean alt = (i % 2 == 1);
            boolean isHovered = mouseX >= getX() && mouseX < getX() + width
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (isHovered) {
                hoveredRow = i;
                graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, EcoColors.BG_MEDIUM);
            } else if (alt) {
                graphics.fill(getX() + 1, rowY, getX() + width - 1, rowY + ROW_HEIGHT, EcoColors.BG_ROW_ALT);
            }

            // Row separator
            graphics.fill(getX() + 1, rowY + ROW_HEIGHT - 1, getX() + width - 1,
                    rowY + ROW_HEIGHT, EcoColors.BG_MEDIUM);

            // Icon
            if (row.icon() != null) {
                int iconX = getX() + 4;
                int iconY = rowY + (ROW_HEIGHT - 16) / 2;
                // Rarity border around icon
                graphics.fill(iconX - 1, iconY - 1, iconX + 17, iconY + 17, row.iconRarityColor());
                graphics.fill(iconX, iconY, iconX + 16, iconY + 16, EcoColors.BG_LIGHT);
                graphics.renderItem(row.icon(), iconX, iconY);
            }

            // Cells
            int cellCount = Math.min(row.cells().size(), columns.size());
            for (int c = 0; c < cellCount; c++) {
                var cell = row.cells().get(c);
                var col = columns.get(c);
                int textX = getAlignedX(font, cell.text(), colX[c], colW[c], col.align());
                int textY = rowY + (ROW_HEIGHT - 8) / 2;
                graphics.drawString(font, cell.text(), textX, textY, cell.color(), false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || hoveredRow < 0 || hoveredRow >= rows.size()) return false;
        var row = rows.get(hoveredRow);
        if (row.onClick() != null) {
            row.onClick().run();
            return true;
        }
        return false;
    }

    private int getAlignedX(Font font, Component text, int colX, int colWidth, TableColumn.Align align) {
        int textWidth = font.width(text);
        return switch (align) {
            case LEFT -> colX + 4;
            case CENTER -> colX + (colWidth - textWidth) / 2;
            case RIGHT -> colX + colWidth - textWidth - 4;
        };
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
```

- [ ] **Step 4: Verify compilation**

Run:
```bash
./gradlew :gui-lib:build
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add gui-lib/src/
git commit -m "feat(gui-lib): add EcoPaginatedTable with sortable columns, icons, and pagination"
```

---

### Task 16: Final Build Verification

- [ ] **Step 1: Run full build with tests**

Run:
```bash
./gradlew clean build
```

Expected: BUILD SUCCESSFUL. All economy-api tests pass. gui-lib compiles cleanly.

- [ ] **Step 2: Verify JAR outputs**

Run:
```bash
ls -la economy-api/build/libs/
ls -la gui-lib/build/libs/
```

Expected: `ecocraft-economy-api-0.1.0.jar` and `ecocraft-gui-lib-0.1.0.jar` exist.

- [ ] **Step 3: Commit any remaining changes**

```bash
git status
```

If clean, no commit needed. If any uncommitted files remain:
```bash
git add -A
git commit -m "chore: clean up Phase 1 build artifacts and config"
```
