# Bidding Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable toast system to gui-lib, a configurable notification framework in auction-house for passive auction events, and bid history UI in the detail view.

**Architecture:** Three layers built bottom-up. (1) gui-lib gets `EcoToast` + `EcoToastManager` as a client-side overlay. (2) auction-house gets `NotificationManager` + local config file + settings UI tab visible to all players. (3) BuyTab detail view gets bid history display with "see all" dialog.

**Tech Stack:** Java 21, NeoForge 1.21.1, gui-lib WidgetTree, StreamCodec payloads, client-side JSON config.

---

## File Map

### gui-lib (new files)
| File | Responsibility |
|------|---------------|
| `gui-lib/src/main/java/net/ecocraft/gui/core/EcoToast.java` | Toast widget: icon, title, message, level, animation, duration |
| `gui-lib/src/main/java/net/ecocraft/gui/core/EcoToastManager.java` | Singleton queue, renders toasts via NeoForge overlay event |
| `gui-lib/src/main/java/net/ecocraft/gui/core/ToastLevel.java` | Enum: INFO, SUCCESS, WARNING, ERROR |
| `gui-lib/src/main/java/net/ecocraft/gui/core/ToastAnimation.java` | Enum: SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, FADE |

### auction-house (new files)
| File | Responsibility |
|------|---------------|
| `auction-house/src/main/java/net/ecocraft/ah/client/NotificationManager.java` | Receives server payloads, reads config, dispatches to chat/toast |
| `auction-house/src/main/java/net/ecocraft/ah/client/NotificationConfig.java` | Loads/saves per-player notification preferences from local JSON |
| `auction-house/src/main/java/net/ecocraft/ah/client/NotificationEventType.java` | Enum of passive event types with defaults |
| `auction-house/src/main/java/net/ecocraft/ah/client/NotificationChannel.java` | Enum: CHAT, TOAST, BOTH, NONE |
| `auction-house/src/main/java/net/ecocraft/ah/network/payload/AHNotificationPayload.java` | Generic server→client notification payload |
| `auction-house/src/main/java/net/ecocraft/ah/screen/NotificationsTab.java` | Notifications settings tab (visible to all players) |

### auction-house (modified files)
| File | Changes |
|------|---------|
| `auction-house/.../network/AHNetworkHandler.java` | Register AHNotificationPayload + RequestBidHistoryPayload + BidHistoryResponsePayload |
| `auction-house/.../network/ClientPayloadHandler.java` | Handle AHNotificationPayload + BidHistoryResponsePayload |
| `auction-house/.../network/ServerPayloadHandler.java` | Handle RequestBidHistoryPayload, send notifications in placeBid/expireListings |
| `auction-house/.../service/AuctionService.java` | Add notification dispatch calls in placeBid() and expireListings() |
| `auction-house/.../screen/AHSettingsScreen.java` | Add "Notifications" tab visible to all, hide admin tabs for non-OP |
| `auction-house/.../screen/AuctionHouseScreen.java` | Open settings for all players (gear → always visible), pass isAdmin flag |
| `auction-house/.../screen/BuyTab.java` | Add bid history section in detail panel + "Voir tout" button + dialog |
| `auction-house/.../network/payload/ListingDetailResponsePayload.java` | Add `List<BidEntry> recentBids` field |
| `auction-house/src/main/resources/assets/ecocraft_ah/lang/fr_fr.json` | New i18n keys |
| `auction-house/src/main/resources/assets/ecocraft_ah/lang/en_us.json` | New i18n keys |
| `auction-house/src/main/resources/assets/ecocraft_ah/lang/es_es.json` | New i18n keys |

### auction-house (new payload files)
| File | Responsibility |
|------|---------------|
| `auction-house/.../network/payload/RequestBidHistoryPayload.java` | Client→server request for full bid list |
| `auction-house/.../network/payload/BidHistoryResponsePayload.java` | Server→client full bid list response |

---

## Task 1: ToastLevel + ToastAnimation enums

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/ToastLevel.java`
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/ToastAnimation.java`

- [ ] **Step 1: Create ToastLevel enum**

```java
package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.Theme;

/**
 * Visual severity level for toasts. Determines accent bar color.
 */
public enum ToastLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR;

    /** Returns the accent color for this level from the given theme. */
    public int getColor(Theme theme) {
        return switch (this) {
            case INFO -> theme.accent;
            case SUCCESS -> theme.success;
            case WARNING -> theme.warning;
            case ERROR -> theme.danger;
        };
    }
}
```

- [ ] **Step 2: Create ToastAnimation enum**

```java
package net.ecocraft.gui.core;

/**
 * Entry/exit animation for toasts.
 * The animation direction refers to where the toast slides FROM on entry
 * (and slides TO on exit).
 */
public enum ToastAnimation {
    SLIDE_UP,
    SLIDE_DOWN,
    SLIDE_LEFT,
    SLIDE_RIGHT,
    FADE
}
```

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/ToastLevel.java gui-lib/src/main/java/net/ecocraft/gui/core/ToastAnimation.java
git commit -m "feat(gui-lib): add ToastLevel and ToastAnimation enums"
```

---

## Task 2: EcoToast widget

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/EcoToast.java`

- [ ] **Step 1: Create EcoToast with builder**

