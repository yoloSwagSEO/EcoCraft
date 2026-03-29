package net.ecocraft.api.transaction;

import net.ecocraft.api.Page;

/** Read-only access to transaction history. */
public interface TransactionLog {

    /**
     * Returns a paginated slice of the transaction history matching the filter.
     * @param filter the filter criteria (player, type, date range, pagination)
     * @return a page of matching transactions
     */
    Page<Transaction> getHistory(TransactionFilter filter);
}
