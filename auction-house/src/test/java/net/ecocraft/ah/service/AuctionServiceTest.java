package net.ecocraft.ah.service;

import net.ecocraft.ah.data.*;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.TransactionResult;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuctionServiceTest {

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /** Simple in-memory EconomyProvider for tests — no Minecraft runtime needed. */
    static class MockEconomy implements EconomyProvider {
        private final Map<String, BigDecimal> balances = new HashMap<>();

        private String key(UUID player, Currency currency) {
            return player + ":" + currency.id();
        }

        public void setBalance(UUID player, Currency currency, BigDecimal amount) {
            balances.put(key(player, currency), amount);
        }

        @Override
        public BigDecimal getVirtualBalance(UUID player, Currency currency) {
            return balances.getOrDefault(key(player, currency), BigDecimal.ZERO);
        }

        @Override
        public BigDecimal getVaultBalance(UUID player, Currency currency) {
            return BigDecimal.ZERO;
        }

        @Override
        public Account getAccount(UUID player, Currency currency) {
            return null;
        }

        @Override
        public TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency) {
            BigDecimal current = getVirtualBalance(player, currency);
            if (current.compareTo(amount) < 0) {
                return TransactionResult.failure("Insufficient funds");
            }
            balances.put(key(player, currency), current.subtract(amount));
            return TransactionResult.success(null);
        }

        @Override
        public TransactionResult deposit(UUID player, BigDecimal amount, Currency currency) {
            BigDecimal current = getVirtualBalance(player, currency);
            balances.put(key(player, currency), current.add(amount));
            return TransactionResult.success(null);
        }

        @Override
        public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency) {
            var w = withdraw(from, amount, currency);
            if (!w.successful()) return w;
            return deposit(to, amount, currency);
        }

        @Override
        public boolean canAfford(UUID player, BigDecimal amount, Currency currency) {
            return getVirtualBalance(player, currency).compareTo(amount) >= 0;
        }
    }

    /** Simple CurrencyRegistry backed by a map. */
    static class MockCurrencyRegistry implements CurrencyRegistry {
        private final Map<String, Currency> map = new LinkedHashMap<>();

        public MockCurrencyRegistry(Currency... currencies) {
            for (Currency c : currencies) map.put(c.id(), c);
        }

        @Override
        public void register(Currency currency) { map.put(currency.id(), currency); }

        @Override
        public Currency getById(String id) { return map.get(id); }

        @Override
        public Currency getDefault() { return map.values().iterator().next(); }

        @Override
        public List<Currency> listAll() { return new ArrayList<>(map.values()); }

        @Override
        public boolean exists(String id) { return map.containsKey(id); }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 2);

    private AuctionStorageProvider storage;
    private MockEconomy economy;
    private MockCurrencyRegistry currencies;
    private AuctionService service;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("ah-service-test");
        storage = new AuctionStorageProvider(tempDir.resolve("ah.db"));
        storage.initialize();
        economy = new MockEconomy();
        currencies = new MockCurrencyRegistry(GOLD);
        service = new AuctionService(storage, economy, currencies);
    }

    @AfterEach
    void tearDown() {
        storage.shutdown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AuctionListing createSword(UUID seller) {
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        return service.createListing(
                seller, "Seller", "minecraft:diamond_sword", "Diamond Sword", null,
                1, ListingType.BUYOUT, new BigDecimal("100.00"),
                24, "gold", ItemCategory.WEAPONS, null, null);
    }

    // -------------------------------------------------------------------------
    // Currency conversion
    // -------------------------------------------------------------------------

    @Test
    void toSmallestUnitConversion() {
        // Prices stored in main unit (Gold), no sub-unit multiplication
        assertEquals(10L, AuctionService.toSmallestUnit(new BigDecimal("10.50"), GOLD));
        assertEquals(100L, AuctionService.toSmallestUnit(new BigDecimal("100.00"), GOLD));
        assertEquals(0L, AuctionService.toSmallestUnit(new BigDecimal("0.01"), GOLD));
    }

    @Test
    void fromSmallestUnitConversion() {
        assertEquals(new BigDecimal("1050"), AuctionService.fromSmallestUnit(1050L, GOLD));
        assertEquals(new BigDecimal("100"), AuctionService.fromSmallestUnit(100L, GOLD));
    }

    @Test
    void conversionRoundTrip() {
        BigDecimal original = new BigDecimal("42");
        long units = AuctionService.toSmallestUnit(original, GOLD);
        assertEquals(original, AuctionService.fromSmallestUnit(units, GOLD));
    }

    // -------------------------------------------------------------------------
    // createListing
    // -------------------------------------------------------------------------

    @Test
    void createListingDeductsDeposit() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));

        service.createListing(seller, "Seller", "minecraft:diamond", "Diamond", null,
                1, ListingType.BUYOUT, new BigDecimal("100.00"), 24, "gold", ItemCategory.MISC, null, null);

        // Deposit = 2% of 100 = 2.00
        BigDecimal expectedBalance = new BigDecimal("1000.00").subtract(new BigDecimal("2.00"));
        assertEquals(expectedBalance, economy.getVirtualBalance(seller, GOLD));
    }

    @Test
    void createListingPersistsToStorage() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = service.createListing(
                seller, "Seller", "minecraft:emerald", "Emerald", null,
                1, ListingType.BUYOUT, new BigDecimal("50.00"), 12, "gold", ItemCategory.MISC, null, null);

        assertNotNull(storage.getListingById(listing.id()));
        assertEquals(ListingStatus.ACTIVE, storage.getListingById(listing.id()).status());
    }

    @Test
    void createListingFailsWithInsufficientFunds() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, BigDecimal.ZERO);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.createListing(seller, "Seller", "minecraft:diamond", "Diamond", null,
                        1, ListingType.BUYOUT, new BigDecimal("100.00"), 24, "gold", ItemCategory.MISC, null, null));
    }

    @Test
    void createListingFailsWithZeroPrice() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("100.00"));

        assertThrows(AuctionService.AuctionException.class, () ->
                service.createListing(seller, "Seller", "minecraft:diamond", "Diamond", null,
                        1, ListingType.BUYOUT, BigDecimal.ZERO, 24, "gold", ItemCategory.MISC, null, null));
    }

    @Test
    void createListingFailsWithUnknownCurrency() {
        UUID seller = UUID.randomUUID();
        assertThrows(AuctionService.AuctionException.class, () ->
                service.createListing(seller, "Seller", "minecraft:diamond", "Diamond", null,
                        1, ListingType.BUYOUT, new BigDecimal("10.00"), 24, "unknown_coin", ItemCategory.MISC, null, null));
    }

    @Test
    void createAuctionListingSetsStartingBid() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = service.createListing(
                seller, "Seller", "minecraft:bow", "Bow", null,
                1, ListingType.AUCTION, new BigDecimal("20.00"), 24, "gold", ItemCategory.WEAPONS, null, null);

        assertEquals(0L, listing.buyoutPrice());
        assertEquals(20L, listing.startingBid()); // 20.00 stored as-is = 20 units
    }

    // -------------------------------------------------------------------------
    // buyListing
    // -------------------------------------------------------------------------

    @Test
    void buyListingTransfersFundsAndCreatesParcels() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(buyer, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = createSword(seller);

        service.buyListing(buyer, "Buyer", listing.id(), 1);

        // Listing should be SOLD
        assertEquals(ListingStatus.SOLD, storage.getListingById(listing.id()).status());

        // Buyer should have 500 - 100 = 400
        assertEquals(new BigDecimal("400.00"), economy.getVirtualBalance(buyer, GOLD));

        // Seller: 1000 - deposit(2) + (100 - tax(5)) = 1000 - 2 + 95 = 1093
        assertEquals(new BigDecimal("1093.00"), economy.getVirtualBalance(seller, GOLD));

        // Item parcel for buyer
        List<AuctionParcel> buyerParcels = storage.getUncollectedParcels(buyer);
        assertEquals(1, buyerParcels.size());
        assertEquals("minecraft:diamond_sword", buyerParcels.get(0).itemId());
        assertEquals(ParcelSource.HDV_PURCHASE, buyerParcels.get(0).source());
    }

    @Test
    void buyListingFailsWithInsufficientFunds() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(buyer, GOLD, BigDecimal.ZERO);

        AuctionListing listing = createSword(seller);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.buyListing(buyer, "Buyer", listing.id(), 1));
    }

    @Test
    void buyListingFailsWhenBuyerIsAlsoSeller() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        AuctionListing listing = createSword(seller);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.buyListing(seller, "Seller", listing.id(), 1));
    }

    @Test
    void buyListingFailsForAlreadySoldListing() {
        UUID seller = UUID.randomUUID();
        UUID buyer1 = UUID.randomUUID();
        UUID buyer2 = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(buyer1, GOLD, new BigDecimal("500.00"));
        economy.setBalance(buyer2, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = createSword(seller);
        service.buyListing(buyer1, "Buyer1", listing.id(), 1);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.buyListing(buyer2, "Buyer2", listing.id(), 1));
    }

    @Test
    void buyListingLogsPrice() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(buyer, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = createSword(seller);
        service.buyListing(buyer, "Buyer", listing.id(), 1);

        AuctionStorageProvider.PriceStats stats = storage.getPriceHistory(
                "minecraft:diamond_sword", "gold", 86_400_000L);
        assertNotNull(stats);
        assertEquals(100L, stats.minPrice()); // 100.00 stored as-is = 100 units
    }

    // -------------------------------------------------------------------------
    // placeBid
    // -------------------------------------------------------------------------

    @Test
    void placeBidHoldsFunds() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(bidder, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = service.createListing(
                seller, "Seller", "minecraft:elytra", "Elytra", null,
                1, ListingType.AUCTION, new BigDecimal("50.00"),
                24, "gold", ItemCategory.MISC, null, null);

        service.placeBid(bidder, "Bidder", listing.id(), new BigDecimal("60.00"));

        // Bidder should have 500 - 60 = 440
        assertEquals(new BigDecimal("440.00"), economy.getVirtualBalance(bidder, GOLD));
        // Listing should have updated bid
        AuctionListing updated = storage.getListingById(listing.id());
        assertNotNull(updated);
        assertEquals(60L, updated.currentBid());
        assertEquals(bidder, updated.currentBidderUuid());
    }

    @Test
    void placeBidRefundsPreviousBidder() {
        UUID seller = UUID.randomUUID();
        UUID bidder1 = UUID.randomUUID();
        UUID bidder2 = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(bidder1, GOLD, new BigDecimal("500.00"));
        economy.setBalance(bidder2, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = service.createListing(
                seller, "Seller", "minecraft:shulker_box", "Shulker Box", null,
                1, ListingType.AUCTION, new BigDecimal("10.00"),
                24, "gold", ItemCategory.MISC, null, null);

        service.placeBid(bidder1, "Bidder1", listing.id(), new BigDecimal("20.00"));
        // bidder1 has 500 - 20 = 480

        service.placeBid(bidder2, "Bidder2", listing.id(), new BigDecimal("30.00"));
        // bidder2 has 500 - 30 = 470
        // bidder1 should be refunded 20 → back to 500

        assertEquals(new BigDecimal("500.00"), economy.getVirtualBalance(bidder1, GOLD));
        assertEquals(new BigDecimal("470.00"), economy.getVirtualBalance(bidder2, GOLD));
    }

    @Test
    void placeBidFailsBelowMinimum() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(bidder, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = service.createListing(
                seller, "Seller", "minecraft:beacon", "Beacon", null,
                1, ListingType.AUCTION, new BigDecimal("100.00"),
                24, "gold", ItemCategory.MISC, null, null);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.placeBid(bidder, "Bidder", listing.id(), new BigDecimal("50.00")));
    }

    @Test
    void placeBidFailsOnBuyoutListing() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(bidder, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = createSword(seller);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.placeBid(bidder, "Bidder", listing.id(), new BigDecimal("50.00")));
    }

    // -------------------------------------------------------------------------
    // cancelListing
    // -------------------------------------------------------------------------

    @Test
    void cancelListingCreatesItemParcel() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));

        AuctionListing listing = createSword(seller);
        service.cancelListing(seller, listing.id());

        assertEquals(ListingStatus.CANCELLED, storage.getListingById(listing.id()).status());

        List<AuctionParcel> parcels = storage.getUncollectedParcels(seller);
        assertEquals(1, parcels.size());
        assertEquals("minecraft:diamond_sword", parcels.get(0).itemId());
        assertEquals(ParcelSource.HDV_EXPIRED, parcels.get(0).source());
    }

    @Test
    void cancelListingFailsIfNotOwner() {
        UUID seller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));

        AuctionListing listing = createSword(seller);

        assertThrows(AuctionService.AuctionException.class, () ->
                service.cancelListing(other, listing.id()));
    }

    @Test
    void cancelListingFailsIfBidsExist() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(bidder, GOLD, new BigDecimal("500.00"));

        AuctionListing listing = service.createListing(
                seller, "Seller", "minecraft:shulker_box", "Shulker Box", null,
                1, ListingType.AUCTION, new BigDecimal("10.00"),
                24, "gold", ItemCategory.MISC, null, null);

        service.placeBid(bidder, "Bidder", listing.id(), new BigDecimal("15.00"));

        assertThrows(AuctionService.AuctionException.class, () ->
                service.cancelListing(seller, listing.id()));
    }

    // -------------------------------------------------------------------------
    // expireListings
    // -------------------------------------------------------------------------

    @Test
    void expireListingsReturnsBuyoutItemToSeller() throws Exception {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));

        // Create a listing that "expires" immediately (past expiry) — we'll inject directly
        long past = System.currentTimeMillis() - 10_000L;
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "Seller",
                "minecraft:diamond", "Diamond", null, 1,
                ListingType.BUYOUT, 1000L, 0L, 0L, null, "gold",
                ItemCategory.MISC, past, ListingStatus.ACTIVE, 20L, past - 86_400_000L, null, null);
        storage.createListing(listing);

        service.expireListings();

        // Item should be returned via parcel
        List<AuctionParcel> parcels = storage.getUncollectedParcels(seller);
        assertEquals(1, parcels.size());
        assertEquals("minecraft:diamond", parcels.get(0).itemId());
        assertEquals(ParcelSource.HDV_EXPIRED, parcels.get(0).source());
        assertEquals(ListingStatus.EXPIRED, storage.getListingById(listing.id()).status());
    }

    @Test
    void expireAuctionTaxCalculatedOnFinalBidNotStartingBid() {
        UUID seller = UUID.randomUUID();
        UUID winner = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(winner, GOLD, new BigDecimal("500.00"));

        // Insert an expired AUCTION listing directly:
        //   startingBid = 10 (i.e. 10.00 Gold), currentBid = 100 (i.e. 100.00 Gold)
        // This simulates an auction where the starting bid was 10 but the winning bid was 100.
        long past = System.currentTimeMillis() - 10_000L;
        long startingBid = 10L;   // 10.00 Gold
        long finalBid = 100L;     // 100.00 Gold — 10x the starting bid
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "Seller",
                "minecraft:diamond_pickaxe", "Diamond Pickaxe", null, 1,
                ListingType.AUCTION, 0L, startingBid, finalBid, winner, "gold",
                ItemCategory.TOOLS, past, ListingStatus.ACTIVE, 0L, past - 86_400_000L, null, null);
        storage.createListing(listing);

        BigDecimal sellerBalanceBefore = economy.getVirtualBalance(seller, GOLD);

        service.expireListings();

        // Tax should be 5% of 100 (final bid) = 5, NOT 5% of 10 (starting bid)
        long expectedTax = Math.max(1, (long) (finalBid * AuctionService.DEFAULT_TAX_RATE));
        assertEquals(5L, expectedTax, "Tax should be 5% of final bid (100), not starting bid (10)");

        long sellerGain = finalBid - expectedTax; // 100 - 5 = 95
        BigDecimal expectedBalance = sellerBalanceBefore.add(
                AuctionService.fromSmallestUnit(sellerGain, GOLD));
        assertEquals(expectedBalance, economy.getVirtualBalance(seller, GOLD),
                "Seller should receive final bid minus tax calculated on final bid");

        // Verify listing is marked as sold
        assertEquals(ListingStatus.SOLD, storage.getListingById(listing.id()).status());

        // Winner should have item parcel
        List<AuctionParcel> winnerParcels = storage.getUncollectedParcels(winner);
        assertEquals(1, winnerParcels.size());
        assertEquals(ParcelSource.HDV_PURCHASE, winnerParcels.get(0).source());
    }

    @Test
    void expireListingsCompletesAuctionWithBids() {
        UUID seller = UUID.randomUUID();
        UUID winner = UUID.randomUUID();

        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(winner, GOLD, new BigDecimal("500.00"));

        // Insert expired auction with a bid directly
        long past = System.currentTimeMillis() - 10_000L;
        AuctionListing listing = new AuctionListing(
                UUID.randomUUID().toString(), seller, "Seller",
                "minecraft:netherite_sword", "Netherite Sword", null, 1,
                ListingType.AUCTION, 0L, 5000L, 8000L, winner, "gold",
                ItemCategory.WEAPONS, past, ListingStatus.ACTIVE, 400L, past - 86_400_000L, null, null);
        storage.createListing(listing);

        BigDecimal sellerBalanceBefore = economy.getVirtualBalance(seller, GOLD);

        service.expireListings();

        assertEquals(ListingStatus.SOLD, storage.getListingById(listing.id()).status());

        // Seller should receive bid (80.00) - tax (4.00) = 76.00
        // Note: tax in listing is 400 units = 4.00, bid is 8000 units = 80.00
        BigDecimal expectedSellerGain = AuctionService.fromSmallestUnit(8000L - 400L, GOLD);
        assertEquals(sellerBalanceBefore.add(expectedSellerGain), economy.getVirtualBalance(seller, GOLD));

        // Winner should have item parcel
        List<AuctionParcel> winnerParcels = storage.getUncollectedParcels(winner);
        assertEquals(1, winnerParcels.size());
        assertEquals(ParcelSource.HDV_PURCHASE, winnerParcels.get(0).source());
    }

    // -------------------------------------------------------------------------
    // collectParcels
    // -------------------------------------------------------------------------

    @Test
    void collectParcelsCreditsCurrencyAndMarksCollected() {
        UUID player = UUID.randomUUID();

        // Create a currency parcel
        AuctionParcel currencyParcel = new AuctionParcel(
                UUID.randomUUID().toString(), player, null, null, null,
                0, 50L, "gold", ParcelSource.HDV_SALE, System.currentTimeMillis(), false, null);
        storage.createParcel(currencyParcel);

        service.collectParcels(player);

        // Should be credited 50
        assertEquals(0, new BigDecimal("50").compareTo(economy.getVirtualBalance(player, GOLD)));
        assertEquals(0, storage.countUncollectedParcels(player));
    }

    @Test
    void collectParcelsReturnsAllParcelsIncludingItems() {
        UUID player = UUID.randomUUID();

        AuctionParcel itemParcel = new AuctionParcel(
                UUID.randomUUID().toString(), player, "minecraft:apple", "Apple", null,
                3, 0L, null, ParcelSource.HDV_PURCHASE, System.currentTimeMillis(), false, null);
        AuctionParcel currParcel = new AuctionParcel(
                UUID.randomUUID().toString(), player, null, null, null,
                0, 200L, "gold", ParcelSource.HDV_OUTBID, System.currentTimeMillis(), false, null);
        storage.createParcel(itemParcel);
        storage.createParcel(currParcel);

        List<AuctionParcel> collected = service.collectParcels(player);

        assertEquals(2, collected.size());
        assertEquals(0, storage.countUncollectedParcels(player));
    }

    // -------------------------------------------------------------------------
    // searchListings / getListingDetail
    // -------------------------------------------------------------------------

    @Test
    void searchListingsReturnsGroupedResults() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));

        service.createListing(seller, "Seller", "minecraft:diamond", "Diamond", null,
                1, ListingType.BUYOUT, new BigDecimal("50.00"), 24, "gold", ItemCategory.MISC, null, null);
        service.createListing(seller, "Seller", "minecraft:diamond", "Diamond", null,
                5, ListingType.BUYOUT, new BigDecimal("45.00"), 24, "gold", ItemCategory.MISC, null, null);
        service.createListing(seller, "Seller", "minecraft:emerald", "Emerald", null,
                1, ListingType.BUYOUT, new BigDecimal("80.00"), 24, "gold", ItemCategory.MISC, null, null);

        List<AuctionStorageProvider.ListingGroupSummary> results = service.searchListings(AHInstance.DEFAULT_ID, null, null, 0, 10);
        assertEquals(2, results.size()); // diamond and emerald grouped
    }

    @Test
    void getListingDetailReturnsAllListingsForItem() {
        UUID seller = UUID.randomUUID();
        economy.setBalance(seller, GOLD, new BigDecimal("1000.00"));

        service.createListing(seller, "Seller", "minecraft:iron_ingot", "Iron Ingot", null,
                1, ListingType.BUYOUT, new BigDecimal("5.00"), 24, "gold", ItemCategory.MISC, null, null);
        service.createListing(seller, "Seller", "minecraft:iron_ingot", "Iron Ingot", null,
                10, ListingType.BUYOUT, new BigDecimal("4.50"), 24, "gold", ItemCategory.MISC, null, null);

        List<AuctionListing> detail = service.getListingDetail(AHInstance.DEFAULT_ID, "minecraft:iron_ingot");
        assertEquals(2, detail.size());
    }
}
