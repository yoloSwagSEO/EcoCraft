package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class SoldEvent implements KubeEvent {
    private final ServerPlayer buyer;
    private final String sellerName;
    private final String listingId;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long totalPrice;
    private final long tax;

    public SoldEvent(ServerPlayer buyer, String sellerName, String listingId, String itemId,
                      String itemName, int quantity, long totalPrice, long tax) {
        this.buyer = buyer;
        this.sellerName = sellerName;
        this.listingId = listingId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
        this.tax = tax;
    }

    public ServerPlayer getBuyer() { return buyer; }
    public String getSellerName() { return sellerName; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getTotalPrice() { return totalPrice; }
    public long getTax() { return tax; }
}
