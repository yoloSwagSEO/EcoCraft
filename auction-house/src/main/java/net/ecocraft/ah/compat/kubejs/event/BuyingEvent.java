package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class BuyingEvent implements KubeEvent {
    private final ServerPlayer buyer;
    private final String listingId;
    private final String itemId;
    private final String itemName;
    private final int quantity;
    private final long totalPrice;
    private boolean cancelled = false;
    private String message = "Achat bloqué par un script";

    public BuyingEvent(ServerPlayer buyer, String listingId, String itemId, String itemName,
                        int quantity, long totalPrice) {
        this.buyer = buyer;
        this.listingId = listingId;
        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.totalPrice = totalPrice;
    }

    public ServerPlayer getBuyer() { return buyer; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public long getTotalPrice() { return totalPrice; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
