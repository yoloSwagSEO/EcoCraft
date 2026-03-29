package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class ListingCancellingEvent implements KubeEvent {
    private final ServerPlayer player;
    private final String listingId;
    private boolean cancelled = false;
    private String message = "Annulation bloquée par un script";

    public ListingCancellingEvent(ServerPlayer player, String listingId) {
        this.player = player;
        this.listingId = listingId;
    }

    public ServerPlayer getPlayer() { return player; }
    public String getListingId() { return listingId; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
