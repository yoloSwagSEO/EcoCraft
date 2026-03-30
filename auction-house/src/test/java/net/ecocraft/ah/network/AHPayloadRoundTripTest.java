package net.ecocraft.ah.network;

import net.ecocraft.ah.network.payload.*;
import net.ecocraft.ah.network.payload.AHInstancesPayload.AHInstanceData;
import net.ecocraft.ah.network.payload.BidHistoryResponsePayload.BidEntry;
import net.ecocraft.ah.network.payload.LedgerResponsePayload.LedgerEntry;
import net.ecocraft.ah.network.payload.ListingDetailResponsePayload.ListingEntry;
import net.ecocraft.ah.network.payload.ListingDetailResponsePayload.PriceInfo;
import net.ecocraft.ah.network.payload.ListingsResponsePayload.ListingSummary;
import net.ecocraft.ah.network.payload.MyListingsResponsePayload.MyListingEntry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AHPayloadRoundTripTest {

    @Test
    void ahActionResultPayload() {
        var original = new AHActionResultPayload(true, "Listing created");
        assertEquals(original, PayloadTestHelper.roundTrip(original, AHActionResultPayload.STREAM_CODEC));
    }

    @Test
    void ahContextPayload() {
        var original = new AHContextPayload("ah-001", "Main Auction House");
        assertEquals(original, PayloadTestHelper.roundTrip(original, AHContextPayload.STREAM_CODEC));
    }

    @Test
    void ahNotificationPayload() {
        var original = new AHNotificationPayload("SOLD", "Diamond Sword", "Steve", 1500L, "gold");
        assertEquals(original, PayloadTestHelper.roundTrip(original, AHNotificationPayload.STREAM_CODEC));
    }

    @Test
    void balanceUpdatePayload() {
        var original = new BalanceUpdatePayload(99999L, "G");
        assertEquals(original, PayloadTestHelper.roundTrip(original, BalanceUpdatePayload.STREAM_CODEC));
    }

    @Test
    void bestPriceResponsePayload() {
        var original = new BestPriceResponsePayload("minecraft:diamond", 500L);
        assertEquals(original, PayloadTestHelper.roundTrip(original, BestPriceResponsePayload.STREAM_CODEC));
    }

    @Test
    void buyListingPayload() {
        var original = new BuyListingPayload("ah-001", "listing-123", 5);
        assertEquals(original, PayloadTestHelper.roundTrip(original, BuyListingPayload.STREAM_CODEC));
    }

    @Test
    void cancelListingPayload() {
        var original = new CancelListingPayload("listing-456");
        assertEquals(original, PayloadTestHelper.roundTrip(original, CancelListingPayload.STREAM_CODEC));
    }

    @Test
    void collectParcelsPayload() {
        assertNotNull(PayloadTestHelper.roundTrip(new CollectParcelsPayload(), CollectParcelsPayload.STREAM_CODEC));
    }

    @Test
    void createAHPayload() {
        var original = new CreateAHPayload("New Auction House");
        assertEquals(original, PayloadTestHelper.roundTrip(original, CreateAHPayload.STREAM_CODEC));
    }

    @Test
    void createListingPayload() {
        var original = new CreateListingPayload("ah-001", "BUYOUT", 1000L, 24, 3);
        assertEquals(original, PayloadTestHelper.roundTrip(original, CreateListingPayload.STREAM_CODEC));
    }

    @Test
    void deleteAHPayload() {
        var original = new DeleteAHPayload("ah-002", "RETURN_ITEMS");
        assertEquals(original, PayloadTestHelper.roundTrip(original, DeleteAHPayload.STREAM_CODEC));
    }

    @Test
    void openAHPayload() {
        var original = new OpenAHPayload(42, "ah-001", "Main AH");
        assertEquals(original, PayloadTestHelper.roundTrip(original, OpenAHPayload.STREAM_CODEC));
    }

    @Test
    void npcSkinPayload() {
        var original = new NPCSkinPayload(10, "Notch", "ah-001");
        assertEquals(original, PayloadTestHelper.roundTrip(original, NPCSkinPayload.STREAM_CODEC));
    }

    @Test
    void updateNPCSkinPayload() {
        var original = new UpdateNPCSkinPayload(10, "Jeb", "ah-002");
        assertEquals(original, PayloadTestHelper.roundTrip(original, UpdateNPCSkinPayload.STREAM_CODEC));
    }

    @Test
    void placeBidPayload() {
        var original = new PlaceBidPayload("ah-001", "listing-789", 2500L);
        assertEquals(original, PayloadTestHelper.roundTrip(original, PlaceBidPayload.STREAM_CODEC));
    }

    @Test
    void requestBestPricePayload() {
        var original = new RequestBestPricePayload("ah-001", "fp-abc", "minecraft:diamond");
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestBestPricePayload.STREAM_CODEC));
    }

    @Test
    void requestBidHistoryPayload() {
        var original = new RequestBidHistoryPayload("listing-123");
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestBidHistoryPayload.STREAM_CODEC));
    }

    @Test
    void requestLedgerPayload() {
        var original = new RequestLedgerPayload("7d", "SALE", 1);
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestLedgerPayload.STREAM_CODEC));
    }

    @Test
    void requestListingDetailPayload() {
        var original = new RequestListingDetailPayload("ah-001", "minecraft:diamond_sword", List.of("sharpness", "fire_aspect"));
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestListingDetailPayload.STREAM_CODEC));
    }

    @Test
    void requestListingsPayload() {
        var original = new RequestListingsPayload("ah-001", "diamond", "weapons", 0, 20);
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestListingsPayload.STREAM_CODEC));
    }

    @Test
    void requestMyListingsPayload() {
        var original = new RequestMyListingsPayload("ACTIVE");
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestMyListingsPayload.STREAM_CODEC));
    }

    @Test
    void ahInstancesPayload() {
        var instance = new AHInstanceData("ah-001", "main-ah", "Main Auction House",
                5, 10, List.of(12, 24, 48), true, true, "server_bank", false, 60, 120);
        var original = new AHInstancesPayload(List.of(instance));
        var decoded = PayloadTestHelper.roundTrip(original, AHInstancesPayload.STREAM_CODEC);
        assertEquals(1, decoded.instances().size());
        var inst = decoded.instances().get(0);
        assertEquals("ah-001", inst.id());
        assertEquals("main-ah", inst.slug());
        assertEquals("Main Auction House", inst.name());
        assertEquals(5, inst.saleRate());
        assertEquals(10, inst.depositRate());
        assertEquals(List.of(12, 24, 48), inst.durations());
        assertTrue(inst.allowBuyout());
        assertTrue(inst.allowAuction());
        assertEquals("server_bank", inst.taxRecipient());
        assertFalse(inst.overridePermTax());
        assertEquals(60, inst.deliveryDelayPurchase());
        assertEquals(120, inst.deliveryDelayExpired());
    }

    @Test
    void ahSettingsPayload() {
        var original = new AHSettingsPayload(true, 5, 10, List.of(12, 24, 48), "MAILBOX");
        var decoded = PayloadTestHelper.roundTrip(original, AHSettingsPayload.STREAM_CODEC);
        assertEquals(original.isAdmin(), decoded.isAdmin());
        assertEquals(original.saleRate(), decoded.saleRate());
        assertEquals(original.depositRate(), decoded.depositRate());
        assertEquals(original.durations(), decoded.durations());
        assertEquals(original.deliveryMode(), decoded.deliveryMode());
    }

    @Test
    void bidHistoryResponsePayload() {
        var bid1 = new BidEntry("Steve", 1000L, 1711700000000L);
        var bid2 = new BidEntry("Alex", 1500L, 1711700060000L);
        var original = new BidHistoryResponsePayload("listing-123", List.of(bid1, bid2));
        var decoded = PayloadTestHelper.roundTrip(original, BidHistoryResponsePayload.STREAM_CODEC);
        assertEquals(original.listingId(), decoded.listingId());
        assertEquals(original.bids(), decoded.bids());
    }

    @Test
    void ledgerResponsePayload() {
        var entry = new LedgerEntry("minecraft:diamond", "Diamond", 0x55FFFF,
                "SALE", 500L, "Alex", 1711700000000L, "ah-001", "Main AH", "{}");
        var original = new LedgerResponsePayload(List.of(entry), 300L, 500L, 200L, 50L, 1, 3);
        var decoded = PayloadTestHelper.roundTrip(original, LedgerResponsePayload.STREAM_CODEC);
        assertEquals(1, decoded.entries().size());
        assertEquals("minecraft:diamond", decoded.entries().get(0).itemId());
        assertEquals(300L, decoded.netProfit());
        assertEquals(500L, decoded.totalSales());
        assertEquals(200L, decoded.totalPurchases());
        assertEquals(50L, decoded.taxesPaid());
        assertEquals(1, decoded.page());
        assertEquals(3, decoded.totalPages());
    }

    @Test
    void listingDetailResponsePayload() {
        var listingEntry = new ListingEntry("l-001", "Steve", 10, 100L, "BUYOUT", 86400000L, "{Enchantments:[]}");
        var priceInfo = new PriceInfo(120L, 80L, 200L, 42);
        var bid = new BidEntry("Alex", 150L, 1711700000000L);
        var original = new ListingDetailResponsePayload("minecraft:diamond_sword", "Diamond Sword", 0x55FFFF,
                List.of(listingEntry), priceInfo, List.of("sharpness", "fire_aspect"), List.of(bid));
        var decoded = PayloadTestHelper.roundTrip(original, ListingDetailResponsePayload.STREAM_CODEC);
        assertEquals("minecraft:diamond_sword", decoded.itemId());
        assertEquals("Diamond Sword", decoded.itemName());
        assertEquals(0x55FFFF, decoded.rarityColor());
        assertEquals(1, decoded.entries().size());
        assertEquals(120L, decoded.priceInfo().avgPrice());
        assertEquals(List.of("sharpness", "fire_aspect"), decoded.availableEnchantments());
        assertEquals(1, decoded.recentBids().size());
    }

    @Test
    void listingsResponsePayload() {
        var summary = new ListingSummary("minecraft:diamond", "Diamond", 0x55FFFF, 100L, 5, 320);
        var original = new ListingsResponsePayload(List.of(summary), 1, 10);
        assertEquals(original, PayloadTestHelper.roundTrip(original, ListingsResponsePayload.STREAM_CODEC));
    }

    @Test
    void myListingsResponsePayload() {
        var entry = new MyListingEntry("l-001", "minecraft:diamond_sword", "Diamond Sword", 0x55FFFF,
                1000L, "BUYOUT", "ACTIVE", 86400000L, 3, false, "{}", "ah-001", "Main AH");
        var original = new MyListingsResponsePayload(List.of(entry), 5000L, 250L, 2, "MAILBOX");
        var decoded = PayloadTestHelper.roundTrip(original, MyListingsResponsePayload.STREAM_CODEC);
        assertEquals(1, decoded.entries().size());
        assertEquals("l-001", decoded.entries().get(0).listingId());
        assertEquals(5000L, decoded.revenue7d());
        assertEquals(250L, decoded.taxesPaid7d());
        assertEquals(2, decoded.parcelsToCollect());
        assertEquals("MAILBOX", decoded.deliveryMode());
    }

    @Test
    void updateAHInstancePayload() {
        var original = new UpdateAHInstancePayload("ah-001", "Updated AH", 3, 5, List.of(6, 12, 24),
                true, false, "treasury", true, 30, 90);
        var decoded = PayloadTestHelper.roundTrip(original, UpdateAHInstancePayload.STREAM_CODEC);
        assertEquals("ah-001", decoded.ahId());
        assertEquals("Updated AH", decoded.name());
        assertEquals(List.of(6, 12, 24), decoded.durations());
        assertTrue(decoded.allowBuyout());
        assertFalse(decoded.allowAuction());
        assertEquals("treasury", decoded.taxRecipient());
        assertTrue(decoded.overridePermTax());
        assertEquals(30, decoded.deliveryDelayPurchase());
        assertEquals(90, decoded.deliveryDelayExpired());
    }

    @Test
    void updateAHSettingsPayload() {
        var original = new UpdateAHSettingsPayload(7, 15, List.of(12, 48), "DIRECT");
        var decoded = PayloadTestHelper.roundTrip(original, UpdateAHSettingsPayload.STREAM_CODEC);
        assertEquals(original.saleRate(), decoded.saleRate());
        assertEquals(original.depositRate(), decoded.depositRate());
        assertEquals(original.durations(), decoded.durations());
        assertEquals(original.deliveryMode(), decoded.deliveryMode());
    }
}
