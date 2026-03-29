package net.ecocraft.gui.core;

import net.ecocraft.gui.theme.DrawUtils;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A toast notification rendered as a global overlay (NOT part of the WidgetTree).
 * <p>
 * Created via {@link Builder}:
 * <pre>{@code
 * EcoToast.builder(theme)
 *     .title("Surenchéri !")
 *     .message("Épée en diamant — 150 Or")
 *     .level(ToastLevel.WARNING)
 *     .animation(ToastAnimation.SLIDE_RIGHT)
 *     .duration(5000)
 *     .build();
 * }</pre>
 * <p>
 * Lifecycle is managed by {@link EcoToastManager}.
 */
public final class EcoToast {

    static final int WIDTH = 200;
    static final int MIN_HEIGHT = 32;
    private static final int ACCENT_BAR_WIDTH = 4;
    private static final int ICON_SIZE = 16;
    private static final int PADDING = 6;
    private static final int ANIM_DURATION_MS = 300;

    private final Theme theme;
    private final String title;
    private final @Nullable String message;
    private final @Nullable ItemStack icon;
    private final ToastLevel level;
    private final ToastAnimation animation;
    private final long durationMs;
    private final boolean dismissOnClick;

    private final int height;

    private long spawnTime = -1;
    private boolean dismissed = false;

    // -------------------------------------------------------------------------
    // Constructor (package-private, use Builder)
    // -------------------------------------------------------------------------