```java
package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A toast notification widget. Not part of the WidgetTree — rendered as a global overlay
 * by {@link EcoToastManager}.
 *
 * <p>Create via {@link #builder(Theme)}.</p>
 */
public class EcoToast {

    private static final int WIDTH = 200;
    private static final int MIN_HEIGHT = 32;
    private static final int ACCENT_BAR_WIDTH = 4;
    private static final int ICON_SIZE = 16;
    private static final int PADDING = 6;
    private static final int ANIM_DURATION_MS = 300;

    private final Theme theme;
    private final String title;
    private final String message;
    private final @Nullable ItemStack icon;
    private final ToastLevel level;
    private final ToastAnimation animation;
    private final long durationMs;
    private final boolean dismissOnClick;

    // Lifecycle state
    private long spawnTime = -1;
    private boolean dismissed = false;
    private int renderHeight;

    private EcoToast(Theme theme, String title, String message, @Nullable ItemStack icon,
                     ToastLevel level, ToastAnimation animation, long durationMs, boolean dismissOnClick) {
        this.theme = theme;
        this.title = title;
        this.message = message;
        this.icon = icon;
        this.level = level;
        this.animation = animation;
        this.durationMs = durationMs;
        this.dismissOnClick = dismissOnClick;

        // Pre-calculate height
        Font font = Minecraft.getInstance().font;
        int textX = ACCENT_BAR_WIDTH + PADDING + (icon != null ? ICON_SIZE + PADDING : 0);
        int textWidth = WIDTH - textX - PADDING;
        int lines = 1; // title
        if (message != null && !message.isEmpty()) {
            lines += countWrappedLines(font, message, textWidth);
        }
        this.renderHeight = Math.max(MIN_HEIGHT, PADDING * 2 + lines * (font.lineHeight + 1));
    }

    private static int countWrappedLines(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return 1;
        // Simple word-wrap estimation
        String[] words = text.split(" ");
        int lines = 1;
        int currentWidth = 0;
        for (String word : words) {
            int wordWidth = font.width(word + " ");
            if (currentWidth + wordWidth > maxWidth && currentWidth > 0) {
                lines++;
                currentWidth = wordWidth;
            } else {
                currentWidth += wordWidth;
            }
        }
        return Math.min(lines, 2); // Max 2 lines for message
    }

    // --- Builder ---

    public static Builder builder(Theme theme) {
        return new Builder(theme);
    }

    public static class Builder {
        private final Theme theme;
        private String title = "";
        private String message = "";
        private @Nullable ItemStack icon;
        private ToastLevel level = ToastLevel.INFO;
        private ToastAnimation animation = ToastAnimation.SLIDE_RIGHT;
        private long durationMs = 5000;
        private boolean dismissOnClick = true;

        private Builder(Theme theme) {
            this.theme = theme;
        }

        public Builder title(String title) { this.title = title; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder icon(@Nullable ItemStack icon) { this.icon = icon; return this; }
        public Builder level(ToastLevel level) { this.level = level; return this; }
        public Builder animation(ToastAnimation animation) { this.animation = animation; return this; }
        public Builder duration(long ms) { this.durationMs = ms; return this; }
        public Builder dismissOnClick(boolean dismiss) { this.dismissOnClick = dismiss; return this; }

        public EcoToast build() {
            return new EcoToast(theme, title, message, icon, level, animation, durationMs, dismissOnClick);
        }
    }

    // --- Lifecycle ---

    void start() {
        this.spawnTime = System.currentTimeMillis();
    }

    public void dismiss() {
        this.dismissed = true;
    }

    /** Returns true if this toast should be removed from the queue. */
    boolean isFinished() {
        if (spawnTime < 0) return false;
        if (dismissed) {
            // Allow exit animation to complete
            return System.currentTimeMillis() - getDismissTime() > ANIM_DURATION_MS;
        }
        if (durationMs == 0) return false; // Permanent toast
        long elapsed = System.currentTimeMillis() - spawnTime;
        return elapsed > durationMs + ANIM_DURATION_MS; // display + exit anim
    }

    private long getDismissTime() {
        if (dismissed) {
            // Approximate: dismiss happened recently
            return System.currentTimeMillis();
        }
        return spawnTime + durationMs;
    }

    boolean isDismissOnClick() {
        return dismissOnClick;
    }

    int getHeight() {
        return renderHeight;
    }

    // --- Animation ---

    /**
     * Returns a progress value 0.0 to 1.0 for the current animation phase.
     * 0.0 = fully off-screen/invisible, 1.0 = fully visible.
     */
    float getVisibility() {
        if (spawnTime < 0) return 0f;
        long elapsed = System.currentTimeMillis() - spawnTime;

        // Entry animation
        if (elapsed < ANIM_DURATION_MS) {
            return easeOut(elapsed / (float) ANIM_DURATION_MS);
        }

        // Display phase
        if (durationMs == 0 && !dismissed) return 1f; // Permanent
        long displayEnd = durationMs;
        if (!dismissed && elapsed < displayEnd) {
            return 1f;
        }

        // Exit animation
        long exitElapsed;
        if (dismissed) {
            exitElapsed = Math.min(elapsed - Math.min(elapsed, displayEnd), ANIM_DURATION_MS);
        } else {
            exitElapsed = elapsed - displayEnd;
        }
        if (exitElapsed >= ANIM_DURATION_MS) return 0f;
        return 1f - easeOut(exitElapsed / (float) ANIM_DURATION_MS);
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    ToastAnimation getAnimation() {
        return animation;
    }

    // --- Rendering ---

    /**
     * Renders the toast at the given position. Called by EcoToastManager.
     * The x/y are the top-left corner BEFORE animation offset is applied.
     */
    void render(GuiGraphics graphics, int x, int y) {
        float visibility = getVisibility();
        if (visibility <= 0f) return;

        Font font = Minecraft.getInstance().font;

        // Apply animation offset
        int offsetX = 0, offsetY = 0;
        float invVis = 1f - visibility;
        switch (animation) {
            case SLIDE_RIGHT -> offsetX = (int) (WIDTH * invVis);
            case SLIDE_LEFT -> offsetX = (int) (-WIDTH * invVis);
            case SLIDE_UP -> offsetY = (int) (-renderHeight * invVis);
            case SLIDE_DOWN -> offsetY = (int) (renderHeight * invVis);
            case FADE -> { /* handled via alpha */ }
        }

        int drawX = x + offsetX;
        int drawY = y + offsetY;

        // For FADE, we'd need alpha support — approximate with full draw when visibility > 0.3
        if (animation == ToastAnimation.FADE && visibility < 0.3f) return;

        // Background panel
        DrawUtils.drawPanel(graphics, drawX, drawY, WIDTH, renderHeight, theme.bgDark, theme.border);

        // Accent bar (left side)
        int accentColor = level.getColor(theme);
        graphics.fill(drawX, drawY + 1, drawX + ACCENT_BAR_WIDTH, drawY + renderHeight - 1, accentColor);

        // Icon
        int contentX = drawX + ACCENT_BAR_WIDTH + PADDING;
        int contentY = drawY + PADDING;
        if (icon != null) {
            graphics.renderItem(icon, contentX, contentY + (renderHeight - PADDING * 2 - ICON_SIZE) / 2);
            contentX += ICON_SIZE + PADDING;
        }

        // Title
        int textWidth = drawX + WIDTH - PADDING - contentX;
        String displayTitle = DrawUtils.truncateText(font, title, textWidth);
        graphics.drawString(font, displayTitle, contentX, contentY, theme.textWhite, false);

        // Message (below title, max 2 lines)
        if (message != null && !message.isEmpty()) {
            int msgY = contentY + font.lineHeight + 2;
            String displayMsg = DrawUtils.truncateText(font, message, textWidth);
            graphics.drawString(font, displayMsg, contentX, msgY, theme.textLight, false);
        }
    }

    /** Hit-test for click-to-dismiss. */
    boolean containsPoint(int renderX, int renderY, double mouseX, double mouseY) {
        return mouseX >= renderX && mouseX < renderX + WIDTH
                && mouseY >= renderY && mouseY < renderY + renderHeight;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/EcoToast.java
git commit -m "feat(gui-lib): add EcoToast widget with builder, animation, and rendering"
```

---

## Task 3: EcoToastManager singleton

**Files:**
- Create: `gui-lib/src/main/java/net/ecocraft/gui/core/EcoToastManager.java`

- [ ] **Step 1: Create EcoToastManager**

