# Economy V2 Phase 8: Decimal Migration

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate all modules to treat currency amounts with `decimals=2` — multiply existing DB values ×100, replace all manual formatting with `CurrencyFormatter`, update commands to accept decimal input like `1000.50`.

**Architecture:** With `decimals=2`, the stored `long` value `15000` represents `150.00 G`. Commands accept `150.00` and convert to `15000` internally. All display uses `CurrencyFormatter.format()`. A DB migration multiplies all existing amounts by `10^decimals`. The `fromSmallestUnit`/`toSmallestUnit` helpers are fixed to properly scale by `currency.decimals()`.

**Tech Stack:** Java 21, NeoForge 1.21.1, SQLite, JUnit 5

---

## Scope

- Set default currency `decimals = 2`
- DB migration: multiply all existing monetary amounts ×100
- Command parsers: accept `1000.50`, convert to smallest unit via `toSmallestUnit()`
- Replace `BuyTab.formatPrice()` with `CurrencyFormatter.format()` in AH
- Replace manual `currencySymbol` formatting in Mail
- Fix `fromSmallestUnit`/`toSmallestUnit` in both AH and Mail
- Update `EcoNumberInput` currency fields to work with decimal amounts

**Convention:** User types `150.00` → stored as `15000` → displayed as `150.00 G`

---

## File Map

### Modified files

| Module | File | Changes |
|--------|------|---------|
| economy-core | `config/EcoConfig.java` | `decimals` default back to 2 |
| economy-core | `storage/SqliteDatabaseProvider.java` | Migration v4: multiply amounts ×100 |
| economy-core | `command/PayCommand.java` | Parse double → toSmallestUnit |
| economy-core | `command/EcoAdminCommand.java` | Parse double → toSmallestUnit |
| economy-core | `command/CurrencyCommand.java` | Parse double → toSmallestUnit |
| economy-core | `command/BalanceCommand.java` | Use CurrencyFormatter consistently |
| economy-core | `impl/EconomyProviderImpl.java` | Fix adapter long↔BigDecimal conversion |
| economy-core | `network/EcoNetworkHandler.java` | Use CurrencyFormatter for messages |
| economy-api | `currency/CurrencyFormatter.java` | Add `toSmallestUnit`/`fromSmallestUnit` static helpers |
| auction-house | `service/AuctionService.java` | Fix fromSmallestUnit/toSmallestUnit |
| auction-house | `screen/BuyTab.java` | Replace formatPrice with CurrencyFormatter |
| auction-house | `screen/SellTab.java` | Replace formatPrice with CurrencyFormatter |
| auction-house | `screen/MyAuctionsTab.java` | Replace formatPrice with CurrencyFormatter |
| auction-house | `screen/LedgerTab.java` | Replace formatPrice with CurrencyFormatter |
| auction-house | `screen/AuctionHouseScreen.java` | Replace formatPrice with CurrencyFormatter |
| auction-house | `storage/AuctionStorageProvider.java` | Migration: multiply amounts ×100 |
| mail | `service/MailService.java` | Fix fromSmallestUnit/toSmallestUnit |
| mail | `screen/MailListView.java` | Use CurrencyFormatter instead of manual |
| mail | `screen/MailDetailView.java` | Use CurrencyFormatter instead of manual |
| mail | `screen/MailComposeView.java` | Currency inputs use smallest unit |
| mail | `storage/MailStorageProvider.java` | Migration: multiply amounts ×100 |

---

## Tasks

### Task 1: Add toSmallestUnit/fromSmallestUnit to CurrencyFormatter + fix config

**Files:**
- Modify: `economy-api/src/main/java/net/ecocraft/api/currency/CurrencyFormatter.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/config/EcoConfig.java`
- Modify: `economy-core/src/test/java/net/ecocraft/core/currency/CurrencyFormatterTest.java`

- [ ] **Step 1: Add conversion tests**

Add to `CurrencyFormatterTest.java`:

