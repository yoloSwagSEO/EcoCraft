# NPC Skin Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow admins to set a player skin on each Auctioneer NPC by entering a username, with skin resolved server-side via Mojang API.

**Architecture:** Entity stores skin data in SynchedEntityData + NBT. Server resolves GameProfile async. Renderer reads skin from entity data. New payloads carry entityId and skin info. Settings screen shows skin field only when opened via NPC.

**Tech Stack:** Java 21, NeoForge SynchedEntityData, Mojang GameProfile API, PlayerModel renderer.

---

### Task 1: Entity — add skin storage and sync

Add `skinPlayerName` and skin texture data to `AuctioneerEntity`, persisted in NBT and synced to client.

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/entity/AuctioneerEntity.java`

- [ ] **Step 1: Add SynchedEntityData for skin**

Add imports and a data accessor for the skin player name:

```java
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import com.mojang.authlib.GameProfile;
import java.util.Optional;
```

Add static field:
```java
    private static final EntityDataAccessor<Optional<CompoundTag>> DATA_SKIN_PROFILE =
            SynchedEntityData.defineId(AuctioneerEntity.class, EntityDataSerializers.OPTIONAL_COMPOUND_TAG);
```

Note: We sync the serialized GameProfile as a CompoundTag. The skin player name is stored in NBT only (not synced — the client only needs the resolved profile).

Add instance field:
```java
    private String skinPlayerName = "";
```

- [ ] **Step 2: Register synched data**

In `defineSynchedData`:
```java
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN_PROFILE, Optional.empty());
    }
```

- [ ] **Step 3: Add save/load NBT**

```java
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkinPlayerName", skinPlayerName);
        Optional<CompoundTag> profileTag = entityData.get(DATA_SKIN_PROFILE);
        profileTag.ifPresent(t -> tag.put("SkinProfile", t));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        skinPlayerName = tag.getString("SkinPlayerName");
        if (tag.contains("SkinProfile")) {
            entityData.set(DATA_SKIN_PROFILE, Optional.of(tag.getCompound("SkinProfile")));
        }
    }
```

- [ ] **Step 4: Add getter/setter**

```java
    public String getSkinPlayerName() { return skinPlayerName; }

    public void setSkinPlayerName(String name) { this.skinPlayerName = name; }

    public Optional<GameProfile> getSkinProfile() {
        return entityData.get(DATA_SKIN_PROFILE).map(tag -> {
            return NbtUtils.readGameProfile(tag);
        });
    }

    public void setSkinProfile(GameProfile profile) {
        if (profile != null) {
            CompoundTag tag = new CompoundTag();
            NbtUtils.writeGameProfile(tag, profile);
            entityData.set(DATA_SKIN_PROFILE, Optional.of(tag));
        } else {
            entityData.set(DATA_SKIN_PROFILE, Optional.empty());
        }
    }
```

- [ ] **Step 5: Send entityId with OpenAHPayload**

In `mobInteract`, change:
```java
            PacketDistributor.sendToPlayer(serverPlayer, new OpenAHPayload());
```
To:
```java
            PacketDistributor.sendToPlayer(serverPlayer, new OpenAHPayload(this.getId()));
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :auction-house:compileJava`
(Will fail on OpenAHPayload — fixed in Task 2)

- [ ] **Step 7: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/entity/AuctioneerEntity.java
git commit -m "feat: add skin storage and sync to AuctioneerEntity"
```

---

### Task 2: Network — OpenAHPayload with entityId + skin payloads

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/payload/OpenAHPayload.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/NPCSkinPayload.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/UpdateNPCSkinPayload.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/block/AuctionTerminalBlock.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/command/AHCommand.java`

- [ ] **Step 1: Update OpenAHPayload to include entityId**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenAHPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenAHPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "open_ah"));

    public static final StreamCodec<ByteBuf, OpenAHPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenAHPayload::entityId,
            OpenAHPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: Update all OpenAHPayload senders**

In `AuctionTerminalBlock`: `new OpenAHPayload(-1)`
In `AHCommand` (both /ah and /ah search): `new OpenAHPayload(-1)`
In `AuctioneerEntity.mobInteract`: `new OpenAHPayload(this.getId())` (already done in Task 1)

- [ ] **Step 3: Create NPCSkinPayload (Server → Client)**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NPCSkinPayload(int entityId, String skinPlayerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NPCSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "npc_skin"));

    public static final StreamCodec<ByteBuf, NPCSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NPCSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, NPCSkinPayload::skinPlayerName,
            NPCSkinPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

- [ ] **Step 4: Create UpdateNPCSkinPayload (Client → Server)**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateNPCSkinPayload(int entityId, String skinPlayerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateNPCSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "update_npc_skin"));

    public static final StreamCodec<ByteBuf, UpdateNPCSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdateNPCSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, UpdateNPCSkinPayload::skinPlayerName,
            UpdateNPCSkinPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

- [ ] **Step 5: Register payloads in AHNetworkHandler**

Add in Server→Client section:
```java
        registrar.playToClient(
                NPCSkinPayload.TYPE,
                NPCSkinPayload.STREAM_CODEC,
                ClientPayloadHandler::handleNPCSkin
        );
