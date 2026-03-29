# NeoForge Permission System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace custom PermissionChecker with NeoForge's native PermissionNode API, making EcoCraft automatically compatible with LuckPerms/FTB Ranks. Add per-player tax/deposit rate overrides and max listings via integer permission nodes.

**Architecture:** Define PermissionNode constants in EcoPermissions (economy-core) and AHPermissions (auction-house). Register via PermissionGatherEvent.Nodes. Replace all hasPermission(N) and permissionChecker calls with PermissionAPI.getPermission(). Add overridePermTax toggle to AHInstance. Remove legacy PermissionChecker/DefaultPermissionChecker.

**Tech Stack:** Java 21, NeoForge 21.1.221, PermissionAPI (net.neoforged.neoforge.server.permission)

---

## File Structure

### economy-core

```
economy-core/src/main/java/net/ecocraft/core/
  permission/
    EcoPermissions.java                        — CREATE: PermissionNode constants + registration
    PermissionChecker.java                     — DELETE
    DefaultPermissionChecker.java              — DELETE
  EcoServerContext.java                        — MODIFY: remove permissions field
  EcoServerEvents.java                        — MODIFY: register permission nodes, remove permissions from context
  command/
    EcoCommands.java                           — MODIFY: remove permissions supplier parameter
    BalanceCommand.java                        — MODIFY: use PermissionAPI
    PayCommand.java                            — MODIFY: use PermissionAPI, remove permissionChecker
    CurrencyCommand.java                       — MODIFY: use PermissionAPI, remove permissionChecker
    EcoAdminCommand.java                       — MODIFY: use PermissionAPI
```

### auction-house

```
auction-house/src/main/java/net/ecocraft/ah/
  permission/
    AHPermissions.java                         — CREATE: PermissionNode constants + registration
  data/
    AHInstance.java                            — MODIFY: add overridePermTax field
  storage/
    AuctionStorageProvider.java                — MODIFY: migration 8 for overridePermTax column
  service/
    AuctionService.java                        — MODIFY: getTaxRate/getDepositRate accept player param
  command/
    AHCommand.java                             — MODIFY: use PermissionAPI
    AHTestCommand.java                         — MODIFY: use PermissionAPI
  network/
    ServerPayloadHandler.java                  — MODIFY: use PermissionAPI, pass rates to service
  AHServerEvents.java                          — MODIFY: register permission nodes
```

---

### Task 1: Create EcoPermissions — economy-core permission nodes

**Files:**
- Create: `economy-core/src/main/java/net/ecocraft/core/permission/EcoPermissions.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java`

- [ ] **Step 1: Create EcoPermissions class**

```java
package net.ecocraft.core.permission;

import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class EcoPermissions {

    public static final PermissionNode<Boolean> BALANCE = new PermissionNode<>(
            "ecocraft", "balance",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> BALANCE_OTHERS = new PermissionNode<>(
            "ecocraft", "balance.others",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> BALANCE_LIST = new PermissionNode<>(
            "ecocraft", "balance.list",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> PAY = new PermissionNode<>(
            "ecocraft", "pay",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> EXCHANGE = new PermissionNode<>(
            "ecocraft", "exchange",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ADMIN_GIVE = new PermissionNode<>(
            "ecocraft", "admin.give",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_TAKE = new PermissionNode<>(
            "ecocraft", "admin.take",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_SET = new PermissionNode<>(
            "ecocraft", "admin.set",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );
}
```

- [ ] **Step 2: Register nodes in EcoServerEvents**

Add a new `@SubscribeEvent` method in `EcoServerEvents`:

```java
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@SubscribeEvent
public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
    event.addNodes(
        EcoPermissions.BALANCE,
        EcoPermissions.BALANCE_OTHERS,
        EcoPermissions.BALANCE_LIST,
        EcoPermissions.PAY,
        EcoPermissions.EXCHANGE,
        EcoPermissions.ADMIN_GIVE,
        EcoPermissions.ADMIN_TAKE,
        EcoPermissions.ADMIN_SET
    );
    LOGGER.info("EcoCraft Economy permission nodes registered.");
}
```

- [ ] **Step 3: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :economy-core:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add economy-core/src/main/java/net/ecocraft/core/permission/EcoPermissions.java \
    economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java
git commit -m "feat(economy-core): define NeoForge permission nodes for economy commands"
```

---

### Task 2: Migrate economy commands to PermissionAPI

**Files:**
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/BalanceCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/PayCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/CurrencyCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/EcoAdminCommand.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/command/EcoCommands.java`

