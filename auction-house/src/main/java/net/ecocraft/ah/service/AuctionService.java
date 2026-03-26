package net.ecocraft.ah.service;

import net.ecocraft.ah.data.*;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Business logic layer for the auction house.
 *
 * <p>Orchestrates {@link AuctionStorageProvider} and {@link EconomyProvider}.
 * All monetary amounts cross the service boundary as {@link BigDecimal} (human-readable),
 * and are converted to/from {@code long} (smallest unit) internally.</p>
 *
 * <p>Minecraft-specific types (ServerPlayer, ItemStack) are kept out of this class so that
 * the service remains fully unit-testable without a running Minecraft server.</p>
 */
public class AuctionService {

    /** Default tax rate applied to completed sales (5%). */
    public static final double DEFAULT_TAX_RATE = 0.05;

    /** Default deposit rate deducted from the seller at listing creation (2%). Refunded if sold. */
    public static final double DEFAULT_DEPOSIT_RATE = 0.02;

    private final AuctionStorageProvider storage;
    private final EconomyProvider economy;
    private final CurrencyRegistry currencies;

    /** System UUID used as a "from" identity for tax/deposit withdrawals. */
    private static final UUID SYSTEM_UUID = new UUID(0, 0);

    public AuctionService(AuctionStorageProvider storage,
                          EconomyProvider economy,
                          CurrencyRegistry currencies) {
        this.storage = storage;
        this.economy = economy;
        this.currencies = currencies;
    }

    // -------------------------------------------------------------------------
    // Create listing
    // -------------------------------------------------------------------------

    /**
     * Creates a new auction listing.
     *
     * <p>Validates inputs, calculates tax + deposit, withdraws the deposit from the seller's
     * account, persists the listing, and returns a result object.</p>
     *
     * @param sellerUuid     UUID of the selling player
     * @param sellerName     Display name of the selling player
     * @param itemId         Registry name of the item
     * @param itemName       Display name of the item
     * @param itemNbt        Serialised NBT (null for plain items)
     * @param quantity       Quantity to list
     * @param listingType    BUYOUT or AUCTION
     * @param price          Buy-now price (or starting bid for AUCTION) as BigDecimal
     * @param durationHours  How long the listing is active
     * @param currencyId     Which currency to use
     * @param category       Item category
     * @return               The created listing, or throws on validation failure
     */
    public AuctionListing createListing(
            UUID sellerUuid,
            String sellerName,
            String itemId,
            String itemName,
            @Nullable String itemNbt,
            int quantity,
            ListingType listingType,
            BigDecimal price,
            int durationHours,
            String currencyId,
            ItemCategory category) {

        // --- Validation ---
        if (quantity <= 0) throw new AuctionException("Quantity must be positive");
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
            throw new AuctionException("Price must be positive");
        if (durationHours <= 0) throw new AuctionException("Duration must be positive");

        Currency currency = currencies.getById(currencyId);
        if (currency == null) throw new AuctionException("Unknown currency: " + currencyId);

        long priceUnits = toSmallestUnit(price, currency);
        long depositUnits = (long) (priceUnits * DEFAULT_DEPOSIT_RATE);
        long taxUnits = (long) (priceUnits * DEFAULT_TAX_RATE);

        // --- Withdraw deposit ---
        BigDecimal depositBD = fromSmallestUnit(depositUnits, currency);
        if (depositBD.compareTo(BigDecimal.ZERO) > 0) {
            var result = economy.withdraw(sellerUuid, depositBD, currency);
            if (!result.successful()) {
                throw new AuctionException("Insufficient funds for listing deposit: " + result.errorMessage());
            }
        }

        // --- Build listing ---
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        AuctionListing listing = new AuctionListing(
                id,
                sellerUuid,
                sellerName,
                itemId,
                itemName,
                itemNbt,
                quantity,
                listingType,
                listingType == ListingType.BUYOUT ? priceUnits : 0L,
                listingType == ListingType.AUCTION ? priceUnits : 0L,
                0L,   // currentBid
                null, // currentBidder
                currencyId,
                category,
                now + (long) durationHours * 3600_000L,
                ListingStatus.ACTIVE,
                taxUnits,
                now
        );

        storage.createListing(listing);
        return listing;
    }

    // -------------------------------------------------------------------------
    // Buy listing (buyout)
    // -------------------------------------------------------------------------