```java
package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Client-side singleton that manages toast display.
 *
 * <p>Toasts are queued FIFO. Up to {@link #MAX_VISIBLE} are rendered simultaneously,
 * stacked vertically in the top-right corner. The manager must be called from
 * a NeoForge render overlay event (e.g., {@code RenderGuiLayerEvent.Post}).</p>
 *
 * <p>Usage from consuming mods:</p>
 * <pre>
 *   // In your mod's client event handler:
 *   @SubscribeEvent
 *   public static void onRenderOverlay(RenderGuiLayerEvent.Post event) {
 *       EcoToastManager.getInstance().render(event.getGuiGraphics(), screenWidth, screenHeight);
 *   }
 * </pre>
 */
public class EcoToastManager {

    private static final EcoToastManager INSTANCE = new EcoToastManager();
    private static final int MAX_VISIBLE = 3;
    private static final int MARGIN_TOP = 8;
    private static final int MARGIN_RIGHT = 8;
    private static final int GAP = 4;

    private final Deque<EcoToast> queue = new ArrayDeque<>();
    private final List<EcoToast> active = new ArrayList<>();

    private EcoToastManager() {}

    public static EcoToastManager getInstance() {
        return INSTANCE;
    }

    /** Enqueue a toast for display. */
    public void show(EcoToast toast) {
        queue.addLast(toast);
    }

    /**
     * Render active toasts. Call this from a NeoForge overlay render event.
     *
     * @param graphics the current GuiGraphics
     * @param screenWidth the screen width in pixels
     * @param screenHeight the screen height in pixels
     */
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        // Promote from queue to active
        while (active.size() < MAX_VISIBLE && !queue.isEmpty()) {
            EcoToast toast = queue.pollFirst();
            toast.start();
            active.add(toast);
        }

        // Remove finished toasts
        active.removeIf(EcoToast::isFinished);

        // Render active toasts, stacked from top-right
        int y = MARGIN_TOP;
        for (EcoToast toast : active) {
            int x = screenWidth - 200 - MARGIN_RIGHT; // EcoToast.WIDTH = 200
            toast.render(graphics, x, y);
            y += toast.getHeight() + GAP;
        }
    }

    /**
     * Handle a mouse click. Returns true if a toast was dismissed.
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int y = MARGIN_TOP;
        for (EcoToast toast : active) {
            int x = screenWidth - 200 - MARGIN_RIGHT;
            if (toast.isDismissOnClick() && toast.containsPoint(x, y, mouseX, mouseY)) {
                toast.dismiss();
                return true;
            }
            y += toast.getHeight() + GAP;
        }
        return false;
    }

    /** Clear all toasts (active + queued). */
    public void clear() {
        queue.clear();
        active.clear();
    }
}
```

- [ ] **Step 2: Add missing import for Minecraft**

Add at top of EcoToastManager.java:
```java
import net.minecraft.client.Minecraft;
```

- [ ] **Step 3: Commit**

```bash
git add gui-lib/src/main/java/net/ecocraft/gui/core/EcoToastManager.java
git commit -m "feat(gui-lib): add EcoToastManager singleton for toast queue and rendering"
```

---

## Task 4: Register toast overlay in auction-house

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/client/AHClientEvents.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/AuctionHouseMod.java`

The toast manager lives in gui-lib, but the NeoForge overlay event must be registered in a mod. We register it in auction-house since gui-lib has no event bus subscriptions.

- [ ] **Step 1: Create AHClientEvents**

```java
package net.ecocraft.ah.client;

import net.ecocraft.ah.AuctionHouseMod;
import net.ecocraft.gui.core.EcoToastManager;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Client-side event handlers for the Auction House mod.
 * Registered on the NeoForge event bus (not the mod bus).
 */
@EventBusSubscriber(modid = AuctionHouseMod.MOD_ID, value = Dist.CLIENT)
public class AHClientEvents {

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        EcoToastManager.getInstance().render(event.getGuiGraphics(), screenWidth, screenHeight);
    }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `./gradlew :auction-house:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/client/AHClientEvents.java
git commit -m "feat: register EcoToastManager overlay rendering via RenderGuiLayerEvent"
```

---

## Task 5: NotificationChannel + NotificationEventType enums

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/client/NotificationChannel.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/client/NotificationEventType.java`

- [ ] **Step 1: Create NotificationChannel enum**

```java
package net.ecocraft.ah.client;

/**
 * Where a notification should be displayed.
 */
public enum NotificationChannel {
    CHAT,
    TOAST,
    BOTH,
    NONE
}
```

- [ ] **Step 2: Create NotificationEventType enum**

```java
package net.ecocraft.ah.client;

import net.ecocraft.gui.core.ToastLevel;

/**
 * Passive auction events that produce notifications.
 * Each type has a default channel and toast level.
 */
public enum NotificationEventType {
    OUTBID("outbid", NotificationChannel.BOTH, ToastLevel.WARNING),
    AUCTION_WON("auction_won", NotificationChannel.BOTH, ToastLevel.SUCCESS),
    AUCTION_LOST("auction_lost", NotificationChannel.BOTH, ToastLevel.ERROR),
    SALE_COMPLETED("sale_completed", NotificationChannel.BOTH, ToastLevel.SUCCESS),
    LISTING_EXPIRED("listing_expired", NotificationChannel.BOTH, ToastLevel.WARNING);

    private final String key;
    private final NotificationChannel defaultChannel;
    private final ToastLevel toastLevel;

    NotificationEventType(String key, NotificationChannel defaultChannel, ToastLevel toastLevel) {
        this.key = key;
        this.defaultChannel = defaultChannel;
        this.toastLevel = toastLevel;
    }

    public String getKey() { return key; }
    public NotificationChannel getDefaultChannel() { return defaultChannel; }
    public ToastLevel getToastLevel() { return toastLevel; }

