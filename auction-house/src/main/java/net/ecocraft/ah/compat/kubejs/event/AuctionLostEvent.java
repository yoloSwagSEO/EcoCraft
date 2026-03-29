package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class AuctionLostEvent implements KubeEvent {
    private final String loserName;
    private final String listingId;
    private final long refundAmount;

    public AuctionLostEvent(String loserName, String listingId, long refundAmount) {
        this.loserName = loserName;
        this.listingId = listingId;
        this.refundAmount = refundAmount;
    }

    public String getLoserName() { return loserName; }
    public String getListingId() { return listingId; }
    public long getRefundAmount() { return refundAmount; }
}
