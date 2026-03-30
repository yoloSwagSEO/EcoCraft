package net.ecocraft.mail.network;

import net.ecocraft.mail.client.MailNotificationManager;
import net.ecocraft.mail.network.payload.*;
import net.ecocraft.mail.screen.MailboxScreen;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles server-to-client payloads for the mail system.
 */
public final class MailClientPayloadHandler {

    private MailClientPayloadHandler() {}

    public static void handleOpenMailbox(OpenMailboxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.open(payload.entityId()));
    }

    public static void handlePostmanSkin(PostmanSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.receivePostmanSkin(payload));
    }

    public static void handleMailListResponse(MailListResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.receiveMailList(payload));
    }

    public static void handleMailDetailResponse(MailDetailResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.receiveMailDetail(payload));
    }

    public static void handleCollectMailResult(CollectMailResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.receiveCollectResult(payload));
    }

    public static void handleSendMailResult(SendMailResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.receiveSendResult(payload));
    }

    public static void handleNotification(MailNotificationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailNotificationManager.handle(payload));
    }

    public static void handleDraftsResponse(DraftsResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MailboxScreen.receiveDrafts(payload));
    }

    public static void handleMailSettings(MailSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var screen = net.minecraft.client.Minecraft.getInstance().screen;
            if (screen instanceof net.ecocraft.mail.screen.MailSettingsScreen settingsScreen) {
                settingsScreen.receiveSettings(payload);
            }
        });
    }
}
