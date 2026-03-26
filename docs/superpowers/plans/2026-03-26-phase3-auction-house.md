# Phase 3: Auction House — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the auction-house module — a WoW-style Auction House with buy/sell/bid, paginated GUI, NPC auctioneer, terminal block, and full server-client networking.

**Architecture:** Server-authoritative. All auction data lives on the server in SQLite. Client sends requests (search, buy, sell, bid) via packets, server responds with data. GUI is a pure `Screen` (no container menu) using gui-lib widgets. Access via NPC entity, block, or `/ah` command.

**Tech Stack:** Java 21, NeoForge 1.21.1, CustomPacketPayload + StreamCodec for networking, gui-lib widgets for UI.

---

## File Structure

```
auction-house/
├── build.gradle
└── src/
    ├── main/
    │   ├── java/net/ecocraft/ah/
    │   │   ├── AuctionHouseMod.java                    # @Mod entry
    │   │   ├── registry/
    │   │   │   └── AHRegistries.java                    # Blocks, items, entities, creative tab
    │   │   ├── data/
    │   │   │   ├── AuctionListing.java                  # Listing data record
    │   │   │   ├── AuctionBid.java                      # Bid data record
    │   │   │   ├── AuctionParcel.java                   # Parcel/delivery record
    │   │   │   ├── ListingType.java                     # BUYOUT, AUCTION enum
    │   │   │   ├── ListingStatus.java                   # ACTIVE, SOLD, EXPIRED, CANCELLED enum
    │   │   │   ├── ParcelSource.java                    # HDV_SALE, HDV_PURCHASE, etc. enum
    │   │   │   └── ItemCategory.java                    # Item categories enum
    │   │   ├── storage/
    │   │   │   ├── AuctionDatabaseSchema.java           # Table creation
    │   │   │   └── AuctionStorageProvider.java          # All auction DB operations
    │   │   ├── service/
    │   │   │   └── AuctionService.java                  # Business logic (list, buy, bid, expire)
    │   │   ├── network/
    │   │   │   ├── AHNetworkHandler.java                # Payload registration
    │   │   │   ├── payload/
    │   │   │   │   ├── OpenAHPayload.java               # Server→Client: open screen
    │   │   │   │   ├── RequestListingsPayload.java      # Client→Server: search/browse
    │   │   │   │   ├── ListingsResponsePayload.java     # Server→Client: listing results
    │   │   │   │   ├── RequestListingDetailPayload.java # Client→Server: view item detail
    │   │   │   │   ├── ListingDetailResponsePayload.java# Server→Client: detail results
    │   │   │   │   ├── CreateListingPayload.java        # Client→Server: sell item
    │   │   │   │   ├── BuyListingPayload.java           # Client→Server: buy/bid
    │   │   │   │   ├── CancelListingPayload.java        # Client→Server: cancel
    │   │   │   │   ├── CollectParcelsPayload.java       # Client→Server: collect
    │   │   │   │   ├── MyListingsResponsePayload.java   # Server→Client: my auctions data
    │   │   │   │   └── LedgerResponsePayload.java       # Server→Client: ledger data
    │   │   │   ├── ClientPayloadHandler.java            # Client-side handlers
    │   │   │   └── ServerPayloadHandler.java            # Server-side handlers
    │   │   ├── screen/
    │   │   │   ├── AuctionHouseScreen.java              # Main screen with tab switching
    │   │   │   ├── BuyTab.java                          # Buy tab content
    │   │   │   ├── SellTab.java                         # Sell tab content
    │   │   │   ├── MyAuctionsTab.java                   # My auctions tab content
    │   │   │   └── LedgerTab.java                       # Ledger tab content
    │   │   ├── block/
    │   │   │   └── AuctionTerminalBlock.java            # Terminal block
    │   │   ├── entity/
    │   │   │   ├── AuctioneerEntity.java                # NPC auctioneer
    │   │   │   └── AuctioneerRenderer.java              # Client renderer
    │   │   └── command/
    │   │       └── AHCommand.java                       # /ah command
    │   ├── resources/
    │   │   └── assets/ecocraft_ah/
    │   │       ├── blockstates/auction_terminal.json
    │   │       ├── models/block/auction_terminal.json
    │   │       ├── models/item/auction_terminal.json
    │   │       └── lang/
    │   │           ├── en_us.json
    │   │           └── fr_fr.json
    │   └── templates/
    │       └── META-INF/
    │           └── neoforge.mods.toml
    └── test/java/net/ecocraft/ah/
        ├── storage/AuctionStorageProviderTest.java
        └── service/AuctionServiceTest.java
```

