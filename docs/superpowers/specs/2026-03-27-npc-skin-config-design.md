# NPC Skin Configuration — Design Spec

## Overview

Allow admins to configure the skin of each Auctioneer NPC by entering a player username. The skin is resolved server-side via the Mojang API and stored in the entity's NBT. The setting is per-NPC and appears in the AH settings screen only when the AH was opened via a PNJ.

## Entity Storage

`AuctioneerEntity` gets two new fields:

- `skinPlayerName` (String, default "") — the username entered by the admin
- `skinProfile` (GameProfile, nullable) — the resolved profile with skin textures

Both are persisted via `addAdditionalSaveData` / `readAdditionalSaveData`.

The `skinPlayerName` is synced to the client via `SynchedEntityData` so the renderer can read it. The resolved `GameProfile` is also synced (serialized as CompoundTag in entity data).

## Skin Resolution (Server-side)

When the admin saves a skin username:

1. Server stores `skinPlayerName` in the entity
2. Server resolves the GameProfile asynchronously via `Util.backgroundExecutor()`:
   - `server.getProfileCache().get(playerName)` → Optional<GameProfile>
   - If found, fill profile properties via `server.getSessionService().fillProfileProperties()`
3. Stores the resolved profile in `skinProfile` on the entity
4. If username not found → reply "Joueur introuvable", entity keeps its current skin (or Steve if none)

## Renderer (Client-side)

`AuctioneerRenderer.getTextureLocation()`:

- Reads `skinProfile` from the entity's synced data
- If a profile with textures exists → use `Minecraft.getInstance().getSkinManager()` to get/download the skin texture
- If no profile → `DefaultPlayerSkin.getDefaultTexture()` (Steve)

The custom name "§6Commissaire-priseur" remains unchanged — it's independent from the skin.

## Network

### Modified: OpenAHPayload

Add `int entityId` field. Set to the NPC's entity ID when opened via NPC, `-1` otherwise (command, block, etc.).

### New: NPCSkinPayload (Server → Client)

Sent alongside other payloads when AH is opened via a NPC. Contains:
- `int entityId`
- `String skinPlayerName` — current skin username (empty if none)

### New: UpdateNPCSkinPayload (Client → Server)

Sent when admin saves a skin username. Contains:
- `int entityId`
- `String skinPlayerName`

Server handler:
1. Verifies `player.hasPermissions(2)`
2. Finds entity by ID, verifies it's an `AuctioneerEntity`
3. Sets `skinPlayerName` on the entity
4. Launches async GameProfile resolution
5. Replies with `AHActionResultPayload`

## Settings Screen

`AHSettingsScreen` receives the `entityId` and `skinPlayerName` from the parent screen.

New section "PNJ" (visible only when `entityId != -1`):
- **"Pseudo du skin:"** — TextInput, pre-filled with current `skinPlayerName`

On save: sends both `UpdateAHSettingsPayload` (global config) and `UpdateNPCSkinPayload` (skin, only if entityId != -1).

## Files

| File | Action | Description |
|------|--------|-------------|
| `auction-house/.../entity/AuctioneerEntity.java` | Modify | Add skinPlayerName/skinProfile fields, NBT save/load, SynchedEntityData |
| `auction-house/.../entity/AuctioneerRenderer.java` | Modify | Read skin from entity profile, resolve texture |
| `auction-house/.../network/payload/OpenAHPayload.java` | Modify | Add entityId field |
| `auction-house/.../network/payload/NPCSkinPayload.java` | Create | Server→Client skin data |
| `auction-house/.../network/payload/UpdateNPCSkinPayload.java` | Create | Client→Server skin update |
| `auction-house/.../network/AHNetworkHandler.java` | Modify | Register new payloads |
| `auction-house/.../network/ClientPayloadHandler.java` | Modify | Handle NPCSkinPayload |
| `auction-house/.../network/ServerPayloadHandler.java` | Modify | Handle UpdateNPCSkinPayload, send NPCSkinPayload |
| `auction-house/.../screen/AuctionHouseScreen.java` | Modify | Store entityId + skinName, pass to settings screen |
| `auction-house/.../screen/AHSettingsScreen.java` | Modify | Add skin TextInput section |
| `auction-house/.../block/AuctionTerminalBlock.java` | Modify | Send OpenAHPayload(-1) |
| `auction-house/.../command/AHCommand.java` | Modify | Send OpenAHPayload(-1) |

## Text (French)

- "PNJ" (section title)
- "Pseudo du skin:" (label)
- "Joueur introuvable." (error if username not found)
