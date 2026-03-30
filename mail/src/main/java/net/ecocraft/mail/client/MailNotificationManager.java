package net.ecocraft.mail.client;

import net.ecocraft.gui.core.EcoToast;
import net.ecocraft.gui.core.EcoToastManager;
import net.ecocraft.gui.core.ToastAnimation;
import net.ecocraft.gui.theme.Theme;
import net.ecocraft.mail.network.payload.MailNotificationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class MailNotificationManager {

    private static final Theme THEME = Theme.dark();

    public static void handle(MailNotificationPayload payload) {
        MailNotificationEventType eventType = MailNotificationEventType.fromKey(payload.eventType());
        if (eventType == null) return;

        MailNotificationChannel channel = MailNotificationConfig.getInstance().getChannel(eventType);
        if (channel == MailNotificationChannel.NONE) return;

        String title = getTitleForEvent(eventType);
        String message = Component.translatable("ecocraft_mail.notification.message", payload.subject(), payload.senderName()).getString();

        if (channel == MailNotificationChannel.CHAT || channel == MailNotificationChannel.BOTH) {
            sendChat(message);
        }
        if (channel == MailNotificationChannel.TOAST || channel == MailNotificationChannel.BOTH) {
            sendToast(eventType, title, message);
        }

        // Play notification sound
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private static String getTitleForEvent(MailNotificationEventType eventType) {
        return switch (eventType) {
            case NEW_MAIL -> Component.translatable("ecocraft_mail.notification.new_mail").getString();
            case COD_RECEIVED -> Component.translatable("ecocraft_mail.notification.cod_received").getString();
            case MAIL_RETURNED -> Component.translatable("ecocraft_mail.notification.mail_returned").getString();
            case READ_RECEIPT -> Component.translatable("ecocraft_mail.notification.read_receipt").getString();
        };
    }

    private static void sendChat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable("ecocraft_mail.notification.chat_prefix").append(Component.literal(message)),
                    false
            );
        }
    }

    private static void sendToast(MailNotificationEventType eventType, String title, String message) {
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