```java
@Test
void toSmallestUnitWithDecimals() {
    // decimals=2: 150.00 → 15000
    assertEquals(15000, CurrencyFormatter.toSmallestUnit(new BigDecimal("150.00"), GOLD));
    assertEquals(15050, CurrencyFormatter.toSmallestUnit(new BigDecimal("150.50"), GOLD));
    assertEquals(100, CurrencyFormatter.toSmallestUnit(new BigDecimal("1.00"), GOLD));
    assertEquals(1, CurrencyFormatter.toSmallestUnit(new BigDecimal("0.01"), GOLD));
}

@Test
void fromSmallestUnitWithDecimals() {
    assertEquals(new BigDecimal("150.00"), CurrencyFormatter.fromSmallestUnit(15000, GOLD));
    assertEquals(new BigDecimal("150.50"), CurrencyFormatter.fromSmallestUnit(15050, GOLD));
    assertEquals(new BigDecimal("0.01"), CurrencyFormatter.fromSmallestUnit(1, GOLD));
}

@Test
void toSmallestUnitNoDecimals() {
    assertEquals(150, CurrencyFormatter.toSmallestUnit(new BigDecimal("150"), GOLD_NO_DECIMALS));
}

@Test
void fromSmallestUnitNoDecimals() {
    assertEquals(new BigDecimal("150"), CurrencyFormatter.fromSmallestUnit(150, GOLD_NO_DECIMALS));
}

@Test
void roundTrip() {
    BigDecimal original = new BigDecimal("123.45");
    long smallest = CurrencyFormatter.toSmallestUnit(original, GOLD);
    BigDecimal back = CurrencyFormatter.fromSmallestUnit(smallest, GOLD);
    assertEquals(original, back);
}
```

- [ ] **Step 2: Implement conversion methods in CurrencyFormatter**

Add to `CurrencyFormatter.java`:

```java
/**
 * Convert a display amount to the smallest unit.
 * E.g., with decimals=2: 150.50 → 15050
 */
public static long toSmallestUnit(BigDecimal displayAmount, Currency currency) {
    return displayAmount.movePointRight(currency.decimals())
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
}

/**
 * Convert from smallest unit to display amount.
 * E.g., with decimals=2: 15050 → 150.50
 */
public static BigDecimal fromSmallestUnit(long smallestUnit, Currency currency) {
    return BigDecimal.valueOf(smallestUnit)
            .movePointLeft(currency.decimals())
            .setScale(currency.decimals(), RoundingMode.DOWN);
}
```

- [ ] **Step 3: Set config default decimals back to 2**

In `EcoConfig.java`, change:
```java
defaultCurrencyDecimals = builder.defineInRange("defaultCurrency.decimals", 2, 0, 4);
```

Also update the live config file at `/home/florian/.minecraft/config/ecocraft_core-server.toml`: set `decimals = 2`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :economy-core:test --tests "*CurrencyFormatterTest*"`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(economy-api): add toSmallestUnit/fromSmallestUnit, set decimals=2 default"
```

---

### Task 2: Database migration — multiply all amounts ×100

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/storage/SqliteDatabaseProvider.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`
- Modify: `mail/src/main/java/net/ecocraft/mail/storage/MailStorageProvider.java`

- [ ] **Step 1: Economy-core migration v4**

In `SqliteDatabaseProvider.initialize()`, after existing migrations, add migration v4:

```java
// Migration v4: multiply all monetary amounts by 100 for decimals=2
try (Statement stmt = connection.createStatement()) {
    // Check if migration already applied
    ResultSet rs = stmt.executeQuery("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1");
    int currentVersion = rs.next() ? rs.getInt("version") : 0;
    if (currentVersion < 4) {
        // Balances are stored as TEXT (BigDecimal string)
        // Multiply each by 100: "150" → "15000"
        PreparedStatement ps = connection.prepareStatement("SELECT player_uuid, currency_id, amount FROM balances");
        ResultSet rows = ps.executeQuery();
        PreparedStatement update = connection.prepareStatement("UPDATE balances SET amount = ? WHERE player_uuid = ? AND currency_id = ?");
        while (rows.next()) {
            BigDecimal old = new BigDecimal(rows.getString("amount"));
            BigDecimal migrated = old.multiply(BigDecimal.valueOf(100));
            update.setString(1, migrated.toPlainString());
            update.setString(2, rows.getString("player_uuid"));
            update.setString(3, rows.getString("currency_id"));
            update.executeUpdate();
        }
        // Transactions
        ps = connection.prepareStatement("SELECT id, amount FROM transactions");
        rows = ps.executeQuery();
        update = connection.prepareStatement("UPDATE transactions SET amount = ? WHERE id = ?");
        while (rows.next()) {
            BigDecimal old = new BigDecimal(rows.getString("amount"));
            BigDecimal migrated = old.multiply(BigDecimal.valueOf(100));
            update.setString(1, migrated.toPlainString());
            update.setString(2, rows.getString("id"));
            update.executeUpdate();
        }
        stmt.executeUpdate("INSERT INTO schema_version (version, applied_at) VALUES (4, " + System.currentTimeMillis() + ")");
        LOGGER.info("Migration v4 applied: multiplied all amounts by 100 for decimals=2");
    }
}
```

- [ ] **Step 2: Auction-house migration**

In `AuctionStorageProvider` initialization, add migration after table creation:

```sql
-- Check if already migrated (add a migration_version table or use a flag)
-- Multiply all INTEGER amounts by 100:
UPDATE ah_listings SET buyout_price = buyout_price * 100, starting_bid = starting_bid * 100, current_bid = current_bid * 100, tax_amount = tax_amount * 100;
UPDATE ah_bids SET amount = amount * 100;
UPDATE ah_parcels SET amount = amount * 100;
UPDATE ah_price_history SET sale_price = sale_price * 100;
UPDATE ah_pending_notifications SET amount = amount * 100;
```

Use a flag column or table to prevent re-migration.

- [ ] **Step 3: Mail migration**

In `MailStorageProvider` initialization, add migration:

```sql
UPDATE mails SET currency_amount = currency_amount * 100, cod_amount = cod_amount * 100;
UPDATE mail_drafts SET currency_amount = currency_amount * 100, cod_amount = cod_amount * 100;
```

Use a flag to prevent re-migration.

- [ ] **Step 4: Build all**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat: DB migration v4 — multiply all amounts ×100 for decimals=2"
```