    /** Find by key string, or null if not found. */
    public static NotificationEventType fromKey(String key) {
        for (NotificationEventType type : values()) {
            if (type.key.equals(key)) return type;
        }
        return null;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/client/NotificationChannel.java auction-house/src/main/java/net/ecocraft/ah/client/NotificationEventType.java
git commit -m "feat: add NotificationChannel and NotificationEventType enums"
```

---

## Task 6: NotificationConfig — local JSON config

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/client/NotificationConfig.java`

- [ ] **Step 1: Create NotificationConfig**

```java
package net.ecocraft.ah.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages per-player notification preferences stored in a local JSON file.
 * File: {@code config/ecocraft_ah_notifications.json}
 */
public class NotificationConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationConfig.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Path.of("config", "ecocraft_ah_notifications.json");
    private static final Type MAP_TYPE = new TypeToken<HashMap<String, String>>() {}.getType();

    private static NotificationConfig instance;

    private final EnumMap<NotificationEventType, NotificationChannel> preferences = new EnumMap<>(NotificationEventType.class);

    private NotificationConfig() {
        // Initialize with defaults
        for (NotificationEventType type : NotificationEventType.values()) {
            preferences.put(type, type.getDefaultChannel());
        }
    }

    public static NotificationConfig getInstance() {
        if (instance == null) {
            instance = new NotificationConfig();
            instance.load();
        }
        return instance;
    }

    public NotificationChannel getChannel(NotificationEventType type) {
        return preferences.getOrDefault(type, type.getDefaultChannel());
    }

    public void setChannel(NotificationEventType type, NotificationChannel channel) {
        preferences.put(type, channel);
        save();
    }

    private void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            String json = Files.readString(CONFIG_PATH);
            Map<String, String> map = GSON.fromJson(json, MAP_TYPE);
            if (map == null) return;
            for (Map.Entry<String, String> entry : map.entrySet()) {
                NotificationEventType type = NotificationEventType.fromKey(entry.getKey());
                if (type == null) continue;
                try {
                    NotificationChannel channel = NotificationChannel.valueOf(entry.getValue());
                    preferences.put(type, channel);
                } catch (IllegalArgumentException ignored) {
                    // Unknown channel value, keep default
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load notification config", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<NotificationEventType, NotificationChannel> entry : preferences.entrySet()) {
                map.put(entry.getKey().getKey(), entry.getValue().name());
            }
            Files.writeString(CONFIG_PATH, GSON.toJson(map));
        } catch (IOException e) {
            LOGGER.error("Failed to save notification config", e);
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/client/NotificationConfig.java
git commit -m "feat: add NotificationConfig for local JSON notification preferences"
```

---

## Task 7: AHNotificationPayload

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/AHNotificationPayload.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java`

- [ ] **Step 1: Create AHNotificationPayload**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server → Client notification for passive auction events (outbid, won, expired, etc.).
 */
public record AHNotificationPayload(
        String eventType,
        String itemName,
        String playerName,
        long amount,
        String currencyId
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AHNotificationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "notification"));

    public static final StreamCodec<ByteBuf, AHNotificationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::eventType,
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::itemName,
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::playerName,
            ByteBufCodecs.VAR_LONG, AHNotificationPayload::amount,
            ByteBufCodecs.STRING_UTF8, AHNotificationPayload::currencyId,
            AHNotificationPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: Register payload in AHNetworkHandler**

In `AHNetworkHandler.java`, add in the `register()` method alongside other `playToClient` registrations:

```java
registrar.playToClient(AHNotificationPayload.TYPE, AHNotificationPayload.STREAM_CODEC,
        ClientPayloadHandler::handleNotification);
```

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/network/payload/AHNotificationPayload.java auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java
git commit -m "feat: add AHNotificationPayload and register in network handler"
```

---

## Task 8: NotificationManager — client-side dispatch

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/client/NotificationManager.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java`

- [ ] **Step 1: Create NotificationManager**

```java
package net.ecocraft.ah.client;

import net.ecocraft.ah.network.payload.AHNotificationPayload;
import net.ecocraft.gui.core.EcoToast;
import net.ecocraft.gui.core.EcoToastManager;
import net.ecocraft.gui.core.ToastAnimation;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Receives notification payloads from the server and dispatches to chat and/or toast
 * based on the player's local {@link NotificationConfig}.
 */
public class NotificationManager {

    private static final Theme THEME = Theme.dark();

    public static void handle(AHNotificationPayload payload) {
        NotificationEventType eventType = NotificationEventType.fromKey(payload.eventType());
        if (eventType == null) return;

        NotificationChannel channel = NotificationConfig.getInstance().getChannel(eventType);
        if (channel == NotificationChannel.NONE) return;

        String title = Component.translatable("ecocraft_ah.notification." + eventType.getKey() + ".title").getString();
        String message = buildMessage(eventType, payload);

        if (channel == NotificationChannel.CHAT || channel == NotificationChannel.BOTH) {
            sendChat(title, message);
        }
        if (channel == NotificationChannel.TOAST || channel == NotificationChannel.BOTH) {
            sendToast(eventType, title, message);
        }
    }

    private static String buildMessage(NotificationEventType eventType, AHNotificationPayload payload) {
        return Component.translatable(
                "ecocraft_ah.notification." + eventType.getKey() + ".message",
                payload.itemName(),
                payload.playerName(),
                payload.amount() > 0 ? String.valueOf(payload.amount()) : ""
        ).getString();
    }

    private static void sendChat(String title, String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("[HDV] ").append(Component.literal(title + " " + message)),
                    false
            );
        }
    }

    private static void sendToast(NotificationEventType eventType, String title, String message) {
        EcoToast toast = EcoToast.builder(THEME)
                .title(title)
                .message(message)
                .level(eventType.getToastLevel())
                .animation(ToastAnimation.SLIDE_RIGHT)
                .duration(5000)
                .dismissOnClick(true)
                .build();
        EcoToastManager.getInstance().show(toast);
    }
}
```

- [ ] **Step 2: Add handler in ClientPayloadHandler**

Add this method to `ClientPayloadHandler.java`:

```java
public static void handleNotification(AHNotificationPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
        LOGGER.debug("AH: Received notification: {}", payload.eventType());
        NotificationManager.handle(payload);
    });
}
```

Add import:
```java
import net.ecocraft.ah.client.NotificationManager;
import net.ecocraft.ah.network.payload.AHNotificationPayload;
```

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/client/NotificationManager.java auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java
git commit -m "feat: add NotificationManager client-side dispatch to chat/toast"
```

---

## Task 9: Server-side notification triggers

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/payload/AHNotificationPayload.java` (no changes needed, already created)

- [ ] **Step 1: Add helper method to AuctionService for sending notifications**

Add a new field and method near the top of AuctionService:

```java
import net.ecocraft.ah.network.payload.AHNotificationPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
```

Add helper method:

```java
/**
 * Sends a notification to a player if they are online.
 * Notifications are fire-and-forget: if the player is offline, the notification is lost.
 */
private void sendNotification(UUID playerUuid, String eventType, String itemName,
                               String otherPlayerName, long amount, String currencyId) {
    MinecraftServer server = this.server;
    if (server == null) return;
    ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
    if (player == null) return; // Offline — notification lost
    PacketDistributor.sendToPlayer(player,
            new AHNotificationPayload(eventType, itemName, otherPlayerName, amount, currencyId));
}
```

Note: `AuctionService` needs a `MinecraftServer` reference. Check if it already has one; if not, add it as a field set during initialization from `AHServerEvents.onServerStarting()`.

- [ ] **Step 2: Add notification in placeBid() — notify previous bidder of outbid**

In `AuctionService.placeBid()`, after the previous bidder refund block (after `economy.deposit()` and parcel creation for outbid), add:

```java
// Notify previous bidder they've been outbid
sendNotification(listing.currentBidderUuid(), "outbid",
        listing.itemName(), bidderName, toSmallestUnit(amount, currency), listing.currencyId());
```

- [ ] **Step 3: Add notifications in completedAuctionSale()**

In `completedAuctionSale()`, after `storage.completeSale(listing.id())`, add:

```java
// Notify winner
sendNotification(listing.currentBidderUuid(), "auction_won",
        listing.itemName(), listing.sellerName(), listing.currentBid(), listing.currencyId());

// Notify seller
sendNotification(listing.sellerUuid(), "sale_completed",
        listing.itemName(), listing.currentBidderName(), listing.currentBid(), listing.currencyId());
```

Note: check if `AuctionListing` has `sellerName()` and `currentBidderName()` fields. If `currentBidderName()` doesn't exist, query the latest bid via `storage.getHighestBid(listing.id())` to get the bidder name, or store it on the listing.

- [ ] **Step 4: Add notifications in expireListings() for no-bid expirations**

In `expireListings()`, after creating the HDV_EXPIRED parcel for listings with no bids, add:

```java
// Notify seller their listing expired
sendNotification(listing.sellerUuid(), "listing_expired",
        listing.itemName(), "", 0L, listing.currencyId());