    /**
     * Executes an immediate buyout purchase.
     *
     * <p>Validates that the listing exists, is ACTIVE, has a buyout price, and that the buyer
     * can afford it. Withdraws the buyout price from the buyer, credits the seller minus tax,
     * creates item + currency parcels, marks the listing SOLD, and logs price history.</p>
     *
     * @param buyerUuid   UUID of the purchasing player
     * @param buyerName   Display name of the buyer
     * @param listingId   The listing to purchase
     */
    public void buyListing(UUID buyerUuid, String buyerName, String listingId) {
        AuctionListing listing = requireActiveListingById(listingId);
        if (listing.buyoutPrice() <= 0)
            throw new AuctionException("Listing has no buyout price");
        if (listing.sellerUuid().equals(buyerUuid))
            throw new AuctionException("Cannot buy your own listing");

        Currency currency = requireCurrency(listing.currencyId());
        BigDecimal buyoutBD = fromSmallestUnit(listing.buyoutPrice(), currency);

        // Withdraw from buyer
        var withdrawResult = economy.withdraw(buyerUuid, buyoutBD, currency);
        if (!withdrawResult.successful())
            throw new AuctionException("Insufficient funds: " + withdrawResult.errorMessage());

        // Credit seller (price - tax). Deposit is included in the net amount (already held).
        long sellerAmountUnits = listing.buyoutPrice() - listing.taxAmount();
        BigDecimal sellerAmountBD = fromSmallestUnit(sellerAmountUnits, currency);
        if (sellerAmountBD.compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(listing.sellerUuid(), sellerAmountBD, currency);
        }

        // Create parcels
        long now = System.currentTimeMillis();

        // Item parcel for buyer
        storage.createParcel(new AuctionParcel(
                UUID.randomUUID().toString(),
                buyerUuid,
                listing.itemId(),
                listing.itemName(),
                listing.itemNbt(),
                listing.quantity(),
                0L,
                null,
                ParcelSource.HDV_PURCHASE,
                now,
                false
        ));

        // Mark listing SOLD with buyer as current_bidder (for purchase history)
        storage.updateListingBid(listingId, listing.buyoutPrice(), buyerUuid);
        storage.completeSale(listingId);

        // Log price history
        storage.logPriceHistory(
                UUID.randomUUID().toString(),
                listing.itemId(),
                listing.currencyId(),
                listing.buyoutPrice(),
                listing.quantity(),
                now
        );
    }

    // -------------------------------------------------------------------------
    // Place bid
    // -------------------------------------------------------------------------

    /**
     * Places a bid on an auction listing.
     *
     * <p>Validates that the bid is higher than the current bid, withdraws the bid amount
     * from the bidder's account, refunds the previous bidder (if any), and records the bid.</p>
     *
     * @param bidderUuid  UUID of the bidding player
     * @param bidderName  Display name of the bidder
     * @param listingId   The listing to bid on
     * @param amount      Bid amount as BigDecimal
     */
    public void placeBid(UUID bidderUuid, String bidderName, String listingId, BigDecimal amount) {
        AuctionListing listing = requireActiveListingById(listingId);
        if (listing.listingType() != ListingType.AUCTION)
            throw new AuctionException("Listing is not an auction");
        if (listing.sellerUuid().equals(bidderUuid))
            throw new AuctionException("Cannot bid on your own listing");

        Currency currency = requireCurrency(listing.currencyId());
        long amountUnits = toSmallestUnit(amount, currency);

        long minimumBid = listing.currentBid() > 0 ? listing.currentBid() + 1 : listing.startingBid();
        if (amountUnits < minimumBid)
            throw new AuctionException("Bid must be at least " + fromSmallestUnit(minimumBid, currency).toPlainString());

        // Withdraw new bid from bidder
        var withdrawResult = economy.withdraw(bidderUuid, amount, currency);
        if (!withdrawResult.successful())
            throw new AuctionException("Insufficient funds: " + withdrawResult.errorMessage());

        // Refund previous bidder
        if (listing.currentBidderUuid() != null && listing.currentBid() > 0) {
            BigDecimal refundBD = fromSmallestUnit(listing.currentBid(), currency);
            economy.deposit(listing.currentBidderUuid(), refundBD, currency);
            // Create outbid parcel (informational — currency already credited above)
            storage.createParcel(new AuctionParcel(
                    UUID.randomUUID().toString(),
                    listing.currentBidderUuid(),
                    null, null, null,
                    0,
                    listing.currentBid(),
                    listing.currencyId(),
                    ParcelSource.HDV_OUTBID,
                    System.currentTimeMillis(),
                    true // auto-collected since we credited directly
            ));
        }

        // Record bid and update listing
        long now = System.currentTimeMillis();
        AuctionBid bid = new AuctionBid(
                UUID.randomUUID().toString(),
                listingId,
                bidderUuid,
                bidderName,
                amountUnits,
                now
        );
        storage.placeBid(bid);
        storage.updateListingBid(listingId, amountUnits, bidderUuid);
    }

