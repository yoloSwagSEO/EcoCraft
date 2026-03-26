package net.ecocraft.ah.data;

import java.util.UUID;

/**
 * Represents a bid placed on an auction listing.
 *
 * <p>The {@link #amount} is stored as {@code long} in the smallest currency unit.</p>
 */
public record AuctionBid(
        /** Unique bid identifier. */
        String id,
        /** ID of the listing this bid belongs to. */
        String listingId,
        /** UUID of the player who placed the bid. */
        UUID bidderUuid,
        /** Display name of the bidder at bid time. */
        String bidderName,
        /** Bid amount in smallest currency unit. */
        long amount,
        /** Timestamp when the bid was placed, as epoch milliseconds. */
        long timestamp
) {}
