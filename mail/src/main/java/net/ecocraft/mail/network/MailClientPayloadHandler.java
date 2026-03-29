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
        context.enqueueWork(MailboxScreen::open);
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
}