```

- [ ] **Step 5: Add auction_lost notifications for non-winning bidders**

In `completedAuctionSale()`, after notifying the winner, query all bids for the listing and notify losers:

```java
// Notify losing bidders
List<AuctionBid> allBids = storage.getBidsForListing(listing.id());
for (AuctionBid bid : allBids) {
    if (!bid.bidderUuid().equals(listing.currentBidderUuid())) {
        sendNotification(bid.bidderUuid(), "auction_lost",
                listing.itemName(), "", bid.amount(), listing.currencyId());
    }
}
```

Note: Losing bidders were already refunded when they were outbid, so this notification is purely informational.

- [ ] **Step 6: Verify build**

Run: `./gradlew :auction-house:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java
git commit -m "feat: send notification payloads on outbid, auction won/lost, sale completed, expired"
```

---

## Task 10: i18n keys for notifications

**Files:**
- Modify: `auction-house/src/main/resources/assets/ecocraft_ah/lang/fr_fr.json`
- Modify: `auction-house/src/main/resources/assets/ecocraft_ah/lang/en_us.json`
- Modify: `auction-house/src/main/resources/assets/ecocraft_ah/lang/es_es.json`

- [ ] **Step 1: Add notification keys to fr_fr.json**

Add the following keys:

```json
  "ecocraft_ah.notification.outbid.title": "Surenchéri !",
  "ecocraft_ah.notification.outbid.message": "%s — %s a enchéri %s",
  "ecocraft_ah.notification.auction_won.title": "Enchère remportée !",
  "ecocraft_ah.notification.auction_won.message": "%s — Vendu par %s pour %s",
  "ecocraft_ah.notification.auction_lost.title": "Enchère perdue",
  "ecocraft_ah.notification.auction_lost.message": "%s — Votre enchère de %3$s n'a pas été retenue",
  "ecocraft_ah.notification.sale_completed.title": "Vente réalisée !",
  "ecocraft_ah.notification.sale_completed.message": "%s — Acheté par %s pour %s",
  "ecocraft_ah.notification.listing_expired.title": "Annonce expirée",
  "ecocraft_ah.notification.listing_expired.message": "%s — Votre annonce a expiré sans vente",
  "ecocraft_ah.settings.notifications": "Notifications",
  "ecocraft_ah.settings.notif.outbid": "Surenchéri",
  "ecocraft_ah.settings.notif.auction_won": "Enchère remportée",
  "ecocraft_ah.settings.notif.auction_lost": "Enchère perdue",
  "ecocraft_ah.settings.notif.sale_completed": "Vente réalisée",
  "ecocraft_ah.settings.notif.listing_expired": "Annonce expirée",
  "ecocraft_ah.settings.notif.channel.chat": "Chat",
  "ecocraft_ah.settings.notif.channel.toast": "Toast",
  "ecocraft_ah.settings.notif.channel.both": "Les deux",
  "ecocraft_ah.settings.notif.channel.none": "Désactivé",
  "ecocraft_ah.bid_history.title": "Historique des enchères",
  "ecocraft_ah.bid_history.no_bids": "Aucune enchère",
  "ecocraft_ah.bid_history.recent": "Dernières enchères",
  "ecocraft_ah.bid_history.see_all": "Voir tout (%d)",
  "ecocraft_ah.bid_history.ago": "il y a %s"
```

- [ ] **Step 2: Add notification keys to en_us.json**

```json
  "ecocraft_ah.notification.outbid.title": "Outbid!",
  "ecocraft_ah.notification.outbid.message": "%s — %s bid %s",
  "ecocraft_ah.notification.auction_won.title": "Auction Won!",
  "ecocraft_ah.notification.auction_won.message": "%s — Sold by %s for %s",
  "ecocraft_ah.notification.auction_lost.title": "Auction Lost",
  "ecocraft_ah.notification.auction_lost.message": "%s — Your bid of %3$s was not the highest",
  "ecocraft_ah.notification.sale_completed.title": "Sale Completed!",
  "ecocraft_ah.notification.sale_completed.message": "%s — Bought by %s for %s",
  "ecocraft_ah.notification.listing_expired.title": "Listing Expired",
  "ecocraft_ah.notification.listing_expired.message": "%s — Your listing expired without a sale",
  "ecocraft_ah.settings.notifications": "Notifications",
  "ecocraft_ah.settings.notif.outbid": "Outbid",
  "ecocraft_ah.settings.notif.auction_won": "Auction Won",
  "ecocraft_ah.settings.notif.auction_lost": "Auction Lost",
  "ecocraft_ah.settings.notif.sale_completed": "Sale Completed",
  "ecocraft_ah.settings.notif.listing_expired": "Listing Expired",
  "ecocraft_ah.settings.notif.channel.chat": "Chat",
  "ecocraft_ah.settings.notif.channel.toast": "Toast",
  "ecocraft_ah.settings.notif.channel.both": "Both",
  "ecocraft_ah.settings.notif.channel.none": "Disabled",
  "ecocraft_ah.bid_history.title": "Bid History",
  "ecocraft_ah.bid_history.no_bids": "No bids",
  "ecocraft_ah.bid_history.recent": "Recent Bids",
  "ecocraft_ah.bid_history.see_all": "See all (%d)",
  "ecocraft_ah.bid_history.ago": "%s ago"
```

- [ ] **Step 3: Add notification keys to es_es.json**

```json
  "ecocraft_ah.notification.outbid.title": "Superado!",
  "ecocraft_ah.notification.outbid.message": "%s — %s pujó %s",
  "ecocraft_ah.notification.auction_won.title": "Subasta ganada!",
  "ecocraft_ah.notification.auction_won.message": "%s — Vendido por %s por %s",
  "ecocraft_ah.notification.auction_lost.title": "Subasta perdida",
  "ecocraft_ah.notification.auction_lost.message": "%s — Tu puja de %3$s no fue la más alta",
  "ecocraft_ah.notification.sale_completed.title": "Venta completada!",
  "ecocraft_ah.notification.sale_completed.message": "%s — Comprado por %s por %s",
  "ecocraft_ah.notification.listing_expired.title": "Anuncio expirado",
  "ecocraft_ah.notification.listing_expired.message": "%s — Tu anuncio expiró sin venta",
  "ecocraft_ah.settings.notifications": "Notificaciones",
  "ecocraft_ah.settings.notif.outbid": "Superado",
  "ecocraft_ah.settings.notif.auction_won": "Subasta ganada",
  "ecocraft_ah.settings.notif.auction_lost": "Subasta perdida",
  "ecocraft_ah.settings.notif.sale_completed": "Venta completada",
  "ecocraft_ah.settings.notif.listing_expired": "Anuncio expirado",
  "ecocraft_ah.settings.notif.channel.chat": "Chat",
  "ecocraft_ah.settings.notif.channel.toast": "Toast",
  "ecocraft_ah.settings.notif.channel.both": "Ambos",
  "ecocraft_ah.settings.notif.channel.none": "Desactivado",
  "ecocraft_ah.bid_history.title": "Historial de pujas",
  "ecocraft_ah.bid_history.no_bids": "Sin pujas",
  "ecocraft_ah.bid_history.recent": "Pujas recientes",
  "ecocraft_ah.bid_history.see_all": "Ver todo (%d)",
  "ecocraft_ah.bid_history.ago": "hace %s"
```

- [ ] **Step 4: Commit**

```bash
git add auction-house/src/main/resources/assets/ecocraft_ah/lang/fr_fr.json auction-house/src/main/resources/assets/ecocraft_ah/lang/en_us.json auction-house/src/main/resources/assets/ecocraft_ah/lang/es_es.json
git commit -m "i18n: add notification and bid history keys in FR/EN/ES"
```

---

## Task 11: NotificationsTab — settings UI

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/screen/NotificationsTab.java`

