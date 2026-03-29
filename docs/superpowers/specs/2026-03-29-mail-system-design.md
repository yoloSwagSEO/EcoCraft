# Mail System Design

## Goal

New standalone `mail` module providing an in-game mailbox system. Players and systems can send mails with text, item attachments, currency, and COD (cash on delivery). Accessible via a craftable Mailbox block, a Postman NPC, or the `/mail` command.

## Motivation

The auction house needs a configurable delivery system (DIRECT / MAILBOX / BOTH). Beyond AH delivery, a mail system enables player-to-player item/currency exchange, admin announcements, quest rewards, and future mod integrations.

## Architecture

### New Module: `mail`

5th Gradle module in the mono-repo. Depends on `economy-api` and `gui-lib`. Independent of `auction-house` — the AH will integrate with mail as an optional dependency in a future Spec 2.

```
mail/
  src/main/java/net/ecocraft/mail/
    MailMod.java                         — NeoForge mod entry point
    MailServerEvents.java                — server lifecycle, permission registration
    config/MailConfig.java               — NeoForge server config
    data/
      Mail.java                          — mail record
      MailItemAttachment.java            — item attachment record
      MailStatus.java                    — enum for mail state helpers
    storage/MailStorageProvider.java      — SQLite (SQL-standard queries)
    service/MailService.java             — business logic
    permission/MailPermissions.java      — NeoForge PermissionNode constants
    block/
      MailboxBlock.java                  — mailbox block (public access point)
      MailboxBlockEntity.java            — block entity (minimal)
    entity/
      PostmanEntity.java                 — NPC facteur (same pattern as Auctioneer)
      PostmanRenderer.java               — renderer
    screen/
      MailboxScreen.java                 — main screen (list + detail + compose)
    network/
      MailNetworkHandler.java            — packet registration
      payload/                           — client↔server payloads
    command/MailCommand.java             — /mail commands
    registry/MailRegistries.java         — blocks, items, entities, creative tab
  src/main/resources/
    assets/ecocraft_mail/lang/           — FR/EN/ES
    assets/ecocraft_mail/textures/       — block/entity textures
    assets/ecocraft_mail/models/         — block/item models
    META-INF/neoforge.mods.toml
```

### Dependencies

```gradle
dependencies {
    implementation project(':economy-api')
    implementation project(':gui-lib')
}
```

No dependency on `economy-core` or `auction-house`. The mail module uses `economy-api` interfaces to handle currency operations.

## Data Model

### Mail Record

```java
record Mail(
    String id,                    // UUID
    UUID senderUuid,              // null for system mails
    String senderName,            // display name ("Hôtel des Ventes", "Grimald", "[Admin]")
    UUID recipientUuid,           // recipient player
    String subject,               // mail subject
    String body,                  // message body (can be empty)

    // Attachments
    List<MailItemAttachment> items, // 0-N item attachments
    long currencyAmount,          // currency amount (0 if none)
    String currencyId,            // null if no currency

    // Cash on Delivery
    long codAmount,               // COD price (0 = no COD)
    String codCurrencyId,         // COD currency

    // State
    boolean read,                 // has been opened
    boolean collected,            // attachments collected
    boolean indestructible,       // no auto-expiry (admin/system mails)
    boolean returned,             // returned to sender (COD refused)

    // Timestamps
    long createdAt,               // creation time
    long availableAt,             // available for collection after this time (delivery delay)
    long expiresAt                // auto-delete after this time (createdAt + 30d default)
)
```

### MailItemAttachment Record

```java
record MailItemAttachment(
    String itemId,                // registry name (e.g., "minecraft:diamond")
    String itemName,              // display name
    String itemNbt,               // serialized NBT/components (nullable)
    int quantity                  // stack count
)
```

### Mail Lifecycle

1. **Created** → `availableAt` in the future (delivery delay) or now (instant)
2. **Available** → visible in mailbox when `availableAt <= now`
3. **Read** → player opened the detail view
4. **Collected** → player clicked "Collect" (items/currency delivered)
5. **Deleted** → player manually deleted (only if read AND collected, or no attachments)
6. **Expired** → `expiresAt <= now` AND not indestructible → auto-deleted
7. **COD flow**: recipient clicks "Pay & Collect" → currency sent to sender, items delivered. Or "Return" → new mail sent back to sender with the items.

### Expiration Rules

