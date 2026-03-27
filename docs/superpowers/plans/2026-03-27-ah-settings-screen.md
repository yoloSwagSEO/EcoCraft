# AH Settings Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an admin settings screen to the AH with configurable tax rates and listing durations, persisted via NeoForge ModConfigSpec.

**Architecture:** New `AHConfig` for persistence, two new network payloads for syncing settings, a full `AHSettingsScreen` with Sliders and Repeater, and a gear icon in the AH header for OP players.

**Tech Stack:** Java 21, NeoForge ModConfigSpec, gui-lib Slider/Repeater/Button, existing AH network patterns.

---

### Task 1: Create AHConfig (ModConfigSpec)

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/config/AHConfig.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/AuctionHouseMod.java`

- [ ] **Step 1: Create AHConfig.java**

```java
package net.ecocraft.ah.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class AHConfig {
    public static final AHConfig CONFIG;
    public static final ModConfigSpec CONFIG_SPEC;

    static {
        Pair<AHConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(AHConfig::new);
        CONFIG = pair.getLeft();
        CONFIG_SPEC = pair.getRight();
    }

    public final ModConfigSpec.IntValue saleRate;
    public final ModConfigSpec.IntValue depositRate;
    public final ModConfigSpec.ConfigValue<List<? extends Integer>> durations;

    private AHConfig(ModConfigSpec.Builder builder) {
        builder.push("taxes");
        saleRate = builder
            .comment("Sale tax rate as percentage (0-50)")
            .defineInRange("saleRate", 5, 0, 50);
        depositRate = builder
            .comment("Listing deposit rate as percentage (0-20)")
            .defineInRange("depositRate", 2, 0, 20);
        builder.pop();

        builder.push("listings");
        durations = builder
            .comment("Available listing durations in hours")
            .defineListAllowEmpty("durations", List.of(12, 24, 48),
                    () -> 24, v -> v instanceof Integer i && i >= 1 && i <= 168);
        builder.pop();
    }

    /** Sale tax rate as a decimal (e.g. 0.05 for 5%). */
    public double getSaleRateDecimal() {
        return saleRate.get() / 100.0;
    }

    /** Deposit rate as a decimal (e.g. 0.02 for 2%). */
    public double getDepositRateDecimal() {
        return depositRate.get() / 100.0;
    }

    /** Listing durations as int array. */
    public int[] getDurationsArray() {
        return durations.get().stream().mapToInt(Integer::intValue).toArray();
    }
}
```

- [ ] **Step 2: Register config in AuctionHouseMod**

In `AuctionHouseMod` constructor, add after `AHRegistries.register(modBus)`:

```java
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER,
                net.ecocraft.ah.config.AHConfig.CONFIG_SPEC);
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :auction-house:compileJava`

- [ ] **Step 4: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/config/AHConfig.java
git add auction-house/src/main/java/net/ecocraft/ah/AuctionHouseMod.java
git commit -m "feat: add AHConfig with ModConfigSpec for tax rates and durations"
```

---

### Task 2: Wire AHConfig into AuctionService and SellTab

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/SellTab.java`

- [ ] **Step 1: Replace constants in AuctionService**

In `AuctionService.java`, replace the constant usages with config reads. Change:

```java
    public static final double DEFAULT_TAX_RATE = 0.05;
    public static final double DEFAULT_DEPOSIT_RATE = 0.02;
```

To:

```java
    /** Fallback tax rate if config unavailable. */
    public static final double DEFAULT_TAX_RATE = 0.05;
    /** Fallback deposit rate if config unavailable. */
    public static final double DEFAULT_DEPOSIT_RATE = 0.02;

    private static double getTaxRate() {
        try { return net.ecocraft.ah.config.AHConfig.CONFIG.getSaleRateDecimal(); }
        catch (Exception e) { return DEFAULT_TAX_RATE; }
    }

    private static double getDepositRate() {
        try { return net.ecocraft.ah.config.AHConfig.CONFIG.getDepositRateDecimal(); }
        catch (Exception e) { return DEFAULT_DEPOSIT_RATE; }
    }