---

### Task 1: Module Scaffolding + Data Model + Storage

**Files:** build.gradle, settings.gradle, mods.toml, mod entry point, all data records, storage layer

- [ ] **Step 1: Add to settings.gradle and gradle.properties**

Add `include ':auction-house'` to settings.gradle.
Add to gradle.properties:
```properties
ah_mod_id=ecocraft_ah
ah_mod_name=EcoCraft Auction House
```

- [ ] **Step 2: Create auction-house/build.gradle**

```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

base {
    archivesName = 'ecocraft-auction-house'
}

neoForge {
    version = project.neo_version

    parchment {
        mappingsVersion = project.parchment_mappings_version
        minecraftVersion = project.parchment_minecraft_version
    }

    runs {
        client { client() }
        server {
            server()
            programArgument '--nogui'
        }
    }

    mods {
        "${project.ah_mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    implementation project(':economy-api')
    implementation project(':economy-core')
    implementation project(':gui-lib')
    implementation 'org.xerial:sqlite-jdbc:3.47.1.0'
}

var generateModMetadata = tasks.register("generateModMetadata", ProcessResources) {
    var replaceProperties = [
        minecraft_version      : project.minecraft_version,
        minecraft_version_range: project.minecraft_version_range,
        neo_version            : project.neo_version,
        loader_version_range   : project.loader_version_range,
        mod_id                 : project.ah_mod_id,
        mod_name               : project.ah_mod_name,
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

- [ ] **Step 3: Create mods.toml, mod entry point, data records, enums**

Create neoforge.mods.toml template with dependencies on neoforge, minecraft, ecocraft_api, ecocraft_core.

Create `AuctionHouseMod.java`:
```java
@Mod(AuctionHouseMod.MOD_ID)
public class AuctionHouseMod {
    public static final String MOD_ID = "ecocraft_ah";
    public AuctionHouseMod(IEventBus modBus, ModContainer container) {
        AHRegistries.register(modBus);
    }
}
```

Create data enums: `ListingType` (BUYOUT, AUCTION), `ListingStatus` (ACTIVE, SOLD, EXPIRED, CANCELLED), `ParcelSource` (HDV_SALE, HDV_PURCHASE, HDV_EXPIRED, HDV_OUTBID), `ItemCategory` (WEAPONS, ARMOR, TOOLS, POTIONS, BLOCKS, FOOD, ENCHANTMENTS, MISC).

Create data records:
- `AuctionListing` — id, sellerUuid, sellerName, itemId, itemName, itemNbt, quantity, listingType, buyoutPrice, startingBid, currentBid, currentBidderUuid, currencyId, category, expiresAt, status, taxAmount, createdAt
- `AuctionBid` — id, listingId, bidderUuid, amount, timestamp
- `AuctionParcel` — id, recipientUuid, type (ITEM/CURRENCY), itemId, itemName, itemNbt, quantity, amount, currencyId, source, createdAt, collected

- [ ] **Step 4: Create AuctionDatabaseSchema and AuctionStorageProvider**

`AuctionDatabaseSchema`: creates tables listings, bids, parcels, price_history with appropriate indexes.

`AuctionStorageProvider`: CRUD operations using JDBC — createListing, getActiveListings (with search/category/pagination), getListingById, getListingsForItem, placeBid, completeSale, cancelListing, expireListings, createParcel, getParcels, collectParcel, logPriceHistory, getPriceHistory, getPlayerStats, getPlayerTransactionHistory.

- [ ] **Step 5: Create AuctionStorageProvider test**

Test: create listings, search, buy flow, bid flow, parcels, expiration.

- [ ] **Step 6: Create AuctionService**

Business logic layer that orchestrates storage + economy-api:
- `createListing(player, itemStack, type, price, duration)` — validates, takes deposit, stores
- `buyListing(player, listingId)` — validates, withdraws money, creates parcels, logs
- `placeBid(player, listingId, amount)` — validates, holds bid amount
- `cancelListing(player, listingId)` — validates ownership, returns item
- `expireListings()` — called periodically, expires old listings, returns items
- `collectParcels(player)` — delivers uncollected parcels
- `searchListings(query, category, page, pageSize)` — grouped by item type
- `getListingDetail(itemId)` — all listings for a specific item

- [ ] **Step 7: Create AuctionService test**

- [ ] **Step 8: Verify build and tests, commit**

```bash
git commit -m "feat(auction-house): scaffold module with data model, storage, and service layer"
```

---

### Task 2: Network Packets

**Files:** All payload records, AHNetworkHandler, ClientPayloadHandler, ServerPayloadHandler

- [ ] **Step 1: Create all payload records**

Each payload implements `CustomPacketPayload` with TYPE and STREAM_CODEC:

- `OpenAHPayload` — empty, server→client
- `RequestListingsPayload(String search, String category, int page)` — client→server
- `ListingsResponsePayload(List<ListingSummary> items, int page, int totalPages)` — server→client
  - `ListingSummary(String itemId, String itemName, int rarityColor, long bestPrice, int listingCount, int totalAvailable)`
- `RequestListingDetailPayload(String itemId)` — client→server
- `ListingDetailResponsePayload(String itemId, String itemName, int rarityColor, List<ListingEntry> listings, PriceInfo priceHistory)` — server→client
  - `ListingEntry(String listingId, String sellerName, int quantity, long unitPrice, String type, long expiresInMs)`
  - `PriceInfo(long avgPrice, long minPrice, long maxPrice, int volume7d)`
- `CreateListingPayload(String type, long price, int durationHours)` — client→server (item from player's hand)
- `BuyListingPayload(String listingId)` — client→server
- `CancelListingPayload(String listingId)` — client→server
- `CollectParcelsPayload()` — client→server
- `MyListingsResponsePayload(List<MyListingEntry> listings, int activeSales, long revenue7d, long taxesPaid7d, int parcelsToCollect)` — server→client
- `LedgerResponsePayload(List<LedgerEntry> entries, long netProfit, long totalSales, long totalPurchases, long taxesPaid, int page, int totalPages)` — server→client

- [ ] **Step 2: Create AHNetworkHandler**

Register all payloads in RegisterPayloadHandlersEvent.

- [ ] **Step 3: Create ServerPayloadHandler**

Handles client requests — delegates to AuctionService, sends responses.

- [ ] **Step 4: Create ClientPayloadHandler**

Handles server responses — updates the AuctionHouseScreen with received data.

- [ ] **Step 5: Verify build, commit**

```bash
git commit -m "feat(auction-house): add network packet layer for server-client communication"
```

---

### Task 3: GUI — Main Screen + Buy Tab

**Files:** AuctionHouseScreen.java, BuyTab.java

- [ ] **Step 1: Create AuctionHouseScreen**

Extends `Screen`. Uses `EcoTabBar` with 4 tabs (Acheter, Vendre, Mes enchères, Livre de compte). Manages tab switching. Delegates rendering to the active tab. Receives data from `ClientPayloadHandler` and routes to the appropriate tab.

Static `open()` method called by ClientPayloadHandler.

Layout: full-width dark background panel, tabs at top, content area below.

- [ ] **Step 2: Create BuyTab**

Two modes: browse (grouped items) and detail (specific item listings).

Browse mode:
- Left sidebar with `EcoScrollbar` + category list using `EcoFilterTags`
- `EcoSearchBar` at top
- `EcoPaginatedTable` showing grouped items (icon, name, best price, listings count, available)
- Click row → switches to detail mode

Detail mode:
- Back button
- Item header (large icon, name, category)
- `EcoPaginatedTable` with individual listings (seller, qty, unit price, type, expires, action button)
- Right panel with item info + price history (using `EcoStatCard`)

- [ ] **Step 3: Verify build, commit**

```bash
git commit -m "feat(auction-house): add main screen with Buy tab (browse + detail views)"
```

---

### Task 4: GUI — Sell Tab + My Auctions Tab + Ledger Tab

**Files:** SellTab.java, MyAuctionsTab.java, LedgerTab.java

- [ ] **Step 1: Create SellTab**

- Item slot area (shows item in player's main hand)
- Market price info panel
- Config form: type toggle (buyout/auction), price input, quantity, duration selector
- Summary with tax calculation
- "List for sale" button

- [ ] **Step 2: Create MyAuctionsTab**

- Sub-tabs (My Sales / My Purchases / Active Bids) using `EcoFilterTags`
- `EcoPaginatedTable` with listings, status badges, action buttons
- Footer stats using `EcoStatCard` row

- [ ] **Step 3: Create LedgerTab**

- Period filter tags (24h, 7d, 30d, All) + type filter tags
- Stats row (`EcoStatCard` x4)
- Transaction history `EcoPaginatedTable`

- [ ] **Step 4: Verify build, commit**

```bash
git commit -m "feat(auction-house): add Sell, My Auctions, and Ledger tabs"
```

---

### Task 5: Access Points — Block, NPC, Command

**Files:** AuctionTerminalBlock.java, AuctioneerEntity.java, AuctioneerRenderer.java, AHCommand.java, AHRegistries.java, assets

- [ ] **Step 1: Create AHRegistries**

Register: auction terminal block + item, auctioneer entity type, creative tab.

- [ ] **Step 2: Create AuctionTerminalBlock**

Simple block that on right-click sends OpenAHPayload to the player.

- [ ] **Step 3: Create AuctioneerEntity**

PathfinderMob with LookAtPlayerGoal. On mobInteract → sends OpenAHPayload. Invulnerable.

- [ ] **Step 4: Create AuctioneerRenderer**

Reuses villager model with custom texture (or default villager texture for now).

- [ ] **Step 5: Create AHCommand**

`/ah` — opens HDV (sends OpenAHPayload)
`/ah sell <price> [duration]` — quick sell item in hand
`/ah search <term>` — open HDV with search pre-filled
`/ah collect` — collect all parcels
`/ah admin reload|clear|expire` — admin commands

- [ ] **Step 6: Create assets (blockstates, models, lang)**

- [ ] **Step 7: Verify build, commit**

```bash
git commit -m "feat(auction-house): add terminal block, auctioneer NPC, and /ah commands"
```

---

### Task 6: Server Lifecycle + Expiration Tick

**Files:** AHServerEvents.java, update AuctionHouseMod.java

- [ ] **Step 1: Create AHServerEvents**

- `ServerStartingEvent`: Initialize AuctionStorageProvider, AuctionService
- `ServerStoppedEvent`: Shutdown storage
- `RegisterCommandsEvent`: Register AH commands
- `ServerTickEvent.Post`: Every 1200 ticks (60 seconds), run `auctionService.expireListings()`
- `PlayerLoggedInEvent`: Notify player if they have uncollected parcels

- [ ] **Step 2: Register entity renderer on client**

In AuctionHouseMod, register AuctioneerRenderer via EntityRenderersEvent.

- [ ] **Step 3: Register network handler on mod bus**

- [ ] **Step 4: Full build verification**

Run: `./gradlew clean build`

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(auction-house): wire server lifecycle, expiration tick, and network registration"
```
