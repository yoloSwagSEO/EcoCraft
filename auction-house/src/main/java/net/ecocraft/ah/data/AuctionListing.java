package net.ecocraft.ah.data;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a listing in the auction house.
 *
 * <p>All monetary amounts (buyoutPrice, startingBid, currentBid, taxAmount) are stored as
 * {@code long} in the smallest currency unit (e.g. cents if the currency has 2 decimals).
 * Conversion to/from {@link java.math.BigDecimal} happens at the service layer boundary.</p>
 */
public record AuctionListing(
        /** Unique listing identifier. */
        String id,
        /** UUID of the player who created the listing. */
        UUID sellerUuid,
        /** Display name of the seller at listing creation time. */
        String sellerName,
        /** Registry name of the item (e.g. {@code minecraft:diamond_sword}). */
        String itemId,
        /** Human-readable item name (may include custom name). */
        String itemName,
        /** Serialised NBT/component data of the item stack. Null for plain items. */
        @Nullable String itemNbt,
        /** Number of items in this listing. */
        int quantity,
        /** Whether this is a fixed-price buyout or a timed auction. */
        ListingType listingType,
        /** Fixed buy-now price in smallest currency unit. 0 means no buyout. */
        long buyoutPrice,
        /** Minimum starting bid in smallest currency unit (auction only). */
        long startingBid,
        /** Current highest bid in smallest currency unit. 0 if no bids yet. */
        long currentBid,
        /** UUID of the current highest bidder. Null if no bids placed. */
        @Nullable UUID currentBidderUuid,
        /** Identifier of the currency used for this listing. */
        String currencyId,
        /** Item category for browsing/filtering. */
        ItemCategory category,
        /** Expiry timestamp as epoch milliseconds. */
        long expiresAt,
        /** Current lifecycle status. */
        ListingStatus status,
        /** Tax charged when the listing was created, in smallest currency unit. */
        long taxAmount,
        /** Creation timestamp as epoch milliseconds. */
        long createdAt,
        /** Fingerprint of significant item components (enchantments, potions, custom name). */
        @Nullable String itemFingerprint,
        /** Auction House instance this listing belongs to. Null means default AH. */
        @Nullable String ahId
) {

    /**
     * Returns a copy of this listing with an updated status.
     */
    public AuctionListing withStatus(ListingStatus newStatus) {
        return new AuctionListing(id, sellerUuid, sellerName, itemId, itemName, itemNbt,
                quantity, listingType, buyoutPrice, startingBid, currentBid, currentBidderUuid,
                currencyId, category, expiresAt, newStatus, taxAmount, createdAt, itemFingerprint, ahId);
    }

    /**
     * Returns a copy of this listing with an updated bid.
     */
    public AuctionListing withBid(long newBid, UUID bidderUuid) {
        return new AuctionListing(id, sellerUuid, sellerName, itemId, itemName, itemNbt,
                quantity, listingType, buyoutPrice, startingBid, newBid, bidderUuid,
                currencyId, category, expiresAt, status, taxAmount, createdAt, itemFingerprint, ahId);
    }

    /** Returns {@code true} if this listing has expired (current time past {@link #expiresAt}). */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
