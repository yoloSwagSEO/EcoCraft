package net.ecocraft.ah.network;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.network.payload.*;
import net.ecocraft.ah.screen.AuctionHouseScreen;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * Handles server-to-client packets for the auction house.
 * Routes data to the active AuctionHouseScreen instance.
 */
public final class ClientPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientPayloadHandler() {}

    public static void handleOpenAH(OpenAHPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received OpenAH entityId={}", payload.entityId());
            AuctionHouseScreen.open(payload.entityId());
        });
    }

    public static void handleNPCSkin(NPCSkinPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received NPCSkin entityId={} skinName='{}'", payload.entityId(), payload.skinPlayerName());
            AuctionHouseScreen.receiveNPCSkin(payload);
        });
    }

    public static void handleListingsResponse(ListingsResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received ListingsResponse with {} items, page {}/{}",
                    payload.items().size(), payload.page(), payload.totalPages());
            AuctionHouseScreen.receiveListings(payload);
        });
    }

    public static void handleListingDetailResponse(ListingDetailResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received ListingDetailResponse for '{}'", payload.itemId());
            AuctionHouseScreen.receiveListingDetail(payload);
        });
    }

    public static void handleActionResult(AHActionResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received ActionResult success={} message='{}'",
                    payload.success(), payload.message());
            AuctionHouseScreen.receiveActionResult(payload);
        });
    }

    public static void handleMyListingsResponse(MyListingsResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received MyListingsResponse with {} entries", payload.entries().size());
            AuctionHouseScreen.receiveMyListings(payload);
        });
    }

    public static void handleLedgerResponse(LedgerResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received LedgerResponse with {} entries, page {}/{}",
                    payload.entries().size(), payload.page(), payload.totalPages());
            AuctionHouseScreen.receiveLedger(payload);
        });
    }

    public static void handleBestPriceResponse(BestPriceResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received BestPriceResponse for '{}': {}", payload.itemId(), payload.bestPrice());
            AuctionHouseScreen.receiveBestPrice(payload);
        });
    }

    public static void handleBalanceUpdate(BalanceUpdatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received BalanceUpdate: {} {}", payload.balance(), payload.currencySymbol());
            AuctionHouseScreen.receiveBalanceUpdate(payload);
        });
    }

    public static void handleAHContext(AHContextPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received context ahId={} ahName='{}'", payload.ahId(), payload.ahName());
            AuctionHouseScreen.receiveAHContext(payload);
        });
    }

    public static void handleAHSettings(AHSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: Received settings isAdmin={} saleRate={} depositRate={} durations={}",
                    payload.isAdmin(), payload.saleRate(), payload.depositRate(), payload.durations());
            AuctionHouseScreen.receiveSettings(payload);
        });
    }
}
