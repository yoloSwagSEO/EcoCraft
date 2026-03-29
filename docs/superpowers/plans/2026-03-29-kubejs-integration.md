# KubeJS Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add optional KubeJS integration to economy-core and auction-house, exposing cancellable pre-events, post-events, and read/write bindings for server admins to script in JavaScript.

**Architecture:** Each module gets a `compat.kubejs` package with a `KubeJSPlugin` implementation registered via `kubejs.plugins.txt`. Event dispatchers are injected into `EconomyProviderImpl` and `AuctionService` as optional functional interfaces — null when KubeJS is absent. Pre-events are cancellable; post-events are listen-only.

**Tech Stack:** Java 21, NeoForge 21.1.221, KubeJS NeoForge 2101.7.2 (compileOnly)

---

## File Structure

### economy-core

```
economy-core/
  build.gradle                                          — MODIFY: add compileOnly kubejs
  src/main/resources/kubejs.plugins.txt                 — CREATE: plugin registration
  src/main/java/net/ecocraft/core/
    impl/EconomyProviderImpl.java                       — MODIFY: add event dispatcher hooks
    EcoServerEvents.java                                — MODIFY: inject dispatcher if kubejs loaded
    compat/kubejs/
      EcoKubeJSPlugin.java                              — CREATE: KubeJSPlugin impl
      EcoEventGroup.java                                — CREATE: EventGroup "ecocraft"
      EcoBindings.java                                  — CREATE: "EcoEconomy" binding
      EcoEventDispatcher.java                           — CREATE: implements TransactionEventDispatcher
      event/
        TransactionPreEvent.java                        — CREATE: cancellable PRE event
        TransactionPostEvent.java                       — CREATE: POST event
        BalanceChangedEvent.java                        — CREATE: POST event
```

### auction-house

```
auction-house/
  build.gradle                                          — MODIFY: add compileOnly kubejs
  src/main/resources/kubejs.plugins.txt                 — CREATE: plugin registration
  src/main/java/net/ecocraft/ah/
    service/AuctionService.java                         — MODIFY: add event dispatcher hooks
    AHServerEvents.java                                 — MODIFY: inject dispatcher if kubejs loaded
    compat/kubejs/
      AHKubeJSPlugin.java                               — CREATE: KubeJSPlugin impl
      AHEventGroup.java                                 — CREATE: EventGroup "ecocraft_ah"
      AHBindings.java                                   — CREATE: "AHAuctions" binding
      AHEventDispatcher.java                            — CREATE: implements AHEventDispatcher interface
      event/
        ListingCreatingEvent.java                       — CREATE: PRE, cancellable
        ListingCreatedEvent.java                        — CREATE: POST
        BuyingEvent.java                                — CREATE: PRE, cancellable
        SoldEvent.java                                  — CREATE: POST
        BidPlacingEvent.java                            — CREATE: PRE, cancellable
        BidPlacedEvent.java                             — CREATE: POST
        AuctionWonEvent.java                            — CREATE: POST
        AuctionLostEvent.java                           — CREATE: POST
        ListingCancellingEvent.java                     — CREATE: PRE, cancellable
        ListingCancelledEvent.java                      — CREATE: POST
        ListingExpiredEvent.java                        — CREATE: POST
```

### docs

```
docs/kubejs-examples/
  economy-basics.js                                     — CREATE: example scripts
  ah-moderation.js                                      — CREATE: example scripts
```

---

### Task 1: Gradle setup — add KubeJS compileOnly dependency

**Files:**
- Modify: `economy-core/build.gradle`
- Modify: `auction-house/build.gradle`
- Modify: `settings.gradle` (if maven repo needs adding at root level)

- [ ] **Step 1: Add maven repo and compileOnly dependency to economy-core**

In `economy-core/build.gradle`, add the maven repository and dependency:

```gradle
// Add inside the existing repositories block, or create one if absent:
repositories {
    maven { url "https://maven.latvian.dev/releases" }
}

// Add to existing dependencies block:
dependencies {
    // ... existing deps ...
    compileOnly "dev.latvian.mods:kubejs-neoforge:2101.7.2"
}
```

- [ ] **Step 2: Add same to auction-house**

In `auction-house/build.gradle`, add identically:

```gradle
repositories {
    maven { url "https://maven.latvian.dev/releases" }
}

dependencies {
    // ... existing deps ...
    compileOnly "dev.latvian.mods:kubejs-neoforge:2101.7.2"
}
```

- [ ] **Step 3: Verify build**

Run: `cd /home/florian/perso/minecraft && ./gradlew clean build`
Expected: BUILD SUCCESSFUL (KubeJS classes available at compile time but not at runtime)

- [ ] **Step 4: Commit**

```bash
git add economy-core/build.gradle auction-house/build.gradle
git commit -m "build: add KubeJS NeoForge as compileOnly dependency"
```

---

### Task 2: Economy event dispatcher interface + hooks in EconomyProviderImpl

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/impl/EconomyProviderImpl.java`

- [ ] **Step 1: Add TransactionEventDispatcher interface and setter**

Add inside `EconomyProviderImpl.java`, after the existing fields:

```java
import org.jetbrains.annotations.Nullable;

// --- Event dispatcher (optional, injected when KubeJS is present) ---

public interface TransactionEventDispatcher {
    /** Returns false if the event was cancelled. */
    boolean firePreTransaction(UUID player, BigDecimal amount, Currency currency,
                                TransactionType type, @Nullable UUID target);
    void firePostTransaction(UUID player, BigDecimal amount, Currency currency,
                              TransactionType type, @Nullable UUID target, boolean success);
    void fireBalanceChanged(UUID player, long oldBalance, long newBalance,
                             Currency currency, String cause);
}