    private EcoToast(Builder builder) {
        this.theme = builder.theme;
        this.title = builder.title != null ? builder.title : "";
        this.message = builder.message;
        this.icon = builder.icon;
        this.level = builder.level != null ? builder.level : ToastLevel.INFO;
        this.animation = builder.animation != null ? builder.animation : ToastAnimation.SLIDE_RIGHT;
        this.durationMs = builder.durationMs;
        this.dismissOnClick = builder.dismissOnClick;

        // Pre-calculate height based on font metrics
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + 2; // ~11px per line
        int contentWidth = WIDTH - ACCENT_BAR_WIDTH - PADDING * 2 - (icon != null ? ICON_SIZE + PADDING : 0);

        int textH = lineHeight; // title always one line
        if (message != null && !message.isEmpty()) {
            // Measure message lines (max 2)
            int msgWidth = font.width(message);
            int lines = Math.min(2, (int) Math.ceil((double) msgWidth / contentWidth));
            if (lines < 1) lines = 1;
            textH += lines * lineHeight;
        }

        int contentH = PADDING * 2 + textH;
        this.height = Math.max(MIN_HEIGHT, contentH);
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder(Theme theme) {
        return new Builder(theme);
    }

    public static final class Builder {
        private final Theme theme;
        private @Nullable String title;
        private @Nullable String message;
        private @Nullable ItemStack icon;
        private @Nullable ToastLevel level;
        private @Nullable ToastAnimation animation;
        private long durationMs = 4000;
        private boolean dismissOnClick = true;

        private Builder(Theme theme) {
            this.theme = theme;
        }

        public Builder title(String title) { this.title = title; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder icon(@Nullable ItemStack icon) { this.icon = icon; return this; }
        public Builder level(ToastLevel level) { this.level = level; return this; }
        public Builder animation(ToastAnimation animation) { this.animation = animation; return this; }
        /** Duration in ms. 0 = permanent (until clicked or manually dismissed). */
        public Builder duration(long ms) { this.durationMs = ms; return this; }
        public Builder dismissOnClick(boolean dismissOnClick) { this.dismissOnClick = dismissOnClick; return this; }

        public EcoToast build() {
            return new EcoToast(this);
        }
    }

    // -------------------------------------------------------------------------
    // Package-private lifecycle (used by EcoToastManager)
    // -------------------------------------------------------------------------

    /** Start the toast — records the spawn time. */
    void start() {
        this.spawnTime = System.currentTimeMillis();
    }

    /** Trigger the dismiss/exit animation. */
    void dismiss() {
        this.dismissed = true;
        if (dismissTime < 0) {
            dismissTime = System.currentTimeMillis();
        }
    }

    /** Whether the toast has fully completed (including exit animation). */
    boolean isFinished() {
        if (spawnTime < 0) return false;
        long now = System.currentTimeMillis();
        long elapsed = now - spawnTime;

        if (dismissed) {
            // Find when dismissal was triggered — approximate: we mark dismissed flag
            // and wait ANIM_DURATION_MS from when visibility would reach 0.
            // We track dismissTime separately for accuracy.
            if (dismissTime >= 0) {
                return now - dismissTime >= ANIM_DURATION_MS;
            }
            return false;
        }

        if (durationMs > 0) {
            // Entry anim + display + exit anim
            return elapsed >= durationMs + ANIM_DURATION_MS;
        }
        return false;
    }

    // Track dismiss time for exit animation
    private long dismissTime = -1;

    @Override
    public String toString() {
        return "EcoToast{title='" + title + "', level=" + level + "}";
    }

    /** 0.0 = invisible, 1.0 = fully visible. */
    float getVisibility() {
        if (spawnTime < 0) return 0f;
        long now = System.currentTimeMillis();
        long elapsed = now - spawnTime;

        if (dismissed) {
            long exitElapsed = now - dismissTime;
            float t = Math.min(1f, (float) exitElapsed / ANIM_DURATION_MS);
            return easeOut(1f - t); // fade out
        }

        // Entry animation
        if (elapsed < ANIM_DURATION_MS) {
            float t = (float) elapsed / ANIM_DURATION_MS;
            return easeOut(t);
        }

        // Full display
        if (durationMs <= 0) return 1f; // permanent

        long displayEnd = durationMs;
        if (elapsed < displayEnd) return 1f;

        // Exit animation
        long exitElapsed = elapsed - displayEnd;
        if (exitElapsed < ANIM_DURATION_MS) {
            float t = (float) exitElapsed / ANIM_DURATION_MS;
            return easeOut(1f - t);
        }

        return 0f;
    }

    ToastAnimation getAnimation() { return animation; }
    int getHeight() { return height; }
    boolean isDismissOnClick() { return dismissOnClick; }

    /** Returns true when the toast has fully slid in and is not yet animating out. */
    boolean isFullyVisible() { return getVisibility() >= 0.99f; }

    /** Returns true if (mouseX, mouseY) is within the rendered bounds of this toast. */
    boolean containsPoint(int renderX, int renderY, double mouseX, double mouseY) {
        return mouseX >= renderX && mouseX < renderX + WIDTH
                && mouseY >= renderY && mouseY < renderY + height;
    }

    /** Render this toast at the given position. */
    void render(GuiGraphics graphics, int x, int y) {
        float visibility = getVisibility();
        if (visibility <= 0f) return;

        Font font = Minecraft.getInstance().font;

        // Apply animation offset
        int offsetX = 0;
        int offsetY = 0;
        float hidden = 1f - visibility;
        switch (animation) {
            case SLIDE_UP    -> offsetY = (int) (-hidden * (height + 4));
            case SLIDE_DOWN  -> offsetY = (int) (hidden * (height + 4));
            case SLIDE_LEFT  -> offsetX = (int) (-hidden * (WIDTH + 8));
            case SLIDE_RIGHT -> offsetX = (int) (hidden * (WIDTH + 8));
            case FADE        -> {} // no positional offset, use alpha only
        }

        int rx = x + offsetX;
        int ry = y + offsetY;

        // Compute alpha (0-255) for FADE and general visibility
        int alpha = (int) (visibility * 255);
        // For non-FADE animations visibility is used purely for timing, draw full alpha once visible
        if (animation != ToastAnimation.FADE) {
            alpha = 255;
        }

        // Draw background panel
        int bg = applyAlpha(theme.bgDark, alpha);
        int border = applyAlpha(theme.border, alpha);
        DrawUtils.drawPanel(graphics, rx, ry, WIDTH, height, bg, border);

        // Left accent bar
        int accentColor = applyAlpha(level.getColor(theme), alpha);
        graphics.fill(rx, ry, rx + ACCENT_BAR_WIDTH, ry + height, accentColor);

        // Icon (optional)
        int textX = rx + ACCENT_BAR_WIDTH + PADDING;
        if (icon != null && !icon.isEmpty()) {
            int iconY = ry + (height - ICON_SIZE) / 2;
            graphics.renderItem(icon, rx + ACCENT_BAR_WIDTH + PADDING, iconY);
            textX += ICON_SIZE + PADDING;
        }

        int contentWidth = WIDTH - ACCENT_BAR_WIDTH - PADDING * 2 - (icon != null ? ICON_SIZE + PADDING : 0);

        // Title
        int titleY = ry + PADDING;
        String titleText = DrawUtils.truncateText(font, title, contentWidth);
        graphics.drawString(font, titleText, textX, titleY, applyAlpha(theme.textWhite, alpha), false);

        // Message (max 2 lines)
        if (message != null && !message.isEmpty()) {
            int msgY = titleY + font.lineHeight + 2;
            // Split message manually into at most 2 lines
            var lines = font.split(net.minecraft.network.chat.Component.literal(message), contentWidth);
            int maxLines = Math.min(2, lines.size());
            for (int i = 0; i < maxLines; i++) {
                graphics.drawString(font, lines.get(i), textX, msgY, applyAlpha(theme.textLight, alpha), false);
                msgY += font.lineHeight + 2;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Smooth ease-out: y = 1 - (1-t)^2 */
    private static float easeOut(float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - clamped) * (1f - clamped);
    }

    /** Apply alpha (0-255) to an ARGB color. */
    private static int applyAlpha(int color, int alpha) {
        int originalAlpha = (color >> 24) & 0xFF;
        int blended = (originalAlpha * alpha) / 255;
        return (color & 0x00FFFFFF) | (blended << 24);
    }
}