---

### Task 3: Fix command parsers — accept decimal input

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/PayCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/EcoAdminCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/CurrencyCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/BalanceCommand.java`

- [ ] **Step 1: Update PayCommand**

The user types `/pay Steve 150.50`. The double `150.50` must be converted to smallest unit `15050` before calling `economy.transfer()`.

Replace the conversion pattern:
```java
// OLD:
economy.transfer(sender.getUUID(), target.getUUID(), BigDecimal.valueOf(amount), currency);
String formatted = CurrencyFormatter.format(BigDecimal.valueOf(amount).longValue(), currency);

// NEW:
long smallestUnit = CurrencyFormatter.toSmallestUnit(BigDecimal.valueOf(amount), currency);
BigDecimal bdAmount = CurrencyFormatter.fromSmallestUnit(smallestUnit, currency);
economy.transfer(sender.getUUID(), target.getUUID(), bdAmount, currency);
String formatted = CurrencyFormatter.format(smallestUnit, currency);
```

- [ ] **Step 2: Update EcoAdminCommand (give/take/set)**

Same pattern for all 3 operations: convert double → toSmallestUnit → fromSmallestUnit for the BigDecimal API call.

- [ ] **Step 3: Update CurrencyCommand (convert)**

Same pattern for the exchange amount.

- [ ] **Step 4: Update BalanceCommand**

Balance display already uses `CurrencyFormatter.format(balance.longValue(), currency)`. Since balances are now stored ×100, this should work correctly with `decimals=2`.

- [ ] **Step 5: Build and test**

Run: `./gradlew :economy-core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(economy-core): commands accept decimal input, convert via toSmallestUnit"
```

---

### Task 4: Fix AuctionService fromSmallestUnit/toSmallestUnit

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java`

- [ ] **Step 1: Replace the broken conversion methods**

Current code (broken — ignores decimals):
```java
public static BigDecimal fromSmallestUnit(long amount, Currency currency) {
    return BigDecimal.valueOf(amount);
}
public static long toSmallestUnit(BigDecimal amount, Currency currency) {
    return amount.longValue();
}
```

Replace with delegation to CurrencyFormatter:
```java
public static BigDecimal fromSmallestUnit(long amount, Currency currency) {
    return CurrencyFormatter.fromSmallestUnit(amount, currency);
}
public static long toSmallestUnit(BigDecimal amount, Currency currency) {
    return CurrencyFormatter.toSmallestUnit(amount, currency);
}
```

- [ ] **Step 2: Fix MailService conversion methods too**

Same replacement in `mail/src/main/java/net/ecocraft/mail/service/MailService.java`.

- [ ] **Step 3: Build and test**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "fix: fromSmallestUnit/toSmallestUnit now respect currency.decimals()"
```

---

### Task 5: Replace BuyTab.formatPrice with CurrencyFormatter in AH

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/BuyTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/SellTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/MyAuctionsTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/LedgerTab.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java`