- Normal mail not collected after 30 days → deleted, items/currency lost
- COD mail not paid after 30 days → auto-returned to sender (new mail with items, no COD)
- Indestructible mail → never auto-deleted, stays until player manually deletes after collection
- Player can delete a mail manually if: (read AND collected) OR (no attachments AND read) — regardless of indestructible flag

## UI

### Main Screen (Mail List)

- **Header**: "Boîte aux lettres" + stats (X mails, Y items à collecter, Z Gold à collecter)
- **"Tout collecter" button** top-right: collects all available items/currency from all mails (skips COD mails)
- **"Nouveau mail" button**: opens compose screen
- **Scrollable mail list**, each row:
  - Envelope icon (closed = unread, open = read)
  - Subject in bold + sender name below in grey
  - Right side: attachment icons (item icon if items, coin icon if currency, COD tag if COD)
  - "Collecter" button on the right IF attachments to collect and NOT COD
  - "Supprimer" button if mail is read and collected (or text-only and read)
- **Sort**: unread first, then by date descending

### Detail Screen (click on a mail)

- Subject in large text
- "De: SenderName" + date
- Message body (scrollable text area)
- Attachment section at bottom: item slots + currency amount displayed
- **If COD**: banner "Contre-remboursement: 500 Gold" + "Payer & Collecter" button + "Retourner" button
- **If normal attachments**: "Collecter" button
- **"Supprimer" button** if eligible
- **"Retour" button** to go back to list

### Compose Screen (new mail)

- "Destinataire" field (player name autocomplete)
- "Objet" field
- "Message" text area
- Attachment zone: slots for drag & drop items from inventory
- "Montant" currency field (optional)
- "Contre-remboursement" toggle + COD amount field
- "Envoyer" button + send cost displayed (if configured)
- "Annuler" button

### Settings Screen (gear icon)

- **Notifications tab** (all players): notification preferences per mail event type (new mail, COD received, mail returned) — chat/toast/both/none
- **General tab** (admin only): all config toggles (allowPlayerMail, allowItemAttachments, allowCurrency, allowCOD, maxItemAttachments, mailExpiryDays, sendCost, codFeePercent, allowMailboxCraft)

## Permissions (NeoForge PermissionAPI)

### Boolean Nodes

| Node | Default | Description |
|------|---------|-------------|
| `ecocraft.mail.read` | everyone | Open mailbox (block/NPC) |
| `ecocraft.mail.command` | everyone | Use `/mail` commands |
| `ecocraft.mail.send` | everyone | Send player-to-player mails |
| `ecocraft.mail.attach.items` | everyone | Attach items to mails |
| `ecocraft.mail.attach.currency` | everyone | Attach currency to mails |
| `ecocraft.mail.cod` | everyone | Send COD mails |
| `ecocraft.mail.admin` | OP (level 2) | Admin commands, system mails, settings |

### Integer Nodes

