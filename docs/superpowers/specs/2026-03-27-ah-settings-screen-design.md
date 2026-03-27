# AH Settings Screen — Design Spec

## Overview

Admin configuration screen for the Auction House, accessible via a gear icon visible only to OP players. Opens a full-screen settings UI with sliders, toggles, and repeaters for configuring AH parameters. Settings are persisted via NeoForge's ModConfigSpec (TOML file).

## Access

- Gear icon (⚙) displayed in the top-right of the AH screen, to the right of the player balance. Balance shifts left to make room.
- Visible only if the player has permission level 2+ (OP).
- The server sends a flag indicating whether the player is OP in a settings payload at AH open time.
- Click opens `AHSettingsScreen`, a full-format Screen replacing the AH.
- "◀ Retour" button in the top-left returns to the AH.

## Layout

Full-screen with the same 90% sizing as the AH. Dark theme panel background.

### Header
- "◀ Retour" button top-left
- Title "Configuration de l'Hôtel des Ventes" centered, accent color

### Section: Taxes
- **Taxe sur les ventes** — Slider, 0–50%, step 1%, default 5%, suffix "%", label AFTER
- **Dépôt de mise en vente** — Slider, 0–20%, step 1%, default 2%, suffix "%", label AFTER

### Section: Durées de listing
- **Durées disponibles** — Repeater of NumberInput (hours), default [12, 24, 48], max 10 entries
- Each row: NumberInput with min=1, max=168 (1 week), suffix "h"

### Footer
- **"Sauvegarder"** button (green, Button.success) — sends settings to server, returns to AH
- **"Annuler"** button (ghost) — returns to AH without saving

## Persistence — AHConfig

New file: `auction-house/src/main/java/net/ecocraft/ah/config/AHConfig.java`

Uses `ModConfigSpec` (NeoForge SERVER config type):

```
[taxes]
saleRate = 5        # 0-50, percentage
depositRate = 2     # 0-20, percentage

[listings]
durations = [12, 24, 48]   # list of integers (hours)
```

Registered in `AuctionHouseMod` constructor: `container.registerConfig(ModConfig.Type.SERVER, AHConfig.CONFIG_SPEC)`.

## Service Integration

`AuctionService` currently uses `DEFAULT_TAX_RATE = 0.05` and `DEFAULT_DEPOSIT_RATE = 0.02` as constants. These are replaced with calls to `AHConfig`:

- `AHConfig.CONFIG.saleRate.get() / 100.0` instead of `DEFAULT_TAX_RATE`
- `AHConfig.CONFIG.depositRate.get() / 100.0` instead of `DEFAULT_DEPOSIT_RATE`

`SellTab` currently uses `DURATIONS = {12, 24, 48}` hardcoded. Replaced with values from the settings payload sent by the server.

## Network

### AHSettingsPayload (Server → Client)

Sent alongside `OpenAHPayload` at AH open time. Contains:
- `boolean isAdmin` — whether the gear icon should be visible
- `int saleRate` — current sale tax rate (0-50)
- `int depositRate` — current deposit rate (0-20)
- `List<Integer> durations` — available listing durations in hours

### UpdateAHSettingsPayload (Client → Server)

Sent when admin clicks "Sauvegarder". Contains:
- `int saleRate`
- `int depositRate`
- `List<Integer> durations`

Server handler verifies `player.hasPermissions(2)` before applying. Updates `AHConfig` values. Replies with `AHActionResultPayload(true, "Settings saved.")`.

## Permission Check

- **Client-side:** Gear icon rendered only if `AHSettingsPayload.isAdmin == true`
- **Server-side:** `UpdateAHSettingsPayload` handler checks `player.hasPermissions(2)`. Rejects with error message if not OP.

## Files

| File | Action | Description |
|------|--------|-------------|
| `auction-house/src/main/java/net/ecocraft/ah/config/AHConfig.java` | Create | ModConfigSpec for AH settings |
| `auction-house/src/main/java/net/ecocraft/ah/screen/AHSettingsScreen.java` | Create | Full settings screen with sliders, repeater, buttons |
| `auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java` | Modify | Add gear icon, handle settings payload, pass admin flag |
| `auction-house/src/main/java/net/ecocraft/ah/screen/SellTab.java` | Modify | Read durations from settings payload instead of hardcoded |
| `auction-house/src/main/java/net/ecocraft/ah/network/payload/AHSettingsPayload.java` | Create | Server→Client settings data |
| `auction-house/src/main/java/net/ecocraft/ah/network/payload/UpdateAHSettingsPayload.java` | Create | Client→Server settings update |
| `auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java` | Modify | Register new payloads |
| `auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java` | Modify | Handle AHSettingsPayload |
| `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java` | Modify | Handle UpdateAHSettingsPayload, send settings on AH open |
| `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java` | Modify | Read tax rates from AHConfig |
| `auction-house/src/main/java/net/ecocraft/ah/AuctionHouseMod.java` | Modify | Register AHConfig |

## Text (French)

- "Configuration de l'Hôtel des Ventes" (title)
- "Taxes" (section)
- "Taxe sur les ventes:" / "Dépôt de mise en vente:"
- "Durées de listing" (section)
- "Sauvegarder" / "Annuler"
- "Paramètres sauvegardés." (success message)
