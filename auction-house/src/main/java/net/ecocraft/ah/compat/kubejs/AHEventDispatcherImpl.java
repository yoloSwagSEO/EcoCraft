package net.ecocraft.ah.compat.kubejs;

import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.data.ListingType;
import net.ecocraft.ah.service.AuctionService;
import net.ecocraft.ah.compat.kubejs.event.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AHEventDispatcherImpl implements AuctionService.AHEventDispatcher {

    private final MinecraftServer server;

    public AHEventDispatcherImpl(MinecraftServer server) {
        this.server = server;
    }

    private @Nullable ServerPlayer getPlayer(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean fireListingCreating(UUID seller, String itemId, String itemName,
                                        int qty, long price, ListingType type, String ahId) {
        if (!AHEventGroup.LISTING_CREATING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(seller);
        if (sp == null) return true;
        var event = new ListingCreatingEvent(sp, itemId, itemName, qty, price, type.name(), ahId);
        AHEventGroup.LISTING_CREATING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireListingCreated(AuctionListing listing) {
        if (!AHEventGroup.LISTING_CREATED.hasListeners()) return;
        AHEventGroup.LISTING_CREATED.post(new ListingCreatedEvent(
                listing.id(), listing.sellerName(), listing.itemId(), listing.itemName(),
                listing.quantity(), listing.buyoutPrice() > 0 ? listing.buyoutPrice() : listing.startingBid(),
                listing.listingType().name(), listing.ahId()));
    }

    @Override
    public boolean fireBuying(UUID buyer, AuctionListing listing, int qty, long totalPrice) {
        if (!AHEventGroup.BUYING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(buyer);
        if (sp == null) return true;
        var event = new BuyingEvent(sp, listing.id(), listing.itemId(), listing.itemName(), qty, totalPrice);
        AHEventGroup.BUYING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireSold(UUID buyer, String buyerName, AuctionListing listing,
                          int qty, long totalPrice, long tax) {
        if (!AHEventGroup.SOLD.hasListeners()) return;
        ServerPlayer sp = getPlayer(buyer);
        if (sp == null) return;
        AHEventGroup.SOLD.post(new SoldEvent(sp, listing.sellerName(), listing.id(),
                listing.itemId(), listing.itemName(), qty, totalPrice, tax));
    }

    @Override
    public boolean fireBidPlacing(UUID bidder, AuctionListing listing, long amount) {
        if (!AHEventGroup.BID_PLACING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(bidder);
        if (sp == null) return true;
        var event = new BidPlacingEvent(sp, listing.id(), amount);
        AHEventGroup.BID_PLACING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireBidPlaced(UUID bidder, String bidderName, AuctionListing listing,
                               long amount, long prevBid, @Nullable UUID prevBidder) {
        if (!AHEventGroup.BID_PLACED.hasListeners()) return;
        ServerPlayer sp = getPlayer(bidder);
        if (sp == null) return;
        // Resolve previous bidder name if available
        String prevName = null;
        if (prevBidder != null) {
            ServerPlayer prevPlayer = getPlayer(prevBidder);
            prevName = prevPlayer != null ? prevPlayer.getName().getString() : null;
        }
        AHEventGroup.BID_PLACED.post(new BidPlacedEvent(sp, listing.id(), amount, prevBid, prevName));
    }

    @Override
    public void fireAuctionWon(UUID winner, String winnerName, AuctionListing listing, long finalPrice) {
        if (!AHEventGroup.AUCTION_WON.hasListeners()) return;
        AHEventGroup.AUCTION_WON.post(new AuctionWonEvent(winnerName, listing.sellerName(),
                listing.id(), listing.itemId(), listing.itemName(), finalPrice));
    }

    @Override
    public void fireAuctionLost(UUID loser, String loserName, AuctionListing listing, long refund) {
        if (!AHEventGroup.AUCTION_LOST.hasListeners()) return;
        AHEventGroup.AUCTION_LOST.post(new AuctionLostEvent(loserName, listing.id(), refund));
    }

    @Override
    public boolean fireListingCancelling(UUID player, AuctionListing listing) {
        if (!AHEventGroup.LISTING_CANCELLING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return true;
        var event = new ListingCancellingEvent(sp, listing.id());
        AHEventGroup.LISTING_CANCELLING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireListingCancelled(UUID player, AuctionListing listing) {
        if (!AHEventGroup.LISTING_CANCELLED.hasListeners()) return;
        ServerPlayer sp = getPlayer(player);
        String name = sp != null ? sp.getName().getString() : "Unknown";
        AHEventGroup.LISTING_CANCELLED.post(new ListingCancelledEvent(name, listing.id(),
                listing.itemId(), listing.itemName()));
    }

    @Override
    public void fireListingExpired(AuctionListing listing, boolean hadBids) {
        if (!AHEventGroup.LISTING_EXPIRED.hasListeners()) return;
        AHEventGroup.LISTING_EXPIRED.post(new ListingExpiredEvent(listing.id(), listing.sellerName(),
                listing.itemId(), listing.itemName(), hadBids));
    }
}
