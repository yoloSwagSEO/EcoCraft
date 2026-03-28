package net.ecocraft.core.storage;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DatabaseProvider {

    void initialize();
    void shutdown();

    // Balances
    BigDecimal getVirtualBalance(UUID player, String currencyId);
    void setVirtualBalance(UUID player, String currencyId, BigDecimal amount);

    /** Returns all balances for a currency, sorted by amount descending. */
    List<BalanceEntry> getAllBalances(String currencyId);

    record BalanceEntry(UUID playerUuid, String currencyId, BigDecimal amount) {}

    // Transactions
    void logTransaction(UUID txId, @Nullable UUID from, @Nullable UUID to,
                        BigDecimal amount, String currencyId, String type, Instant timestamp);

    List<TransactionRecord> getTransactionHistory(UUID player, @Nullable String type,
                                                   @Nullable Instant from, @Nullable Instant to,
                                                   int offset, int limit);

    long getTransactionCount(UUID player, @Nullable String type,
                             @Nullable Instant from, @Nullable Instant to);

    record TransactionRecord(
        UUID id, @Nullable UUID from, @Nullable UUID to,
        BigDecimal amount, String currencyId, String type, Instant timestamp
    ) {}
}
