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

        // Server → Client (no client-to-server handler needed)
        registrar.playToClient(
                OpenAHPayload.TYPE,
                OpenAHPayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenAH
        );

        registrar.playToClient(
                ListingsResponsePayload.TYPE,
                ListingsResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleListingsResponse
        );

        registrar.playToClient(
                ListingDetailResponsePayload.TYPE,
                ListingDetailResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleListingDetailResponse
        );

        registrar.playToClient(
                AHActionResultPayload.TYPE,
                AHActionResultPayload.STREAM_CODEC,
                ClientPayloadHandler::handleActionResult
        );

        registrar.playToClient(
                MyListingsResponsePayload.TYPE,
                MyListingsResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleMyListingsResponse
        );

        registrar.playToClient(
                LedgerResponsePayload.TYPE,
                LedgerResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleLedgerResponse
        );

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
                RequestLedgerPayload.TYPE,
                RequestLedgerPayload.STREAM_CODEC,
                ServerPayloadHandler::handleRequestLedger
        );
    }
}
