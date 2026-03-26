package net.ecocraft.api.transaction;

import net.ecocraft.api.Page;

public interface TransactionLog {
    Page<Transaction> getHistory(TransactionFilter filter);
}