- [ ] **Step 1: Read all command files**

Read BalanceCommand.java, PayCommand.java, CurrencyCommand.java, EcoAdminCommand.java, EcoCommands.java to understand current structure.

- [ ] **Step 2: Create a helper method for Brigadier .requires()**

The challenge: Brigadier's `.requires(CommandSourceStack -> bool)` runs before the command, but `PermissionAPI.getPermission()` needs a `ServerPlayer`. We need a helper:

Add to `EcoPermissions`:

```java
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.permission.PermissionAPI;

public static boolean check(CommandSourceStack source, PermissionNode<Boolean> node) {
    try {
        var player = source.getPlayerOrException();
        return PermissionAPI.getPermission(player, node);
    } catch (Exception e) {
        return source.hasPermission(2); // console/command blocks fallback
    }
}
```

- [ ] **Step 3: Update BalanceCommand**

Replace:
- `/balance list` requires: `src -> src.hasPermission(2)` → `src -> EcoPermissions.check(src, EcoPermissions.BALANCE_LIST)`
- `/balance of <name>` requires: `src -> src.hasPermission(1)` → `src -> EcoPermissions.check(src, EcoPermissions.BALANCE_OTHERS)`

- [ ] **Step 4: Update PayCommand**

Remove the `permissionChecker` parameter. Replace:
- `permissions.hasPermission(sender, "economy.pay")` → `PermissionAPI.getPermission(sender, EcoPermissions.PAY)`

- [ ] **Step 5: Update CurrencyCommand**

Remove the `permissionChecker` parameter. Replace:
- `permissions.hasPermission(player, "economy.exchange")` → `PermissionAPI.getPermission(player, EcoPermissions.EXCHANGE)`

- [ ] **Step 6: Update EcoAdminCommand**

Replace:
- `.requires(src -> src.hasPermission(2))` on the main `/eco` command → split per subcommand:
  - `/eco give` → `src -> EcoPermissions.check(src, EcoPermissions.ADMIN_GIVE)`
  - `/eco take` → `src -> EcoPermissions.check(src, EcoPermissions.ADMIN_TAKE)`
  - `/eco set` → `src -> EcoPermissions.check(src, EcoPermissions.ADMIN_SET)`

- [ ] **Step 7: Update EcoCommands**

Remove the `Supplier<PermissionChecker> permissions` parameter from `register()`. Update all command registrations to not pass permissions. The commands now use `PermissionAPI` directly.

- [ ] **Step 8: Update EcoServerEvents**

In `onRegisterCommands()`, remove the permissions supplier:

Before: `EcoCommands.register(event.getDispatcher(), () -> ..economy.., () -> ..registry.., () -> ..exchange.., () -> ..permissions..);`
After: `EcoCommands.register(event.getDispatcher(), () -> ..economy.., () -> ..registry.., () -> ..exchange..);`

- [ ] **Step 9: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :economy-core:build :economy-core:test`
Expected: BUILD SUCCESSFUL, tests pass

- [ ] **Step 10: Commit**

```bash
git add economy-core/src/main/java/net/ecocraft/core/command/ \
    economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java \
    economy-core/src/main/java/net/ecocraft/core/permission/EcoPermissions.java
git commit -m "refactor(economy-core): migrate commands to NeoForge PermissionAPI"
```

---

### Task 3: Remove legacy permission system

**Files:**
- Delete: `economy-core/src/main/java/net/ecocraft/core/permission/PermissionChecker.java`
- Delete: `economy-core/src/main/java/net/ecocraft/core/permission/DefaultPermissionChecker.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoServerContext.java`
- Modify: `economy-core/src/main/java/net/ecocraft/core/EcoServerEvents.java`

- [ ] **Step 1: Remove permissions from EcoServerContext**

Read EcoServerContext.java. Remove the `PermissionChecker` field, the constructor parameter, and the getter. Update the constructor call in EcoServerEvents.

- [ ] **Step 2: Delete PermissionChecker.java and DefaultPermissionChecker.java**

- [ ] **Step 3: Remove any remaining imports/references**

Grep for `PermissionChecker`, `DefaultPermissionChecker`, `getPermissions` across economy-core and fix any remaining references.

- [ ] **Step 4: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :economy-core:build :economy-core:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add -A economy-core/
git commit -m "refactor(economy-core): remove legacy PermissionChecker/DefaultPermissionChecker"
```

