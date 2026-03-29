package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class ListingCreatingEvent implements KubeEvent {
    private final ServerPlayer seller;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long price;
    private final String listingType;
    private final String ahId;
    private boolean cancelled = false;
    private String message = "Mise en vente bloquée par un script";

    public ListingCreatingEvent(ServerPlayer seller, String itemId, String itemName,
                                 int quantity, long price, String listingType, String ahId) {
        this.seller = seller;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.price = price;
        this.listingType = listingType;
        this.ahId = ahId;
    }

    public ServerPlayer getSeller() { return seller; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getPrice() { return price; }
    public String getListingType() { return listingType; }
    public String getAhId() { return ahId; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
