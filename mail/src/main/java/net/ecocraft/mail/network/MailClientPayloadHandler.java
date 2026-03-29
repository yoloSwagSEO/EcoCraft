package net.ecocraft.mail.network;

import net.ecocraft.mail.network.payload.*;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Handles server-to-client payloads for the mail system.
 * Placeholder stubs — actual client-side handling will be added in Task 7/8 (UI).
 */
public final class MailClientPayloadHandler {

    private MailClientPayloadHandler() {}

    public static void handleOpenMailbox(OpenMailboxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Task 7: open MailboxScreen
        });
    }

    public static void handleMailListResponse(MailListResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Task 7: populate mail list view
        });
    }

    public static void handleMailDetailResponse(MailDetailResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Task 7: populate mail detail view
        });
    }

    public static void handleCollectMailResult(CollectMailResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Task 7: show collect result, refresh list
        });
    }

    public static void handleSendMailResult(SendMailResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Task 8: show send result, close compose view
        });
    }

    public static void handleNotification(MailNotificationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Task 8: dispatch notification to toast/chat
        });
    }
}
