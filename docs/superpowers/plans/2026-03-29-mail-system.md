# Mail System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a standalone `mail` module with mailbox block, postman NPC, player-to-player messaging with item/currency attachments, COD, expiration, and configurable settings.

**Architecture:** New 5th Gradle module depending on economy-api + gui-lib. SQLite storage with SQL-standard queries. Mailbox block + Postman NPC as public access points. EcoScreen-based UI (list → detail → compose). NeoForge PermissionAPI for permissions. Network payloads for client↔server. Public `MailService.sendSystemMail()` API for future AH integration.

**Tech Stack:** Java 21, NeoForge 21.1.221, SQLite, gui-lib V2 widget tree, NeoForge PermissionAPI

---

## File Structure

```
mail/
  build.gradle
  src/main/java/net/ecocraft/mail/
    MailMod.java
    MailServerEvents.java
    config/MailConfig.java
    data/
      Mail.java
      MailItemAttachment.java
    storage/MailStorageProvider.java
    service/MailService.java
    permission/MailPermissions.java
    block/
      MailboxBlock.java
      MailboxBlockEntity.java
    entity/
      PostmanEntity.java
      PostmanRenderer.java
    screen/
      MailboxScreen.java
      MailListView.java
      MailDetailView.java
      MailComposeView.java
      MailSettingsScreen.java
    network/
      MailNetworkHandler.java
      MailServerPayloadHandler.java
      payload/
        OpenMailboxPayload.java
        RequestMailListPayload.java
        MailListResponsePayload.java
        RequestMailDetailPayload.java
        MailDetailResponsePayload.java
        CollectMailPayload.java
        CollectMailResultPayload.java
        SendMailPayload.java
        SendMailResultPayload.java
        DeleteMailPayload.java
        ReturnCODPayload.java
        PayCODPayload.java
        MailNotificationPayload.java
        MailSettingsPayload.java
        UpdateMailSettingsPayload.java
    command/MailCommand.java
    client/
      MailClientEvents.java
      MailNotificationManager.java
      MailNotificationConfig.java
    registry/MailRegistries.java
  src/main/resources/
    assets/ecocraft_mail/lang/fr_fr.json
    assets/ecocraft_mail/lang/en_us.json
    assets/ecocraft_mail/lang/es_es.json
    assets/ecocraft_mail/blockstates/mailbox.json
    assets/ecocraft_mail/models/block/mailbox.json
    assets/ecocraft_mail/models/item/mailbox.json
    assets/ecocraft_mail/textures/block/mailbox.png (placeholder)
  src/main/templates/META-INF/neoforge.mods.toml
  src/test/java/net/ecocraft/mail/
    storage/MailStorageProviderTest.java
    service/MailServiceTest.java
```

---

### Task 1: Gradle module scaffolding

**Files:**
- Modify: `settings.gradle`
- Modify: `gradle.properties`
- Create: `mail/build.gradle`
- Create: `mail/src/main/templates/META-INF/neoforge.mods.toml`
- Create: `mail/src/main/java/net/ecocraft/mail/MailMod.java`

- [ ] **Step 1: Add module to settings.gradle**

Add `':mail'` to the include list in `settings.gradle`.

- [ ] **Step 2: Add mail properties to gradle.properties**

```properties
# Mail
mail_mod_id=ecocraft_mail
mail_mod_name=EcoCraft Mail
```

- [ ] **Step 3: Create mail/build.gradle**

```gradle
plugins {
    id 'net.neoforged.moddev' version '2.0.141'
}

base {
    archivesName = 'ecocraft-mail'
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
        "${project.mail_mod_id}" {
            sourceSet(sourceSets.main)
        }
    }
}

dependencies {
    implementation project(':economy-api')
    implementation project(':gui-lib')
    implementation 'org.xerial:sqlite-jdbc:3.47.1.0'
    testImplementation 'org.slf4j:slf4j-simple:2.0.9'
}

var generateModMetadata = tasks.register("generateModMetadata", ProcessResources) {
    var replaceProperties = [
        minecraft_version      : project.minecraft_version,
        minecraft_version_range: project.minecraft_version_range,
        neo_version            : project.neo_version,
        loader_version_range   : project.loader_version_range,
        mod_id                 : project.mail_mod_id,
        mod_name               : project.mail_mod_name,
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

- [ ] **Step 4: Create neoforge.mods.toml template**

File: `mail/src/main/templates/META-INF/neoforge.mods.toml`

```toml
modLoader="javafml"
loaderVersion="${loader_version_range}"
license="MIT"

