package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class ListingCreatedEvent implements KubeEvent {
    private final String listingId;
    private final String sellerName;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long price;
    private final String listingType;
    private final String ahId;

    public ListingCreatedEvent(String listingId, String sellerName, String itemId, String itemName,
                                int quantity, long price, String listingType, String ahId) {
        this.listingId = listingId;
        this.sellerName = sellerName;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.listingType = listingType;
        this.ahId = ahId;
    }

    public String getListingId() { return listingId; }
    public String getSellerName() { return sellerName; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getPrice() { return price; }
    public String getListingType() { return listingType; }
    public String getAhId() { return ahId; }
}
