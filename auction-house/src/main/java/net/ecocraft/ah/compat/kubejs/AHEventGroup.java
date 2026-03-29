package net.ecocraft.ah.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import net.ecocraft.ah.compat.kubejs.event.*;

public interface AHEventGroup {
    EventGroup GROUP = EventGroup.of("EcocraftAHEvents");

    EventHandler LISTING_CREATING = GROUP.server("listingCreating", () -> ListingCreatingEvent.class);
    EventHandler LISTING_CREATED = GROUP.server("listingCreated", () -> ListingCreatedEvent.class);
    EventHandler BUYING = GROUP.server("buying", () -> BuyingEvent.class);
    EventHandler SOLD = GROUP.server("sold", () -> SoldEvent.class);
    EventHandler BID_PLACING = GROUP.server("bidPlacing", () -> BidPlacingEvent.class);
    EventHandler BID_PLACED = GROUP.server("bidPlaced", () -> BidPlacedEvent.class);
    EventHandler AUCTION_WON = GROUP.server("auctionWon", () -> AuctionWonEvent.class);
    EventHandler AUCTION_LOST = GROUP.server("auctionLost", () -> AuctionLostEvent.class);
    EventHandler LISTING_CANCELLING = GROUP.server("listingCancelling", () -> ListingCancellingEvent.class);
    EventHandler LISTING_CANCELLED = GROUP.server("listingCancelled", () -> ListingCancelledEvent.class);
    EventHandler LISTING_EXPIRED = GROUP.server("listingExpired", () -> ListingExpiredEvent.class);
}
