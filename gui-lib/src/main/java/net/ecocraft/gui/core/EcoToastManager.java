package net.ecocraft.gui.core;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Client-side singleton that manages toast notifications.
 * <p>
 * Usage:
 * <pre>{@code
 * EcoToastManager.getInstance().show(
 *     EcoToast.builder(theme)
 *         .title("Surenchéri !")
 *         .level(ToastLevel.WARNING)
 *         .build()
 * );
 * }</pre>
 * <p>
 * Call {@link #render(GuiGraphics, int, int)} each frame from the HUD overlay event,
 * and {@link #mouseClicked(double, double)} from the mouse click event.
 */
public final class EcoToastManager {

    private static final int MAX_VISIBLE = 3;
    private static final int MARGIN_RIGHT = 8;
    private static final int MARGIN_TOP = 8;
    private static final int GAP = 4;

    private static final EcoToastManager INSTANCE = new EcoToastManager();

    /** Active (currently displayed) toasts — at most MAX_VISIBLE. */
    private final List<EcoToast> active = new ArrayList<>();

    /** Waiting toasts — promoted to active when slots open up. */
    private final Deque<EcoToast> queue = new ArrayDeque<>();

    private EcoToastManager() {}

    public static EcoToastManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Enqueue a toast for display. It will be shown as soon as a slot is available. */
    public void show(EcoToast toast) {
        queue.addLast(toast);
    }

    /** Remove all queued and active toasts immediately. */
    public void clear() {
        active.clear();
        queue.clear();
    }

    // -------------------------------------------------------------------------
    // Per-frame render
    // -------------------------------------------------------------------------

    /**
     * Called every render frame.
     * Promotes toasts from queue, culls finished ones, then draws them
     * stacked from the top-right corner.
     */
    public void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        // 1. Remove finished toasts
        active.removeIf(EcoToast::isFinished);

        // 2. Promote from queue while there is room
        while (active.size() < MAX_VISIBLE && !queue.isEmpty()) {
            EcoToast next = queue.pollFirst();
            next.start();
            active.add(next);
        }

        // 3. Render active toasts stacked top-right
        int baseX = screenWidth - EcoToast.WIDTH - MARGIN_RIGHT;
        int currentY = MARGIN_TOP;

        for (EcoToast toast : active) {
            toast.render(graphics, baseX, currentY);
            currentY += toast.getHeight() + GAP;
        }
    }

    // -------------------------------------------------------------------------
    // Mouse interaction
    // -------------------------------------------------------------------------

    /**
     * Forward mouse click events here so toasts with {@code dismissOnClick} can be dismissed.
     *
     * @return true if a toast consumed the click
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        // We need to know where each toast was rendered — recompute positions
        // This mirrors the layout logic in render().
        // NOTE: screenWidth must be consistent; we use a cached approach via Minecraft.
        int screenWidth = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int baseX = screenWidth - EcoToast.WIDTH - MARGIN_RIGHT;
        int currentY = MARGIN_TOP;

        for (EcoToast toast : active) {
            if (toast.containsPoint(baseX, currentY, mouseX, mouseY)) {
                if (toast.isDismissOnClick()) {
                    toast.dismiss();
                    return true;
                }
            }
            currentY += toast.getHeight() + GAP;
        }
        return false;
    }
}
