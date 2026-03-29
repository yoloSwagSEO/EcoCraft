# NeoForge Permission System Integration Design

## Goal

Replace the custom `PermissionChecker`/`DefaultPermissionChecker` with NeoForge's native `PermissionNode` API, making EcoCraft automatically compatible with LuckPerms, FTB Ranks, and any NeoForge permission mod — with zero dependency.

## Motivation

The current system uses vanilla OP levels (`src.hasPermission(2)`) and a custom `PermissionChecker` interface. Permission mods like LuckPerms have no effect on EcoCraft because the code never calls NeoForge's `PermissionAPI`. Migrating to the native API gives server admins fine-grained group-based permission control out of the box.

## Architecture

### Overview

Two permission registration classes:
- `EcoPermissions` in economy-core — economy permission nodes + event registration
- `AHPermissions` in auction-house — AH permission nodes + event registration

Both register their nodes via `PermissionGatherEvent.Nodes` (NeoForge event bus). Permission checks use `PermissionAPI.getPermission(player, node)`.

### Removed

- `economy-core/.../permission/PermissionChecker.java` — interface, deleted
- `economy-core/.../permission/DefaultPermissionChecker.java` — implementation, deleted
- `EcoServerContext` no longer holds a `permissions` field
- All `src.hasPermission(2)` replaced with proper permission node checks

### Resolution cascade (no external dependency)

```
Without LuckPerms: PermissionAPI → DefaultPermissionHandler → our default resolver lambda
With LuckPerms:    PermissionAPI → LuckPermsHandler → LuckPerms DB → fallback to our default
```

Zero dependency. LuckPerms/FTB Ranks inject themselves via `PermissionGatherEvent.Handler`.

## Permission Nodes

### Economy — Boolean Nodes (economy-core)

| Node | Description | Default |
|------|-------------|---------|
| `ecocraft.balance` | View own balance | everyone |
| `ecocraft.balance.others` | View another player's balance | OP (level 2) |
| `ecocraft.balance.list` | View balance leaderboard | everyone |
| `ecocraft.pay` | Send money to another player | everyone |
| `ecocraft.admin.give` | `/eco give` — give money | OP (level 2) |
| `ecocraft.admin.take` | `/eco take` — take money | OP (level 2) |
| `ecocraft.admin.set` | `/eco set` — set balance | OP (level 2) |

### Auction House — Boolean Nodes (auction-house)

| Node | Description | Default |
|------|-------------|---------|
| `ecocraft.ah.use` | Open AH, browse, buy | everyone |
| `ecocraft.ah.sell` | List items for sale | everyone |
| `ecocraft.ah.bid` | Place bids on auctions | everyone |
| `ecocraft.ah.cancel` | Cancel own listings | everyone |
| `ecocraft.ah.admin.cancel` | Cancel any player's listing | OP (level 2) |
| `ecocraft.ah.admin.settings` | Access admin tabs in settings | OP (level 2) |
| `ecocraft.ah.admin.reload` | Reload AH configuration | OP (level 2) |

### Auction House — Integer Nodes (auction-house)

| Node | Description | Default | Convention |
|------|-------------|---------|------------|
| `ecocraft.ah.max_listings` | Max active listings per player | -1 | -1=unlimited, 0=forbidden, N=limit |
| `ecocraft.ah.tax_rate` | Tax rate override (percentage) | -1 | -1=use AH config, 0=exempt, N=N% |
| `ecocraft.ah.deposit_rate` | Deposit rate override (percentage) | -1 | -1=use AH config, 0=exempt, N=N% |

## Override Toggle (per AH instance)

Each `AHInstance` gains a boolean field `overridePermTax` (default: `false`), stored in DB alongside existing AH config.

- `false` — player permission takes priority (if != -1), otherwise use AH rate
- `true` — AH rate always applies, permissions ignored

Exposed in the admin settings screen as a toggle in the AH instance configuration.

### Tax/Deposit Resolution Cascade

```
getTaxRate(player, ahId):
  1. AH instance = loadAHInstance(ahId)
  2. if AH.overridePermTax → return AH.taxRate
  3. permRate = PermissionAPI.getPermission(player, AH_TAX_RATE)
  4. if permRate >= 0 → return permRate / 100.0
  5. else → return AH.taxRate

getDepositRate(player, ahId):
  1. AH instance = loadAHInstance(ahId)
  2. if AH.overridePermTax → return AH.depositRate
  3. permRate = PermissionAPI.getPermission(player, AH_DEPOSIT_RATE)
  4. if permRate >= 0 → return permRate / 100.0
  5. else → return AH.depositRate
```

## Integration Points

### economy-core

**Commands (EcoCommands / BalanceCommand / PayCommand / EcoAdminCommand):**
- Replace all `.requires(src -> src.hasPermission(N))` with `.requires(src -> PermissionAPI.getPermission(src.getPlayerOrException(), EcoPermissions.NODE))`
- Note: command `.requires()` runs before execution, so the player is guaranteed to exist

**Registration:**
- `EcoPermissions` class with static `PermissionNode<Boolean>` constants
- Register via `@SubscribeEvent` on `PermissionGatherEvent.Nodes` in `EcoServerEvents`

### auction-house

**Commands (AHCommand):**
- Replace OP checks with `AHPermissions` node checks

**AuctionService:**
- `getTaxRate(String ahId)` → `getTaxRate(UUID playerUuid, String ahId)` — needs player for permission lookup
- `getDepositRate(String ahId)` → `getDepositRate(UUID playerUuid, String ahId)` — same
- `createListing()` — check `ecocraft.ah.max_listings` before allowing new listing
- Note: AuctionService doesn't have access to ServerPlayer (by design, for testability). The permission check must happen in `ServerPayloadHandler` or a wrapper, passing the resolved values to the service.

**ServerPayloadHandler:**
- Check `AH_USE`, `AH_SELL`, `AH_BID`, `AH_CANCEL` before delegating to service
- Resolve `max_listings`, `tax_rate`, `deposit_rate` from permissions, pass to service

**AHSettingsScreen (client-side):**
- `AH_ADMIN_SETTINGS` check determines if admin tabs are shown (already uses `isAdmin` boolean, sourced from server)
- Add `overridePermTax` toggle in admin AH instance panel

**AHInstance (data record):**
- Add `overridePermTax` boolean field
- DB migration to add the column (default false)

**Registration:**
- `AHPermissions` class with static constants
- Register via `@SubscribeEvent` on `PermissionGatherEvent.Nodes` in `AHServerEvents`

## Testing

- Unit tests: `EconomyProviderImpl` tests are unaffected (no PermissionAPI calls inside provider)
- `AuctionService` tests: service methods that need tax/deposit rates will receive them as parameters (no PermissionAPI call inside service)
- Integration testing: manual, in-game. Install LuckPerms to verify group-based overrides work.

## What This Does NOT Change

- The `NotificationConfig` (client-side per-player notification preferences) — unrelated to server permissions
- The KubeJS integration — events fire regardless of permissions
- The economy API interfaces — `EconomyProvider` has no permission concept