```

Then replace all `DEFAULT_TAX_RATE` usages with `getTaxRate()` and `DEFAULT_DEPOSIT_RATE` with `getDepositRate()` in the method bodies (createListing and buyListing).

- [ ] **Step 2: Update SellTab to use configurable rates and durations**

In `SellTab.java`, the constants:
```java
    private static final double TAX_RATE = 0.05;
    private static final double DEPOSIT_RATE = 0.02;
    private static final int[] DURATIONS = {12, 24, 48};
    private static final String[] DURATION_LABELS = {"12h", "24h", "48h"};
```

Replace with dynamic reads. Since SellTab is client-side, it needs the values from the server. For now, read from a static field that will be set by the settings payload (Task 3). Add:

```java
    // Set by AuctionHouseScreen when settings are received from server
    static int[] activeDurations = {12, 24, 48};
    static double activeTaxRate = 0.05;
    static double activeDepositRate = 0.02;
```

Replace `DURATIONS` with `activeDurations`, `TAX_RATE` with `activeTaxRate`, `DEPOSIT_RATE` with `activeDepositRate`. Compute `DURATION_LABELS` dynamically from `activeDurations`.

- [ ] **Step 3: Verify build and tests**

Run: `./gradlew clean build`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: wire AHConfig into AuctionService and SellTab"
```

---

### Task 3: Network payloads and server-side sync

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/AHSettingsPayload.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/UpdateAHSettingsPayload.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`

- [ ] **Step 1: Create AHSettingsPayload.java (Server → Client)**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record AHSettingsPayload(boolean isAdmin, int saleRate, int depositRate, List<Integer> durations) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AHSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "ah_settings"));

    public static final StreamCodec<ByteBuf, AHSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AHSettingsPayload decode(ByteBuf buf) {
            boolean isAdmin = ByteBufCodecs.BOOL.decode(buf);
            int saleRate = ByteBufCodecs.VAR_INT.decode(buf);
            int depositRate = ByteBufCodecs.VAR_INT.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> durations = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) durations.add(ByteBufCodecs.VAR_INT.decode(buf));
            return new AHSettingsPayload(isAdmin, saleRate, depositRate, durations);
        }

        @Override
        public void encode(ByteBuf buf, AHSettingsPayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.isAdmin());
            ByteBufCodecs.VAR_INT.encode(buf, payload.saleRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.depositRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.durations().size());
            for (int d : payload.durations()) ByteBufCodecs.VAR_INT.encode(buf, d);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

- [ ] **Step 2: Create UpdateAHSettingsPayload.java (Client → Server)**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record UpdateAHSettingsPayload(int saleRate, int depositRate, List<Integer> durations) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateAHSettingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "update_ah_settings"));

    public static final StreamCodec<ByteBuf, UpdateAHSettingsPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public UpdateAHSettingsPayload decode(ByteBuf buf) {
            int saleRate = ByteBufCodecs.VAR_INT.decode(buf);
            int depositRate = ByteBufCodecs.VAR_INT.decode(buf);
            int count = ByteBufCodecs.VAR_INT.decode(buf);
            List<Integer> durations = new java.util.ArrayList<>();
            for (int i = 0; i < count; i++) durations.add(ByteBufCodecs.VAR_INT.decode(buf));
            return new UpdateAHSettingsPayload(saleRate, depositRate, durations);
        }

        @Override
        public void encode(ByteBuf buf, UpdateAHSettingsPayload payload) {
            ByteBufCodecs.VAR_INT.encode(buf, payload.saleRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.depositRate());
            ByteBufCodecs.VAR_INT.encode(buf, payload.durations().size());
            for (int d : payload.durations()) ByteBufCodecs.VAR_INT.encode(buf, d);
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
```

- [ ] **Step 3: Register payloads in AHNetworkHandler**

Add in the Server→Client section:
```java
        registrar.playToClient(
                AHSettingsPayload.TYPE,
                AHSettingsPayload.STREAM_CODEC,
                ClientPayloadHandler::handleAHSettings
        );
```

Add in the Client→Server section:
```java
        registrar.playToServer(
                UpdateAHSettingsPayload.TYPE,
                UpdateAHSettingsPayload.STREAM_CODEC,
                ServerPayloadHandler::handleUpdateAHSettings
        );
```

- [ ] **Step 4: Add sendSettings helper in ServerPayloadHandler**

