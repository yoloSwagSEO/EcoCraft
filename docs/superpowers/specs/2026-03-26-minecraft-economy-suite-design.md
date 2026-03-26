# Minecraft Economy Suite — Design Spec

## Overview

A suite of NeoForge mods for Minecraft 1.21.x providing a unified economy API and a WoW-style Auction House. The project targets multiplayer servers and focuses on interoperability between economy mods.

## Project Structure

Mono-repo with 4 Gradle modules, each producing an independent .jar:

```
minecraft-economy/
├── gui-lib/              # Reusable GUI component library
├── economy-api/          # Pure interfaces — what other modders depend on
├── economy-core/         # Default implementation (storage, commands, permissions, bank)
├── integrations/
│   ├── lightmans/        # Bridge: Lightman's Currency
│   ├── numismatics/      # Bridge: Numismatics (Create addon)
│   └── ...               # Future integrations
├── auction-house/        # The Auction House mod
├── build.gradle          # Parent build
└── settings.gradle       # Module declarations
```

### Module Dependencies

- `gui-lib` — standalone, no dependencies on other modules
- `economy-api` — standalone, pure interfaces
- `economy-core` — depends on `economy-api`, optional dependency on `gui-lib`
- `integrations/*` — each depends on `economy-api` + its target mod
- `auction-house` — depends on `economy-api` + `gui-lib`

---

## Module 1: gui-lib — Reusable GUI Library

### Purpose

A shared GUI component library with a WoW-inspired dark theme. Used by all mods in the suite and available for other modders.

### Visual Style

