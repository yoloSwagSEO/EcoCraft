# KubeJS Integration Design

## Goal

Integrate KubeJS into EcoCraft's economy-core and auction-house modules as an optional dependency, exposing events (pre + post) and bindings so server admins can script custom behaviors in JavaScript.

## Motivation

KubeJS is the most popular scripting mod in the NeoForge ecosystem. Providing native integration makes EcoCraft more attractive for modded servers and increases visibility on CurseForge/Modrinth.

## Architecture

### Module Structure (Pattern B — in-module integration)

KubeJS compatibility lives inside existing modules in a dedicated package:

- `economy-core/src/.../core/compat/kubejs/` — economy events + bindings
- `auction-house/src/.../ah/compat/kubejs/` — AH events + bindings

Each module has its own `KubeJSPlugin` implementation registered via `kubejs.plugins.txt`. NeoForge ignores this file if KubeJS is not installed — zero runtime impact without KubeJS.

No code outside `compat.kubejs` packages references KubeJS classes.

### Dependency

```gradle
// In economy-core/build.gradle and auction-house/build.gradle
repositories {
    maven { url "https://maven.latvian.dev/releases" }
}
dependencies {
    compileOnly "dev.latvian.mods:kubejs-neoforge:2101.7.2"
}
```

KubeJS version: **2101.7.2-build.348** (latest stable for MC 1.21.1 NeoForge).

### Plugin Registration

Each module provides a `kubejs.plugins.txt` in `src/main/resources/`:

```
net.ecocraft.core.compat.kubejs.EcoKubeJSPlugin
```
```
net.ecocraft.ah.compat.kubejs.AHKubeJSPlugin
```

### Conditional Loading

The plugins are only instantiated by KubeJS's own class loader when KubeJS is present. No `ModList.isLoaded()` guard needed — the `kubejs.plugins.txt` mechanism handles this natively.

## Economy Integration (economy-core)

### Files

```
economy-core/src/main/java/net/ecocraft/core/compat/kubejs/
  EcoKubeJSPlugin.java          — implements KubeJSPlugin, registers events + bindings
  EcoEventGroup.java            — EventGroup "ecocraft"
  EcoBindings.java              — static methods exposed as "EcoEconomy" binding
  event/
    TransactionPreEvent.java    — PRE, cancellable
    TransactionPostEvent.java   — POST
    BalanceChangedEvent.java    — POST
```

`src/main/resources/kubejs.plugins.txt`:
```
net.ecocraft.core.compat.kubejs.EcoKubeJSPlugin
```

### Events (EventGroup: `"ecocraft"`)

#### `ecocraft.transaction` (PRE, cancellable)

Fired before any deposit, withdraw, or transfer. Cancelling prevents the operation.

Script access:
```javascript
EcocraftEvents.transaction(event => {
  event.player    // ServerPlayer
  event.amount    // long (smallest unit)
  event.currency  // String (currency id)
  event.type      // String: "DEPOSIT", "WITHDRAWAL", "TRANSFER", "PAYMENT", etc.
  event.target    // ServerPlayer | null (for transfers)
  event.cancel()  // prevent the transaction
})
```

#### `ecocraft.transaction.after` (POST)

Fired after a transaction completes (success or failure).

Script access:
```javascript
EcocraftEvents.transactionAfter(event => {
  event.player    // ServerPlayer
  event.amount    // long
  event.currency  // String
  event.type      // String
  event.target    // ServerPlayer | null
  event.success   // boolean
})
```

#### `ecocraft.balanceChanged` (POST)

Fired after any balance change (deposit, withdraw, transfer, admin set).

Script access:
```javascript
EcocraftEvents.balanceChanged(event => {
  event.player      // ServerPlayer
  event.oldBalance  // long
  event.newBalance  // long
  event.currency    // String
  event.cause       // String: "DEPOSIT", "WITHDRAWAL", "TRANSFER", "ADMIN_SET"
})
```

### Bindings (`EcoEconomy`)

Registered as global binding `"EcoEconomy"`.

```javascript
// Read
EcoEconomy.getBalance(player)             // long — default currency
EcoEconomy.getBalance(player, currencyId) // long — specific currency
EcoEconomy.canAfford(player, amount)      // boolean

// Write
EcoEconomy.deposit(player, amount)        // boolean (success)
EcoEconomy.withdraw(player, amount)       // boolean (success)
EcoEconomy.transfer(from, to, amount)     // boolean (success)
EcoEconomy.setBalance(player, amount)     // void (admin operation)
```