---

### Task 4: Create AHPermissions — auction-house permission nodes

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/permission/AHPermissions.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/AHServerEvents.java`

- [ ] **Step 1: Create AHPermissions class**

```java
package net.ecocraft.ah.permission;

import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public class AHPermissions {

    // --- Boolean nodes ---

    public static final PermissionNode<Boolean> USE = new PermissionNode<>(
            "ecocraft", "ah.use",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> SELL = new PermissionNode<>(
            "ecocraft", "ah.sell",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> BID = new PermissionNode<>(
            "ecocraft", "ah.bid",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> CANCEL = new PermissionNode<>(
            "ecocraft", "ah.cancel",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> true
    );

    public static final PermissionNode<Boolean> ADMIN_CANCEL = new PermissionNode<>(
            "ecocraft", "ah.admin.cancel",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_SETTINGS = new PermissionNode<>(
            "ecocraft", "ah.admin.settings",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    public static final PermissionNode<Boolean> ADMIN_RELOAD = new PermissionNode<>(
            "ecocraft", "ah.admin.reload",
            PermissionTypes.BOOLEAN,
            (player, uuid, ctx) -> player != null && player.hasPermissions(2)
    );

    // --- Integer nodes ---

    public static final PermissionNode<Integer> MAX_LISTINGS = new PermissionNode<>(
            "ecocraft", "ah.max_listings",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = unlimited
    );

    public static final PermissionNode<Integer> TAX_RATE = new PermissionNode<>(
            "ecocraft", "ah.tax_rate",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = use AH config
    );

    public static final PermissionNode<Integer> DEPOSIT_RATE = new PermissionNode<>(
            "ecocraft", "ah.deposit_rate",
            PermissionTypes.INTEGER,
            (player, uuid, ctx) -> -1  // -1 = use AH config
    );

    // --- Helper for Brigadier .requires() ---

    public static boolean check(CommandSourceStack source, PermissionNode<Boolean> node) {
        try {
            var player = source.getPlayerOrException();
            return PermissionAPI.getPermission(player, node);
        } catch (Exception e) {
            return source.hasPermission(2);
        }
    }
}
```

- [ ] **Step 2: Register nodes in AHServerEvents**

Add a `@SubscribeEvent` method:

```java
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@SubscribeEvent
public static void onPermissionGather(PermissionGatherEvent.Nodes event) {
    event.addNodes(
        AHPermissions.USE,
        AHPermissions.SELL,
        AHPermissions.BID,
        AHPermissions.CANCEL,
        AHPermissions.ADMIN_CANCEL,
        AHPermissions.ADMIN_SETTINGS,
        AHPermissions.ADMIN_RELOAD,
        AHPermissions.MAX_LISTINGS,
        AHPermissions.TAX_RATE,
        AHPermissions.DEPOSIT_RATE
    );
    LOGGER.info("EcoCraft Auction House permission nodes registered.");
}
```

- [ ] **Step 3: Build**

Run: `cd /home/florian/perso/minecraft && ./gradlew :auction-house:build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/permission/AHPermissions.java \
    auction-house/src/main/java/net/ecocraft/ah/AHServerEvents.java
git commit -m "feat(auction-house): define NeoForge permission nodes for AH"
```

---

### Task 5: Migrate AH commands and payload handlers to PermissionAPI

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/command/AHCommand.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/command/AHTestCommand.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`

- [ ] **Step 1: Read all files to modify**

Read AHCommand.java, AHTestCommand.java, ServerPayloadHandler.java.

- [ ] **Step 2: Update AHCommand**

Replace all `.requires(src -> src.hasPermission(2))`:
- `/ah admin` branch → `src -> AHPermissions.check(src, AHPermissions.ADMIN_RELOAD)` (covers all admin subcommands)
- `/ah testnotif`, `/ah testtoast`, `/ah testbids` → `src -> AHPermissions.check(src, AHPermissions.ADMIN_RELOAD)`

- [ ] **Step 3: Update AHTestCommand**

Replace all `.requires(src -> src.hasPermission(2))`:
- `/ah populate` → `src -> AHPermissions.check(src, AHPermissions.ADMIN_RELOAD)`
- `/ah simulate` → `src -> AHPermissions.check(src, AHPermissions.ADMIN_RELOAD)`

- [ ] **Step 4: Update ServerPayloadHandler — admin checks**

Replace all `player.hasPermissions(2)` in admin handlers with `PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS)`:
- `handleCreateAH`
- `handleDeleteAH`
- `handleUpdateAHInstance`
- `handleUpdateAHSettings`
- `handleUpdateNPCSkin`

For the `sendAHSettings` method (determines `isAdmin` flag sent to client):
```java
boolean isAdmin = PermissionAPI.getPermission(player, AHPermissions.ADMIN_SETTINGS);
```

- [ ] **Step 5: Update ServerPayloadHandler — add permission checks for user actions**

Add checks in non-admin handlers:
- `handleCreateListing`: check `PermissionAPI.getPermission(player, AHPermissions.SELL)`, reject if false
- `handleBuyListing`: check `PermissionAPI.getPermission(player, AHPermissions.USE)`, reject if false
- `handlePlaceBid`: check `PermissionAPI.getPermission(player, AHPermissions.BID)`, reject if false
- `handleCancelListing`: check `PermissionAPI.getPermission(player, AHPermissions.CANCEL)`, reject if false

Pattern:
```java
if (!PermissionAPI.getPermission(player, AHPermissions.SELL)) {
    PacketDistributor.sendToPlayer(player, new AHActionResultPayload(false, "Permission refusée"));
    return;
}
```

- [ ] **Step 6: Update ServerPayloadHandler — max listings check**

In `handleCreateListing`, before calling `service.createListing()`:

```java
int maxListings = PermissionAPI.getPermission(player, AHPermissions.MAX_LISTINGS);
if (maxListings >= 0) {
    int currentCount = service.getMyListings(player.getUUID(), 0, Integer.MAX_VALUE).size();
    if (currentCount >= maxListings) {
        PacketDistributor.sendToPlayer(player, new AHActionResultPayload(false,
                "Nombre maximum d'annonces atteint (" + maxListings + ")"));
        return;
    }
}
```

- [ ] **Step 7: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :auction-house:build :auction-house:test`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/command/ \
    auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java
git commit -m "refactor(auction-house): migrate commands and handlers to PermissionAPI"
```

---

### Task 6: Add overridePermTax to AHInstance + tax/deposit rate resolution

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/data/AHInstance.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`

- [ ] **Step 1: Read all files**

Read AHInstance.java, AuctionStorageProvider.java (focus on AH instance CRUD and migrations), AuctionService.java (focus on getTaxRate/getDepositRate).

- [ ] **Step 2: Add overridePermTax to AHInstance record**

Add a new field to the record:

```java
public record AHInstance(
    String id,
    String slug,
    String name,
    int saleRate,
    int depositRate,
    List<Integer> durations,
    boolean allowBuyout,
    boolean allowAuction,
    String taxRecipient,
    boolean overridePermTax  // NEW
) {
    public static final String DEFAULT_ID = "default";
}
```

- [ ] **Step 3: Add DB migration**

In `AuctionStorageProvider.initialize()`, add migration 8:

```java
new Migration(8, conn -> {
    try (var stmt = conn.createStatement()) {
        stmt.execute("ALTER TABLE ah_instances ADD COLUMN override_perm_tax INTEGER NOT NULL DEFAULT 0");
    }
})
```

Update the `getAHInstance()` and `createAHInstance()` / `updateAHInstance()` methods to read/write the new column.

- [ ] **Step 4: Change AuctionService.getTaxRate() and getDepositRate() signatures**

These methods currently take only `String ahId`. They need the player UUID to check permissions. But AuctionService deliberately avoids Minecraft types.

Solution: add `taxRateOverride` and `depositRateOverride` parameters to the service methods that need them. The caller (ServerPayloadHandler) resolves the permission and passes the value.

Change `createListing()` to accept `double effectiveTaxRate, double effectiveDepositRate` instead of computing them internally. Same for `buyListing()` and `completedAuctionSale()`.

Actually, simpler approach: add overload parameters to `getTaxRate()` and `getDepositRate()`:

```java
private double getTaxRate(String ahId, int permOverride) {
    try {
        AHInstance ah = storage.getAHInstance(ahId);
        if (ah != null) {
            double ahRate = ah.saleRate() / 100.0;
            if (ah.overridePermTax()) return ahRate;
            if (permOverride >= 0) return permOverride / 100.0;
            return ahRate;
        }
    } catch (Exception ignored) {}
    if (permOverride >= 0) return permOverride / 100.0;
    return DEFAULT_TAX_RATE;
}

private double getDepositRate(String ahId, int permOverride) {
    try {
        AHInstance ah = storage.getAHInstance(ahId);
        if (ah != null) {
            double ahRate = ah.depositRate() / 100.0;
            if (ah.overridePermTax()) return ahRate;
            if (permOverride >= 0) return permOverride / 100.0;
            return ahRate;
        }
    } catch (Exception ignored) {}
    if (permOverride >= 0) return permOverride / 100.0;
    return DEFAULT_DEPOSIT_RATE;
}
```

- [ ] **Step 5: Update createListing() to use new signatures**

Change internal calls from `getTaxRate(effectiveAhId)` to `getTaxRate(effectiveAhId, taxPermOverride)`. Add `int taxPermOverride, int depositPermOverride` parameters to `createListing()`.

- [ ] **Step 6: Update buyListing() and completedAuctionSale()**

`buyListing()` needs `int taxPermOverride` parameter. The caller passes the resolved permission value.

`completedAuctionSale()` is called from `expireListings()` where the buyer may be offline. For expired auctions, use -1 (no override, use AH config) since the player isn't online to check permissions.

- [ ] **Step 7: Update ServerPayloadHandler callers**

In `handleCreateListing()`:
```java
int taxPerm = PermissionAPI.getPermission(player, AHPermissions.TAX_RATE);
int depositPerm = PermissionAPI.getPermission(player, AHPermissions.DEPOSIT_RATE);
service.createListing(..., taxPerm, depositPerm);
```

In `handleBuyListing()`:
```java
int taxPerm = PermissionAPI.getPermission(player, AHPermissions.TAX_RATE);
service.buyListing(..., taxPerm);
```

- [ ] **Step 8: Build and test**

Run: `cd /home/florian/perso/minecraft && ./gradlew :auction-house:build :auction-house:test`
Expected: BUILD SUCCESSFUL (tests pass -1 for overrides, same behavior as before)

- [ ] **Step 9: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/data/AHInstance.java \
    auction-house/src/main/java/net/ecocraft/ah/storage/AuctionStorageProvider.java \
    auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java \
    auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java
git commit -m "feat(auction-house): overridePermTax toggle + permission-based tax/deposit rates"
```

---

### Task 7: Add overridePermTax toggle in admin settings UI

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AHSettingsScreen.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/payload/` (if AH settings payload needs the new field)

- [ ] **Step 1: Read AHSettingsScreen.java and the relevant payload**

Understand how AH instance settings are displayed and saved.

- [ ] **Step 2: Add an EcoToggle for overridePermTax**

In the AH instance settings panel (admin tab), add a toggle:

```java
var overridePermTaxToggle = new EcoToggle(x, y, toggleWidth, 20, theme);
overridePermTaxToggle.setValue(ahInstance.overridePermTax());
overridePermTaxToggle.setResponder(val -> {
    // send update to server
});
```

Label: "Forcer les taux de cet AH" (i18n key: `ecocraft_ah.settings.override_perm_tax`)

- [ ] **Step 3: Update the save/load payload**

If the AH instance update payload doesn't include `overridePermTax`, add it. Update `handleUpdateAHInstance` in ServerPayloadHandler to read and persist it.

- [ ] **Step 4: Add i18n keys**

Add to FR/EN/ES lang files:
```json
"ecocraft_ah.settings.override_perm_tax": "Forcer les taux de cet AH"
```

- [ ] **Step 5: Build and deploy**

Run: `cd /home/florian/perso/minecraft && ./gradlew clean build`
Deploy JARs.

- [ ] **Step 6: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/screen/ \
    auction-house/src/main/java/net/ecocraft/ah/network/ \
    auction-house/src/main/resources/
git commit -m "feat(auction-house): overridePermTax toggle in admin settings UI"
```

---

## Self-Review

**Spec coverage:**
- ✅ EcoPermissions: 8 boolean nodes (Task 1)
- ✅ AHPermissions: 7 boolean + 3 integer nodes (Task 4)
- ✅ Command migration: economy (Task 2), AH (Task 5)
- ✅ Legacy removal: Task 3
- ✅ overridePermTax toggle: Tasks 6+7
- ✅ Tax/deposit cascade with permission override: Task 6
- ✅ Max listings check: Task 5

**Placeholder scan:** No TBD/TODO. All code blocks provided.

**Type consistency:** `EcoPermissions.check()` and `AHPermissions.check()` use same signature. `getTaxRate(String, int)` consistent between definition and callers.