```

Add in Client→Server section:
```java
        registrar.playToServer(
                UpdateNPCSkinPayload.TYPE,
                UpdateNPCSkinPayload.STREAM_CODEC,
                ServerPayloadHandler::handleUpdateNPCSkin
        );
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :auction-house:compileJava`

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add entityId to OpenAHPayload, create NPCSkin payloads"
```

---

### Task 3: Server handlers — skin resolution + NPC skin payload

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java`

- [ ] **Step 1: Add sendNPCSkin in ServerPayloadHandler**

In `AuctioneerEntity.mobInteract`, after the existing `sendAHSettings` call, add sending the NPC skin data. But better — add a helper in `ServerPayloadHandler`:

```java
    public static void sendNPCSkin(ServerPlayer player, int entityId) {
        if (entityId < 0) return;
        var entity = player.level().getEntity(entityId);
        if (entity instanceof net.ecocraft.ah.entity.AuctioneerEntity npc) {
            PacketDistributor.sendToPlayer(player, new NPCSkinPayload(entityId, npc.getSkinPlayerName()));
        }
    }
```

Call it from `AuctioneerEntity.mobInteract` — add after `sendAHSettings`:
```java
            ServerPayloadHandler.sendNPCSkin(serverPlayer, this.getId());
```

- [ ] **Step 2: Add handleUpdateNPCSkin in ServerPayloadHandler**

```java
    public static void handleUpdateNPCSkin(UpdateNPCSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }

            var entity = player.level().getEntity(payload.entityId());
            if (!(entity instanceof net.ecocraft.ah.entity.AuctioneerEntity npc)) {
                context.reply(new AHActionResultPayload(false, "PNJ introuvable."));
                return;
            }

            String skinName = payload.skinPlayerName().trim();
            npc.setSkinPlayerName(skinName);

            if (skinName.isEmpty()) {
                npc.setSkinProfile(null);
                context.reply(new AHActionResultPayload(true, "Skin réinitialisé."));
                return;
            }

            // Resolve GameProfile asynchronously
            var server = player.getServer();
            if (server == null) {
                context.reply(new AHActionResultPayload(false, "Serveur non disponible."));
                return;
            }

            net.minecraft.Util.backgroundExecutor().execute(() -> {
                try {
                    var profileCache = server.getProfileCache();
                    if (profileCache == null) {
                        server.execute(() -> context.reply(new AHActionResultPayload(false, "Cache de profils non disponible.")));
                        return;
                    }
                    var optProfile = profileCache.get(skinName);
                    if (optProfile.isEmpty()) {
                        server.execute(() -> {
                            context.reply(new AHActionResultPayload(false, "Joueur introuvable: " + skinName));
                        });
                        return;
                    }

                    var profile = optProfile.get();
                    // Fill skin textures
                    var filledProfile = server.getSessionService().fillProfileProperties(profile, false);

                    server.execute(() -> {
                        npc.setSkinProfile(filledProfile);
                        context.reply(new AHActionResultPayload(true, "Skin mis à jour: " + skinName));
                    });
                } catch (Exception e) {
                    LOGGER.error("Error resolving skin for " + skinName, e);
                    server.execute(() -> {
                        context.reply(new AHActionResultPayload(false, "Erreur lors de la résolution du skin."));
                    });
                }
            });
        });
    }
```

- [ ] **Step 3: Add handleNPCSkin in ClientPayloadHandler**

```java
    public static void handleNPCSkin(NPCSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received NPCSkin entityId={} skinName='{}'", payload.entityId(), payload.skinPlayerName());
            AuctionHouseScreen.receiveNPCSkin(payload);
        });
    }
```

- [ ] **Step 4: Update AuctionHouseScreen — store entityId and skinName**

Add fields:
```java
    private int npcEntityId = -1;
    private String npcSkinName = "";
```

Update `handleOpenAH` in `ClientPayloadHandler` to pass entityId:
```java
    public static void handleOpenAH(OpenAHPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received OpenAH entityId={}", payload.entityId());
            AuctionHouseScreen.open(payload.entityId());
        });
    }
```

Update `AuctionHouseScreen.open()`:
```java
    public static void open(int entityId) {
        AuctionHouseScreen screen = new AuctionHouseScreen();
        screen.npcEntityId = entityId;
        Minecraft.getInstance().setScreen(screen);
    }
```

Keep the old `open()` for backward compat:
```java
    public static void open() { open(-1); }