- [ ] **Step 1: Create NotificationsTab**

```java
package net.ecocraft.ah.screen;

import net.ecocraft.ah.client.NotificationChannel;
import net.ecocraft.ah.client.NotificationConfig;
import net.ecocraft.ah.client.NotificationEventType;
import net.ecocraft.gui.core.*;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Notifications preferences tab in the Settings screen.
 * Visible to all players (not just OP).
 * Each notification type has a dropdown to choose the channel.
 */
public class NotificationsTab extends BaseWidget {

    private static final Theme THEME = Theme.dark();
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 4;

    private final Font font;

    public NotificationsTab(Font font, int x, int y, int width, int height) {
        super(x, y, width, height);
        this.font = font;
        buildWidgets();
    }

    private void buildWidgets() {
        clearChildren();

        List<String> channelLabels = List.of(
                Component.translatable("ecocraft_ah.settings.notif.channel.chat").getString(),
                Component.translatable("ecocraft_ah.settings.notif.channel.toast").getString(),
                Component.translatable("ecocraft_ah.settings.notif.channel.both").getString(),
                Component.translatable("ecocraft_ah.settings.notif.channel.none").getString()
        );
        // Channel order matches: CHAT=0, TOAST=1, BOTH=2, NONE=3
        NotificationChannel[] channelOrder = {
                NotificationChannel.CHAT, NotificationChannel.TOAST,
                NotificationChannel.BOTH, NotificationChannel.NONE
        };

        int contentX = getX() + 8;
        int contentW = getWidth() - 16;
        int y = getY() + 8;

        // Title
        Label title = new Label(font, contentX, y,
                Component.translatable("ecocraft_ah.settings.notifications"), THEME);
        title.setColor(THEME.accent);
        addChild(title);
        y += font.lineHeight + 8;

        // One row per event type
        NotificationConfig config = NotificationConfig.getInstance();
        int labelW = (int) (contentW * 0.55);
        int dropdownW = (int) (contentW * 0.35);
        int dropdownX = contentX + contentW - dropdownW;

        for (NotificationEventType eventType : NotificationEventType.values()) {
            // Label
            Label label = new Label(font, contentX, y + (ROW_HEIGHT - font.lineHeight) / 2,
                    Component.translatable("ecocraft_ah.settings.notif." + eventType.getKey()), THEME);
            label.setColor(THEME.textLight);
            addChild(label);

            // Dropdown
            int currentIndex = indexOf(channelOrder, config.getChannel(eventType));
            EcoDropdown dropdown = new EcoDropdown(dropdownX, y, dropdownW, ROW_HEIGHT - 4, THEME);
            dropdown.options(channelLabels);
            dropdown.selectedIndex(currentIndex);

            // Capture eventType for lambda
            final NotificationEventType type = eventType;
            dropdown.responder(index -> {
                if (index >= 0 && index < channelOrder.length) {
                    config.setChannel(type, channelOrder[index]);
                }
            });
            addChild(dropdown);

            y += ROW_HEIGHT + ROW_GAP;
        }
    }

    private static int indexOf(NotificationChannel[] arr, NotificationChannel target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) return i;
        }
        return 2; // default to BOTH
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No custom rendering needed — children handle it
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/screen/NotificationsTab.java
git commit -m "feat: add NotificationsTab for per-event channel config dropdowns"
```

---

## Task 12: Integrate NotificationsTab into AHSettingsScreen

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AHSettingsScreen.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java`

- [ ] **Step 1: Make gear button always visible in AuctionHouseScreen**

In `AuctionHouseScreen.java`, change the gear button visibility:

Find:
```java
gearButton.setVisible(isAdmin);
```
Replace both occurrences with:
```java
gearButton.setVisible(true);
```

- [ ] **Step 2: Pass isAdmin flag to AHSettingsScreen**

In `AuctionHouseScreen.java`, modify `onGearClicked()`:

Find:
```java
private void onGearClicked() {
    Minecraft.getInstance().setScreen(new AHSettingsScreen(
            this, npcEntityId, npcSkinName,
            npcLinkedAhId, new java.util.ArrayList<>(ahInstances)));
}
```
Replace with:
```java
private void onGearClicked() {
    Minecraft.getInstance().setScreen(new AHSettingsScreen(
            this, npcEntityId, npcSkinName,
            npcLinkedAhId, new java.util.ArrayList<>(ahInstances), isAdmin));
}
```

- [ ] **Step 3: Update AHSettingsScreen constructor to accept isAdmin**

Add field:
```java
private final boolean isAdmin;
```

Update constructor signature:
```java
public AHSettingsScreen(Screen parent, int npcEntityId, String skinPlayerName,
                        String currentAhId, List<AHInstancesPayload.AHInstanceData> ahInstances,
                        boolean isAdmin) {
```

Add in constructor body:
```java
this.isAdmin = isAdmin;
```

- [ ] **Step 4: Add Notifications tab to sidebar, conditionally hide admin tabs**

In `initSidebar()`, wrap the General tab and AH instance tabs in an `if (isAdmin)` block. Always add a Notifications tab button:

```java
private void initSidebar() {
    int btnX = guiLeft + 4;
    int btnW = sidebarWidth - 8;
    int btnH = 18;
    int y = guiTop + 8;

    // Notifications tab (always visible, tab index = 0 for non-admin)
    int notifTabIndex = 0;
    EcoButton notifBtn = createSidebarButton(
            Component.translatable("ecocraft_ah.settings.notifications").getString(),
            notifTabIndex, btnX, y, btnW, btnH);
    getTree().addChild(notifBtn);
    sidebarButtons.add(notifBtn);
    y += btnH + 4;

    if (isAdmin) {
        y += 4; // separator gap

        // Tab 1: "General"
        EcoButton generalBtn = createSidebarButton(
                Component.translatable("ecocraft_ah.settings.general").getString(),
                1, btnX, y, btnW, btnH);
        getTree().addChild(generalBtn);
        sidebarButtons.add(generalBtn);
        y += btnH + 4;
        y += 4;

        // One button per AH instance
        for (int i = 0; i < ahInstances.size(); i++) {
            AHInstancesPayload.AHInstanceData data = ahInstances.get(i);
            String label = getAHDisplayName(data);
            EcoButton ahBtn = createSidebarButton(label, i + 2, btnX, y, btnW, btnH);
            getTree().addChild(ahBtn);
            sidebarButtons.add(ahBtn);
            y += btnH + 2;
        }

        // "+ Creer un AH" button at bottom
        int createY = guiTop + guiHeight - 30 - 30;
        EcoButton createBtn = EcoButton.builder(
                Component.translatable("ecocraft_ah.settings.create_ah"), this::onCreateAH)
                .theme(THEME).bounds(btnX, createY, btnW, btnH)
                .bgColor(THEME.successBg).borderColor(THEME.success)
                .textColor(THEME.success).hoverBg(0xFF2A4A2A).build();
        getTree().addChild(createBtn);
    }
}
```

- [ ] **Step 5: Update initRightPanel() to handle the Notifications tab**

In `initRightPanel()`, add a check for `selectedTab == 0`:

```java
private void initRightPanel() {
    int panelX = guiLeft + sidebarWidth;
    int panelY = guiTop;
    int panelW = guiWidth - sidebarWidth;
    int panelH = guiHeight;

    if (selectedTab == 0) {
        // Notifications tab
        NotificationsTab notifTab = new NotificationsTab(
                Minecraft.getInstance().font, panelX, panelY, panelW, panelH);
        getTree().addChild(notifTab);
        return;
    }

    if (!isAdmin) return; // Non-admin only sees notifications tab

    if (selectedTab == 1) {
        initGeneralPanel(panelX, panelY, panelW, panelH);
    } else {
        int ahIndex = selectedTab - 2;
        if (ahIndex >= 0 && ahIndex < ahInstances.size()) {
            initAHPanel(panelX, panelY, panelW, panelH, ahInstances.get(ahIndex));
        }
    }
}
```

Note: The existing `initGeneralPanel()` and `initAHPanel()` methods stay as-is. Only the tab index offsets change (+1 from before since Notifications is now tab 0).

- [ ] **Step 6: Update tab index references throughout the class**

All references to `selectedTab == 0` meaning "General" now need to be `selectedTab == 1`. References to AH instance tabs (`selectedTab - 1`) now need to be `selectedTab - 2`. Update `onTabClicked()`, `onSave()`, and any other methods that check `selectedTab`.

- [ ] **Step 7: Set default tab to 0 (Notifications) for non-admin**

Change the default `selectedTab`:
```java
private int selectedTab = 0; // Already 0, which is now Notifications — correct for all players
```

- [ ] **Step 8: Verify build**

Run: `./gradlew :auction-house:compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/screen/AHSettingsScreen.java auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java
git commit -m "feat: notifications tab visible to all players, admin tabs hidden for non-OP"
```

---

## Task 13: Bid history payloads

**Files:**
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/RequestBidHistoryPayload.java`
- Create: `auction-house/src/main/java/net/ecocraft/ah/network/payload/BidHistoryResponsePayload.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java`

- [ ] **Step 1: Create RequestBidHistoryPayload**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestBidHistoryPayload(String listingId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestBidHistoryPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "request_bid_history"));

    public static final StreamCodec<ByteBuf, RequestBidHistoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestBidHistoryPayload::listingId,
            RequestBidHistoryPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: Create BidHistoryResponsePayload**