@Nullable
private TransactionEventDispatcher eventDispatcher;

public void setEventDispatcher(@Nullable TransactionEventDispatcher dispatcher) {
    this.eventDispatcher = dispatcher;
}
```

- [ ] **Step 2: Add event hooks in withdraw()**

Modify the `withdraw()` method to fire events. Insert PRE check before the balance check, and POST + balanceChanged after:

```java
@Override
public TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency) {
    synchronized (db) {
        if (amount.signum() <= 0) {
            return TransactionResult.failure("Amount must be positive");
        }

        // PRE event — cancellable
        if (eventDispatcher != null &&
            !eventDispatcher.firePreTransaction(player, amount, currency, TransactionType.WITHDRAWAL, null)) {
            return TransactionResult.failure("Operation cancelled");
        }

        BigDecimal current = db.getVirtualBalance(player, currency.id());
        if (current.compareTo(amount) < 0) {
            if (eventDispatcher != null) {
                eventDispatcher.firePostTransaction(player, amount, currency, TransactionType.WITHDRAWAL, null, false);
            }
            return TransactionResult.failure("Insufficient funds");
        }
        BigDecimal newBalance = current.subtract(amount);
        db.setVirtualBalance(player, currency.id(), newBalance);

        var tx = new Transaction(UUID.randomUUID(), player, null, amount, currency,
            TransactionType.WITHDRAWAL, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        // POST events
        if (eventDispatcher != null) {
            eventDispatcher.firePostTransaction(player, amount, currency, TransactionType.WITHDRAWAL, null, true);
            long oldBal = current.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            long newBal = newBalance.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            eventDispatcher.fireBalanceChanged(player, oldBal, newBal, currency, "WITHDRAWAL");
        }

        return TransactionResult.success(tx);
    }
}
```

- [ ] **Step 3: Add same pattern to deposit()**

```java
@Override
public TransactionResult deposit(UUID player, BigDecimal amount, Currency currency) {
    synchronized (db) {
        if (amount.signum() <= 0) {
            return TransactionResult.failure("Amount must be positive");
        }

        if (eventDispatcher != null &&
            !eventDispatcher.firePreTransaction(player, amount, currency, TransactionType.DEPOSIT, null)) {
            return TransactionResult.failure("Operation cancelled");
        }

        BigDecimal current = db.getVirtualBalance(player, currency.id());
        BigDecimal newBalance = current.add(amount);
        db.setVirtualBalance(player, currency.id(), newBalance);

        var tx = new Transaction(UUID.randomUUID(), null, player, amount, currency,
            TransactionType.DEPOSIT, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        if (eventDispatcher != null) {
            eventDispatcher.firePostTransaction(player, amount, currency, TransactionType.DEPOSIT, null, true);
            long oldBal = current.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            long newBal = newBalance.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            eventDispatcher.fireBalanceChanged(player, oldBal, newBal, currency, "DEPOSIT");
        }

        return TransactionResult.success(tx);
    }
}
```

- [ ] **Step 4: Add same pattern to transfer()**

```java
@Override
public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency) {
    synchronized (db) {
        if (amount.signum() <= 0) {
            return TransactionResult.failure("Amount must be positive");
        }

        if (eventDispatcher != null &&
            !eventDispatcher.firePreTransaction(from, amount, currency, TransactionType.TRANSFER, to)) {
            return TransactionResult.failure("Operation cancelled");
        }

        BigDecimal senderBalance = db.getVirtualBalance(from, currency.id());
        if (senderBalance.compareTo(amount) < 0) {
            if (eventDispatcher != null) {
                eventDispatcher.firePostTransaction(from, amount, currency, TransactionType.TRANSFER, to, false);
            }
            return TransactionResult.failure("Insufficient funds");
        }

        db.setVirtualBalance(from, currency.id(), senderBalance.subtract(amount));
        BigDecimal receiverBalance = db.getVirtualBalance(to, currency.id());
        db.setVirtualBalance(to, currency.id(), receiverBalance.add(amount));

        var tx = new Transaction(UUID.randomUUID(), from, to, amount, currency,
            TransactionType.PAYMENT, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        if (eventDispatcher != null) {
            eventDispatcher.firePostTransaction(from, amount, currency, TransactionType.TRANSFER, to, true);
            long oldSender = senderBalance.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            long newSender = senderBalance.subtract(amount).setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            eventDispatcher.fireBalanceChanged(from, oldSender, newSender, currency, "TRANSFER");
            long oldReceiver = receiverBalance.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            long newReceiver = receiverBalance.add(amount).setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            eventDispatcher.fireBalanceChanged(to, oldReceiver, newReceiver, currency, "TRANSFER");
        }

        return TransactionResult.success(tx);
    }
}
```

- [ ] **Step 5: Add balanceChanged to setBalance()**

```java
public void setBalance(UUID player, BigDecimal amount, Currency currency) {
    synchronized (db) {
        BigDecimal oldBalance = db.getVirtualBalance(player, currency.id());
        db.setVirtualBalance(player, currency.id(), amount);

        var tx = new Transaction(UUID.randomUUID(), null, player, amount, currency,
            TransactionType.ADMIN_SET, Instant.now());
        db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
            currency.id(), tx.type().name(), tx.timestamp());

        if (eventDispatcher != null) {
            long oldBal = oldBalance.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            long newBal = amount.setScale(0, java.math.RoundingMode.DOWN).longValueExact();
            eventDispatcher.fireBalanceChanged(player, oldBal, newBal, currency, "ADMIN_SET");
        }
    }
}
```

- [ ] **Step 6: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :economy-core:build :economy-core:test`
Expected: BUILD SUCCESSFUL, all tests pass (eventDispatcher is null in tests — no behavior change)

- [ ] **Step 7: Commit**

```bash
git add economy-core/src/main/java/net/ecocraft/core/impl/EconomyProviderImpl.java
git commit -m "feat(economy-core): add TransactionEventDispatcher hooks in EconomyProviderImpl"
```

---

### Task 3: Economy KubeJS plugin — events and bindings

**Files:**
- Create: `economy-core/src/main/resources/kubejs.plugins.txt`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoKubeJSPlugin.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoEventGroup.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoBindings.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoEventDispatcher.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/event/TransactionPreEvent.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/event/TransactionPostEvent.java`
- Create: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/event/BalanceChangedEvent.java`

- [ ] **Step 1: Create kubejs.plugins.txt**

File: `economy-core/src/main/resources/kubejs.plugins.txt`
```
net.ecocraft.core.compat.kubejs.EcoKubeJSPlugin
```

- [ ] **Step 2: Create event classes**

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/event/TransactionPreEvent.java`
```java
package net.ecocraft.core.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class TransactionPreEvent implements KubeEvent {
    private final ServerPlayer player;
    private final long amount;
    private final String currency;
    private final String type;
    private final @Nullable ServerPlayer target;
    private boolean cancelled = false;
    private String message = "Operation cancelled by script";

    public TransactionPreEvent(ServerPlayer player, long amount, String currency,
                                String type, @Nullable ServerPlayer target) {
        this.player = player;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.target = target;
    }

    public ServerPlayer getPlayer() { return player; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getType() { return type; }
    public @Nullable ServerPlayer getTarget() { return target; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }

    public void cancel() { this.cancelled = true; }
    public void setMessage(String message) { this.message = message; }
}
```

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/event/TransactionPostEvent.java`
```java
package net.ecocraft.core.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class TransactionPostEvent implements KubeEvent {
    private final ServerPlayer player;
    private final long amount;
    private final String currency;
    private final String type;
    private final @Nullable ServerPlayer target;
    private final boolean success;

    public TransactionPostEvent(ServerPlayer player, long amount, String currency,
                                 String type, @Nullable ServerPlayer target, boolean success) {
        this.player = player;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.target = target;
        this.success = success;
    }

    public ServerPlayer getPlayer() { return player; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getType() { return type; }
    public @Nullable ServerPlayer getTarget() { return target; }
    public boolean isSuccess() { return success; }
}
```

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/event/BalanceChangedEvent.java`
```java
package net.ecocraft.core.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class BalanceChangedEvent implements KubeEvent {
    private final ServerPlayer player;
    private final long oldBalance;
    private final long newBalance;
    private final String currency;
    private final String cause;

    public BalanceChangedEvent(ServerPlayer player, long oldBalance, long newBalance,
                                String currency, String cause) {
        this.player = player;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.currency = currency;
        this.cause = cause;
    }

    public ServerPlayer getPlayer() { return player; }
    public long getOldBalance() { return oldBalance; }
    public long getNewBalance() { return newBalance; }
    public String getCurrency() { return currency; }
    public String getCause() { return cause; }
}
```

- [ ] **Step 3: Create EcoEventGroup**

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoEventGroup.java`
```java
package net.ecocraft.core.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import net.ecocraft.core.compat.kubejs.event.BalanceChangedEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPostEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPreEvent;

public interface EcoEventGroup {
    EventGroup GROUP = EventGroup.of("EcocraftEvents");

    EventHandler TRANSACTION = GROUP.server("transaction", () -> TransactionPreEvent.class);
    EventHandler TRANSACTION_AFTER = GROUP.server("transactionAfter", () -> TransactionPostEvent.class);
    EventHandler BALANCE_CHANGED = GROUP.server("balanceChanged", () -> BalanceChangedEvent.class);
}
```

- [ ] **Step 4: Create EcoEventDispatcher**

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoEventDispatcher.java`
```java
package net.ecocraft.core.compat.kubejs;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.compat.kubejs.event.BalanceChangedEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPreEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPostEvent;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class EcoEventDispatcher implements EconomyProviderImpl.TransactionEventDispatcher {

    private final MinecraftServer server;

    public EcoEventDispatcher(MinecraftServer server) {
        this.server = server;
    }

    private @Nullable ServerPlayer getPlayer(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean firePreTransaction(UUID player, BigDecimal amount, Currency currency,
                                       TransactionType type, @Nullable UUID target) {
        if (!EcoEventGroup.TRANSACTION.hasListeners()) return true;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return true; // offline player — skip event, allow operation

        long amountLong = amount.setScale(0, RoundingMode.DOWN).longValueExact();
        ServerPlayer targetPlayer = target != null ? getPlayer(target) : null;
        var event = new TransactionPreEvent(sp, amountLong, currency.id(), type.name(), targetPlayer);
        EcoEventGroup.TRANSACTION.post(event);
        return !event.isCancelled();
    }

    @Override
    public void firePostTransaction(UUID player, BigDecimal amount, Currency currency,
                                     TransactionType type, @Nullable UUID target, boolean success) {
        if (!EcoEventGroup.TRANSACTION_AFTER.hasListeners()) return;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return;

        long amountLong = amount.setScale(0, RoundingMode.DOWN).longValueExact();
        ServerPlayer targetPlayer = target != null ? getPlayer(target) : null;
        EcoEventGroup.TRANSACTION_AFTER.post(new TransactionPostEvent(sp, amountLong, currency.id(),
                type.name(), targetPlayer, success));
    }

    @Override
    public void fireBalanceChanged(UUID player, long oldBalance, long newBalance,
                                    Currency currency, String cause) {
        if (!EcoEventGroup.BALANCE_CHANGED.hasListeners()) return;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return;

        EcoEventGroup.BALANCE_CHANGED.post(new BalanceChangedEvent(sp, oldBalance, newBalance,
                currency.id(), cause));
    }
}
```

- [ ] **Step 5: Create EcoBindings**

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoBindings.java`
```java
package net.ecocraft.core.compat.kubejs;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.core.EcoServerEvents;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;

public class EcoBindings {

    public static long getBalance(ServerPlayer player) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return 0;
        Currency currency = reg.getDefault();
        return eco.getVirtualBalance(player.getUUID(), currency)
                .setScale(0, java.math.RoundingMode.DOWN).longValueExact();
    }

    public static long getBalance(ServerPlayer player, String currencyId) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return 0;
        Currency currency = reg.getById(currencyId);
        if (currency == null) return 0;
        return eco.getVirtualBalance(player.getUUID(), currency)
                .setScale(0, java.math.RoundingMode.DOWN).longValueExact();
    }

    public static boolean canAfford(ServerPlayer player, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.canAfford(player.getUUID(), BigDecimal.valueOf(amount), currency);
    }

    public static boolean deposit(ServerPlayer player, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.deposit(player.getUUID(), BigDecimal.valueOf(amount), currency).successful();
    }

    public static boolean withdraw(ServerPlayer player, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.withdraw(player.getUUID(), BigDecimal.valueOf(amount), currency).successful();
    }

    public static boolean transfer(ServerPlayer from, ServerPlayer to, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.transfer(from.getUUID(), to.getUUID(), BigDecimal.valueOf(amount), currency).successful();
    }

    public static void setBalance(ServerPlayer player, long amount) {
        var ctx = EcoServerEvents.getContext();
        if (ctx == null) return;
        var eco = (EconomyProviderImpl) ctx.getEconomyProvider();
        Currency currency = ctx.getCurrencyRegistry().getDefault();
        eco.setBalance(player.getUUID(), BigDecimal.valueOf(amount), currency);
    }
}
```

- [ ] **Step 6: Create EcoKubeJSPlugin**

File: `economy-core/src/main/java/net/ecocraft/core/compat/kubejs/EcoKubeJSPlugin.java`
```java
package net.ecocraft.core.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.binding.BindingRegistry;

public class EcoKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(EcoEventGroup.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("EcoEconomy", EcoBindings.class);
    }
}
```

- [ ] **Step 7: Build and verify**

Run: `cd /home/florian/perso/minecraft && ./gradlew :economy-core:build :economy-core:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add economy-core/src/main/resources/kubejs.plugins.txt \
    economy-core/src/main/java/net/ecocraft/core/compat/kubejs/
git commit -m "feat(economy-core): KubeJS plugin with events and bindings"
```

---

### Task 4: Wire economy dispatcher in EcoServerEvents

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java`

- [ ] **Step 1: Inject dispatcher on server start**

In `onServerStarting()`, after creating the `EcoServerContext`, add:

```java
// Wire KubeJS event dispatcher if KubeJS is loaded
if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
    try {
        var dispatcher = new net.ecocraft.core.compat.kubejs.EcoEventDispatcher(server);
        economyProvider.setEventDispatcher(dispatcher);
        LOGGER.info("KubeJS integration enabled for EcoCraft Economy");
    } catch (Exception e) {
        LOGGER.warn("Failed to initialize KubeJS integration: {}", e.getMessage());
    }
}
```

Note: the `economyProvider` variable is the local `EconomyProviderImpl` created just above.

- [ ] **Step 2: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :economy-core:build :economy-core:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java
git commit -m "feat(economy-core): wire KubeJS dispatcher on server start"
```

---

### Task 5: AH event dispatcher interface + hooks in AuctionService

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java`

- [ ] **Step 1: Add AHEventDispatcher interface**

Add inside `AuctionService.java`, after the existing `NotificationSender` and `ProfileResolver` interfaces:

```java
public interface AHEventDispatcher {
    boolean fireListingCreating(UUID seller, String itemId, String itemName, int qty,
                                 long price, ListingType type, String ahId);
    void fireListingCreated(AuctionListing listing);
    boolean fireBuying(UUID buyer, AuctionListing listing, int qty, long totalPrice);
    void fireSold(UUID buyer, String buyerName, AuctionListing listing, int qty,
                   long totalPrice, long tax);
    boolean fireBidPlacing(UUID bidder, AuctionListing listing, long amount);
    void fireBidPlaced(UUID bidder, String bidderName, AuctionListing listing,
                        long amount, long prevBid, @Nullable UUID prevBidder);
    void fireAuctionWon(UUID winner, String winnerName, AuctionListing listing, long finalPrice);
    void fireAuctionLost(UUID loser, String loserName, AuctionListing listing, long refund);
    boolean fireListingCancelling(UUID player, AuctionListing listing);
    void fireListingCancelled(UUID player, AuctionListing listing);
    void fireListingExpired(AuctionListing listing, boolean hadBids);
}

@Nullable
private AHEventDispatcher ahEventDispatcher;

public void setAHEventDispatcher(@Nullable AHEventDispatcher dispatcher) {
    this.ahEventDispatcher = dispatcher;
}
```

- [ ] **Step 2: Add hooks in createListing()**

After validation, before withdraw deposit:
```java
// KubeJS PRE event
if (ahEventDispatcher != null &&
    !ahEventDispatcher.fireListingCreating(sellerUuid, itemId, itemName, quantity,
            priceUnits, listingType, effectiveAhId)) {
    throw new AuctionException("Mise en vente bloquée par un script");
}
```

After `storage.createListing(listing)` and before `return listing`:
```java
if (ahEventDispatcher != null) {
    ahEventDispatcher.fireListingCreated(listing);
}
```

- [ ] **Step 3: Add hooks in buyListing()**

After validation, before withdraw from buyer:
```java
if (ahEventDispatcher != null &&
    !ahEventDispatcher.fireBuying(buyerUuid, listing, buyQuantity, totalPrice)) {
    throw new AuctionException("Achat bloqué par un script");
}
```

After all parcels created and sale complete, before price history logging:
```java
if (ahEventDispatcher != null) {
    ahEventDispatcher.fireSold(buyerUuid, buyerName, listing, buyQuantity, totalPrice, proportionalTax);
}
```

- [ ] **Step 4: Add hooks in placeBid()**

After validation, before withdraw:
```java
if (ahEventDispatcher != null &&
    !ahEventDispatcher.fireBidPlacing(bidderUuid, listing, amountUnits)) {
    throw new AuctionException("Enchère bloquée par un script");
}
```

After `storage.updateListingBid`:
```java
if (ahEventDispatcher != null) {
    ahEventDispatcher.fireBidPlaced(bidderUuid, bidderName, listing, amountUnits,
            listing.currentBid(), listing.currentBidderUuid());
}
```

- [ ] **Step 5: Add hooks in cancelListing()**

After ownership validation, before setting status:
```java
if (ahEventDispatcher != null &&
    !ahEventDispatcher.fireListingCancelling(playerUuid, listing)) {
    throw new AuctionException("Annulation bloquée par un script");
}
```

After cancel completes:
```java
if (ahEventDispatcher != null) {
    ahEventDispatcher.fireListingCancelled(playerUuid, listing);
}
```

- [ ] **Step 6: Add hooks in expireListings() and completedAuctionSale()**

In `completedAuctionSale()`, after crediting seller and creating parcels:
```java
if (ahEventDispatcher != null) {
    ahEventDispatcher.fireAuctionWon(listing.currentBidderUuid(),
            listing.sellerName(), listing, listing.currentBid());
}
```

In `expireListings()`, for auction with bids (after completedAuctionSale), notify losing bidders:
```java
// fireAuctionLost is called from within completedAuctionSale or the losing bidder loop
```

In `expireListings()`, for expired listings:
```java
if (ahEventDispatcher != null) {
    ahEventDispatcher.fireListingExpired(listing, hadBids);
}
```

- [ ] **Step 7: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :auction-house:build :auction-house:test`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 8: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java
git commit -m "feat(auction-house): add AHEventDispatcher hooks in AuctionService"
```

---

### Task 6: AH KubeJS plugin — events, bindings, and all event classes

**Files:**
- Create: `auction-house/src/main/resources/kubejs.plugins.txt`
- Create: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHKubeJSPlugin.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHEventGroup.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHBindings.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHEventDispatcherImpl.java`
- Create: 11 event classes in `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/event/`

- [ ] **Step 1: Create kubejs.plugins.txt**

File: `auction-house/src/main/resources/kubejs.plugins.txt`
```
net.ecocraft.ah.compat.kubejs.AHKubeJSPlugin
```

- [ ] **Step 2: Create all 11 event classes**

Each event class follows the same pattern. PRE events have `cancel()` + `setMessage()`. POST events are read-only. All implement `KubeEvent`.

Create each in `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/event/`:

**ListingCreatingEvent.java** (PRE, cancellable):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class ListingCreatingEvent implements KubeEvent {
    private final ServerPlayer seller;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long price;
    private final String listingType;
    private final String ahId;
    private boolean cancelled = false;
    private String message = "Mise en vente bloquée par un script";

    public ListingCreatingEvent(ServerPlayer seller, String itemId, String itemName,
                                 int quantity, long price, String listingType, String ahId) {
        this.seller = seller; this.itemId = itemId; this.itemName = itemName;
        this.quantity = quantity; this.price = price; this.listingType = listingType; this.ahId = ahId;
    }

    public ServerPlayer getSeller() { return seller; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getPrice() { return price; }
    public String getListingType() { return listingType; }
    public String getAhId() { return ahId; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
```

**ListingCreatedEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class ListingCreatedEvent implements KubeEvent {
    private final String listingId;
    private final String sellerName;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long price;
    private final String listingType;
    private final String ahId;

    public ListingCreatedEvent(String listingId, String sellerName, String itemId, String itemName,
                                int quantity, long price, String listingType, String ahId) {
        this.listingId = listingId; this.sellerName = sellerName; this.itemId = itemId;
        this.itemName = itemName; this.quantity = quantity; this.price = price;
        this.listingType = listingType; this.ahId = ahId;
    }

    public String getListingId() { return listingId; }
    public String getSellerName() { return sellerName; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getPrice() { return price; }
    public String getListingType() { return listingType; }
    public String getAhId() { return ahId; }
}
```

**BuyingEvent.java** (PRE, cancellable):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class BuyingEvent implements KubeEvent {
    private final ServerPlayer buyer;
    private final String listingId;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long totalPrice;
    private boolean cancelled = false;
    private String message = "Achat bloqué par un script";

    public BuyingEvent(ServerPlayer buyer, String listingId, String itemId, String itemName,
                        int quantity, long totalPrice) {
        this.buyer = buyer; this.listingId = listingId; this.itemId = itemId;
        this.itemName = itemName; this.quantity = quantity; this.totalPrice = totalPrice;
    }

    public ServerPlayer getBuyer() { return buyer; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getTotalPrice() { return totalPrice; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
```

**SoldEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class SoldEvent implements KubeEvent {
    private final ServerPlayer buyer;
    private final String sellerName;
    private final String listingId;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long totalPrice;
    private final long tax;

    public SoldEvent(ServerPlayer buyer, String sellerName, String listingId, String itemId,
                      String itemName, int quantity, long totalPrice, long tax) {
        this.buyer = buyer; this.sellerName = sellerName; this.listingId = listingId;
        this.itemId = itemId; this.itemName = itemName; this.quantity = quantity;
        this.totalPrice = totalPrice; this.tax = tax;
    }

    public ServerPlayer getBuyer() { return buyer; }
    public String getSellerName() { return sellerName; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getTotalPrice() { return totalPrice; }
    public long getTax() { return tax; }
}
```

**BidPlacingEvent.java** (PRE, cancellable):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class BidPlacingEvent implements KubeEvent {
    private final ServerPlayer bidder;
    private final String listingId;
    private final long amount;
    private boolean cancelled = false;
    private String message = "Enchère bloquée par un script";

    public BidPlacingEvent(ServerPlayer bidder, String listingId, long amount) {
        this.bidder = bidder; this.listingId = listingId; this.amount = amount;
    }

    public ServerPlayer getBidder() { return bidder; }
    public String getListingId() { return listingId; }
    public long getAmount() { return amount; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
```

**BidPlacedEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class BidPlacedEvent implements KubeEvent {
    private final ServerPlayer bidder;
    private final String listingId;
    private final long amount;
    private final long previousBid;
    private final @Nullable String previousBidderName;

    public BidPlacedEvent(ServerPlayer bidder, String listingId, long amount,
                           long previousBid, @Nullable String previousBidderName) {
        this.bidder = bidder; this.listingId = listingId; this.amount = amount;
        this.previousBid = previousBid; this.previousBidderName = previousBidderName;
    }

    public ServerPlayer getBidder() { return bidder; }
    public String getListingId() { return listingId; }
    public long getAmount() { return amount; }
    public long getPreviousBid() { return previousBid; }
    public @Nullable String getPreviousBidderName() { return previousBidderName; }
}
```

**AuctionWonEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class AuctionWonEvent implements KubeEvent {
    private final String winnerName;
    private final String sellerName;
    private final String listingId;
    private final String itemId;
    private final String itemName;
    private final long finalPrice;

    public AuctionWonEvent(String winnerName, String sellerName, String listingId,
                            String itemId, String itemName, long finalPrice) {
        this.winnerName = winnerName; this.sellerName = sellerName; this.listingId = listingId;
        this.itemId = itemId; this.itemName = itemName; this.finalPrice = finalPrice;
    }

    public String getWinnerName() { return winnerName; }
    public String getSellerName() { return sellerName; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public long getFinalPrice() { return finalPrice; }
}
```

**AuctionLostEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class AuctionLostEvent implements KubeEvent {
    private final String loserName;
    private final String listingId;
    private final long refundAmount;

    public AuctionLostEvent(String loserName, String listingId, long refundAmount) {
        this.loserName = loserName; this.listingId = listingId; this.refundAmount = refundAmount;
    }

    public String getLoserName() { return loserName; }
    public String getListingId() { return listingId; }
    public long getRefundAmount() { return refundAmount; }
}
```

**ListingCancellingEvent.java** (PRE, cancellable):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class ListingCancellingEvent implements KubeEvent {
    private final ServerPlayer player;
    private final String listingId;
    private boolean cancelled = false;
    private String message = "Annulation bloquée par un script";

    public ListingCancellingEvent(ServerPlayer player, String listingId) {
        this.player = player; this.listingId = listingId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getListingId() { return listingId; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
```

**ListingCancelledEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class ListingCancelledEvent implements KubeEvent {
    private final String playerName;
    private final String listingId;
    private final String itemId;
    private final String itemName;

    public ListingCancelledEvent(String playerName, String listingId, String itemId, String itemName) {
        this.playerName = playerName; this.listingId = listingId;
        this.itemId = itemId; this.itemName = itemName;
    }

    public String getPlayerName() { return playerName; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
}
```

**ListingExpiredEvent.java** (POST):
```java
package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class ListingExpiredEvent implements KubeEvent {
    private final String listingId;
    private final String sellerName;
    private final String itemId;
    private final String itemName;
    private final boolean hadBids;

    public ListingExpiredEvent(String listingId, String sellerName, String itemId,
                                String itemName, boolean hadBids) {
        this.listingId = listingId; this.sellerName = sellerName; this.itemId = itemId;
        this.itemName = itemName; this.hadBids = hadBids;
    }

    public String getListingId() { return listingId; }
    public String getSellerName() { return sellerName; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public boolean hadBids() { return hadBids; }
}
```

- [ ] **Step 3: Create AHEventGroup**

File: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHEventGroup.java`
```java
package net.ecocraft.ah.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import net.ecocraft.ah.compat.kubejs.event.*;

public interface AHEventGroup {
    EventGroup GROUP = EventGroup.of("EcocraftAHEvents");

    EventHandler LISTING_CREATING = GROUP.server("listingCreating", () -> ListingCreatingEvent.class);
    EventHandler LISTING_CREATED = GROUP.server("listingCreated", () -> ListingCreatedEvent.class);
    EventHandler BUYING = GROUP.server("buying", () -> BuyingEvent.class);
    EventHandler SOLD = GROUP.server("sold", () -> SoldEvent.class);
    EventHandler BID_PLACING = GROUP.server("bidPlacing", () -> BidPlacingEvent.class);
    EventHandler BID_PLACED = GROUP.server("bidPlaced", () -> BidPlacedEvent.class);
    EventHandler AUCTION_WON = GROUP.server("auctionWon", () -> AuctionWonEvent.class);
    EventHandler AUCTION_LOST = GROUP.server("auctionLost", () -> AuctionLostEvent.class);
    EventHandler LISTING_CANCELLING = GROUP.server("listingCancelling", () -> ListingCancellingEvent.class);
    EventHandler LISTING_CANCELLED = GROUP.server("listingCancelled", () -> ListingCancelledEvent.class);
    EventHandler LISTING_EXPIRED = GROUP.server("listingExpired", () -> ListingExpiredEvent.class);
}
```

- [ ] **Step 4: Create AHEventDispatcherImpl**

File: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHEventDispatcherImpl.java`
```java
package net.ecocraft.ah.compat.kubejs;

import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.data.ListingType;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.compat.kubejs.event.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AHEventDispatcherImpl implements AuctionService.AHEventDispatcher {

    private final MinecraftServer server;

    public AHEventDispatcherImpl(MinecraftServer server) {
        this.server = server;
    }

    private @Nullable ServerPlayer getPlayer(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean fireListingCreating(UUID seller, String itemId, String itemName,
                                        int qty, long price, ListingType type, String ahId) {
        if (!AHEventGroup.LISTING_CREATING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(seller);
        if (sp == null) return true;
        var event = new ListingCreatingEvent(sp, itemId, itemName, qty, price, type.name(), ahId);
        AHEventGroup.LISTING_CREATING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireListingCreated(AuctionListing listing) {
        if (!AHEventGroup.LISTING_CREATED.hasListeners()) return;
        AHEventGroup.LISTING_CREATED.post(new ListingCreatedEvent(
                listing.id(), listing.sellerName(), listing.itemId(), listing.itemName(),
                listing.quantity(), listing.buyoutPrice() > 0 ? listing.buyoutPrice() : listing.startingBid(),
                listing.listingType().name(), listing.ahId()));
    }

    @Override
    public boolean fireBuying(UUID buyer, AuctionListing listing, int qty, long totalPrice) {
        if (!AHEventGroup.BUYING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(buyer);
        if (sp == null) return true;
        var event = new BuyingEvent(sp, listing.id(), listing.itemId(), listing.itemName(), qty, totalPrice);
        AHEventGroup.BUYING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireSold(UUID buyer, String buyerName, AuctionListing listing,
                          int qty, long totalPrice, long tax) {
        if (!AHEventGroup.SOLD.hasListeners()) return;
        ServerPlayer sp = getPlayer(buyer);
        if (sp == null) return;
        AHEventGroup.SOLD.post(new SoldEvent(sp, listing.sellerName(), listing.id(),
                listing.itemId(), listing.itemName(), qty, totalPrice, tax));
    }

    @Override
    public boolean fireBidPlacing(UUID bidder, AuctionListing listing, long amount) {
        if (!AHEventGroup.BID_PLACING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(bidder);
        if (sp == null) return true;
        var event = new BidPlacingEvent(sp, listing.id(), amount);
        AHEventGroup.BID_PLACING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireBidPlaced(UUID bidder, String bidderName, AuctionListing listing,
                               long amount, long prevBid, @Nullable UUID prevBidder) {
        if (!AHEventGroup.BID_PLACED.hasListeners()) return;
        ServerPlayer sp = getPlayer(bidder);
        if (sp == null) return;
        String prevName = prevBidder != null ? listing.sellerName() : null; // approximation
        AHEventGroup.BID_PLACED.post(new BidPlacedEvent(sp, listing.id(), amount, prevBid, prevName));
    }

    @Override
    public void fireAuctionWon(UUID winner, String winnerName, AuctionListing listing, long finalPrice) {
        if (!AHEventGroup.AUCTION_WON.hasListeners()) return;
        AHEventGroup.AUCTION_WON.post(new AuctionWonEvent(winnerName, listing.sellerName(),
                listing.id(), listing.itemId(), listing.itemName(), finalPrice));
    }

    @Override
    public void fireAuctionLost(UUID loser, String loserName, AuctionListing listing, long refund) {
        if (!AHEventGroup.AUCTION_LOST.hasListeners()) return;
        AHEventGroup.AUCTION_LOST.post(new AuctionLostEvent(loserName, listing.id(), refund));
    }

    @Override
    public boolean fireListingCancelling(UUID player, AuctionListing listing) {
        if (!AHEventGroup.LISTING_CANCELLING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return true;
        var event = new ListingCancellingEvent(sp, listing.id());
        AHEventGroup.LISTING_CANCELLING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireListingCancelled(UUID player, AuctionListing listing) {
        if (!AHEventGroup.LISTING_CANCELLED.hasListeners()) return;
        ServerPlayer sp = getPlayer(player);
        String name = sp != null ? sp.getName().getString() : "Unknown";
        AHEventGroup.LISTING_CANCELLED.post(new ListingCancelledEvent(name, listing.id(),
                listing.itemId(), listing.itemName()));
    }

    @Override
    public void fireListingExpired(AuctionListing listing, boolean hadBids) {
        if (!AHEventGroup.LISTING_EXPIRED.hasListeners()) return;
        AHEventGroup.LISTING_EXPIRED.post(new ListingExpiredEvent(listing.id(), listing.sellerName(),
                listing.itemId(), listing.itemName(), hadBids));
    }
}
```

- [ ] **Step 5: Create AHBindings**

File: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHBindings.java`
```java
package net.ecocraft.ah.compat.kubejs;

import net.ecocraft.ah.AHServerEvents;
import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AHBindings {

    private static AuctionService getService() {
        return AHServerEvents.getAuctionService();
    }

    public static List<?> getListings(String ahId) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        return service.searchListings(ahId, "", null, 0, 100);
    }

    public static List<AuctionListing> getListingsByItem(String ahId, String itemId) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        return service.getListingDetail(ahId, itemId);
    }

    public static Map<String, Object> getPlayerStats(ServerPlayer player) {
        var service = getService();
        if (service == null) return Collections.emptyMap();
        var stats = service.getPlayerStats(player.getUUID(), 0L);
        Map<String, Object> result = new HashMap<>();
        result.put("totalSales", stats.totalSales());
        result.put("totalPurchases", stats.totalPurchases());
        result.put("totalRevenue", stats.totalRevenue());
        result.put("totalSpent", stats.totalSpent());
        return result;
    }

    public static List<AuctionListing> getPlayerListings(ServerPlayer player) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        return service.getMyListings(player.getUUID(), 0, 100);
    }

    public static long getBestPrice(String itemId) {
        var service = getService();
        if (service == null) return 0;
        return service.getBestPrice(null, null, itemId);
    }

    public static boolean cancelListing(String listingId) {
        var service = getService();
        if (service == null) return false;
        try {
            // Admin cancel — use system UUID
            service.cancelListing(new java.util.UUID(0, 0), listingId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 6: Create AHKubeJSPlugin**

File: `auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/AHKubeJSPlugin.java`
```java
package net.ecocraft.ah.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.binding.BindingRegistry;

public class AHKubeJSPlugin implements KubeJSPlugin {

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(AHEventGroup.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("AHAuctions", AHBindings.class);
    }
}
```

- [ ] **Step 7: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :auction-house:build :auction-house:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add auction-house/src/main/resources/kubejs.plugins.txt \
    auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/
git commit -m "feat(auction-house): KubeJS plugin with 11 events and bindings"
```

---

### Task 7: Wire AH dispatcher in AHServerEvents

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/AHServerEvents.java`

- [ ] **Step 1: Add static accessor for AuctionService**

If not already present, add a public accessor:
```java
public static AuctionService getAuctionService() {
    return auctionService;
}
```

- [ ] **Step 2: Inject AH dispatcher on server start**

In `onServerStarting()`, after creating `auctionService` and setting the notification sender:

```java
// Wire KubeJS event dispatcher if KubeJS is loaded
if (net.neoforged.fml.ModList.get().isLoaded("kubejs")) {
    try {
        var dispatcher = new net.ecocraft.ah.compat.kubejs.AHEventDispatcherImpl(server);
        auctionService.setAHEventDispatcher(dispatcher);
        LOGGER.info("KubeJS integration enabled for EcoCraft Auction House");
    } catch (Exception e) {
        LOGGER.warn("Failed to initialize KubeJS AH integration: {}", e.getMessage());
    }
}
```

- [ ] **Step 3: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew clean build`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/AHServerEvents.java
git commit -m "feat(auction-house): wire KubeJS AH dispatcher on server start"
```

---

### Task 8: Example scripts and final build

**Files:**
- Create: `docs/kubejs-examples/economy-basics.js`
- Create: `docs/kubejs-examples/ah-moderation.js`

- [ ] **Step 1: Create economy example script**

File: `docs/kubejs-examples/economy-basics.js`
```javascript
// EcoCraft Economy — KubeJS Example Scripts
// Place in: kubejs/server_scripts/

// Log all transactions
EcocraftEvents.transactionAfter(event => {
  console.log(`[Eco] ${event.player.name.string}: ${event.type} ${event.amount} ${event.currency} (${event.success ? 'OK' : 'FAILED'})`)
})

// Block withdrawals over 10000
EcocraftEvents.transaction(event => {
  if (event.type === 'WITHDRAWAL' && event.amount > 10000) {
    event.setMessage('Withdrawals over 10,000 are not allowed!')
    event.cancel()
  }
})

// Notify on large balance changes
EcocraftEvents.balanceChanged(event => {
  let diff = event.newBalance - event.oldBalance
  if (Math.abs(diff) > 1000) {
    event.player.tell(`Your balance changed by ${diff} ${event.currency}`)
  }
})

// Give bonus gold on first join (alternative to config)
// PlayerEvents.loggedIn(event => {
//   if (EcoEconomy.getBalance(event.player) === 0) {
//     EcoEconomy.deposit(event.player, 500)
//     event.player.tell('Welcome! You received 500 Gold!')
//   }
// })
```

- [ ] **Step 2: Create AH example script**

File: `docs/kubejs-examples/ah-moderation.js`
```javascript
// EcoCraft Auction House — KubeJS Example Scripts
// Place in: kubejs/server_scripts/

// Block listings of specific items
EcocraftAHEvents.listingCreating(event => {
  if (event.itemId.includes('netherite')) {
    event.setMessage('Netherite items cannot be sold on the AH!')
    event.cancel()
  }
})

// Max price enforcement
EcocraftAHEvents.listingCreating(event => {
  if (event.price > 100000) {
    event.setMessage('Maximum listing price is 100,000!')
    event.cancel()
  }
})

// Announce sales server-wide
EcocraftAHEvents.sold(event => {
  if (event.totalPrice > 5000) {
    event.buyer.server.tell(`[HDV] ${event.buyer.name.string} bought ${event.itemName} for ${event.totalPrice} Gold!`)
  }
})

// Log all bids
EcocraftAHEvents.bidPlaced(event => {
  console.log(`[HDV] Bid: ${event.bidder.name.string} placed ${event.amount} on listing ${event.listingId}`)
})

// Notify when auctions expire
EcocraftAHEvents.listingExpired(event => {
  console.log(`[HDV] Listing expired: ${event.itemName} by ${event.sellerName} (had bids: ${event.hadBids()})`)
})

// Read data with bindings
// let bestPrice = AHAuctions.getBestPrice('minecraft:diamond')
// let stats = AHAuctions.getPlayerStats(player)
// console.log(`Player sold: ${stats.totalSales}, spent: ${stats.totalSpent}`)
```

- [ ] **Step 3: Final full build and deploy**

Run: `cd /home/florian/perso/minecraft && ./gradlew clean build`
Expected: BUILD SUCCESSFUL

Deploy:
```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 4: Commit**

```bash
git add docs/kubejs-examples/
git commit -m "docs: add KubeJS example scripts for economy and AH"
```