- Dark background (#0d0d1a, #12122a, #1a1a2e)
- Gold accents (#ffd700) for headers, borders, highlights
- WoW rarity colors: white (common), green #1eff00 (uncommon), blue #0070dd (rare), purple #a335ee (epic), orange #FF9800 (legendary)
- Clean sans-serif feel adapted to Minecraft's rendering

### Components

| Component | Description |
|-----------|-------------|
| **Scrollbar** | WoW-style vertical scrollbar, draggable, mouse wheel support |
| **PaginatedTable** | Sortable headers, alternating row colors, hover highlight, page navigation |
| **TabBar** | Top-level navigation tabs (gold active, grey inactive) |
| **SubTabBar** | Secondary tab navigation |
| **SearchBar** | Text input with search icon |
| **FilterTags** | Clickable tag pills with active/inactive state |
| **Button** | Variants: primary (gold), success (green), auction (orange), danger (red), ghost |
| **StatCard** | Metric display with label, value, and optional trend indicator |
| **InfoPanel** | Side panel for item details, stats, metadata |
| **ItemSlot** | Item display with rarity border, icon rendering, drag & drop support |
| **Tooltip** | Hover tooltip for items, Minecraft/WoW hybrid style |
| **BarChart** | Simple bar chart for activity/stats visualization |
| **Theme** | Centralized color palette and style constants |

---

## Module 2: economy-api — Economy API

### Purpose

Pure Java interfaces that define the economy contract. No implementation. This is what other modders add as a dependency to integrate with the unified economy.

### Data Model

#### Currency

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique identifier (e.g., "gold", "diamonds") |
| `name` | String | Display name (e.g., "Or", "Diamants") |
| `symbol` | String | Short symbol (e.g., "⛁", "$") |
| `decimals` | int | Decimal places (0 = whole coins, 2 = 10.50 style) |
| `physical` | boolean | Whether backed by an in-game item |
| `itemType` | ResourceLocation | If physical, which Minecraft item it represents |
| `isDefault` | boolean | Whether this is the server's official currency |

#### Account

| Field | Type | Description |
|-------|------|-------------|
| `owner` | UUID | Player UUID (or faction/entity ID) |
| `currency` | Currency | Which currency |
| `virtualBalance` | BigDecimal | Pure virtual balance (earned via sales, /pay, etc.) |
| `vaultBalance` | BigDecimal | Value of physical items stored in the vault block |
| `totalBalance` | BigDecimal | virtualBalance + vaultBalance |

#### ExchangeRate

| Field | Type | Description |
|-------|------|-------------|
| `from` | Currency | Source currency |
| `to` | Currency | Target currency |
| `rate` | BigDecimal | Conversion rate (e.g., 1 gold = 100 silver) |
| `fee` | BigDecimal | Optional conversion commission |

#### Transaction

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `from` | UUID | Sender |
| `to` | UUID | Recipient |
| `amount` | BigDecimal | Amount |
| `currency` | Currency | Currency used |
| `type` | TransactionType | PAYMENT, EXCHANGE, TAX, HDV_SALE, HDV_PURCHASE, DEPOSIT, WITHDRAWAL |
| `timestamp` | Instant | When it occurred |

### API Interfaces

```java
// Core economy operations
EconomyProvider {
    getBalance(UUID player, Currency currency) → BigDecimal
    getVirtualBalance(UUID player, Currency currency) → BigDecimal
    getVaultBalance(UUID player, Currency currency) → BigDecimal
    withdraw(UUID player, BigDecimal amount, Currency currency) → TransactionResult
    deposit(UUID player, BigDecimal amount, Currency currency) → TransactionResult
    transfer(UUID from, UUID to, BigDecimal amount, Currency currency) → TransactionResult
}

// Currency exchange
ExchangeService {
    convert(UUID player, BigDecimal amount, Currency from, Currency to) → TransactionResult
    getRate(Currency from, Currency to) → ExchangeRate
    listRates() → List<ExchangeRate>
}

// Transaction history
TransactionLog {
    getHistory(UUID player, TransactionFilter filters) → Page<Transaction>
}

// Currency registry
CurrencyRegistry {
    register(Currency currency)
    getDefault() → Currency
    getById(String id) → Currency
    listAll() → List<Currency>
}
```

### Payment Priority

When a player spends money, the system follows this priority:
1. **Virtual balance first** — deduct from pure virtual balance
2. **Vault balance second** — if virtual is insufficient, remove physical items from the vault block to cover the remainder

---

## Module 3: economy-core — Default Implementation

### Purpose

The actual mod that runs on the server. Implements all economy-api interfaces and provides the default economy system.

### Storage

- **Default**: SQLite (single file in world save)
- **Optional**: MySQL / PostgreSQL for large servers wanting centralized storage
- Configured via mod config file
- Database abstraction layer so adding new backends is straightforward

### Permissions

Dual system:
- **Built-in**: Simple permission nodes checked against a config-based role system
- **LuckPerms integration**: Optional, auto-detected. When present, delegates permission checks to LuckPerms.
- Permission nodes: `economy.balance`, `economy.balance.others`, `economy.pay`, `economy.exchange`, `economy.admin.*`, etc.

### Vault Block (Coffre-fort synchronisé)

A special block (simple block initially, multiblock Immersive Engineering-style in the future) that acts as a synchronized physical-virtual bridge:

- **Deposit**: Player places currency items in the vault → virtual balance increases by the item's monetary value
- **Withdraw**: Player removes items from the vault → virtual balance decreases
- **Synchronized spending**: When a purchase occurs and virtual balance is insufficient, the system automatically removes physical items from the vault to cover the cost
- The vault always reflects the exact physical portion of the player's balance
- One vault per player (linked to UUID)

### Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/balance [player] [currency]` | `economy.balance` | View balance |
| `/pay <player> <amount> [currency]` | `economy.pay` | Pay another player |
| `/currency list` | `economy.currency.list` | List server currencies |
| `/currency convert <amount> <from> <to>` | `economy.exchange` | Convert currencies |
| `/eco give <player> <amount> [currency]` | `economy.admin.give` | Admin: give money |
| `/eco take <player> <amount> [currency]` | `economy.admin.take` | Admin: take money |
| `/eco set <player> <amount> [currency]` | `economy.admin.set` | Admin: set balance |
| `/bank` | `economy.bank` | Open vault block GUI |
| `/bank balance` | `economy.bank` | View virtual + vault breakdown |

### Access Points

The vault block is accessible via:
- **Physical block** placed in the world
- **Command** `/bank`

---

## Module 4: auction-house — Auction House

### Purpose

A WoW-style Auction House for buying and selling items between players. Depends on economy-api for all monetary operations.

### Data Model

#### Listing

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `seller` | UUID | Seller's UUID |
| `itemStack` | ItemStack | The Minecraft item (with NBT/components) |
| `quantity` | int | Quantity for sale |
| `listingType` | ListingType | BUYOUT or AUCTION |
| `buyoutPrice` | BigDecimal | Buy-now price |
| `startingBid` | BigDecimal | Starting bid (auction only) |
| `currentBid` | BigDecimal | Current highest bid |
| `currentBidder` | UUID | Current highest bidder |
| `currency` | Currency | Which currency |
| `category` | Category | Item category |
| `duration` | Duration | Listing duration |
| `expiresAt` | Instant | Expiration timestamp |
| `status` | ListingStatus | ACTIVE, SOLD, EXPIRED, CANCELLED |
| `taxAmount` | BigDecimal | Tax amount charged |

#### Bid

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `listing` | UUID | Associated listing |
| `bidder` | UUID | Bidder's UUID |
| `amount` | BigDecimal | Bid amount |
| `timestamp` | Instant | When the bid was placed |

#### PriceHistory

| Field | Type | Description |
|-------|------|-------------|
| `itemType` | ResourceLocation | Item type identifier |
| `averagePrice` | BigDecimal | Average sale price |
| `minPrice` | BigDecimal | Minimum sale price |
| `maxPrice` | BigDecimal | Maximum sale price |
| `volume` | int | Number of sales |
| `period` | Period | DAY, WEEK, MONTH |

#### Parcel (Delivery System)

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier |
| `recipient` | UUID | Player UUID |
| `type` | ParcelType | ITEM, CURRENCY_VIRTUAL, CURRENCY_PHYSICAL |
| `itemStack` | ItemStack | If delivering an item |
| `amount` | BigDecimal | If delivering currency |
| `currency` | Currency | If delivering currency |
| `source` | ParcelSource | HDV_SALE, HDV_PURCHASE, HDV_EXPIRED, HDV_OUTBID |
| `createdAt` | Instant | Creation timestamp |
| `collected` | boolean | Whether the player has picked it up |

### Delivery System

Configurable by server admin. Modes:
- **Mailbox**: Parcels go to a mailbox block/NPC. Player gets a notification and collects manually.
- **Bank**: Parcels go to the player's vault/bank.
- **Direct inventory**: Items go straight to inventory (queued if offline, delivered on login).

Currency delivery follows special rules:
- **Virtual currency** → always credited directly to the seller's balance (no delivery needed)
- **Physical currency / Items** → routed through the configured delivery system

### Categories

Default categories based on Minecraft item tags:
- Armes (Weapons)
- Armures (Armor)
- Outils (Tools)
- Potions
- Blocs (Blocks)
- Nourriture (Food)
- Enchantements (Enchantments)
- Divers (Miscellaneous)

Categories are configurable by the server admin.

### Tax System

- Configurable tax rate on sales (default: 5%)
- Optional listing deposit (refunded if sold, lost if expired)
- Tax amounts shown in the sell confirmation UI
- All tax transactions recorded in the transaction log

### Listing Duration

Configurable by server admin. Default tiers: 12h, 24h, 48h. Admins can define their own set of available durations.

### GUI — 4 Tabs

#### Tab 1: Buy (Acheter)

Layout: Classic WoW — sidebar categories left, results right.

- **Left sidebar**: Category list with scroll, active category highlighted with gold left border
- **Top**: Search bar + sort dropdown + filters button
- **Main area**: Paginated table showing grouped items
  - Columns: Item icon (with rarity border) | Item name (rarity color) | Best price | Number of listings | Total available
  - Click a row → opens detail view

**Detail view** (after clicking an item):
- Back button + item header (large icon, name, category, listing count)
- **Left**: Table of all individual listings
  - Columns: Seller | Quantity | Unit price | Type (Buy/Auction) | Expires | Action button
  - Best price highlighted with green left border
  - Action buttons: green "Buy" for buyout, orange "Bid" for auctions
- **Right**: Info panel
  - Large item icon with rarity border
  - Item stats/properties
  - Price history section: average price, min/max, sales volume (7d)

#### Tab 2: Sell (Vendre)

- **Left column**:
  - Item slot (drag & drop from inventory) showing item icon, name, enchantments
  - Market price info panel (average, min/max, active listings, recent sales)
- **Right column**: Sale configuration form
  - Sale type toggle: Buyout (green) / Auction (orange)
  - Price input field + currency indicator
  - Quantity selector (+/− buttons)
  - Duration selector (configured tier pills)
  - Summary box: sale price, tax amount, deposit, net amount received
  - "List for sale" confirmation button

#### Tab 3: My Auctions (Mes enchères)

- **Sub-tabs**: My Sales | My Purchases | Active Bids
- Paginated table:
  - Columns: Item icon | Item name | Price | Type | Status badge | Expires | Action
  - Status badges: "En vente" (green), "3 enchères" (orange), "Vendu" (gold), "Expiré" (red)
  - Actions: "Cancel" (red) for active listings, "Collect" (green) for sold/expired
- **Footer stats**: Active listings count | Revenue (7d) | Taxes paid (7d) | Parcels to collect

#### Tab 4: Ledger (Livre de compte)

- **Filters bar**: Period (24h, 7d, 30d, All) + Type (All, Purchases, Sales, Auctions, Expirations)
- **Stat cards**: Net profit (with trend %), Total sales, Total purchases, Taxes paid
- **Activity bar chart**: Sales (green) vs Purchases (blue) over selected period
- **Transaction history table**:
  - Columns: Item icon | Item name | Type badge (Sale/Purchase/Auction/Tax/Expired) | Amount (+green/−red) | Counterparty | Date
  - Tax entries shown as dedicated rows (system as counterparty)
  - Pagination

### Access Points

The Auction House is accessible via:
- **NPC**: A placeable auctioneer NPC entity
- **Block**: An auction terminal block
- **Command**: `/ah` opens the GUI directly

### Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/ah` | `ah.use` | Open auction house GUI |
| `/ah sell <price> [duration]` | `ah.sell` | Quick-sell item in hand |
| `/ah search <term>` | `ah.use` | Quick search |
| `/ah collect` | `ah.use` | Collect all parcels |
| `/ah stats [player]` | `ah.stats` | View sale statistics |
| `/ah admin reload` | `ah.admin.reload` | Reload configuration |
| `/ah admin clear` | `ah.admin.clear` | Clear all listings |
| `/ah admin expire` | `ah.admin.expire` | Force-expire all listings |

---

## Scrollbar & Pagination Strategy

- **Pagination** for all main data tables (item listings, transactions, ledger) — better for server performance with large datasets
- **Scrollbar** for sidebar categories, sub-lists, and any bounded UI area that may overflow
- Both components provided by `gui-lib` for consistency across all mods

---

## Integration Modules

Each integration is a small standalone mod that:
1. Detects the presence of the target mod at runtime
2. Registers a bridge that converts the target mod's currency into the unified economy system
3. Allows automatic conversion to/from the server's default currency

Planned integrations:
- **Lightman's Currency** — physical coins/bills
- **Numismatics** (Create addon) — Create-ecosystem currency
- **Grand Economy** — simple server-side economy
- **FTB Coins / FTB Money** — FTB ecosystem

---

## Technical Notes

- **Target**: NeoForge for Minecraft 1.21.x
- **Language**: Java 21
- **Build**: Gradle multi-module
- **Storage**: SQLite default, MySQL/PostgreSQL optional
- **Networking**: Custom packets for GUI sync (server-authoritative, client renders only)
- **Config**: NeoForge config system (TOML)
- **Localization**: Full i18n support (French + English minimum)
