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

        // Server -> Client (lambdas defer client class loading for dist safety)
        registrar.playToClient(
                OpenMailboxPayload.TYPE,
                OpenMailboxPayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleOpenMailbox(payload, ctx)
        );

        registrar.playToClient(
                MailListResponsePayload.TYPE,
                MailListResponsePayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleMailListResponse(payload, ctx)
        );

        registrar.playToClient(
                MailDetailResponsePayload.TYPE,
                MailDetailResponsePayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleMailDetailResponse(payload, ctx)
        );

        registrar.playToClient(
                CollectMailResultPayload.TYPE,
                CollectMailResultPayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleCollectMailResult(payload, ctx)
        );

        registrar.playToClient(
                SendMailResultPayload.TYPE,
                SendMailResultPayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleSendMailResult(payload, ctx)
        );

        registrar.playToClient(
                MailNotificationPayload.TYPE,
                MailNotificationPayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleNotification(payload, ctx)
        );

        registrar.playToClient(
                MailSettingsPayload.TYPE,
                MailSettingsPayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleMailSettings(payload, ctx)
        );

        registrar.playToClient(
                PostmanSkinPayload.TYPE,
                PostmanSkinPayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handlePostmanSkin(payload, ctx)
        );

        registrar.playToClient(
                DraftsResponsePayload.TYPE,
                DraftsResponsePayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleDraftsResponse(payload, ctx)
        );

        registrar.playToClient(
                SentMailsResponsePayload.TYPE,
                SentMailsResponsePayload.STREAM_CODEC,
                (payload, ctx) -> MailClientPayloadHandler.handleSentMailsResponse(payload, ctx)
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

        registrar.playToServer(
                MarkReadPayload.TYPE,
                MarkReadPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleMarkRead
        );

        registrar.playToServer(
                UpdatePostmanSkinPayload.TYPE,
                UpdatePostmanSkinPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleUpdatePostmanSkin
        );

        registrar.playToServer(
                SaveDraftPayload.TYPE,
                SaveDraftPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleSaveDraft
        );

        registrar.playToServer(
                RequestDraftsPayload.TYPE,
                RequestDraftsPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleRequestDrafts
        );

        registrar.playToServer(
                DeleteDraftPayload.TYPE,
                DeleteDraftPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleDeleteDraft
        );

        registrar.playToServer(
                RequestSentMailsPayload.TYPE,
                RequestSentMailsPayload.STREAM_CODEC,
                MailServerPayloadHandler::handleRequestSentMails
        );
    }
}
