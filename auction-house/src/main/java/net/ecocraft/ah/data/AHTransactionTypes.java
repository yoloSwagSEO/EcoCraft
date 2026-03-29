package net.ecocraft.ah.data;

import net.ecocraft.api.transaction.TransactionType;

/**
 * Auction-house-specific transaction types.
 */
public final class AHTransactionTypes {

    public static final TransactionType HDV_SALE = TransactionType.of("HDV_SALE");
    public static final TransactionType HDV_PURCHASE = TransactionType.of("HDV_PURCHASE");
    public static final TransactionType HDV_LISTING_FEE = TransactionType.of("HDV_LISTING_FEE");
    public static final TransactionType HDV_EXPIRED_REFUND = TransactionType.of("HDV_EXPIRED_REFUND");

    private AHTransactionTypes() {}
}