    // -------------------------------------------------------------------------
    // Cancel listing
    // -------------------------------------------------------------------------

    /**
     * Cancels an active listing owned by the given player.
     *
     * <p>Returns the item via a parcel. The deposit is forfeited (not refunded) on cancellation.</p>
     *
     * @param playerUuid   UUID of the requesting player (must be the seller)
     * @param listingId    The listing to cancel
     */
    public void cancelListing(UUID playerUuid, String listingId) {
        AuctionListing listing = requireActiveListingById(listingId);
        if (!listing.sellerUuid().equals(playerUuid))
            throw new AuctionException("You do not own this listing");
        if (listing.listingType() == ListingType.AUCTION && listing.currentBid() > 0)
            throw new AuctionException("Cannot cancel an auction that already has bids");

        // Return item to seller via parcel
        long now = System.currentTimeMillis();
        storage.createParcel(new AuctionParcel(
                UUID.randomUUID().toString(),
                playerUuid,
                listing.itemId(),
                listing.itemName(),
                listing.itemNbt(),
                listing.quantity(),
                0L,
                null,
                ParcelSource.HDV_EXPIRED,
                now,
                false
        ));

        storage.cancelListing(listingId);
    }

    // -------------------------------------------------------------------------
    // Expire listings
    // -------------------------------------------------------------------------

    /**
     * Processes all ACTIVE listings that have passed their expiry time.
     *
     * <p>For BUYOUT listings: returns item to seller via parcel (deposit already forfeited at creation).</p>
     * <p>For AUCTION listings with a highest bidder: completes the sale (transfers item to bidder,
     * credits seller). For auctions with no bids: returns item to seller.</p>
     */
    public void expireListings() {
        long now = System.currentTimeMillis();
        List<String> expiredIds = storage.expireOldListings(now);

        for (String id : expiredIds) {
            AuctionListing listing = storage.getListingById(id);
            if (listing == null) continue;

            if (listing.listingType() == ListingType.AUCTION && listing.currentBid() > 0
                    && listing.currentBidderUuid() != null) {
                // Complete auction sale
                completedAuctionSale(listing, now);
            } else {
                // Return item to seller
                storage.createParcel(new AuctionParcel(
                        UUID.randomUUID().toString(),
                        listing.sellerUuid(),
                        listing.itemId(),
                        listing.itemName(),
                        listing.itemNbt(),
                        listing.quantity(),
                        0L,
                        null,
                        ParcelSource.HDV_EXPIRED,
                        now,
                        false
                ));
            }
        }
    }

    private void completedAuctionSale(AuctionListing listing, long now) {
        Currency currency = currencies.getById(listing.currencyId());
        if (currency == null) return;

        // Credit seller (final bid - tax)
        long sellerAmount = listing.currentBid() - listing.taxAmount();
        if (sellerAmount > 0) {
            economy.deposit(listing.sellerUuid(), fromSmallestUnit(sellerAmount, currency), currency);
        }

        // Item parcel for winner
        storage.createParcel(new AuctionParcel(
                UUID.randomUUID().toString(),
                listing.currentBidderUuid(),
                listing.itemId(),
                listing.itemName(),
                listing.itemNbt(),
                listing.quantity(),
                0L,
                null,
                ParcelSource.HDV_PURCHASE,
                now,
                false
        ));

        // Log price history
        storage.logPriceHistory(
                UUID.randomUUID().toString(),
                listing.itemId(),
                listing.currencyId(),
                listing.currentBid(),
                listing.quantity(),
                now
        );

        storage.completeSale(listing.id());
    }

    // -------------------------------------------------------------------------
    // Collect parcels
    // -------------------------------------------------------------------------

