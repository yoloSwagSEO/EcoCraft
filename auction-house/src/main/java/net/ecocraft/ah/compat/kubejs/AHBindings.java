package net.ecocraft.ah.compat.kubejs;

import net.ecocraft.ah.AHServerEvents;
import net.ecocraft.ah.data.AuctionListing;
import net.ecocraft.ah.service.AuctionService;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AHBindings {

    private static AuctionService getService() {
        return AHServerEvents.getAuctionService();
    }

    public static List<?> getListings(String ahId) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        return service.searchListings(ahId, "", null, 0, 100);
    }

    public static List<AuctionListing> getListingsByItem(String ahId, String itemId) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        return service.getListingDetail(ahId, itemId);
    }

    public static Map<String, Object> getPlayerStats(ServerPlayer player) {
        var service = getService();
        if (service == null) return Collections.emptyMap();
        var stats = service.getPlayerStats(player.getUUID(), 0L);
        Map<String, Object> result = new HashMap<>();
        result.put("totalSalesRevenue", stats.totalSalesRevenue());
        result.put("totalPurchases", stats.totalPurchases());
        result.put("taxesPaid", stats.taxesPaid());
        return result;
    }

    public static List<AuctionListing> getPlayerListings(ServerPlayer player) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        return service.getMyListings(player.getUUID(), 0, 100);
    }

    public static long getBestPrice(String itemId) {
        var service = getService();
        if (service == null) return 0;
        return service.getBestPrice(null, null, itemId);
    }

    public static boolean cancelListing(String listingId) {
        var service = getService();
        if (service == null) return false;
        try {
            // Admin cancel — use system UUID
            service.cancelListing(new java.util.UUID(0, 0), listingId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
