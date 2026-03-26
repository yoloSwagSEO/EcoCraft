package net.ecocraft.ah.network;

import com.mojang.logging.LogUtils;
import net.ecocraft.ah.network.payload.*;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.List;

/**
 * Handles client-to-server packets for the auction house.
 * Stub implementations log and send empty responses; real logic is wired in Task 6.
 */
public final class ServerPayloadHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ServerPayloadHandler() {}

    public static void handleRequestListings(RequestListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: RequestListings search='{}' category='{}' page={}",
                    payload.search(), payload.category(), payload.page());
            context.reply(new ListingsResponsePayload(List.of(), payload.page(), 0));
        });
    }

    public static void handleRequestListingDetail(RequestListingDetailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: RequestListingDetail itemId='{}'", payload.itemId());
            context.reply(new ListingDetailResponsePayload(
                    payload.itemId(), "", 0xFFFFFFFF, List.of(),
                    new ListingDetailResponsePayload.PriceInfo(0, 0, 0, 0)
            ));
        });
    }

    public static void handleCreateListing(CreateListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: CreateListing type='{}' price={} duration={}h",
                    payload.listingType(), payload.price(), payload.durationHours());
            context.reply(new AHActionResultPayload(false, "Not yet implemented"));
        });
    }

    public static void handleBuyListing(BuyListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: BuyListing listingId='{}'", payload.listingId());
            context.reply(new AHActionResultPayload(false, "Not yet implemented"));
        });
    }

    public static void handlePlaceBid(PlaceBidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: PlaceBid listingId='{}' amount={}", payload.listingId(), payload.amount());
            context.reply(new AHActionResultPayload(false, "Not yet implemented"));
        });
    }

    public static void handleCancelListing(CancelListingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: CancelListing listingId='{}'", payload.listingId());
            context.reply(new AHActionResultPayload(false, "Not yet implemented"));
        });
    }

    public static void handleCollectParcels(CollectParcelsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: CollectParcels");
            context.reply(new AHActionResultPayload(false, "Not yet implemented"));
        });
    }

    public static void handleRequestMyListings(RequestMyListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: RequestMyListings subTab='{}'", payload.subTab());
            context.reply(new MyListingsResponsePayload(List.of(), 0, 0, 0));
        });
    }

    public static void handleRequestLedger(RequestLedgerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.debug("AH: RequestLedger period='{}' typeFilter='{}' page={}",
                    payload.period(), payload.typeFilter(), payload.page());
            context.reply(new LedgerResponsePayload(List.of(), 0, 0, 0, 0, payload.page(), 0));
        });
    }
}