    /**
     * Returns all uncollected parcels for a player and marks them as collected.
     *
     * <p>Currency parcels are credited directly to the player's account.
     * Item parcels are returned for the caller to handle (e.g. give to inventory).</p>
     *
     * @param playerUuid  Player to collect for
     * @return            List of parcels that were collected (items need physical delivery by caller)
     */
    public List<AuctionParcel> collectParcels(UUID playerUuid) {
        List<AuctionParcel> parcels = storage.getUncollectedParcels(playerUuid);
        for (AuctionParcel parcel : parcels) {
            // Currency parcels: credit directly
            if (parcel.hasCurrency()) {
                Currency currency = currencies.getById(parcel.currencyId());
                if (currency != null) {
                    economy.deposit(playerUuid, fromSmallestUnit(parcel.amount(), currency), currency);
                }
            }
            storage.markParcelCollected(parcel.id());
        }
        return parcels;
    }

    // -------------------------------------------------------------------------
    // Search / browse
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated grouped view of active listings for the browse tab.
     */
    public List<AuctionStorageProvider.ListingGroupSummary> searchListings(
            @Nullable String query,
            @Nullable ItemCategory category,
            int page,
            int pageSize) {
        return storage.getListingsGroupedByItem(query, category, page, pageSize);
    }

    /**
     * Returns all active individual listings for a specific item (detail view).
     */
    public List<AuctionListing> getListingDetail(String itemId) {
        return storage.getListingsForItem(itemId);
    }

    // -------------------------------------------------------------------------
    // My listings / history
    // -------------------------------------------------------------------------

    public List<AuctionListing> getMyListings(UUID playerUuid, int page, int pageSize) {
        return storage.getPlayerListings(playerUuid, page, pageSize);
    }

    public List<AuctionListing> getMyPurchases(UUID playerUuid, int page, int pageSize) {
        return storage.getPlayerPurchases(playerUuid, page, pageSize);
    }

    public List<AuctionListing> getMyBids(UUID playerUuid) {
        return storage.getPlayerBids(playerUuid);
    }

    public List<AuctionParcel> getLedger(UUID playerUuid, @Nullable ParcelSource source,
                                         long sinceMs, int page, int pageSize) {
        return storage.getPlayerLedger(playerUuid, source, sinceMs, page, pageSize);
    }

    public AuctionStorageProvider.PlayerStats getPlayerStats(UUID playerUuid, long sinceMs) {
        return storage.getPlayerStats(playerUuid, sinceMs);
    }

    public int countUncollectedParcels(UUID playerUuid) {
        return storage.countUncollectedParcels(playerUuid);
    }

    // -------------------------------------------------------------------------
    // Currency conversion helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a BigDecimal amount to the smallest currency unit (long).
     * Example: 10.50 gold with 2 decimals → 1050L
     */
    public static long toSmallestUnit(BigDecimal amount, Currency currency) {
        BigDecimal multiplier = BigDecimal.TEN.pow(currency.decimals());
        return amount.multiply(multiplier).setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /**
     * Converts a smallest-unit long back to a BigDecimal.
     * Example: 1050L with 2 decimals → 10.50
     */
    public static BigDecimal fromSmallestUnit(long amount, Currency currency) {
        if (currency.decimals() == 0) return BigDecimal.valueOf(amount);
        BigDecimal divisor = BigDecimal.TEN.pow(currency.decimals());
        return BigDecimal.valueOf(amount).divide(divisor, currency.decimals(), RoundingMode.UNNECESSARY);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private AuctionListing requireActiveListingById(String listingId) {
        AuctionListing listing = storage.getListingById(listingId);
        if (listing == null) throw new AuctionException("Listing not found: " + listingId);
        if (listing.status() != ListingStatus.ACTIVE)
            throw new AuctionException("Listing is not active (status=" + listing.status() + ")");
        if (listing.isExpired())
            throw new AuctionException("Listing has expired");
        return listing;
    }

    private Currency requireCurrency(String currencyId) {
        Currency c = currencies.getById(currencyId);
        if (c == null) throw new AuctionException("Unknown currency: " + currencyId);
        return c;
    }

    // -------------------------------------------------------------------------
    // Exception type
    // -------------------------------------------------------------------------

    /** Thrown when an auction operation fails due to a business rule violation. */
    public static class AuctionException extends RuntimeException {
        public AuctionException(String message) {
            super(message);
        }
    }
}
