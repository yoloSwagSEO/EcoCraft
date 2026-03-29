package net.ecocraft.ah.service;

import net.ecocraft.ah.data.*;
import net.ecocraft.ah.storage.AuctionStorageProvider;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(AuctionService.class);

    /** Default tax rate applied to completed sales (5%). */
    public static final double DEFAULT_TAX_RATE = 0.05;

    /** Default deposit rate deducted from the seller at listing creation (2%). Refunded if sold. */
    public static final double DEFAULT_DEPOSIT_RATE = 0.02;

    private double getTaxRate(String ahId, int permOverride) {
        try {
            AHInstance ah = storage.getAHInstance(ahId);
            if (ah != null) {
                double ahRate = ah.saleRate() / 100.0;
                if (ah.overridePermTax()) return ahRate;
                if (permOverride >= 0) return permOverride / 100.0;
                return ahRate;
            }
        } catch (Exception ignored) {}
        if (permOverride >= 0) return permOverride / 100.0;
        return DEFAULT_TAX_RATE;
    }

    private double getDepositRate(String ahId, int permOverride) {
        try {
            AHInstance ah = storage.getAHInstance(ahId);
            if (ah != null) {
                double ahRate = ah.depositRate() / 100.0;
                if (ah.overridePermTax()) return ahRate;
                if (permOverride >= 0) return permOverride / 100.0;
                return ahRate;
            }
        } catch (Exception ignored) {}
        if (permOverride >= 0) return permOverride / 100.0;
        return DEFAULT_DEPOSIT_RATE;
    }

    /** Credits tax amount to the AH's designated tax recipient, if configured. */
    private void creditTaxRecipient(String ahId, long taxAmount, Currency currency) {
        if (taxAmount <= 0) return;
        try {
            AHInstance ah = storage.getAHInstance(ahId);
            if (ah == null || ah.taxRecipient() == null || ah.taxRecipient().isEmpty()) return;

            // Resolve recipient UUID from name via injected profile resolver
            if (profileResolver == null) return;
            UUID recipientUuid = profileResolver.resolve(ah.taxRecipient());
            if (recipientUuid == null) return;

            BigDecimal amount = fromSmallestUnit(taxAmount, currency);
            economy.deposit(recipientUuid, amount, currency);
            LOGGER.info("Tax credited to {}: {} {}", ah.taxRecipient(), amount, currency.symbol());
        } catch (Exception e) {
            LOGGER.error("Failed to credit tax recipient: {}", e.getMessage());
        }
    }

    private final AuctionStorageProvider storage;
    private final EconomyProvider economy;
    private final CurrencyRegistry currencies;

    /**
     * Callback interface for sending notifications to online players.
     * Decoupled from Minecraft classes so {@link AuctionService} stays unit-testable.
     */
    @FunctionalInterface
    public interface NotificationSender {
        /**
         * Sends a notification to the player with the given UUID.
         * Implementations should silently no-op if the player is offline.
         */
        void send(UUID playerUuid, String eventType, String itemName,
                  String otherPlayerName, long amount, String currencyId);
    }

    /**
     * Resolves a player name to their UUID via the server's profile cache.
     * Returns null if the player is unknown.
     */
    @FunctionalInterface
    public interface ProfileResolver {
        @Nullable UUID resolve(String playerName);
    }

    /**
     * Event dispatcher interface for KubeJS integration.
     * PRE methods return false to cancel the operation; POST methods are fire-and-forget.
     * Null when KubeJS is not loaded.
     */
    public interface AHEventDispatcher {
        boolean fireListingCreating(UUID seller, String itemId, String itemName, int qty,
                                     long price, ListingType type, String ahId);
        void fireListingCreated(AuctionListing listing);
        boolean fireBuying(UUID buyer, AuctionListing listing, int qty, long totalPrice);
        void fireSold(UUID buyer, String buyerName, AuctionListing listing, int qty,
                       long totalPrice, long tax);
        boolean fireBidPlacing(UUID bidder, AuctionListing listing, long amount);
        void fireBidPlaced(UUID bidder, String bidderName, AuctionListing listing,
                            long amount, long prevBid, @Nullable UUID prevBidder);
        void fireAuctionWon(UUID winner, String winnerName, AuctionListing listing, long finalPrice);
        void fireAuctionLost(UUID loser, String loserName, AuctionListing listing, long refund);
        boolean fireListingCancelling(UUID player, AuctionListing listing);
        void fireListingCancelled(UUID player, AuctionListing listing);
        void fireListingExpired(AuctionListing listing, boolean hadBids);
    }

    /** Notification sender injected from AHServerEvents; null means no notifications. */
    @Nullable
    private NotificationSender notificationSender;

    /** Profile resolver injected from AHServerEvents; null means tax recipient lookup is skipped. */
    @Nullable
    private ProfileResolver profileResolver;

    /** KubeJS event dispatcher; null when KubeJS is not loaded. */
    @Nullable
    private AHEventDispatcher ahEventDispatcher;

    /** System UUID used as a "from" identity for tax/deposit withdrawals. */
    private static final UUID SYSTEM_UUID = new UUID(0, 0);

    public AuctionService(AuctionStorageProvider storage,
                          EconomyProvider economy,
                          CurrencyRegistry currencies) {
        this.storage = storage;
        this.economy = economy;
        this.currencies = currencies;
    }

    /** Sets the notification sender (called from AHServerEvents when server starts/stops). */
    public void setNotificationSender(@Nullable NotificationSender sender) {
        this.notificationSender = sender;
    }

    /** Sets the profile resolver (called from AHServerEvents when server starts/stops). */
    public void setProfileResolver(@Nullable ProfileResolver resolver) {
        this.profileResolver = resolver;
    }

    /** Sets the KubeJS event dispatcher (called from AHServerEvents when KubeJS is loaded). */
    public void setAHEventDispatcher(@Nullable AHEventDispatcher dispatcher) {
        this.ahEventDispatcher = dispatcher;
    }

    /**
     * Sends a notification to a player if a sender is configured.
     * Silently does nothing if no sender is set.
     */
    private void sendNotification(UUID playerUuid, String eventType, String itemName,
                                   String otherPlayerName, long amount, String currencyId) {
        if (notificationSender == null) return;
        notificationSender.send(playerUuid, eventType, itemName, otherPlayerName, amount, currencyId);
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
            @Nullable String fingerprint,
            @Nullable String ahId,
            int taxPermOverride,
            int depositPermOverride) {

        // --- Validation ---
        if (quantity <= 0) throw new AuctionException("La quantité doit être positive");
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0)
            throw new AuctionException("Le prix doit être positif");
        if (durationHours <= 0) throw new AuctionException("La durée doit être positive");

        Currency currency = currencies.getById(currencyId);
        if (currency == null) throw new AuctionException("Devise inconnue : " + currencyId);

        long priceUnits = toSmallestUnit(price, currency);
        long totalPrice = priceUnits * quantity;
        String effectiveAhId = ahId != null ? ahId : AHInstance.DEFAULT_ID;
        long depositUnits = Math.max(1, (long) (totalPrice * getDepositRate(effectiveAhId, depositPermOverride)));
        long taxUnits = Math.max(1, (long) (totalPrice * getTaxRate(effectiveAhId, taxPermOverride)));

        LOGGER.info("CREATE LISTING: seller={} item={} qty={} unitPrice={} totalPrice={} deposit={} tax={}",
                sellerName, itemName, quantity, priceUnits, totalPrice, depositUnits, taxUnits);

        // KubeJS PRE event
        if (ahEventDispatcher != null &&
            !ahEventDispatcher.fireListingCreating(sellerUuid, itemId, itemName, quantity,
                    priceUnits, listingType, effectiveAhId)) {
            throw new AuctionException("Mise en vente bloquée par un script");
        }

        // --- Withdraw deposit ---
        BigDecimal depositBD = fromSmallestUnit(depositUnits, currency);
        if (depositBD.compareTo(BigDecimal.ZERO) > 0) {
            var result = economy.withdraw(sellerUuid, depositBD, currency);
            if (!result.successful()) {
                LOGGER.error("CREATE LISTING FAILED: insufficient funds. seller={} deposit={}", sellerName, depositBD);
                throw new AuctionException("Fonds insuffisants pour le dépôt de l'annonce : " + result.errorMessage());
            }
            LOGGER.info("Deposit withdrawn: {} {} from {}", depositBD, currency.symbol(), sellerName);

            // Send deposit to tax recipient if configured
            creditTaxRecipient(effectiveAhId, depositUnits, currency);
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
                ahId != null ? ahId : AHInstance.DEFAULT_ID
        );

        storage.createListing(listing);

        // KubeJS POST event
        if (ahEventDispatcher != null) {
            ahEventDispatcher.fireListingCreated(listing);
        }

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
                    true, // auto-collected (already withdrawn)
                    null
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
    public void buyListing(UUID buyerUuid, String buyerName, String listingId, int buyQuantity, int taxPermOverride) {
        AuctionListing listing = requireActiveListingById(listingId);
        if (listing.buyoutPrice() <= 0)
            throw new AuctionException("L'annonce n'a pas de prix d'achat immédiat");
        if (listing.sellerUuid().equals(buyerUuid))
            throw new AuctionException("Vous ne pouvez pas acheter votre propre annonce");
        if (buyQuantity <= 0 || buyQuantity > listing.quantity())
            throw new AuctionException("Quantité invalide (demandé " + buyQuantity + ", disponible " + listing.quantity() + ")");

        Currency currency = requireCurrency(listing.currencyId());
        long totalPrice = listing.buyoutPrice() * buyQuantity;
        BigDecimal buyoutBD = fromSmallestUnit(totalPrice, currency);

        LOGGER.info("BUY: buyer={} item={} buyQty={}/{} unitPrice={} totalPrice={}",
                buyerName, listing.itemName(), buyQuantity, listing.quantity(), listing.buyoutPrice(), totalPrice);

        // KubeJS PRE event
        if (ahEventDispatcher != null &&
            !ahEventDispatcher.fireBuying(buyerUuid, listing, buyQuantity, totalPrice)) {
            throw new AuctionException("Achat bloqué par un script");
        }

        // Withdraw from buyer (unit price × buy quantity)
        var withdrawResult = economy.withdraw(buyerUuid, buyoutBD, currency);
        if (!withdrawResult.successful()) {
            LOGGER.error("BUY FAILED: insufficient funds. buyer={} needed={}", buyerName, buyoutBD);
            throw new AuctionException("Fonds insuffisants : " + withdrawResult.errorMessage());
        }
        LOGGER.info("Buyer charged: {} {}", buyoutBD, currency.symbol());

        // Credit seller (total price - proportional tax)
        String effectiveAhId = listing.ahId() != null ? listing.ahId() : AHInstance.DEFAULT_ID;
        long proportionalTax = Math.max(1, (long) (totalPrice * getTaxRate(effectiveAhId, taxPermOverride)));
        long sellerAmountUnits = totalPrice - proportionalTax;
        BigDecimal sellerAmountBD = fromSmallestUnit(sellerAmountUnits, currency);
        if (sellerAmountBD.compareTo(BigDecimal.ZERO) > 0) {
            economy.deposit(listing.sellerUuid(), sellerAmountBD, currency);
        }
        LOGGER.info("Seller credited: {} {} (tax: {} {})", sellerAmountBD, currency.symbol(), proportionalTax, currency.symbol());

        // Send sale tax to tax recipient if configured
        creditTaxRecipient(effectiveAhId, proportionalTax, currency);

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
                false,
                listing.ahId()
        ));
        LOGGER.info("Parcel created: {}x {} for buyer {}", buyQuantity, listing.itemName(), buyerName);

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
                true, // auto-collected (already deposited)
                listing.ahId()
        ));

        // Update listing: if all bought → SOLD, otherwise reduce quantity
        int remaining = listing.quantity() - buyQuantity;
        if (remaining <= 0) {
            storage.updateListingBid(listingId, listing.buyoutPrice(), buyerUuid);
            storage.completeSale(listingId);
            LOGGER.info("Listing {} SOLD (fully purchased)", listingId.substring(0, 8));
        } else {
            storage.updateListingQuantity(listingId, remaining);
            LOGGER.info("Listing {} qty reduced: {} -> {}", listingId.substring(0, 8), listing.quantity(), remaining);
        }

        // KubeJS POST event
        if (ahEventDispatcher != null) {
            ahEventDispatcher.fireSold(buyerUuid, buyerName, listing, buyQuantity, totalPrice, proportionalTax);
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
            throw new AuctionException("L'annonce n'est pas une enchère");
        if (listing.sellerUuid().equals(bidderUuid))
            throw new AuctionException("Vous ne pouvez pas enchérir sur votre propre annonce");

        Currency currency = requireCurrency(listing.currencyId());
        long amountUnits = toSmallestUnit(amount, currency);

        long minimumBid = listing.currentBid() > 0 ? listing.currentBid() + 1 : listing.startingBid();
        if (amountUnits < minimumBid)
            throw new AuctionException("L'enchère doit être d'au moins " + fromSmallestUnit(minimumBid, currency).toPlainString());

        // KubeJS PRE event
        if (ahEventDispatcher != null &&
            !ahEventDispatcher.fireBidPlacing(bidderUuid, listing, amountUnits)) {
            throw new AuctionException("Enchère bloquée par un script");
        }

        // Withdraw new bid from bidder
        var withdrawResult = economy.withdraw(bidderUuid, amount, currency);
        if (!withdrawResult.successful())
            throw new AuctionException("Fonds insuffisants : " + withdrawResult.errorMessage());

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
                    true, // auto-collected since we credited directly
                    listing.ahId()
            ));
            // Notify outbid player
            sendNotification(listing.currentBidderUuid(), "outbid",
                    listing.itemName(), bidderName, amountUnits, listing.currencyId());
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

        // KubeJS POST event
        if (ahEventDispatcher != null) {
            ahEventDispatcher.fireBidPlaced(bidderUuid, bidderName, listing, amountUnits,
                    listing.currentBid(), listing.currentBidderUuid());
        }
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
            throw new AuctionException("Vous n'êtes pas le propriétaire de cette annonce");
        if (listing.listingType() == ListingType.AUCTION && listing.currentBid() > 0)
            throw new AuctionException("Impossible d'annuler une enchère ayant déjà des offres");

        // KubeJS PRE event
        if (ahEventDispatcher != null &&
            !ahEventDispatcher.fireListingCancelling(playerUuid, listing)) {
            throw new AuctionException("Annulation bloquée par un script");
        }

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
                false,
                listing.ahId()
        ));

        storage.cancelListing(listingId);

        // KubeJS POST event
        if (ahEventDispatcher != null) {
            ahEventDispatcher.fireListingCancelled(playerUuid, listing);
        }
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
                        false,
                        listing.ahId()
                ));
                // Notify seller that their listing expired with no bids
                sendNotification(listing.sellerUuid(), "listing_expired",
                        listing.itemName(), "", 0L, listing.currencyId());

                // KubeJS POST event — expired with no bids
                if (ahEventDispatcher != null) {
                    ahEventDispatcher.fireListingExpired(listing, false);
                }
            }
        }
    }

    private void completedAuctionSale(AuctionListing listing, long now) {
        Currency currency = currencies.getById(listing.currencyId());
        if (currency == null) return;

        // Credit seller (final bid - recalculated tax based on final bid, not starting bid)
        // Use -1 for permission override since the buyer may be offline during auction expiry
        String effectiveAhId = listing.ahId() != null ? listing.ahId() : AHInstance.DEFAULT_ID;
        long proportionalTax = Math.max(1, (long) (listing.currentBid() * getTaxRate(effectiveAhId, -1)));
        long sellerAmount = listing.currentBid() - proportionalTax;
        if (sellerAmount > 0) {
            economy.deposit(listing.sellerUuid(), fromSmallestUnit(sellerAmount, currency), currency);
        }

        // Send sale tax to tax recipient if configured
        creditTaxRecipient(effectiveAhId, proportionalTax, currency);

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
                false,
                listing.ahId()
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

        // Resolve winner name from highest bid record (the currentBidderUuid is set on the listing)
        AuctionBid highestBid = storage.getHighestBid(listing.id());
        String winnerName = highestBid != null ? highestBid.bidderName() : "";

        // Notify winner
        sendNotification(listing.currentBidderUuid(), "auction_won",
                listing.itemName(), listing.sellerName(), listing.currentBid(), listing.currencyId());

        // KubeJS POST event — auction won
        if (ahEventDispatcher != null) {
            ahEventDispatcher.fireAuctionWon(listing.currentBidderUuid(),
                    winnerName, listing, listing.currentBid());
        }

        // Notify seller
        sendNotification(listing.sellerUuid(), "sale_completed",
                listing.itemName(), winnerName, listing.currentBid(), listing.currencyId());

        // Notify losing bidders
        List<AuctionBid> allBids = storage.getBidsForListing(listing.id());
        for (AuctionBid bid : allBids) {
            if (!bid.bidderUuid().equals(listing.currentBidderUuid())) {
                sendNotification(bid.bidderUuid(), "auction_lost",
                        listing.itemName(), "", bid.amount(), listing.currencyId());

                // KubeJS POST event — auction lost
                if (ahEventDispatcher != null) {
                    ahEventDispatcher.fireAuctionLost(bid.bidderUuid(),
                            bid.bidderName(), listing, bid.amount());
                }
            }
        }

        // KubeJS POST event — listing expired with bids (auction completed)
        if (ahEventDispatcher != null) {
            ahEventDispatcher.fireListingExpired(listing, true);
        }
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

    public List<AuctionBid> getBidsForListing(String listingId) {
        return storage.getBidsForListing(listingId);
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
        BigDecimal balance = economy.getVirtualBalance(playerUuid, currency);
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
        if (listing == null) throw new AuctionException("Annonce introuvable : " + listingId);
        if (listing.status() != ListingStatus.ACTIVE)
            throw new AuctionException("L'annonce n'est pas active (statut=" + listing.status() + ")");
        if (listing.isExpired())
            throw new AuctionException("L'annonce a expiré");
        return listing;
    }

    private Currency requireCurrency(String currencyId) {
        Currency c = currencies.getById(currencyId);
        if (c == null) throw new AuctionException("Devise inconnue : " + currencyId);
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