Add a static method that sends current settings to a player. Call it from all places that send `OpenAHPayload`:

```java
    public static void sendAHSettings(ServerPlayer player) {
        try {
            var config = net.ecocraft.ah.config.AHConfig.CONFIG;
            boolean isAdmin = player.hasPermissions(2);
            int saleRate = config.saleRate.get();
            int depositRate = config.depositRate.get();
            List<Integer> durations = new ArrayList<>(config.durations.get());
            PacketDistributor.sendToPlayer(player, new AHSettingsPayload(isAdmin, saleRate, depositRate, durations));
        } catch (Exception e) {
            LOGGER.error("Error sending AH settings", e);
        }
    }
```

Add `sendAHSettings(serverPlayer)` call in `AuctionTerminalBlock`, `AuctioneerEntity`, and `AHCommand` — right after each `sendToPlayer(... new OpenAHPayload())` call (same places as `sendBalanceUpdate`).

- [ ] **Step 5: Add handleUpdateAHSettings in ServerPayloadHandler**

```java
    public static void handleUpdateAHSettings(UpdateAHSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                context.reply(new AHActionResultPayload(false, "Permission denied."));
                return;
            }
            try {
                var config = net.ecocraft.ah.config.AHConfig.CONFIG;
                config.saleRate.set(Math.max(0, Math.min(50, payload.saleRate())));
                config.depositRate.set(Math.max(0, Math.min(20, payload.depositRate())));
                List<Integer> validDurations = payload.durations().stream()
                        .filter(d -> d >= 1 && d <= 168).toList();
                if (!validDurations.isEmpty()) {
                    config.durations.set(validDurations);
                }
                context.reply(new AHActionResultPayload(true, "Paramètres sauvegardés."));
            } catch (Exception e) {
                LOGGER.error("Error updating AH settings", e);
                context.reply(new AHActionResultPayload(false, "Erreur lors de la sauvegarde."));
            }
        });
    }
```

- [ ] **Step 6: Add handleAHSettings in ClientPayloadHandler**

```java
    public static void handleAHSettings(AHSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received settings isAdmin={} saleRate={} depositRate={} durations={}",
                    payload.isAdmin(), payload.saleRate(), payload.depositRate(), payload.durations());
            AuctionHouseScreen.receiveSettings(payload);
        });
    }
```

- [ ] **Step 7: Verify build**

Run: `./gradlew :auction-house:compileJava`
(Will fail on `AuctionHouseScreen.receiveSettings` — fixed in Task 4)

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add AHSettings network payloads and server handlers"
```

---

### Task 4: AHSettingsScreen and gear icon

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/screen/AHSettingsScreen.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java`

- [ ] **Step 1: Add settings state to AuctionHouseScreen**

Add fields:
```java
    private boolean isAdmin = false;
    private int settingsSaleRate = 5;
    private int settingsDepositRate = 2;
    private java.util.List<Integer> settingsDurations = java.util.List.of(12, 24, 48);
```

Add static receiver:
```java
    public static void receiveSettings(AHSettingsPayload payload) {
        if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
            screen.onReceiveSettings(payload);
        }
    }

    protected void onReceiveSettings(AHSettingsPayload payload) {
        this.isAdmin = payload.isAdmin();
        this.settingsSaleRate = payload.saleRate();
        this.settingsDepositRate = payload.depositRate();
        this.settingsDurations = new java.util.ArrayList<>(payload.durations());
        // Update SellTab with current settings
        SellTab.activeDurations = payload.durations().stream().mapToInt(Integer::intValue).toArray();
        SellTab.activeTaxRate = payload.saleRate() / 100.0;
        SellTab.activeDepositRate = payload.depositRate() / 100.0;
    }
```

- [ ] **Step 2: Add gear icon rendering and click handling**

In the `render()` method, after rendering the balance, add gear icon rendering (only if `isAdmin`):

