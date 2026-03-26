package net.ecocraft.ah.data;

public enum ParcelSource {
    /** Currency proceeds from a completed sale (sent to seller). */
    HDV_SALE,
    /** Item purchased by a buyer (sent to buyer). */
    HDV_PURCHASE,
    /** Item returned after a listing expired unsold (sent to seller). */
    HDV_EXPIRED,
    /** Refund of a previous bid that was outbid by a higher offer (sent to previous bidder). */
    HDV_OUTBID,
    /** Listing fee (deposit) charged when creating a listing. */
    HDV_LISTING_FEE
}