```

Add receiver for NPCSkin:
```java
    public static void receiveNPCSkin(NPCSkinPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.npcEntityId = payload.entityId();
            screen.npcSkinName = payload.skinPlayerName();
        }
    }
```

- [ ] **Step 5: Pass entityId + skinName to AHSettingsScreen**

Update the gear click handler to pass these values:
```java
                Minecraft.getInstance().setScreen(new AHSettingsScreen(
                        this, settingsSaleRate, settingsDepositRate, settingsDurations,
                        npcEntityId, npcSkinName));
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :auction-house:compileJava`

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add NPC skin handlers and wire entityId through screens"
```

---

### Task 4: Settings screen — skin input + renderer

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AHSettingsScreen.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/entity/AuctioneerRenderer.java`

- [ ] **Step 1: Add skin field to AHSettingsScreen**

Update constructor to accept entityId + skinName:
```java
    private final int npcEntityId;
    private String skinPlayerName;

    public AHSettingsScreen(Screen parent, int saleRate, int depositRate, List<Integer> durations,
                            int npcEntityId, String skinPlayerName) {
        super(Component.literal("AH Settings"));
        this.parentScreen = parent;
        this.saleRate = saleRate;
        this.depositRate = depositRate;
        this.durations = new ArrayList<>(durations);
        this.npcEntityId = npcEntityId;
        this.skinPlayerName = skinPlayerName != null ? skinPlayerName : "";
    }
```

- [ ] **Step 2: Add TextInput for skin in init()**

After the repeater section, add (only if npcEntityId != -1):

```java
        // Section: PNJ (only if opened via NPC)
        if (npcEntityId != -1) {
            y = getY_afterRepeater(); // calculate from repeater position
            // ... add section title and TextInput
        }
```

Concretely, after the repeater `values()` call, add:

```java
        if (npcEntityId != -1) {
            int skinSectionY = ... ; // below the repeater
            // rendered in render()

            skinInput = new TextInput(font, contentX, skinSectionY + 14, contentW, 16,
                    Component.literal("Pseudo Minecraft..."), THEME);
            skinInput.setValue(skinPlayerName);
            skinInput.responder(val -> skinPlayerName = val);
            addRenderableWidget(skinInput);
        }
```

Add the field: `private TextInput skinInput;`

Add import: `import net.ecocraft.gui.widget.TextInput;`

- [ ] **Step 3: Render skin section label**

In `render()`, after the durations section, add (only if npcEntityId != -1):

```java
        if (npcEntityId != -1) {
            int skinY = ... ; // match the position from init
            graphics.drawString(font, "PNJ", contentX, skinY, THEME.textWhite, false);
            DrawUtils.drawAccentSeparator(graphics, contentX, skinY + 10, contentW, THEME);
            graphics.drawString(font, "Pseudo du skin:", contentX, skinY + 14, THEME.textGrey, false);
        }
```

- [ ] **Step 4: Send skin update on save**

In `onSave()`, after sending `UpdateAHSettingsPayload`, add:
```java
        if (npcEntityId != -1) {
            PacketDistributor.sendToServer(new UpdateNPCSkinPayload(npcEntityId, skinPlayerName));
        }
```

Add import for `UpdateNPCSkinPayload`.

- [ ] **Step 5: Update AuctioneerRenderer to use skin profile**

```java
package net.ecocraft.ah.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import com.mojang.authlib.GameProfile;

import java.util.Optional;

public class AuctioneerRenderer extends MobRenderer<AuctioneerEntity, PlayerModel<AuctioneerEntity>> {

    public AuctioneerRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(AuctioneerEntity entity) {
        Optional<GameProfile> profile = entity.getSkinProfile();
        if (profile.isPresent()) {
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile.get());
            return skin.texture();
        }
        return DefaultPlayerSkin.getDefaultTexture();
    }

    @Override
    protected boolean shouldShowName(AuctioneerEntity entity) {
        return true;
    }
}
```

- [ ] **Step 6: Full build and deploy**

Run: `./gradlew clean build`
Deploy:
```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: add NPC skin config in settings screen with Mojang profile resolution"
```

---

### Testing Instructions

1. `/summon ecocraft_ah:auctioneer` — spawn NPC (Steve skin)
2. Right-click NPC → AH opens
3. Click gear icon → settings screen
4. Section "PNJ" should be visible with "Pseudo du skin:" field
5. Enter "Notch" → click Sauvegarder
6. NPC should now have Notch's skin
7. Close AH, right-click NPC again → skin persisted
8. Open settings → field shows "Notch"
9. Enter invalid name "xyznonexistent123" → save → error message "Joueur introuvable"
10. Clear the field → save → NPC reverts to Steve
11. Open AH via `/ah` → settings screen should NOT show the PNJ section
12. Quit and restart → NPC keeps its skin (NBT persisted)