```java
        // Gear icon for admin settings (right of balance)
        if (isAdmin) {
            Font font = Minecraft.getInstance().font;
            String gear = "\u2699";
            int gearW = font.width(gear);
            int gearX = guiLeft + guiWidth - gearW - 4;
            int gearY = guiTop + 4;
            // Shift balance left to make room
            // (adjust balance rendering bx to be gearX - textW - 8 instead of guiLeft + guiWidth - textW - 8)
            boolean gearHovered = mouseX >= gearX - 2 && mouseX <= gearX + gearW + 2
                    && mouseY >= gearY - 2 && mouseY <= gearY + 12;
            int gearColor = gearHovered ? THEME.textWhite : THEME.accent;
            graphics.drawString(font, gear, gearX, gearY, gearColor, false);
        }
```

Adjust the balance rendering position to shift left when `isAdmin`:
```java
        if (playerBalance >= 0) {
            Font font = Minecraft.getInstance().font;
            String balanceText = BuyTab.formatPrice(playerBalance);
            int textW = font.width(balanceText);
            int rightEdge = isAdmin ? guiLeft + guiWidth - font.width("\u2699") - 12 : guiLeft + guiWidth;
            int bx = rightEdge - textW - 8;
            int by = guiTop + 10;
            graphics.drawString(font, balanceText, bx, by, THEME.accent, false);
        }
```

In `mouseClicked()`, add gear click detection before the dialog check:
```java
        // Gear icon click
        if (isAdmin) {
            Font font = Minecraft.getInstance().font;
            String gear = "\u2699";
            int gearW = font.width(gear);
            int gearX = guiLeft + guiWidth - gearW - 4;
            int gearY = guiTop + 4;
            if (mouseX >= gearX - 2 && mouseX <= gearX + gearW + 2
                    && mouseY >= gearY - 2 && mouseY <= gearY + 12) {
                Minecraft.getInstance().setScreen(new AHSettingsScreen(
                        settingsSaleRate, settingsDepositRate, settingsDurations));
                return true;
            }
        }
```

- [ ] **Step 3: Create AHSettingsScreen.java**

