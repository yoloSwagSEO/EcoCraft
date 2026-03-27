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

    private static double getTaxRate() {
        try { return net.ecocraft.ah.config.AHConfig.CONFIG.getSaleRateDecimal(); }
        catch (Throwable e) { return DEFAULT_TAX_RATE; }
    }

    private static double getDepositRate() {
        try { return net.ecocraft.ah.config.AHConfig.CONFIG.getDepositRateDecimal(); }
        catch (Throwable e) { return DEFAULT_DEPOSIT_RATE; }
    }

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
            ItemCategory category,
            @Nullable String fingerprint) {

        // --- Validation ---
        if (quantity <= 0) throw new AuctionException("Quantity must be positive");
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
            throw new AuctionException("Price must be positive");
        if (durationHours <= 0) throw new AuctionException("Duration must be positive");

        Currency currency = currencies.getById(currencyId);
        if (currency == null) throw new AuctionException("Unknown currency: " + currencyId);

        long priceUnits = toSmallestUnit(price, currency);
        long totalPrice = priceUnits * quantity;
        long depositUnits = Math.max(1, (long) (totalPrice * getDepositRate()));
        long taxUnits = Math.max(1, (long) (totalPrice * getTaxRate()));

        System.out.println("[AH] CREATE LISTING: seller=" + sellerName + " item=" + itemName + " qty=" + quantity
                + " unitPrice=" + priceUnits + " totalPrice=" + totalPrice + " deposit=" + depositUnits + " tax=" + taxUnits);

        // --- Withdraw deposit ---
        BigDecimal depositBD = fromSmallestUnit(depositUnits, currency);
        if (depositBD.compareTo(BigDecimal.ZERO) > 0) {
            var result = economy.withdraw(sellerUuid, depositBD, currency);
            if (!result.successful()) {
                System.err.println("[AH] CREATE LISTING FAILED: insufficient funds. seller=" + sellerName + " deposit=" + depositBD);
                throw new AuctionException("Insufficient funds for listing deposit: " + result.errorMessage());
            }
            System.out.println("[AH] Deposit withdrawn: " + depositBD + " " + currency.symbol() + " from " + sellerName);
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
                now,
                fingerprint,
                null // ahId — will be set to DEFAULT_ID by storage
        );

        storage.createListing(listing);

        // Log deposit fee in ledger as a parcel entry
        if (depositUnits > 0) {
            storage.createParcel(new AuctionParcel(
                    UUID.randomUUID().toString(),
                    sellerUuid,
                    itemId,
                    itemName,
                    null,
                    0,
                    depositUnits,
                    currencyId,
                    ParcelSource.HDV_LISTING_FEE,
                    now,
                    true // auto-collected (already withdrawn)
            ));
        }

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
    public void buyListing(UUID buyerUuid, String buyerName, String listingId, int buyQuantity) {
        AuctionListing listing = requireActiveListingById(listingId);
        if (listing.buyoutPrice() <= 0)
            throw new AuctionException("Listing has no buyout price");
        if (listing.sellerUuid().equals(buyerUuid))
            throw new AuctionException("Cannot buy your own listing");
        if (buyQuantity <= 0 || buyQuantity > listing.quantity())
            throw new AuctionException("Invalid quantity (requested " + buyQuantity + ", available " + listing.quantity() + ")");

        Currency currency = requireCurrency(listing.currencyId());
        long totalPrice = listing.buyoutPrice() * buyQuantity;
        BigDecimal buyoutBD = fromSmallestUnit(totalPrice, currency);

        System.out.println("[AH] BUY: buyer=" + buyerName + " item=" + listing.itemName() + " buyQty=" + buyQuantity
                + "/" + listing.quantity() + " unitPrice=" + listing.buyoutPrice() + " totalPrice=" + totalPrice);

        // Withdraw from buyer (unit price × buy quantity)
        var withdrawResult = economy.withdraw(buyerUuid, buyoutBD, currency);
        if (!withdrawResult.successful()) {
            System.err.println("[AH] BUY FAILED: insufficient funds. buyer=" + buyerName + " needed=" + buyoutBD);
            throw new AuctionException("Insufficient funds: " + withdrawResult.errorMessage());
        }
        System.out.println("[AH] Buyer charged: " + buyoutBD + " " + currency.symbol());

        // Credit seller (total price - proportional tax)
        long proportionalTax = Math.max(1, (long) (totalPrice * getTaxRate()));
        long sellerAmountUnits = totalPrice - proportionalTax;
        BigDecimal sellerAmountBD = fromSmallestUnit(sellerAmountUnits, currency);
        if (sellerAmountBD.compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(listing.sellerUuid(), sellerAmountBD, currency);
        }
        System.out.println("[AH] Seller credited: " + sellerAmountBD + " " + currency.symbol() + " (tax: " + proportionalTax + " " + currency.symbol() + ")");

        // Create parcels
        long now = System.currentTimeMillis();

        // Item parcel for buyer (only the purchased quantity, with price paid)
        storage.createParcel(new AuctionParcel(
                UUID.randomUUID().toString(),
                buyerUuid,
                listing.itemId(),
                listing.itemName(),
                listing.itemNbt(),
                buyQuantity,
                totalPrice,
                listing.currencyId(),
                ParcelSource.HDV_PURCHASE,
                now,
                false
        ));
        System.out.println("[AH] Parcel created: " + buyQuantity + "x " + listing.itemName() + " for buyer " + buyerName);

        // Sale parcel for seller (revenue entry in ledger)
        storage.createParcel(new AuctionParcel(
                UUID.randomUUID().toString(),
                listing.sellerUuid(),
                listing.itemId(),
                listing.itemName(),
                listing.itemNbt(),
                buyQuantity,
                sellerAmountUnits,
                listing.currencyId(),
                ParcelSource.HDV_SALE,
                now,
                true // auto-collected (already deposited)
        ));

        // Update listing: if all bought → SOLD, otherwise reduce quantity
        int remaining = listing.quantity() - buyQuantity;
        if (remaining <= 0) {
            storage.updateListingBid(listingId, listing.buyoutPrice(), buyerUuid);
            storage.completeSale(listingId);
            System.out.println("[AH] Listing " + listingId.substring(0, 8) + " SOLD (fully purchased)");
        } else {
            storage.updateListingQuantity(listingId, remaining);
            System.out.println("[AH] Listing " + listingId.substring(0, 8) + " qty reduced: " + listing.quantity() + " -> " + remaining);
        }

        // Log price history
        storage.logPriceHistory(
                listing.ahId() != null ? listing.ahId() : AHInstance.DEFAULT_ID,
                UUID.randomUUID().toString(),
                listing.itemId(),
                listing.currencyId(),
                listing.buyoutPrice(),
                buyQuantity,
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
                listing.ahId() != null ? listing.ahId() : AHInstance.DEFAULT_ID,
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
            String ahId,
            @Nullable String query,
            @Nullable ItemCategory category,
            int page,
            int pageSize) {
        return storage.getListingsGroupedByItem(ahId, query, category, page, pageSize);
    }

    /**
     * Returns all active individual listings for a specific item (detail view).
     */
    public List<AuctionListing> getListingDetail(String ahId, String itemId) {
        return storage.getListingsForItem(ahId, itemId);
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
     * Converts a BigDecimal amount to long for storage.
     * Stores in the main currency unit (Gold), not sub-units.
     */
    public static long toSmallestUnit(BigDecimal amount, Currency currency) {
        return amount.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    /**
     * Converts a stored long back to a BigDecimal.
     */
    public static BigDecimal fromSmallestUnit(long amount, Currency currency) {
        return BigDecimal.valueOf(amount);
    }

    // -------------------------------------------------------------------------
    // Balance query
    // -------------------------------------------------------------------------

    /** Returns the player's balance in the default currency (as smallest units). */
    public long getPlayerBalance(UUID playerUuid) {
        Currency currency = currencies.getDefault();
        BigDecimal balance = economy.getBalance(playerUuid, currency);
        return toSmallestUnit(balance, currency);
    }

    /** Returns the default currency's symbol. */
    public String getDefaultCurrencySymbol() {
        return currencies.getDefault().symbol();
    }

    /** Returns the best (lowest) active buyout price for an item, or -1 if none. */
    public long getBestPrice(String ahId, String fingerprint, String itemId) {
        return storage.getBestPrice(ahId, fingerprint, itemId);
    }

    /** Returns the default currency's id. */
    public String getDefaultCurrencyId() {
        return currencies.getDefault().id();
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
