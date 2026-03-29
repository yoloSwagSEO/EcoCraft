package net.ecocraft.ah.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

public class ListingCancelledEvent implements KubeEvent {
    private final String playerName;
    private final String listingId;
    private final String itemId;
    private final String itemName;

    public ListingCancelledEvent(String playerName, String listingId, String itemId, String itemName) {
        this.playerName = playerName;
        this.listingId = listingId;
        this.itemId = itemId;
        this.itemName = itemName;
    }

    public String getPlayerName() { return playerName; }
    public String getListingId() { return listingId; }
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
}
