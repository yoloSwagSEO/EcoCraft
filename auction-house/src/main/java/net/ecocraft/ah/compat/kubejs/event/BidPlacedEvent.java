package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class BidPlacedEvent implements KubeEvent {
    private final ServerPlayer bidder;
    private final String listingId;
    private final long amount;
    private final long previousBid;
    private final @Nullable String previousBidderName;

    public BidPlacedEvent(ServerPlayer bidder, String listingId, long amount,
                           long previousBid, @Nullable String previousBidderName) {
        this.bidder = bidder;
        this.listingId = listingId;
        this.amount = amount;
        this.previousBid = previousBid;
        this.previousBidderName = previousBidderName;
    }

    public ServerPlayer getBidder() { return bidder; }
    public String getListingId() { return listingId; }
    public long getAmount() { return amount; }
    public long getPreviousBid() { return previousBid; }
    public @Nullable String getPreviousBidderName() { return previousBidderName; }
}
