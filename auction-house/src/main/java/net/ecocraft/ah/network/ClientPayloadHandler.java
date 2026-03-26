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
            LOGGER.debug("AH: Received OpenAH");
            AuctionHouseScreen.open();
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
}