```java
package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record BidHistoryResponsePayload(
        String listingId,
        List<BidEntry> bids
) implements CustomPacketPayload {

    public record BidEntry(String bidderName, long amount, long timestamp) {}

    public static final StreamCodec<ByteBuf, BidEntry> BID_ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BidEntry::bidderName,
            ByteBufCodecs.VAR_LONG, BidEntry::amount,
            ByteBufCodecs.VAR_LONG, BidEntry::timestamp,
            BidEntry::new
    );

    public static final CustomPacketPayload.Type<BidHistoryResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "bid_history_response"));

    public static final StreamCodec<ByteBuf, BidHistoryResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BidHistoryResponsePayload::listingId,
            BID_ENTRY_CODEC.apply(ByteBufCodecs.list()), BidHistoryResponsePayload::bids,
            BidHistoryResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 3: Register payloads in AHNetworkHandler**

Add in `register()`:
```java
registrar.playToServer(RequestBidHistoryPayload.TYPE, RequestBidHistoryPayload.STREAM_CODEC,
        ServerPayloadHandler::handleRequestBidHistory);
registrar.playToClient(BidHistoryResponsePayload.TYPE, BidHistoryResponsePayload.STREAM_CODEC,
        ClientPayloadHandler::handleBidHistoryResponse);
