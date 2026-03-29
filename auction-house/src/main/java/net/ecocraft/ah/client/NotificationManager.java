package net.ecocraft.ah.client;

import net.ecocraft.ah.network.payload.AHNotificationPayload;
import net.ecocraft.gui.core.EcoToast;
import net.ecocraft.gui.core.EcoToastManager;
import net.ecocraft.gui.core.ToastAnimation;
import net.ecocraft.gui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