- [ ] **Step 1: Add Currency reference to AuctionHouseScreen**

The AH screen needs access to the full `Currency` object (not just the symbol string) for `CurrencyFormatter`. Add a field:
```java
private Currency currency; // set from server payload alongside currencySymbol
```

Add a getter: `public Currency getCurrency()`.

The `currency` can be reconstructed from the existing fields:
```java
this.currency = Currency.virtual(currencyId != null ? currencyId : "gold", "Gold", currencySymbol, 2);
```

Or better: send the full currency info from the server. For now, reconstruct from what we have.

- [ ] **Step 2: Replace all BuyTab.formatPrice calls**

Pattern:
```java
// OLD:
BuyTab.formatPrice(amount, parent.getCurrencySymbol())

// NEW:
CurrencyFormatter.format(amount, parent.getCurrency())
```

Do this in: BuyTab (2 calls), SellTab (6 calls), LedgerTab (2 calls), MyAuctionsTab (5 calls), AuctionHouseScreen (1 call).

Keep `BuyTab.formatPrice()` as a deprecated wrapper that delegates to CurrencyFormatter for backward compat.

- [ ] **Step 3: Add import**

Add `import net.ecocraft.api.currency.CurrencyFormatter;` and `import net.ecocraft.api.currency.Currency;` to all modified files.

- [ ] **Step 4: Build**

Run: `./gradlew :auction-house:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "refactor(auction-house): replace BuyTab.formatPrice with CurrencyFormatter"
```

---

### Task 6: Replace manual formatting in Mail

**Files:**
- Modify: `mail/src/main/java/net/ecocraft/mail/screen/MailListView.java`
- Modify: `mail/src/main/java/net/ecocraft/mail/screen/MailDetailView.java`
- Modify: `mail/src/main/java/net/ecocraft/mail/screen/MailComposeView.java`
- Modify: `mail/src/main/java/net/ecocraft/mail/screen/MailboxScreen.java`

- [ ] **Step 1: Add Currency object to MailboxScreen**

Like AH, the mail screen needs the full Currency object. Add:
```java
Currency currency; // reconstructed from currencySymbol + server data
```

Reconstruct in `receiveMailList`:
```java
this.currency = Currency.virtual("gold", "Gold", payload.currencySymbol(), 2);
```

- [ ] **Step 2: Replace manual formatting in MailListView**

Replace stats line manual formatting:
```java
// OLD:
sb.append(goldTotal).append(" ").append(screen.currencySymbol);

// NEW:
sb.append(CurrencyFormatter.format(goldTotal, screen.currency));
```

Replace tag formatting:
```java
// OLD:
Component.translatable("ecocraft_mail.list.tag_gold", mail.currencyAmount(), screen.currencySymbol)

// NEW — use CurrencyFormatter directly:
Component.literal("[" + CurrencyFormatter.format(mail.currencyAmount(), screen.currency) + "]")
```

- [ ] **Step 3: Replace in MailDetailView**

Replace currency/COD display with CurrencyFormatter.

- [ ] **Step 4: Update MailComposeView cost display**

The `computeTotalCost()` returns a long in smallest unit. Display should use CurrencyFormatter.

- [ ] **Step 5: Build**

Run: `./gradlew :mail:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "refactor(mail): replace manual currency formatting with CurrencyFormatter"
```

---

### Task 7: Integration test — full build + deploy

- [ ] **Step 1: Full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 2: Deploy**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
cp mail/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 3: Verify in-game**

Test checklist:
- `/balance` shows `100.00 G` (was 100, now ×100 = 10000 stored, displayed as 100.00)
- `/pay Steve 10.50` works and shows `10.50 G`
- `/eco give Steve 1000` gives `1000.00 G`
- AH prices display with 2 decimals
- Mail amounts display with 2 decimals
- Bureau de change works with decimals

- [ ] **Step 4: Commit and push**

```bash
git push origin master
```

---

## Migration Safety

**Important:** The DB migrations must be idempotent — they should NOT re-multiply amounts on a second startup. Each migration uses a version flag:
- economy-core: `schema_version` table (v4)
- auction-house: needs migration tracking (add `schema_version` or flag table)
- mail: needs migration tracking (add `schema_version` or flag table)

**Rollback:** If something goes wrong, the user can set `decimals = 0` in config and divide all amounts by 100 in the DB. Not automated but possible.