```

- [ ] **Step 4: Add server handler for bid history**

Add to `ServerPayloadHandler.java`:

```java
public static void handleRequestBidHistory(RequestBidHistoryPayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
        try {
            AuctionService service = requireService();
            List<AuctionBid> bids = service.getBidsForListing(payload.listingId());
            List<BidHistoryResponsePayload.BidEntry> entries = bids.stream()
                    .map(b -> new BidHistoryResponsePayload.BidEntry(
                            b.bidderName(), b.amount(), b.timestamp()))
                    .toList();
            context.reply(new BidHistoryResponsePayload(payload.listingId(), entries));
        } catch (Exception e) {
            LOGGER.error("Error handling RequestBidHistory", e);
        }
    });
}
```

Add import:
```java
import net.ecocraft.ah.data.AuctionBid;
import net.ecocraft.ah.network.payload.RequestBidHistoryPayload;
import net.ecocraft.ah.network.payload.BidHistoryResponsePayload;
```

- [ ] **Step 5: Add getBidsForListing to AuctionService if not already exposed**

In `AuctionService.java`, if not already present as a public method:
```java
public List<AuctionBid> getBidsForListing(String listingId) {
    return storage.getBidsForListing(listingId);
}
```

- [ ] **Step 6: Add client handler for bid history**

Add to `ClientPayloadHandler.java`:

```java
public static void handleBidHistoryResponse(BidHistoryResponsePayload payload, IPayloadContext context) {
    context.enqueueWork(() -> {
        LOGGER.debug("AH: Received BidHistoryResponse for listing '{}'", payload.listingId());
        AuctionHouseScreen.receiveBidHistory(payload);
    });
}
```

- [ ] **Step 7: Add static receiver in AuctionHouseScreen**

Add to `AuctionHouseScreen.java`:

```java
public static void receiveBidHistory(BidHistoryResponsePayload payload) {
    if (Minecraft.getInstance().screen instanceof AuctionHouseScreen screen) {
        screen.buyTab.onReceiveBidHistory(payload);
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/network/payload/RequestBidHistoryPayload.java auction-house/src/main/java/net/ecocraft/ah/network/payload/BidHistoryResponsePayload.java auction-house/src/main/java/net/ecocraft/ah/network/AHNetworkHandler.java auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java auction-house/src/main/java/net/ecocraft/ah/network/ClientPayloadHandler.java auction-house/src/main/java/net/ecocraft/ah/service/AuctionService.java auction-house/src/main/java/net/ecocraft/ah/screen/AuctionHouseScreen.java
git commit -m "feat: add bid history request/response payloads and handlers"
```

---

## Task 14: Enrich ListingDetailResponsePayload with recent bids

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/payload/ListingDetailResponsePayload.java`
- Modify: `auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java`

- [ ] **Step 1: Add recentBids field to ListingDetailResponsePayload**

Add a new field `List<BidHistoryResponsePayload.BidEntry> recentBids` to the record. Use the same `BidEntry` type from `BidHistoryResponsePayload` to stay DRY.

Update the record definition to include the new field. Add a backward-compatible constructor that defaults `recentBids` to `List.of()`.

Update `STREAM_CODEC` to include the new field.

- [ ] **Step 2: Populate recentBids when building the response**

In `ServerPayloadHandler.java`, where `ListingDetailResponsePayload` is constructed (in `handleRequestListingDetail`), query the top 3 bids for each AUCTION listing:

```java
// After building entries list:
List<BidHistoryResponsePayload.BidEntry> recentBids = List.of();
if (!entries.isEmpty() && "AUCTION".equals(entries.get(0).type())) {
    // Get top 3 bids for this item's first listing (or across all listings)
    List<AuctionBid> bids = service.getBidsForListing(entries.get(0).listingId());
    recentBids = bids.stream()
            .limit(3)
            .map(b -> new BidHistoryResponsePayload.BidEntry(b.bidderName(), b.amount(), b.timestamp()))
            .toList();
}
```

Pass `recentBids` to the `ListingDetailResponsePayload` constructor.

- [ ] **Step 3: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/network/payload/ListingDetailResponsePayload.java auction-house/src/main/java/net/ecocraft/ah/network/ServerPayloadHandler.java
git commit -m "feat: enrich ListingDetailResponsePayload with top 3 recent bids"
```

---

## Task 15: Bid history UI in BuyTab detail panel

**Files:**
- Modify: `auction-house/src/main/java/net/ecocraft/ah/screen/BuyTab.java`

- [ ] **Step 1: Add bid history state fields to BuyTab**

Add fields:
```java
private List<BidHistoryResponsePayload.BidEntry> recentBids = List.of();
private List<BidHistoryResponsePayload.BidEntry> fullBidHistory = null;
```

- [ ] **Step 2: Store recent bids when receiving detail response**

In the method that receives `ListingDetailResponsePayload`, store `recentBids`:
```java
this.recentBids = payload.recentBids();
```

- [ ] **Step 3: Add onReceiveBidHistory method**

```java
public void onReceiveBidHistory(BidHistoryResponsePayload payload) {
    this.fullBidHistory = payload.bids();
    // Show dialog with full bid history
    showBidHistoryDialog(payload.bids());
}
```

- [ ] **Step 4: Render recent bids in detail panel**

In the detail panel rendering method (inside the AUCTION branch), after the existing bid info section, add:

```java
// Recent bids section
int bidSectionY = /* after existing content, e.g., panelY + 150 */;
Font font = Minecraft.getInstance().font;

DrawUtils.drawAccentSeparator(graphics, panelX + 8, bidSectionY, panelW - 16, THEME);
bidSectionY += 6;

String recentLabel = Component.translatable("ecocraft_ah.bid_history.recent").getString();
graphics.drawString(font, recentLabel, panelX + 8, bidSectionY, THEME.textGrey, false);
bidSectionY += font.lineHeight + 4;

if (recentBids.isEmpty()) {
    String noBids = Component.translatable("ecocraft_ah.bid_history.no_bids").getString();
    graphics.drawString(font, noBids, panelX + 8, bidSectionY, THEME.textDim, false);
} else {
    for (BidHistoryResponsePayload.BidEntry bid : recentBids) {
        String line = bid.bidderName() + " — " + formatPrice(bid.amount());
        String timeAgo = formatTimeAgo(bid.timestamp());
        graphics.drawString(font, line, panelX + 8, bidSectionY, THEME.textLight, false);
        int timeW = font.width(timeAgo);
        graphics.drawString(font, timeAgo, panelX + panelW - 8 - timeW, bidSectionY, THEME.textDim, false);
        bidSectionY += font.lineHeight + 2;
    }
}

// "Voir tout" button (if more than 3 bids)
```

- [ ] **Step 5: Add "Voir tout" button**

Create the button during `buildWidgets()` for DETAIL mode:

```java
if (recentBids.size() >= 3) {
    // There might be more bids — show "See all" button
    seeAllBidsButton = EcoButton.builder(
            Component.translatable("ecocraft_ah.bid_history.see_all", recentBids.size()),
            this::onSeeAllBids)
            .theme(THEME).bounds(panelX + 8, bidSectionY + 4, panelW - 16, 16)
            .bgColor(THEME.bgMedium).borderColor(THEME.borderLight)
            .textColor(THEME.textLight).hoverBg(THEME.bgLight).build();
    addChild(seeAllBidsButton);
}
```

- [ ] **Step 6: Implement onSeeAllBids and showBidHistoryDialog**

```java
private void onSeeAllBids() {
    if (selectedEntryIndex >= 0) {
        var filtered = getFilteredEntries();
        if (selectedEntryIndex < filtered.size()) {
            String listingId = filtered.get(selectedEntryIndex).listingId();
            PacketDistributor.sendToServer(new RequestBidHistoryPayload(listingId));
        }
    }
}

private void showBidHistoryDialog(List<BidHistoryResponsePayload.BidEntry> bids) {
    Font font = Minecraft.getInstance().font;
    String title = Component.translatable("ecocraft_ah.bid_history.title").getString();

    // Build content string
    StringBuilder content = new StringBuilder();
    int rank = 1;
    for (BidHistoryResponsePayload.BidEntry bid : bids) {
        content.append("#").append(rank++).append(" ")
                .append(bid.bidderName()).append(" — ")
                .append(formatPrice(bid.amount())).append(" — ")
                .append(formatTimestamp(bid.timestamp())).append("\n");
    }

    EcoDialog dialog = EcoDialog.alert(THEME, title, content.toString().trim(),
            () -> {}, parent.width, parent.height);
    parent.getTree().addPortal(dialog);
}
```

- [ ] **Step 7: Add time formatting helpers**

```java
private static String formatTimeAgo(long timestamp) {
    long elapsed = System.currentTimeMillis() - timestamp;
    long minutes = elapsed / 60000;
    if (minutes < 60) {
        return Component.translatable("ecocraft_ah.bid_history.ago", minutes + "m").getString();
    }
    long hours = minutes / 60;
    if (hours < 24) {
        return Component.translatable("ecocraft_ah.bid_history.ago", hours + "h").getString();
    }
    long days = hours / 24;
    return Component.translatable("ecocraft_ah.bid_history.ago", days + "d").getString();
}

private static String formatTimestamp(long timestamp) {
    java.time.LocalDateTime dt = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
    return String.format("%02d/%02d/%04d %02d:%02d",
            dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(),
            dt.getHour(), dt.getMinute());
}
```

- [ ] **Step 8: Commit**

```bash
git add auction-house/src/main/java/net/ecocraft/ah/screen/BuyTab.java
git commit -m "feat: add bid history display in detail panel with 'see all' dialog"
```

---

## Task 16: Build, deploy, and verify

**Files:** None (verification only)

- [ ] **Step 1: Full build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run existing tests**

Run: `./gradlew :auction-house:test`
Expected: All tests pass

- [ ] **Step 3: Deploy to Minecraft**

```bash
rm /home/florian/.minecraft/mods/ecocraft-*
cp economy-api/build/libs/*.jar /home/florian/.minecraft/mods/
cp gui-lib/build/libs/*.jar /home/florian/.minecraft/mods/
cp economy-core/build/libs/*.jar /home/florian/.minecraft/mods/
cp auction-house/build/libs/*.jar /home/florian/.minecraft/mods/
```

- [ ] **Step 4: In-game verification checklist**

1. Open AH (`/ah`) — gear button should be visible for all players
2. Click gear — non-OP sees only Notifications tab, OP sees all tabs
3. Notifications tab: 5 rows with dropdowns, change values and reopen to verify persistence
4. Place a bid on an auction listing
5. Place a higher bid from another account — verify outbid notification appears (toast + chat)
6. Let an auction expire with bids — verify auction_won/sale_completed/auction_lost notifications
7. Let a listing expire without bids — verify listing_expired notification
8. In detail view, select an auction listing — verify recent bids section shows
9. Click "Voir tout" — verify dialog opens with full bid history

- [ ] **Step 5: Commit any fixes**

```bash
git add -A
git commit -m "fix: post-integration adjustments for bidding polish"
```
