package net.ecocraft.ah.data;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a pending delivery to a player (item or currency).
 *
 * <p>A parcel is created whenever the auction house needs to deliver something to a player:
 * <ul>
 *   <li>Items purchased (sent to buyer)</li>
 *   <li>Currency from a sale (sent to seller)</li>
 *   <li>Items returned after expiry or cancellation (sent to seller)</li>
 *   <li>Bid refunds when outbid (sent to previous bidder)</li>
 * </ul>
 * </p>
 *
 * <p>Monetary amounts are stored as {@code long} in the smallest currency unit.</p>
 */
public record AuctionParcel(
        /** Unique parcel identifier. */
        String id,
        /** UUID of the player who should receive this parcel. */
        UUID recipientUuid,
        /** Registry name of the item (null for currency-only parcels). */
        @Nullable String itemId,
        /** Human-readable item name (null for currency-only parcels). */
        @Nullable String itemName,
        /** Serialised NBT/component data (null if no data or currency parcel). */
        @Nullable String itemNbt,
        /** Item quantity (0 for currency-only parcels). */
        int quantity,
        /** Currency amount in smallest unit (0 for item-only parcels). */
        long amount,
        /** Currency identifier (null for item-only parcels). */
        @Nullable String currencyId,
        /** What triggered the creation of this parcel. */
        ParcelSource source,
        /** Creation timestamp as epoch milliseconds. */
        long createdAt,
        /** Whether the player has already collected this parcel. */
        boolean collected
) {

    /** Returns {@code true} if this parcel contains an item to deliver. */
    public boolean hasItem() {
        return itemId != null && quantity > 0;
    }

    /** Returns {@code true} if this parcel contains currency to deliver. */
    public boolean hasCurrency() {
        return currencyId != null && amount > 0;
    }

    /** Returns a copy of this parcel marked as collected. */
    public AuctionParcel asCollected() {
        return new AuctionParcel(id, recipientUuid, itemId, itemName, itemNbt,
                quantity, amount, currencyId, source, createdAt, true);
    }
}
