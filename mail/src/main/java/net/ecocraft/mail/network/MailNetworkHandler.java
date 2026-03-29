package net.ecocraft.mail.network;

import net.ecocraft.mail.network.payload.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all mail network payloads on the MOD event bus.
 */
public final class MailNetworkHandler {

    private MailNetworkHandler() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Server -> Client
        registrar.playToClient(
                OpenMailboxPayload.TYPE,
                OpenMailboxPayload.STREAM_CODEC,
                MailClientPayloadHandler::handleOpenMailbox
        );

        registrar.playToClient(
                MailListResponsePayload.TYPE,
                MailListResponsePayload.STREAM_CODEC,
                MailClientPayloadHandler::handleMailListResponse
        );

        registrar.playToClient(
                MailDetailResponsePayload.TYPE,
                MailDetailResponsePayload.STREAM_CODEC,
                MailClientPayloadHandler::handleMailDetailResponse
        );

        registrar.playToClient(
                CollectMailResultPayload.TYPE,
                CollectMailResultPayload.STREAM_CODEC,
                MailClientPayloadHandler::handleCollectMailResult
        );

        registrar.playToClient(
                SendMailResultPayload.TYPE,
                SendMailResultPayload.STREAM_CODEC,
                MailClientPayloadHandler::handleSendMailResult
        );

        registrar.playToClient(
                MailNotificationPayload.TYPE,
                MailNotificationPayload.STREAM_CODEC,
                MailClientPayloadHandler::handleNotification
        );

        registrar.playToClient(
                MailSettingsPayload.TYPE,
                MailSettingsPayload.STREAM_CODEC,
                MailClientPayloadHandler::handleMailSettings
        );

        // Client -> Server
        registrar.playToServer(
                RequestMailListPayload.TYPE,
                RequestMailListPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleRequestMailList
        );

        registrar.playToServer(
                RequestMailDetailPayload.TYPE,
                RequestMailDetailPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleRequestMailDetail
        );

        registrar.playToServer(
                CollectMailPayload.TYPE,
                CollectMailPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleCollectMail
        );

        registrar.playToServer(
                SendMailPayload.TYPE,
                SendMailPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleSendMail
        );

        registrar.playToServer(
                DeleteMailPayload.TYPE,
                DeleteMailPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleDeleteMail
        );

        registrar.playToServer(
                ReturnCODPayload.TYPE,
                ReturnCODPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleReturnCOD
        );

        registrar.playToServer(
                PayCODPayload.TYPE,
                PayCODPayload.STREAM_CODEC,
                MailServerPayloadHandler::handlePayCOD
        );

        registrar.playToServer(
                UpdateMailSettingsPayload.TYPE,
                UpdateMailSettingsPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleUpdateMailSettings
        );
    }
}
