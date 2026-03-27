package net.ecocraft.ah.storage;

import net.ecocraft.ah.data.*;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuctionStorageProviderTest {

    private AuctionStorageProvider db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ah-test");
        db = new AuctionStorageProvider(tempDir.resolve("ah-test.db"));
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuctionListing makeListing(String id, UUID seller, String itemId,
                                        ListingType type, long price, long expiresAt) {
        return new AuctionListing(
                id, seller, "SellerName", itemId, "Item Name", null,
                1, type, type == ListingType.BUYOUT ? price : 0L, type == ListingType.AUCTION ? price : 0L,
                0L, null, "gold", ItemCategory.MISC,
                expiresAt, ListingStatus.ACTIVE, 10L, System.currentTimeMillis(), null
        );
    }

    private AuctionBid makeBid(String id, String listingId, UUID bidder, long amount) {
        return new AuctionBid(id, listingId, bidder, "BidderName", amount, System.currentTimeMillis());
    }

    private AuctionParcel makeItemParcel(String id, UUID recipient, String itemId) {
        return new AuctionParcel(id, recipient, itemId, "Item Name", null,
                1, 0L, null, ParcelSource.HDV_PURCHASE, System.currentTimeMillis(), false);
    }

    private AuctionParcel makeCurrencyParcel(String id, UUID recipient, long amount) {
        return new AuctionParcel(id, recipient, null, null, null,
                0, amount, "gold", ParcelSource.HDV_SALE, System.currentTimeMillis(), false);
    }

    // -------------------------------------------------------------------------
    // Listing CRUD
    // -------------------------------------------------------------------------

    @Test
    void createAndRetrieveListing() {
        UUID seller = UUID.randomUUID();
        String id = UUID.randomUUID().toString();
        long expires = System.currentTimeMillis() + 86_400_000L;

        AuctionListing listing = makeListing(id, seller, "minecraft:diamond_sword", ListingType.BUYOUT, 1000L, expires);
        db.createListing(listing);

        AuctionListing retrieved = db.getListingById(id);
        assertNotNull(retrieved);
        assertEquals(id, retrieved.id());
        assertEquals(seller, retrieved.sellerUuid());
        assertEquals("minecraft:diamond_sword", retrieved.itemId());
        assertEquals(1000L, retrieved.buyoutPrice());
        assertEquals(ListingStatus.ACTIVE, retrieved.status());
    }

    @Test
    void getListingByIdReturnsNullForUnknownId() {
        assertNull(db.getListingById("does-not-exist"));
    }

    @Test
    void updateListingStatus() {
        UUID seller = UUID.randomUUID();
        String id = UUID.randomUUID().toString();
        db.createListing(makeListing(id, seller, "minecraft:diamond", ListingType.BUYOUT, 500L,
                System.currentTimeMillis() + 86_400_000L));

        db.updateListingStatus(id, ListingStatus.SOLD);

        assertEquals(ListingStatus.SOLD, db.getListingById(id).status());
    }

    @Test
    void completeSaleMarksSold() {
        String id = UUID.randomUUID().toString();
        db.createListing(makeListing(id, UUID.randomUUID(), "minecraft:iron_ingot",
                ListingType.BUYOUT, 100L, System.currentTimeMillis() + 3600_000L));

        db.completeSale(id);
        assertEquals(ListingStatus.SOLD, db.getListingById(id).status());
    }

    @Test
    void cancelListingMarksCancelled() {
        String id = UUID.randomUUID().toString();
        db.createListing(makeListing(id, UUID.randomUUID(), "minecraft:gold_ingot",
                ListingType.BUYOUT, 200L, System.currentTimeMillis() + 3600_000L));

        db.cancelListing(id);
        assertEquals(ListingStatus.CANCELLED, db.getListingById(id).status());
    }

    // -------------------------------------------------------------------------
    // Active listing search / filter
    // -------------------------------------------------------------------------

    @Test
    void getActiveListingsReturnOnlyActive() {
        UUID seller = UUID.randomUUID();
        long exp = System.currentTimeMillis() + 86_400_000L;

        db.createListing(makeListing(UUID.randomUUID().toString(), seller, "minecraft:apple", ListingType.BUYOUT, 10L, exp));
        db.createListing(makeListing(UUID.randomUUID().toString(), seller, "minecraft:bread", ListingType.BUYOUT, 20L, exp));
        String soldId = UUID.randomUUID().toString();
        db.createListing(makeListing(soldId, seller, "minecraft:cake", ListingType.BUYOUT, 30L, exp));
        db.completeSale(soldId);

        List<AuctionListing> active = db.getActiveListings(null, null, 0, 10);
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(l -> l.status() == ListingStatus.ACTIVE));
    }

    @Test
    void getActiveListingsFiltersOnCategory() {
        long exp = System.currentTimeMillis() + 86_400_000L;
        UUID seller = UUID.randomUUID();

        AuctionListing swordListing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond_sword", "Diamond Sword", null,
                1, ListingType.BUYOUT, 500L, 0L, 0L, null, "gold",
                ItemCategory.WEAPONS, exp, ListingStatus.ACTIVE, 10L, System.currentTimeMillis(), null);
        db.createListing(swordListing);

        AuctionListing breadListing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:bread", "Bread", null,
                1, ListingType.BUYOUT, 5L, 0L, 0L, null, "gold",
                ItemCategory.FOOD, exp, ListingStatus.ACTIVE, 0L, System.currentTimeMillis(), null);
        db.createListing(breadListing);

        List<AuctionListing> weapons = db.getActiveListings(null, ItemCategory.WEAPONS, 0, 10);
        assertEquals(1, weapons.size());
        assertEquals("minecraft:diamond_sword", weapons.get(0).itemId());
    }

    @Test
    void getActiveListingsFiltersOnSearchText() {
        long exp = System.currentTimeMillis() + 86_400_000L;
        UUID seller = UUID.randomUUID();

        db.createListing(new AuctionListing(
                UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond_sword", "Diamond Sword", null,
                1, ListingType.BUYOUT, 500L, 0L, 0L, null, "gold",
                ItemCategory.WEAPONS, exp, ListingStatus.ACTIVE, 10L, System.currentTimeMillis(), null));
        db.createListing(new AuctionListing(
                UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:iron_pickaxe", "Iron Pickaxe", null,
                1, ListingType.BUYOUT, 100L, 0L, 0L, null, "gold",
                ItemCategory.TOOLS, exp, ListingStatus.ACTIVE, 2L, System.currentTimeMillis(), null));

        List<AuctionListing> results = db.getActiveListings("diamond", null, 0, 10);
        assertEquals(1, results.size());
        assertEquals("minecraft:diamond_sword", results.get(0).itemId());
    }

    @Test
    void getActiveListingsPagination() {
        long exp = System.currentTimeMillis() + 86_400_000L;
        UUID seller = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            db.createListing(makeListing(UUID.randomUUID().toString(), seller,
                    "minecraft:item_" + i, ListingType.BUYOUT, (i + 1) * 100L, exp));
        }

        List<AuctionListing> page0 = db.getActiveListings(null, null, 0, 3);
        List<AuctionListing> page1 = db.getActiveListings(null, null, 1, 3);

        assertEquals(3, page0.size());
        assertEquals(2, page1.size());
        long total = db.countActiveListings(null, null);
        assertEquals(5, total);
    }

    // -------------------------------------------------------------------------
    // Grouped browse view
    // -------------------------------------------------------------------------

    @Test
    void getListingsGroupedByItemAggregatesCorrectly() {
        long exp = System.currentTimeMillis() + 86_400_000L;
        UUID seller = UUID.randomUUID();

        // Three listings for the same item at different prices
        db.createListing(new AuctionListing(UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond", "Diamond", null, 1, ListingType.BUYOUT, 300L, 0L, 0L, null,
                "gold", ItemCategory.MISC, exp, ListingStatus.ACTIVE, 5L, System.currentTimeMillis(), null));
        db.createListing(new AuctionListing(UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond", "Diamond", null, 5, ListingType.BUYOUT, 100L, 0L, 0L, null,
                "gold", ItemCategory.MISC, exp, ListingStatus.ACTIVE, 1L, System.currentTimeMillis(), null));
        db.createListing(new AuctionListing(UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond", "Diamond", null, 2, ListingType.BUYOUT, 200L, 0L, 0L, null,
                "gold", ItemCategory.MISC, exp, ListingStatus.ACTIVE, 3L, System.currentTimeMillis(), null));

        List<AuctionStorageProvider.ListingGroupSummary> groups = db.getListingsGroupedByItem(null, null, 0, 10);
        assertEquals(1, groups.size());

        AuctionStorageProvider.ListingGroupSummary g = groups.get(0);
        assertEquals("minecraft:diamond", g.itemId());
        assertEquals(100L, g.bestPrice());
        assertEquals(3, g.listingCount());
        assertEquals(8, g.totalQuantity());
    }

    // -------------------------------------------------------------------------
    // Player listings
    // -------------------------------------------------------------------------

    @Test
    void getPlayerListingsReturnsSellersListings() {
        UUID seller = UUID.randomUUID();
        UUID otherSeller = UUID.randomUUID();
        long exp = System.currentTimeMillis() + 86_400_000L;

        db.createListing(makeListing(UUID.randomUUID().toString(), seller, "minecraft:a", ListingType.BUYOUT, 10L, exp));
        db.createListing(makeListing(UUID.randomUUID().toString(), seller, "minecraft:b", ListingType.BUYOUT, 20L, exp));
        db.createListing(makeListing(UUID.randomUUID().toString(), otherSeller, "minecraft:c", ListingType.BUYOUT, 30L, exp));

        List<AuctionListing> mine = db.getPlayerListings(seller, 0, 10);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(l -> l.sellerUuid().equals(seller)));
    }

    // -------------------------------------------------------------------------
    // Bids
    // -------------------------------------------------------------------------

    @Test
    void placeBidAndGetHighestBid() {
        String listingId = UUID.randomUUID().toString();
        db.createListing(makeListing(listingId, UUID.randomUUID(), "minecraft:bow",
                ListingType.AUCTION, 100L, System.currentTimeMillis() + 86_400_000L));

        UUID bidder1 = UUID.randomUUID();
        UUID bidder2 = UUID.randomUUID();

        db.placeBid(makeBid(UUID.randomUUID().toString(), listingId, bidder1, 150L));
        db.placeBid(makeBid(UUID.randomUUID().toString(), listingId, bidder2, 200L));

        AuctionBid highest = db.getHighestBid(listingId);
        assertNotNull(highest);
        assertEquals(200L, highest.amount());
        assertEquals(bidder2, highest.bidderUuid());
    }

    @Test
    void getHighestBidReturnsNullWhenNoBids() {
        String listingId = UUID.randomUUID().toString();
        db.createListing(makeListing(listingId, UUID.randomUUID(), "minecraft:crossbow",
                ListingType.AUCTION, 100L, System.currentTimeMillis() + 86_400_000L));

        assertNull(db.getHighestBid(listingId));
    }

    @Test
    void updateListingBidPersists() {
        String listingId = UUID.randomUUID().toString();
        db.createListing(makeListing(listingId, UUID.randomUUID(), "minecraft:trident",
                ListingType.AUCTION, 100L, System.currentTimeMillis() + 86_400_000L));

        UUID bidder = UUID.randomUUID();
        db.updateListingBid(listingId, 250L, bidder);

        AuctionListing updated = db.getListingById(listingId);
        assertNotNull(updated);
        assertEquals(250L, updated.currentBid());
        assertEquals(bidder, updated.currentBidderUuid());
    }

    // -------------------------------------------------------------------------
    // Parcels
    // -------------------------------------------------------------------------

    @Test
    void createAndRetrieveItemParcel() {
        UUID recipient = UUID.randomUUID();
        String parcelId = UUID.randomUUID().toString();

        db.createParcel(makeItemParcel(parcelId, recipient, "minecraft:diamond"));

        List<AuctionParcel> parcels = db.getUncollectedParcels(recipient);
        assertEquals(1, parcels.size());
        assertEquals(parcelId, parcels.get(0).id());
        assertEquals("minecraft:diamond", parcels.get(0).itemId());
        assertFalse(parcels.get(0).collected());
    }

    @Test
    void markParcelCollectedHidesFromUncollected() {
        UUID recipient = UUID.randomUUID();
        String parcelId = UUID.randomUUID().toString();
        db.createParcel(makeItemParcel(parcelId, recipient, "minecraft:emerald"));

        assertEquals(1, db.getUncollectedParcels(recipient).size());
        db.markParcelCollected(parcelId);
        assertEquals(0, db.getUncollectedParcels(recipient).size());
    }

    @Test
    void countUncollectedParcels() {
        UUID recipient = UUID.randomUUID();
        db.createParcel(makeItemParcel(UUID.randomUUID().toString(), recipient, "minecraft:a"));
        db.createParcel(makeItemParcel(UUID.randomUUID().toString(), recipient, "minecraft:b"));
        db.createParcel(makeCurrencyParcel(UUID.randomUUID().toString(), recipient, 500L));

        assertEquals(3, db.countUncollectedParcels(recipient));

        db.markParcelCollected(db.getUncollectedParcels(recipient).get(0).id());
        assertEquals(2, db.countUncollectedParcels(recipient));
    }

    @Test
    void currencyParcelHasCurrencyFlag() {
        UUID recipient = UUID.randomUUID();
        db.createParcel(makeCurrencyParcel(UUID.randomUUID().toString(), recipient, 1000L));

        AuctionParcel parcel = db.getUncollectedParcels(recipient).get(0);
        assertTrue(parcel.hasCurrency());
        assertFalse(parcel.hasItem());
        assertEquals(1000L, parcel.amount());
        assertEquals("gold", parcel.currencyId());
    }

    // -------------------------------------------------------------------------
    // Expiration
    // -------------------------------------------------------------------------

    @Test
    void expireOldListingsOnlyExpiresPastExpiry() {
        long now = System.currentTimeMillis();
        String expiredId = UUID.randomUUID().toString();
        String activeId = UUID.randomUUID().toString();

        db.createListing(makeListing(expiredId, UUID.randomUUID(), "minecraft:x",
                ListingType.BUYOUT, 100L, now - 1000L));  // already expired
        db.createListing(makeListing(activeId, UUID.randomUUID(), "minecraft:y",
                ListingType.BUYOUT, 100L, now + 86_400_000L));  // still active

        List<String> expired = db.expireOldListings(now);
        assertEquals(1, expired.size());
        assertEquals(expiredId, expired.get(0));

        assertEquals(ListingStatus.EXPIRED, db.getListingById(expiredId).status());
        assertEquals(ListingStatus.ACTIVE, db.getListingById(activeId).status());
    }

    @Test
    void expireOldListingsSkipsNonActive() {
        long now = System.currentTimeMillis();
        String soldId = UUID.randomUUID().toString();
        db.createListing(makeListing(soldId, UUID.randomUUID(), "minecraft:sold",
                ListingType.BUYOUT, 100L, now - 1000L));
        db.completeSale(soldId);

        List<String> expired = db.expireOldListings(now);
        assertEquals(0, expired.size());
        // Status should remain SOLD, not be changed to EXPIRED
        assertEquals(ListingStatus.SOLD, db.getListingById(soldId).status());
    }

    // -------------------------------------------------------------------------
    // Price history
    // -------------------------------------------------------------------------

    @Test
    void logAndQueryPriceHistory() {
        long now = System.currentTimeMillis();
        db.logPriceHistory(UUID.randomUUID().toString(), "minecraft:diamond", "gold", 300L, 1, now);
        db.logPriceHistory(UUID.randomUUID().toString(), "minecraft:diamond", "gold", 100L, 2, now);
        db.logPriceHistory(UUID.randomUUID().toString(), "minecraft:diamond", "gold", 200L, 1, now);

        AuctionStorageProvider.PriceStats stats = db.getPriceHistory("minecraft:diamond", "gold", 7 * 86_400_000L);
        assertNotNull(stats);
        assertEquals(100L, stats.minPrice());
        assertEquals(300L, stats.maxPrice());
        // avg of 300, 100, 200 = 200
        assertEquals(200L, stats.avgPrice());
        assertEquals(4, stats.volume()); // sum of quantities: 1+2+1
    }

    @Test
    void getPriceHistoryReturnsNullWhenNoData() {
        assertNull(db.getPriceHistory("minecraft:unknown_item", "gold", 86_400_000L));
    }

    // -------------------------------------------------------------------------
    // Player stats
    // -------------------------------------------------------------------------

    @Test
    void getPlayerStatsCountsSalesAndTaxes() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Create a sold listing for seller with tax 50
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond", "Diamond", null, 1,
                ListingType.BUYOUT, 1000L, 0L, 1000L, buyer, "gold",
                ItemCategory.MISC, now + 3600_000L, ListingStatus.SOLD, 50L, now - 1000L, null);
        db.createListing(listing);
        db.completeSale(listing.id());

        // Create a sale parcel for the seller
        db.createParcel(new AuctionParcel(UUID.randomUUID().toString(), seller,
                null, null, null, 0, 950L, "gold",
                ParcelSource.HDV_SALE, now, false));

        AuctionStorageProvider.PlayerStats stats = db.getPlayerStats(seller, now - 10_000L);
        assertEquals(950L, stats.totalSalesRevenue());
        assertEquals(50L, stats.taxesPaid());
    }

    @Test
    void getPlayerStatsIncludesDepositFeesInTaxes() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        long now = System.currentTimeMillis();

        // Create a sold listing for seller with tax 50
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "SellerName",
                "minecraft:diamond", "Diamond", null, 1,
                ListingType.BUYOUT, 1000L, 0L, 1000L, buyer, "gold",
                ItemCategory.MISC, now + 3600_000L, ListingStatus.SOLD, 50L, now - 1000L, null);
        db.createListing(listing);
        db.completeSale(listing.id());

        // Create a deposit fee parcel (HDV_LISTING_FEE) for the seller
        db.createParcel(new AuctionParcel(UUID.randomUUID().toString(), seller,
                "minecraft:diamond", "Diamond", null, 0, 20L, "gold",
                ParcelSource.HDV_LISTING_FEE, now, true));

        AuctionStorageProvider.PlayerStats stats = db.getPlayerStats(seller, now - 10_000L);
        // taxesPaid should include both the sale tax (50) and the deposit fee (20)
        assertEquals(70L, stats.taxesPaid());
    }
}
