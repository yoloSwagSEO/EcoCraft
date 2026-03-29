package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class BidPlacingEvent implements KubeEvent {
    private final ServerPlayer bidder;
    private final String listingId;
    private final long amount;
    private boolean cancelled = false;
    private String message = "Enchère bloquée par un script";

    public BidPlacingEvent(ServerPlayer bidder, String listingId, long amount) {
        this.bidder = bidder;
        this.listingId = listingId;
        this.amount = amount;
    }

    public ServerPlayer getBidder() { return bidder; }
    public String getListingId() { return listingId; }
    public long getAmount() { return amount; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