All write operations go through the existing `EconomyProvider` (validated, synchronized, logged).

### Hook Points in Existing Code

Events are fired from `EconomyProviderImpl`:
- `deposit()` → fire PRE before, POST after
- `withdraw()` → fire PRE before, POST after
- `transfer()` → fire PRE before, POST after
- `setBalance()` → fire `balanceChanged` after

The hook must be injected conditionally. `EcoServerEvents` checks `ModList.get().isLoaded("kubejs")` once at server start and stores a boolean. The `EconomyProviderImpl` receives an optional event dispatcher (functional interface) that the KubeJS plugin provides.

Pattern:
```java
// In EconomyProviderImpl
public interface TransactionEventDispatcher {
    boolean firePreTransaction(UUID player, BigDecimal amount, Currency currency, TransactionType type, @Nullable UUID target);
    void firePostTransaction(UUID player, BigDecimal amount, Currency currency, TransactionType type, @Nullable UUID target, boolean success);
    void fireBalanceChanged(UUID player, long oldBalance, long newBalance, Currency currency, String cause);
}

private @Nullable TransactionEventDispatcher eventDispatcher;
public void setEventDispatcher(@Nullable TransactionEventDispatcher dispatcher) { ... }
```

## Auction House Integration (auction-house)

### Files

```
auction-house/src/main/java/net/ecocraft/ah/compat/kubejs/
  AHKubeJSPlugin.java           — implements KubeJSPlugin, registers events + bindings
  AHEventGroup.java             — EventGroup "ecocraft_ah"
  AHBindings.java               — static methods exposed as "AHAuctions" binding
  event/
    ListingCreatingEvent.java   — PRE, cancellable
    ListingCreatedEvent.java    — POST
    BuyingEvent.java            — PRE, cancellable
    SoldEvent.java              — POST
    BidPlacingEvent.java        — PRE, cancellable
    BidPlacedEvent.java         — POST
    AuctionWonEvent.java        — POST
    AuctionLostEvent.java       — POST
    ListingCancellingEvent.java — PRE, cancellable
    ListingCancelledEvent.java  — POST
    ListingExpiredEvent.java    — POST
```

`src/main/resources/kubejs.plugins.txt`:
```
net.ecocraft.ah.compat.kubejs.AHKubeJSPlugin
```

### Events (EventGroup: `"ecocraft_ah"`)

#### Listing Lifecycle

| Event | Type | Cancellable | Fields |
|-------|------|-------------|--------|
| `ecocraft_ah.listingCreating` | PRE | yes | seller (ServerPlayer), itemId, itemName, quantity, price (long), listingType, ahId |
| `ecocraft_ah.listingCreated` | POST | no | listingId, seller (ServerPlayer), itemId, itemName, quantity, price, listingType, ahId |

#### Buy Flow

| Event | Type | Cancellable | Fields |
|-------|------|-------------|--------|
| `ecocraft_ah.buying` | PRE | yes | buyer (ServerPlayer), listingId, itemId, itemName, quantity, totalPrice |
| `ecocraft_ah.sold` | POST | no | buyer (ServerPlayer), seller (UUID+name), listingId, itemId, itemName, quantity, totalPrice, tax |

#### Bid Flow

| Event | Type | Cancellable | Fields |
|-------|------|-------------|--------|
| `ecocraft_ah.bidPlacing` | PRE | yes | bidder (ServerPlayer), listingId, amount |
| `ecocraft_ah.bidPlaced` | POST | no | bidder (ServerPlayer), listingId, amount, previousBid, previousBidder |

#### Auction Completion

| Event | Type | Cancellable | Fields |
|-------|------|-------------|--------|
| `ecocraft_ah.auctionWon` | POST | no | winner (UUID+name), seller (UUID+name), listingId, itemId, itemName, finalPrice |
| `ecocraft_ah.auctionLost` | POST | no | loser (UUID+name), listingId, refundAmount |

#### Cancellation & Expiry

| Event | Type | Cancellable | Fields |
|-------|------|-------------|--------|
| `ecocraft_ah.listingCancelling` | PRE | yes | player (ServerPlayer), listingId |
| `ecocraft_ah.listingCancelled` | POST | no | player (ServerPlayer), listingId, itemId, itemName |
| `ecocraft_ah.listingExpired` | POST | no | listingId, seller (UUID+name), itemId, itemName, hadBids |

#### Script Examples

