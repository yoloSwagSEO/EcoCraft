package net.ecocraft.ah.data;

public enum ListingStatus {
    /** Currently visible and available to buy/bid. */
    ACTIVE,
    /** Successfully sold (buyout purchased or auction completed). */
    SOLD,
    /** Time elapsed without a buyer; item returned to seller. */
    EXPIRED,
    /** Seller cancelled the listing manually. */
    CANCELLED
}
