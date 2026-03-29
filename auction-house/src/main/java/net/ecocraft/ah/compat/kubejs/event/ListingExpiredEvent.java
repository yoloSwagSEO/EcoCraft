package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class ListingExpiredEvent implements KubeEvent {
    private final String listingId;
    private final String sellerName;
    private final String itemId;
    private final String itemName;
    private final boolean hadBids;

    public ListingExpiredEvent(String listingId, String sellerName, String itemId,
                                String itemName, boolean hadBids) {
        this.listingId = listingId;
        this.sellerName = sellerName;
        this.itemId = itemId;
        this.itemName = itemName;
        this.hadBids = hadBids;
    }

    public String getListingId() { return listingId; }
    public String getSellerName() { return sellerName; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public boolean hadBids() { return hadBids; }
}
