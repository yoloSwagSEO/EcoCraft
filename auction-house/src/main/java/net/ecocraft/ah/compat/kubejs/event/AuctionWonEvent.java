package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class AuctionWonEvent implements KubeEvent {
    private final String winnerName;
    private final String sellerName;
    private final String listingId;
    private final String itemId;
    private final String itemName;
    private final long finalPrice;

    public AuctionWonEvent(String winnerName, String sellerName, String listingId,
                            String itemId, String itemName, long finalPrice) {
        this.winnerName = winnerName;
        this.sellerName = sellerName;
        this.listingId = listingId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.finalPrice = finalPrice;
    }

    public String getWinnerName() { return winnerName; }
    public String getSellerName() { return sellerName; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public long getFinalPrice() { return finalPrice; }
}
