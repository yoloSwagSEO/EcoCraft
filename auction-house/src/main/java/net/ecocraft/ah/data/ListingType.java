package net.ecocraft.ah.data;

public enum ListingType {
    /** Fixed buy-now price. First buyer gets the item. */
    BUYOUT,
    /** Auction with a starting bid and optional buyout. Highest bidder wins at expiry. */
    AUCTION
}
