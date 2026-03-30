package net.ecocraft.ah.network;

import net.ecocraft.ah.network.payload.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers all auction-house network payloads on the MOD event bus.
 */
public final class AHNetworkHandler {

    private AHNetworkHandler() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        // Server -> Client (lambdas defer client class loading for dist safety)
        registrar.playToClient(
                OpenAHPayload.TYPE,
                OpenAHPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleOpenAH(payload, ctx)
        );

        registrar.playToClient(
                ListingsResponsePayload.TYPE,
                ListingsResponsePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleListingsResponse(payload, ctx)
        );

        registrar.playToClient(
                ListingDetailResponsePayload.TYPE,
                ListingDetailResponsePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleListingDetailResponse(payload, ctx)
        );

        registrar.playToClient(
                AHActionResultPayload.TYPE,
                AHActionResultPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleActionResult(payload, ctx)
        );

        registrar.playToClient(
                MyListingsResponsePayload.TYPE,
                MyListingsResponsePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleMyListingsResponse(payload, ctx)
        );

        registrar.playToClient(
                LedgerResponsePayload.TYPE,
                LedgerResponsePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleLedgerResponse(payload, ctx)
        );

        registrar.playToClient(
                BalanceUpdatePayload.TYPE,
                BalanceUpdatePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleBalanceUpdate(payload, ctx)
        );

        registrar.playToClient(
                BestPriceResponsePayload.TYPE,
                BestPriceResponsePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleBestPriceResponse(payload, ctx)
        );

        registrar.playToClient(
                AHSettingsPayload.TYPE,
                AHSettingsPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleAHSettings(payload, ctx)
        );

        registrar.playToClient(
                NPCSkinPayload.TYPE,
                NPCSkinPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleNPCSkin(payload, ctx)
        );

        registrar.playToClient(
                AHContextPayload.TYPE,
                AHContextPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleAHContext(payload, ctx)
        );

        registrar.playToClient(
                AHInstancesPayload.TYPE,
                AHInstancesPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleAHInstances(payload, ctx)
        );

        registrar.playToClient(AHNotificationPayload.TYPE, AHNotificationPayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleNotification(payload, ctx));

        registrar.playToClient(BidHistoryResponsePayload.TYPE, BidHistoryResponsePayload.STREAM_CODEC,
                (payload, ctx) -> ClientPayloadHandler.handleBidHistoryResponse(payload, ctx));

        // Client → Server
        registrar.playToServer(
                RequestListingsPayload.TYPE,
                RequestListingsPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestListings
        );

        registrar.playToServer(
                RequestListingDetailPayload.TYPE,
                RequestListingDetailPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestListingDetail
        );

        registrar.playToServer(
                CreateListingPayload.TYPE,
                CreateListingPayload.STREAM_CODEC,
                ServerPayloadHandler::handleCreateListing
        );

        registrar.playToServer(
                BuyListingPayload.TYPE,
                BuyListingPayload.STREAM_CODEC,
                ServerPayloadHandler::handleBuyListing
        );

        registrar.playToServer(
                PlaceBidPayload.TYPE,
                PlaceBidPayload.STREAM_CODEC,
                ServerPayloadHandler::handlePlaceBid
        );

        registrar.playToServer(
                CancelListingPayload.TYPE,
                CancelListingPayload.STREAM_CODEC,
                ServerPayloadHandler::handleCancelListing
        );

        registrar.playToServer(
                CollectParcelsPayload.TYPE,
                CollectParcelsPayload.STREAM_CODEC,
                ServerPayloadHandler::handleCollectParcels
        );

        registrar.playToServer(
                RequestMyListingsPayload.TYPE,
                RequestMyListingsPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestMyListings
        );

        registrar.playToServer(
                RequestBestPricePayload.TYPE,
                RequestBestPricePayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestBestPrice
        );

        registrar.playToServer(
                RequestLedgerPayload.TYPE,
                RequestLedgerPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestLedger
        );

        registrar.playToServer(
                UpdateAHSettingsPayload.TYPE,
                UpdateAHSettingsPayload.STREAM_CODEC,
                ServerPayloadHandler::handleUpdateAHSettings
        );

        registrar.playToServer(
                UpdateNPCSkinPayload.TYPE,
                UpdateNPCSkinPayload.STREAM_CODEC,
                ServerPayloadHandler::handleUpdateNPCSkin
        );

        registrar.playToServer(
                CreateAHPayload.TYPE,
                CreateAHPayload.STREAM_CODEC,
                ServerPayloadHandler::handleCreateAH
        );

        registrar.playToServer(
                DeleteAHPayload.TYPE,
                DeleteAHPayload.STREAM_CODEC,
                ServerPayloadHandler::handleDeleteAH
        );

        registrar.playToServer(
                UpdateAHInstancePayload.TYPE,
                UpdateAHInstancePayload.STREAM_CODEC,
                ServerPayloadHandler::handleUpdateAHInstance
        );

        registrar.playToServer(RequestBidHistoryPayload.TYPE, RequestBidHistoryPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestBidHistory);
    }
}