| Node | Default | Convention |
|------|---------|------------|
| `ecocraft.mail.max_attachments` | -1 | -1=unlimited, 0=forbidden, N=limit |

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/mail` | `ecocraft.mail.command` | Opens mailbox UI |
| `/mail send <player> <subject>` | `ecocraft.mail.send` | Send a text-only mail (no attachments, use UI for that) |
| `/mail admin send <player> <subject> <message>` | `ecocraft.mail.admin` | Send system mail (indestructible) |
| `/mail admin sendall <subject> <message>` | `ecocraft.mail.admin` | Send system mail to all players |
| `/mail admin clear <player>` | `ecocraft.mail.admin` | Delete all mails for a player |
| `/mail admin purge` | `ecocraft.mail.admin` | Delete all expired mails from DB |

## Config (NeoForge Server Config)

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `allowPlayerMail` | boolean | true | Players can send mails to each other |
| `allowItemAttachments` | boolean | true | Allow item attachments |
| `allowCurrencyAttachments` | boolean | true | Allow currency attachments |
| `allowCOD` | boolean | true | Allow cash on delivery |
| `maxItemAttachments` | int | 12 | Max items per mail |
| `mailExpiryDays` | int | 30 | Days before auto-expiry |
| `sendCost` | long | 0 | Cost to send a mail (0 = free) |
| `codFeePercent` | int | 0 | Fee % on COD payments (taken from sender's received payment) |
| `allowMailboxCraft` | boolean | true | Players can craft the mailbox block |

## Block & Entity

### Mailbox Block

- Custom block extending `BaseEntityBlock`
- Public access point — any player can interact, sees their own mails
- Right-click opens MailboxScreen
- Craftable (recipe TBD later, config `allowMailboxCraft` gates it)
- Available in creative tab "EcoCraft"

### Postman NPC

- Entity extending `Mob` (same pattern as AuctioneerEntity)
- `setNoGravity(true)`, `setInvulnerable(true)`, `LookAtPlayerGoal`
- Right-click opens MailboxScreen (identical to block)
- Spawn egg in creative tab
- Admin places them in towns/spawns

## Storage (SQLite)

### Table: `mails`

```sql
CREATE TABLE mails (
    id               TEXT PRIMARY KEY,
    sender_uuid      TEXT,
    sender_name      TEXT NOT NULL,
    recipient_uuid   TEXT NOT NULL,
    subject          TEXT NOT NULL,
    body             TEXT NOT NULL DEFAULT '',
    currency_amount  INTEGER NOT NULL DEFAULT 0,
    currency_id      TEXT,
    cod_amount       INTEGER NOT NULL DEFAULT 0,
    cod_currency_id  TEXT,
    read             INTEGER NOT NULL DEFAULT 0,
    collected        INTEGER NOT NULL DEFAULT 0,
    indestructible   INTEGER NOT NULL DEFAULT 0,
    returned         INTEGER NOT NULL DEFAULT 0,
    created_at       INTEGER NOT NULL,
    available_at     INTEGER NOT NULL,
    expires_at       INTEGER NOT NULL
);
CREATE INDEX idx_mails_recipient ON mails(recipient_uuid, collected);
```

### Table: `mail_items`

```sql
CREATE TABLE mail_items (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    mail_id    TEXT NOT NULL REFERENCES mails(id) ON DELETE CASCADE,
    item_id    TEXT NOT NULL,
    item_name  TEXT NOT NULL,
    item_nbt   TEXT,
    quantity   INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX idx_mail_items_mail ON mail_items(mail_id);
```

Note: All queries use SQL-standard syntax (no SQLite-specific features) to ease future MariaDB migration.

## Notifications

Same pattern as auction-house:
- `MailNotificationEventType`: NEW_MAIL, COD_RECEIVED, MAIL_RETURNED, MAIL_EXPIRED
- Per-player local config (JSON file): chat/toast/both/none per event type
- Toast overlay via EcoToastManager (already in gui-lib)

## Network Payloads

| Payload | Direction | Description |
|---------|-----------|-------------|
| `OpenMailboxPayload` | S→C | Trigger mailbox screen open |
| `RequestMailListPayload` | C→S | Request mail list |
| `MailListResponsePayload` | S→C | List of mails (summaries) |
| `RequestMailDetailPayload` | C→S | Request full mail content |
| `MailDetailResponsePayload` | S→C | Full mail with attachments |
| `CollectMailPayload` | C→S | Collect one or all mails |
| `CollectMailResultPayload` | S→C | Collection result |
| `SendMailPayload` | C→S | Send a new mail (with attachments) |
| `SendMailResultPayload` | S→C | Send result (success/error) |
| `DeleteMailPayload` | C→S | Delete a mail |
| `ReturnCODPayload` | C→S | Return a COD mail to sender |
| `PayCODPayload` | C→S | Pay COD and collect |
| `MailNotificationPayload` | S→C | New mail notification (toast/chat) |
| `MailSettingsPayload` | S→C | Config values for the settings screen |
| `UpdateMailSettingsPayload` | C→S | Admin updates config |

## API for Other Modules

`MailService` exposes a public method for system mails:

```java
public String sendSystemMail(
    String senderName,         // "Hôtel des Ventes", "[Admin]"
    UUID recipientUuid,
    String subject,
    String body,
    List<MailItemAttachment> items,
    long currencyAmount,
    String currencyId,
    boolean indestructible,
    long availableAt           // delivery delay (epoch ms, or 0 for instant)
)
```

Returns the mail ID. This is the entry point for AH integration (Spec 2), quest rewards, admin tools, etc.

## What This Spec Does NOT Cover (Spec 2)

- AH ↔ Mail integration (delivery mode config, parcel-to-mail conversion)
- AH delivery delay configuration per parcel type
- Migration of existing AH parcels to mails
- Mailbox craft recipe (deferred)