```java
package net.ecocraft.ah.screen;

import net.ecocraft.ah.network.payload.UpdateAHSettingsPayload;
import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.gui.widget.Button;
import net.ecocraft.gui.widget.NumberInput;
import net.ecocraft.gui.widget.Repeater;
import net.ecocraft.gui.widget.Slider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class AHSettingsScreen extends Screen {

    private static final Theme THEME = Theme.dark();

    private int guiWidth, guiHeight, guiLeft, guiTop;

    // Current settings values
    private int saleRate;
    private int depositRate;
    private List<Integer> durations;

    // Widgets
    private Slider saleRateSlider;
    private Slider depositRateSlider;
    private Repeater<Integer> durationsRepeater;
    private Button saveButton;
    private Button cancelButton;
    private Button backButton;

    public AHSettingsScreen(int saleRate, int depositRate, List<Integer> durations) {
        super(Component.literal("AH Settings"));
        this.saleRate = saleRate;
        this.depositRate = depositRate;
        this.durations = new ArrayList<>(durations);
    }

    @Override
    protected void init() {
        guiWidth = (int) (width * 0.9);
        guiHeight = (int) (height * 0.9);
        guiLeft = (width - guiWidth) / 2;
        guiTop = (height - guiHeight) / 2;

        Font font = Minecraft.getInstance().font;
        int contentX = guiLeft + 20;
        int contentW = guiWidth - 40;
        int y = guiTop + 30;

        // Back button
        backButton = Button.builder(Component.literal("\u25C0 Retour"), this::onCancel)
                .theme(THEME).bounds(guiLeft + 4, guiTop + 4, 60, 14)
                .bgColor(THEME.accentBg).borderColor(THEME.borderAccent)
                .textColor(THEME.accent).hoverBg(THEME.accentBgDim).build();
        addRenderableWidget(backButton);

        // Section: Taxes
        y += 20; // space for section title drawn in render

        // Sale rate slider
        y += 14; // label space
        saleRateSlider = new Slider(font, contentX, y, contentW, 16, THEME);
        saleRateSlider.min(0).max(50).step(1).value(saleRate).suffix("%")
                .labelPosition(Slider.LabelPosition.AFTER);
        saleRateSlider.responder(val -> saleRate = (int) val);
        addRenderableWidget(saleRateSlider);
        y += 24;

        // Deposit rate slider
        y += 14;
        depositRateSlider = new Slider(font, contentX, y, contentW, 16, THEME);
        depositRateSlider.min(0).max(20).step(1).value(depositRate).suffix("%")
                .labelPosition(Slider.LabelPosition.AFTER);
        depositRateSlider.responder(val -> depositRate = (int) val);
        addRenderableWidget(depositRateSlider);
        y += 30;

        // Section: Durées de listing
        y += 20; // section title space

        durationsRepeater = new Repeater<>(contentX, y, contentW, 150, THEME);
        durationsRepeater.itemFactory(() -> 24);
        durationsRepeater.rowHeight(22);
        durationsRepeater.maxItems(10);
        durationsRepeater.rowRenderer((value, ctx) -> {
            NumberInput input = new NumberInput(ctx.font(), ctx.x(), ctx.y() + 2, ctx.width(), 18, ctx.theme());
            input.min(1).max(168).step(1).showButtons(true);
            input.setValue(value);
            input.responder(newVal -> ctx.setValue(newVal.intValue()));
            ctx.addWidget(input);
        });
        durationsRepeater.values(durations);
        durationsRepeater.responder(vals -> durations = new ArrayList<>(vals));
        addRenderableWidget(durationsRepeater);

        // Footer buttons
        int footerY = guiTop + guiHeight - 30;
        int btnW = 100;
        int gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnStartX = guiLeft + (guiWidth - totalBtnW) / 2;

        cancelButton = Button.ghost(THEME, Component.literal("Annuler"), this::onCancel);
        cancelButton.setX(btnStartX);
        cancelButton.setY(footerY);
        cancelButton.setWidth(btnW);
        cancelButton.setHeight(20);
        addRenderableWidget(cancelButton);

        saveButton = Button.success(THEME, Component.literal("Sauvegarder"), this::onSave);
        saveButton.setX(btnStartX + btnW + gap);
        saveButton.setY(footerY);
        saveButton.setWidth(btnW);
        saveButton.setHeight(20);
        addRenderableWidget(saveButton);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        DrawUtils.drawPanel(graphics, guiLeft, guiTop, guiWidth, guiHeight, THEME.bgDarkest, THEME.borderAccent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        Font font = Minecraft.getInstance().font;

        // Title
        String title = "Configuration de l'H\u00f4tel des Ventes";
        int titleW = font.width(title);
        graphics.drawString(font, title, guiLeft + (guiWidth - titleW) / 2, guiTop + 8, THEME.accent, false);

        int contentX = guiLeft + 20;
        int y = guiTop + 50;

        // Section: Taxes
        graphics.drawString(font, "Taxes", contentX, y, THEME.textWhite, false);
        DrawUtils.drawAccentSeparator(graphics, contentX, y + 10, guiWidth - 40, THEME);
        y += 16;

        // Sale rate label
        graphics.drawString(font, "Taxe sur les ventes:", contentX, y, THEME.textGrey, false);
        y += 38;

        // Deposit rate label
        graphics.drawString(font, "D\u00e9p\u00f4t de mise en vente:", contentX, y, THEME.textGrey, false);
        y += 44;

        // Section: Durées
        graphics.drawString(font, "Dur\u00e9es de listing (heures)", contentX, y, THEME.textWhite, false);
        DrawUtils.drawAccentSeparator(graphics, contentX, y + 10, guiWidth - 40, THEME);
    }

    private void onSave() {
        PacketDistributor.sendToServer(new UpdateAHSettingsPayload(saleRate, depositRate, durations));
        Minecraft.getInstance().setScreen(new AuctionHouseScreen());
    }

    private void onCancel() {
        Minecraft.getInstance().setScreen(new AuctionHouseScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
```

- [ ] **Step 4: Verify full build**

Run: `./gradlew clean build`

- [ ] **Step 5: Deploy**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "feat: add AH settings screen with gear icon, sliders, and repeater"
```

---

### Testing Instructions

1. Start Minecraft as OP player
2. Open AH → gear icon should be visible top-right
3. Click gear → settings screen opens
4. Adjust sale tax slider → value changes in real-time
5. Adjust deposit slider → value changes
6. Add/remove listing durations in repeater
7. Click "Sauvegarder" → returns to AH
8. Verify: put an item for sale → tax/deposit should use new rates
9. Verify: sell tab duration options match the configured list
10. Non-OP player → no gear icon visible