```javascript
// Block diamond sales
EcocraftAHEvents.listingCreating(event => {
  if (event.itemId.includes('diamond')) {
    event.cancel()
    event.seller.tell('Diamond sales are forbidden!')
  }
})

// Bonus XP on purchase
EcocraftAHEvents.sold(event => {
  event.buyer.addXP(50)
})

// Log all bids
EcocraftAHEvents.bidPlaced(event => {
  console.log(`${event.bidder.name} bid ${event.amount} on ${event.listingId}`)
})

// Anti-snipe: extend auction if bid in last 30 seconds
EcocraftAHEvents.bidPlacing(event => {
  // Scripts can read listing data and act on it
})
```

### Bindings (`AHAuctions`)

Registered as global binding `"AHAuctions"`.

```javascript
// Read
AHAuctions.getListings(ahId)              // List of listing summaries
AHAuctions.getListingsByItem(ahId, itemId) // List of listings for an item
AHAuctions.getPlayerStats(player)          // { totalSales, totalPurchases, totalBids, ... }
AHAuctions.getPlayerListings(player)       // List of player's active listings
AHAuctions.getBestPrice(itemId)            // long — lowest buyout price

// Write
AHAuctions.cancelListing(listingId)        // boolean — admin cancel
```

### Hook Points in Existing Code

Same pattern as economy: `AuctionService` receives an optional event dispatcher interface.

```java
public interface AHEventDispatcher {
    boolean fireListingCreating(UUID seller, String itemId, String itemName, int qty, long price, ListingType type, String ahId);
    void fireListingCreated(AuctionListing listing);
    boolean fireBuying(UUID buyer, AuctionListing listing, int qty, long totalPrice);
    void fireSold(UUID buyer, String buyerName, AuctionListing listing, int qty, long totalPrice, long tax);
    boolean fireBidPlacing(UUID bidder, AuctionListing listing, long amount);
    void fireBidPlaced(UUID bidder, String bidderName, AuctionListing listing, long amount, long prevBid, UUID prevBidder);
    void fireAuctionWon(UUID winner, String winnerName, AuctionListing listing, long finalPrice);
    void fireAuctionLost(UUID loser, String loserName, AuctionListing listing, long refund);
    boolean fireListingCancelling(UUID player, AuctionListing listing);
    void fireListingCancelled(UUID player, AuctionListing listing);
    void fireListingExpired(AuctionListing listing, boolean hadBids);
}
```

Injected from `AHServerEvents.onServerStarting()` if KubeJS is loaded.

## Error Handling

- If a PRE event is cancelled, the operation throws `AuctionException` / returns `TransactionResult.failure()` with a generic message. The script can set a custom message via `event.setMessage("reason")`.
- Exceptions thrown inside KubeJS scripts are caught and logged (KubeJS handles this natively). They never crash the server.
- If the event dispatcher is null (KubeJS not installed), all operations proceed normally with zero overhead.

## Testing

- Unit tests for event dispatcher interfaces (mock dispatcher, verify events fire at correct points)
- Manual testing with KubeJS installed: example scripts that log events and cancel specific operations
- Example scripts provided in `docs/kubejs-examples/` for users

## Example Scripts (for documentation)

`docs/kubejs-examples/economy-basics.js`:
```javascript
// Give 100 Gold bonus on first join
PlayerEvents.loggedIn(event => {
  let balance = EcoEconomy.getBalance(event.player)
  if (balance === 0) {
    EcoEconomy.deposit(event.player, 100)
    event.player.tell('Welcome! You received 100 Gold!')
  }
})

// Tax all transfers at 5%
EcocraftEvents.transaction(event => {
  if (event.type === 'TRANSFER') {
    // Let the transfer happen, then withdraw 5% from sender
    // (handled in post-event instead)
  }
})
EcocraftEvents.transactionAfter(event => {
  if (event.type === 'TRANSFER' && event.success) {
    let tax = Math.floor(event.amount * 0.05)
    if (tax > 0) EcoEconomy.withdraw(event.player, tax)
  }
})
```

`docs/kubejs-examples/ah-moderation.js`:
```javascript
// Block items worth more than 10000 Gold
EcocraftAHEvents.listingCreating(event => {
  if (event.price > 10000) {
    event.cancel()
    event.seller.tell('Max listing price is 10,000 Gold!')
  }
})

// Announce sales in chat
EcocraftAHEvents.sold(event => {
  event.server.tell(`[HDV] ${event.buyer.name} bought ${event.itemName} for ${event.totalPrice} Gold!`)
})
```