[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
description='''
In-game mail system for EcoCraft. Mailbox block, Postman NPC, player-to-player messaging with item/currency attachments and COD.
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

[[dependencies.${mod_id}]]
modId="ecocraft_gui"
type="required"
versionRange="[${mod_version},)"
ordering="AFTER"
side="BOTH"
```

- [ ] **Step 5: Create MailMod entry point**

File: `mail/src/main/java/net/ecocraft/mail/MailMod.java`

```java
package net.ecocraft.mail;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(MailMod.MOD_ID)
public class MailMod {
    public static final String MOD_ID = "ecocraft_mail";

    public MailMod(IEventBus modBus, ModContainer container) {
        // Registries, config, and event handlers will be added in subsequent tasks
    }
}
```

- [ ] **Step 6: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add settings.gradle gradle.properties mail/
git commit -m "feat(mail): scaffold new mail module with Gradle config"
```

---

### Task 2: Data records + SQLite storage

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/data/Mail.java`
- Create: `mail/src/main/java/net/ecocraft/mail/data/MailItemAttachment.java`
- Create: `mail/src/main/java/net/ecocraft/mail/storage/MailStorageProvider.java`
- Create: `mail/src/test/java/net/ecocraft/mail/storage/MailStorageProviderTest.java`

- [ ] **Step 1: Create MailItemAttachment record**

```java
package net.ecocraft.mail.data;

import org.jetbrains.annotations.Nullable;

public record MailItemAttachment(
    String itemId,
    String itemName,
    @Nullable String itemNbt,
    int quantity
) {}
```

- [ ] **Step 2: Create Mail record**

```java
package net.ecocraft.mail.data;

import org.jetbrains.annotations.Nullable;
import java.util.List;
import java.util.UUID;

public record Mail(
    String id,
    @Nullable UUID senderUuid,
    String senderName,
    UUID recipientUuid,
    String subject,
    String body,
    List<MailItemAttachment> items,
    long currencyAmount,
    @Nullable String currencyId,
    long codAmount,
    @Nullable String codCurrencyId,
    boolean read,
    boolean collected,
    boolean indestructible,
    boolean returned,
    long createdAt,
    long availableAt,
    long expiresAt
) {
    public boolean hasItems() { return items != null && !items.isEmpty(); }
    public boolean hasCurrency() { return currencyAmount > 0 && currencyId != null; }
    public boolean hasCOD() { return codAmount > 0 && codCurrencyId != null; }
    public boolean hasAttachments() { return hasItems() || hasCurrency(); }
    public boolean isAvailable() { return availableAt <= System.currentTimeMillis(); }
    public boolean isExpired() { return !indestructible && expiresAt <= System.currentTimeMillis(); }
    public boolean canDelete() { return read && (!hasAttachments() || collected); }
}
```

- [ ] **Step 3: Create MailStorageProvider**

```java
package net.ecocraft.mail.storage;

import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MailStorageProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailStorageProvider.class);
    private Connection connection;

    public MailStorageProvider(Path dbPath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            connection.createStatement().execute("PRAGMA journal_mode=WAL");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to mail database", e);
        }
    }

    public synchronized void initialize() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mails (
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
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mails_recipient ON mails(recipient_uuid, collected)");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mail_items (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    mail_id    TEXT NOT NULL,
                    item_id    TEXT NOT NULL,
                    item_name  TEXT NOT NULL,
                    item_nbt   TEXT,
                    quantity   INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (mail_id) REFERENCES mails(id) ON DELETE CASCADE
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mail_items_mail ON mail_items(mail_id)");
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize mail database", e);
        }
    }

    public synchronized void shutdown() {
        try { if (connection != null) connection.close(); }
        catch (SQLException e) { LOGGER.error("Failed to close mail database", e); }
    }

    // --- Create ---

    public synchronized void createMail(Mail mail) {
        String sql = "INSERT INTO mails (id, sender_uuid, sender_name, recipient_uuid, subject, body, " +
                "currency_amount, currency_id, cod_amount, cod_currency_id, " +
                "read, collected, indestructible, returned, created_at, available_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mail.id());
            ps.setString(2, mail.senderUuid() != null ? mail.senderUuid().toString() : null);
            ps.setString(3, mail.senderName());
            ps.setString(4, mail.recipientUuid().toString());
            ps.setString(5, mail.subject());
            ps.setString(6, mail.body());
            ps.setLong(7, mail.currencyAmount());
            ps.setString(8, mail.currencyId());
            ps.setLong(9, mail.codAmount());
            ps.setString(10, mail.codCurrencyId());
            ps.setInt(11, mail.read() ? 1 : 0);
            ps.setInt(12, mail.collected() ? 1 : 0);
            ps.setInt(13, mail.indestructible() ? 1 : 0);
            ps.setInt(14, mail.returned() ? 1 : 0);
            ps.setLong(15, mail.createdAt());
            ps.setLong(16, mail.availableAt());
            ps.setLong(17, mail.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to create mail {}", mail.id(), e);
        }

        // Insert item attachments
        if (mail.items() != null) {
            for (MailItemAttachment item : mail.items()) {
                createMailItem(mail.id(), item);
            }
        }
    }

    private synchronized void createMailItem(String mailId, MailItemAttachment item) {
        String sql = "INSERT INTO mail_items (mail_id, item_id, item_name, item_nbt, quantity) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ps.setString(2, item.itemId());
            ps.setString(3, item.itemName());
            ps.setString(4, item.itemNbt());
            ps.setInt(5, item.quantity());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to create mail item for mail {}", mailId, e);
        }
    }

    // --- Read ---

    public synchronized List<Mail> getMailsForPlayer(UUID playerUuid) {
        String sql = "SELECT * FROM mails WHERE recipient_uuid = ? ORDER BY read ASC, created_at DESC";
        List<Mail> mails = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                mails.add(mapMail(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get mails for player {}", playerUuid, e);
        }
        return mails;
    }

    public synchronized Mail getMailById(String mailId) {
        String sql = "SELECT * FROM mails WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapMail(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get mail {}", mailId, e);
        }
        return null;
    }

    public synchronized List<MailItemAttachment> getMailItems(String mailId) {
        String sql = "SELECT * FROM mail_items WHERE mail_id = ?";
        List<MailItemAttachment> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new MailItemAttachment(
                    rs.getString("item_id"),
                    rs.getString("item_name"),
                    rs.getString("item_nbt"),
                    rs.getInt("quantity")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get mail items for {}", mailId, e);
        }
        return items;
    }

    public synchronized int countAvailableMails(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM mails WHERE recipient_uuid = ? AND available_at <= ? AND (indestructible = 1 OR expires_at > ?)";
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, now);
            ps.setLong(3, now);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.error("Failed to count mails for {}", playerUuid, e);
        }
        return 0;
    }

    // --- Update ---

    public synchronized void markRead(String mailId) {
        execute("UPDATE mails SET read = 1 WHERE id = ?", mailId);
    }

    public synchronized void markCollected(String mailId) {
        execute("UPDATE mails SET collected = 1 WHERE id = ?", mailId);
    }

    public synchronized void deleteMail(String mailId) {
        execute("DELETE FROM mail_items WHERE mail_id = ?", mailId);
        execute("DELETE FROM mails WHERE id = ?", mailId);
    }

    public synchronized void deleteExpiredMails() {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM mails WHERE indestructible = 0 AND expires_at <= ?")) {
            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                deleteMail(id);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete expired mails", e);
        }
    }

    public synchronized List<Mail> getExpiredCODMails() {
        String sql = "SELECT * FROM mails WHERE indestructible = 0 AND expires_at <= ? AND cod_amount > 0 AND collected = 0 AND returned = 0";
        long now = System.currentTimeMillis();
        List<Mail> mails = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) mails.add(mapMail(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get expired COD mails", e);
        }
        return mails;
    }

    public synchronized void markReturned(String mailId) {
        execute("UPDATE mails SET returned = 1 WHERE id = ?", mailId);
    }

    // --- Helpers ---

    private void execute(String sql, String param) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("SQL error: {}", sql, e);
        }
    }

    private Mail mapMail(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String senderUuidStr = rs.getString("sender_uuid");
        return new Mail(
            id,
            senderUuidStr != null ? UUID.fromString(senderUuidStr) : null,
            rs.getString("sender_name"),
            UUID.fromString(rs.getString("recipient_uuid")),
            rs.getString("subject"),
            rs.getString("body"),
            getMailItems(id),
            rs.getLong("currency_amount"),
            rs.getString("currency_id"),
            rs.getLong("cod_amount"),
            rs.getString("cod_currency_id"),
            rs.getInt("read") == 1,
            rs.getInt("collected") == 1,
            rs.getInt("indestructible") == 1,
            rs.getInt("returned") == 1,
            rs.getLong("created_at"),
            rs.getLong("available_at"),
            rs.getLong("expires_at")
        );
    }
}
```

- [ ] **Step 4: Write storage tests**

File: `mail/src/test/java/net/ecocraft/mail/storage/MailStorageProviderTest.java`

Write tests for:
- `createMail` + `getMailById` (round-trip)
- `createMail` with item attachments + `getMailItems`
- `getMailsForPlayer` returns correct mails sorted (unread first)
- `markRead` and `markCollected` update flags
- `deleteMail` removes mail and its items
- `deleteExpiredMails` only deletes expired non-indestructible mails
- `getExpiredCODMails` returns only expired uncollected unreturned COD mails
- `countAvailableMails` counts correctly (excludes unavailable and expired)

Use temp directory for SQLite file, same pattern as economy-core tests.

- [ ] **Step 5: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build :mail:test`

- [ ] **Step 6: Commit**

```bash
git add mail/src/main/java/net/ecocraft/mail/data/ mail/src/main/java/net/ecocraft/mail/storage/ mail/src/test/
git commit -m "feat(mail): data records + SQLite storage with tests"
```

---

### Task 3: MailService — business logic

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/service/MailService.java`
- Create: `mail/src/test/java/net/ecocraft/mail/service/MailServiceTest.java`

- [ ] **Step 1: Create MailService**

The service handles:
- `sendMail(senderUuid, senderName, recipientUuid, subject, body, items, currencyAmount, currencyId, codAmount, codCurrencyId)` — validates permissions/config, withdraws items/currency from sender, creates mail
- `sendSystemMail(senderName, recipientUuid, subject, body, items, currencyAmount, currencyId, indestructible, availableAt)` — public API for other modules
- `collectMail(playerUuid, mailId)` — delivers items/currency to player, marks collected
- `collectAllMails(playerUuid)` — collects all non-COD available mails
- `payCOD(playerUuid, mailId)` — pays COD amount to sender, delivers items to recipient
- `returnCOD(playerUuid, mailId)` — returns items to sender as new mail
- `deleteMail(playerUuid, mailId)` — validates canDelete, then deletes
- `expireMails()` — auto-return expired CODs, delete expired normal mails
- `getMailsForPlayer(playerUuid)` — returns available, non-expired mails
- `getMailDetail(mailId)` — returns full mail, marks as read

The service takes `MailStorageProvider` and `EconomyProvider` in constructor. Like AuctionService, it avoids Minecraft types for testability. Item delivery is handled via an injectable `ItemDeliverer` functional interface.

```java
@FunctionalInterface
public interface ItemDeliverer {
    void deliver(UUID playerUuid, List<MailItemAttachment> items);
}
```

- [ ] **Step 2: Write service tests**

Test all flows:
- Send mail (P2P): creates mail in storage, withdraws currency from sender if attached
- Send system mail: creates indestructible mail with custom availableAt
- Collect mail: marks collected, credits currency via economy API
- Collect all: collects multiple mails, skips COD
- Pay COD: withdraws from recipient, deposits to sender (minus fee), marks collected
- Return COD: marks returned, creates return mail to sender with items
- Delete mail: only works if canDelete() is true
- Expire mails: returns expired CODs, deletes expired normals

Use mock EconomyProvider (same pattern as AuctionServiceTest).

- [ ] **Step 3: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build :mail:test`

- [ ] **Step 4: Commit**

```bash
git add mail/src/main/java/net/ecocraft/mail/service/ mail/src/test/
git commit -m "feat(mail): MailService business logic with tests"
```

---

### Task 4: Permissions + Config + Commands

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/permission/MailPermissions.java`
- Create: `mail/src/main/java/net/ecocraft/mail/config/MailConfig.java`
- Create: `mail/src/main/java/net/ecocraft/mail/command/MailCommand.java`
- Create: `mail/src/main/java/net/ecocraft/mail/MailServerEvents.java`

- [ ] **Step 1: Create MailPermissions**

8 permission nodes (7 boolean + 1 integer) following the EcoPermissions pattern. Register via PermissionGatherEvent.Nodes.

- [ ] **Step 2: Create MailConfig**

NeoForge server config with all options from the spec: allowPlayerMail, allowItemAttachments, allowCurrencyAttachments, allowCOD, maxItemAttachments, mailExpiryDays, sendCost, codFeePercent, allowMailboxCraft.

- [ ] **Step 3: Create MailCommand**

Register `/mail` command tree:
- `/mail` — opens mailbox (sends OpenMailboxPayload)
- `/mail send <player> <subject>` — sends text-only mail
- `/mail admin send <player> <subject> <message>` — system mail (indestructible)
- `/mail admin sendall <subject> <message>` — system mail to all online players
- `/mail admin clear <player>` — delete all mails for a player
- `/mail admin purge` — delete expired mails

Use MailPermissions.check() for .requires().

- [ ] **Step 4: Create MailServerEvents**

Server lifecycle:
- `onServerStarting`: initialize storage, create MailService, inject economy provider
- `onServerStopped`: shutdown storage
- `onRegisterCommands`: register MailCommand
- `onPermissionGather`: register permission nodes
- `onServerTick`: periodic expiration check (every 60 seconds)
- `onPlayerLoggedIn`: send mail count notification

- [ ] **Step 5: Update MailMod**

Wire everything in the constructor: register MailRegistries (next task), config, network handler.

- [ ] **Step 6: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build`

- [ ] **Step 7: Commit**

```bash
git add mail/src/main/java/net/ecocraft/mail/
git commit -m "feat(mail): permissions, config, commands, server events"
```

---

### Task 5: Block + Entity + Registries

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/block/MailboxBlock.java`
- Create: `mail/src/main/java/net/ecocraft/mail/block/MailboxBlockEntity.java`
- Create: `mail/src/main/java/net/ecocraft/mail/entity/PostmanEntity.java`
- Create: `mail/src/main/java/net/ecocraft/mail/entity/PostmanRenderer.java`
- Create: `mail/src/main/java/net/ecocraft/mail/registry/MailRegistries.java`
- Create: block/item models and textures (placeholders)

- [ ] **Step 1: Create MailboxBlock**

Extends `BaseEntityBlock`. Right-click sends `OpenMailboxPayload` to the player. Follow VaultBlock pattern. Check `MailPermissions.READ` permission.

- [ ] **Step 2: Create MailboxBlockEntity**

Minimal block entity. No stored data (mailbox is a public terminal).

- [ ] **Step 3: Create PostmanEntity**

Extends `Mob`. Same pattern as AuctioneerEntity: no gravity, invulnerable, LookAtPlayerGoal. Right-click sends `OpenMailboxPayload` (same as block).

- [ ] **Step 4: Create PostmanRenderer**

Follow AuctioneerRenderer pattern. Use villager model or player model with custom texture.

- [ ] **Step 5: Create MailRegistries**

Register: mailbox block, mailbox block item, mailbox block entity type, postman entity type, postman spawn egg, creative tab "EcoCraft Mail".

- [ ] **Step 6: Create placeholder assets**

- `blockstates/mailbox.json`
- `models/block/mailbox.json` (use a simple cube model with chest-like texture)
- `models/item/mailbox.json`
- Placeholder texture

- [ ] **Step 7: Update MailMod**

Call `MailRegistries.register(modBus)`, add `registerRenderers` and `registerAttributes` listeners.

- [ ] **Step 8: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build`

- [ ] **Step 9: Commit**

```bash
git add mail/
git commit -m "feat(mail): mailbox block, postman NPC, registries, assets"
```

---

### Task 6: Network payloads + server handler

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/network/MailNetworkHandler.java`
- Create: `mail/src/main/java/net/ecocraft/mail/network/MailServerPayloadHandler.java`
- Create: 15 payload records in `mail/src/main/java/net/ecocraft/mail/network/payload/`

- [ ] **Step 1: Create all payload records**

Each payload is a record implementing `CustomPacketPayload` with a `TYPE` and `STREAM_CODEC`. Follow the exact pattern from auction-house payloads.

Payloads:
- `OpenMailboxPayload` (S→C, empty)
- `RequestMailListPayload` (C→S, empty)
- `MailListResponsePayload` (S→C, list of mail summaries: id, senderName, subject, read, collected, hasItems, hasCurrency, hasCOD, createdAt)
- `RequestMailDetailPayload` (C→S, mailId)
- `MailDetailResponsePayload` (S→C, full mail data)
- `CollectMailPayload` (C→S, mailId or "ALL")
- `CollectMailResultPayload` (S→C, success, message, itemsCollected, currencyCollected)
- `SendMailPayload` (C→S, recipientName, subject, body, items from inventory slots, currencyAmount, codAmount)
- `SendMailResultPayload` (S→C, success, message)
- `DeleteMailPayload` (C→S, mailId)
- `ReturnCODPayload` (C→S, mailId)
- `PayCODPayload` (C→S, mailId)
- `MailNotificationPayload` (S→C, eventType, subject, senderName)
- `MailSettingsPayload` (S→C, all config values + isAdmin flag)
- `UpdateMailSettingsPayload` (C→S, all config values)

- [ ] **Step 2: Create MailNetworkHandler**

Register all payloads with `@SubscribeEvent` on `RegisterPayloadHandlersEvent`.

- [ ] **Step 3: Create MailServerPayloadHandler**

Handle all C→S payloads:
- `handleRequestMailList`: get mails for player, filter available/non-expired, send response
- `handleRequestMailDetail`: get full mail, mark read, send response
- `handleCollectMail`: call service.collectMail or collectAllMails, deliver items to inventory, send result
- `handleSendMail`: validate permissions/config, extract items from player inventory, call service.sendMail, send result
- `handleDeleteMail`: call service.deleteMail
- `handleReturnCOD`: call service.returnCOD
- `handlePayCOD`: call service.payCOD, deliver items to inventory
- `handleUpdateMailSettings`: check admin permission, update config

- [ ] **Step 4: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build`

- [ ] **Step 5: Commit**

```bash
git add mail/src/main/java/net/ecocraft/mail/network/
git commit -m "feat(mail): network payloads + server handler"
```

---

### Task 7: Mailbox UI — list view + detail view

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/screen/MailboxScreen.java`
- Create: `mail/src/main/java/net/ecocraft/mail/screen/MailListView.java`
- Create: `mail/src/main/java/net/ecocraft/mail/screen/MailDetailView.java`

- [ ] **Step 1: Create MailboxScreen**

Extends `EcoScreen`. Manages 3 views: list, detail, compose. Only one visible at a time. Has a navigation stack (back button returns to previous view).

Size: 90% of screen (matching existing AH pattern).

- [ ] **Step 2: Create MailListView**

Extends `BaseWidget`. Contains:
- Header panel: title "Boîte aux lettres" + stats (X mails, Y items, Z Gold)
- "Tout collecter" button (top-right)
- "Nouveau mail" button
- ScrollPane with mail rows (EcoRepeater or manual layout)
- Each row: envelope icon, subject (bold), sender (grey), attachment icons, collect/delete buttons

Request mail list on init via `RequestMailListPayload`. Populate on response.

- [ ] **Step 3: Create MailDetailView**

Extends `BaseWidget`. Shows:
- Subject in large text
- "De: SenderName" + date label
- Body in ScrollPane
- Attachment panel: EcoItemSlots for items + currency label
- COD banner with "Payer & Collecter" + "Retourner" buttons (if COD)
- "Collecter" button (if normal attachments, not yet collected)
- "Supprimer" button (if canDelete)
- "Retour" button

Requests detail via `RequestMailDetailPayload`. Marks as read.

- [ ] **Step 4: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build`

- [ ] **Step 5: Commit**

```bash
git add mail/src/main/java/net/ecocraft/mail/screen/
git commit -m "feat(mail): mailbox UI — list view + detail view"
```

---

### Task 8: Compose view + client events + notifications

**Files:**
- Create: `mail/src/main/java/net/ecocraft/mail/screen/MailComposeView.java`
- Create: `mail/src/main/java/net/ecocraft/mail/screen/MailSettingsScreen.java`
- Create: `mail/src/main/java/net/ecocraft/mail/client/MailClientEvents.java`
- Create: `mail/src/main/java/net/ecocraft/mail/client/MailNotificationManager.java`
- Create: `mail/src/main/java/net/ecocraft/mail/client/MailNotificationConfig.java`

- [ ] **Step 1: Create MailComposeView**

Extends `BaseWidget`. Contains:
- EcoTextInput for recipient (with player name autocomplete via tab-complete)
- EcoTextInput for subject
- EcoTextArea for message body
- Item attachment zone: EcoInventoryGrid or EcoItemSlots (player drags items in)
- EcoNumberInput for currency amount
- EcoToggle for COD + EcoNumberInput for COD amount
- "Envoyer" button + send cost label
- "Annuler" button

On send: creates `SendMailPayload` with item slot indices from player inventory.

- [ ] **Step 2: Create MailSettingsScreen**

Same pattern as AHSettingsScreen. Gear icon in MailboxScreen header.
- Notifications tab (all players): per-event type (NEW_MAIL, COD_RECEIVED, MAIL_RETURNED) with channel dropdown (chat/toast/both/none)
- General tab (admin only): all config toggles

- [ ] **Step 3: Create MailClientEvents**

Register `RenderGuiLayerEvent.Post` for toast overlay (same pattern as AHClientEvents).

- [ ] **Step 4: Create MailNotificationManager + MailNotificationConfig**

Same pattern as AH notification system. Local JSON config file `config/ecocraft_mail_notifications.json`. Handle `MailNotificationPayload` → dispatch to chat/toast based on config.

- [ ] **Step 5: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :mail:build`

- [ ] **Step 6: Commit**

```bash
git add mail/src/main/java/net/ecocraft/mail/screen/ mail/src/main/java/net/ecocraft/mail/client/
git commit -m "feat(mail): compose view, settings screen, notifications"
```

---

### Task 9: i18n + deploy script + final integration

**Files:**
- Create: `mail/src/main/resources/assets/ecocraft_mail/lang/fr_fr.json`
- Create: `mail/src/main/resources/assets/ecocraft_mail/lang/en_us.json`
- Create: `mail/src/main/resources/assets/ecocraft_mail/lang/es_es.json`
- Modify: `CLAUDE.md` (update deploy script to include mail module)

- [ ] **Step 1: Create FR lang file**

All UI strings in French: mail list headers, buttons, labels, notifications, settings, commands, error messages. ~60 keys.

- [ ] **Step 2: Create EN + ES lang files**

Translate all keys.

- [ ] **Step 3: Update CLAUDE.md deploy script**

Add `cp mail/build/libs/*.jar /home/florian/.minecraft/mods/` to the deploy commands.

- [ ] **Step 4: Final full build and deploy**

```bash
cd /home/florian/perso/minecraft && ./gradlew clean build
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar gui-lib/build/libs/*.jar economy-core/build/libs/*.jar auction-house/build/libs/*.jar mail/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 5: Commit**

```bash
git add mail/ CLAUDE.md
git commit -m "feat(mail): i18n FR/EN/ES, deploy integration, module complete"
```
