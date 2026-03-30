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

    /** Checks whether a player has an existing account row for the given currency. */
    boolean hasAccount(UUID player, String currencyId);

    // Transactions
    void logTransaction(UUID txId, @Nullable UUID from, @Nullable UUID to,
                        BigDecimal amount, String currencyId, String type, Instant timestamp);

    List<TransactionRecord> getTransactionHistory(@Nullable UUID player, @Nullable String type,
                                                   @Nullable Instant from, @Nullable Instant to,
                                                   int offset, int limit);

    long getTransactionCount(@Nullable UUID player, @Nullable String type,
                             @Nullable Instant from, @Nullable Instant to);

    record TransactionRecord(
        UUID id, @Nullable UUID from, @Nullable UUID to,
        BigDecimal amount, String currencyId, String type, Instant timestamp
    ) {}

    // --- Exchange rates ---
    record StoredExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate, BigDecimal feeRate) {}

    void saveExchangeRate(String fromCurrency, String toCurrency, BigDecimal rate, BigDecimal feeRate);
    @Nullable StoredExchangeRate getExchangeRate(String fromCurrency, String toCurrency);
    void deleteExchangeRate(String fromCurrency, String toCurrency);
    List<StoredExchangeRate> getAllExchangeRates();

    // --- Daily exchange limits ---
    void recordDailyExchange(String playerUuid, String fromCurrency, String toCurrency, long amount);
    long getDailyExchangeTotal(String playerUuid, String fromCurrency, String toCurrency);

    // --- Currencies ---
    record StoredCurrency(String id, String name, String symbol, int decimals,
                          boolean physical, @Nullable String itemId,
                          boolean exchangeable, BigDecimal referenceRate) {}

    void saveCurrency(String id, String name, String symbol, int decimals,
                      boolean physical, @Nullable String itemId,
                      boolean exchangeable, BigDecimal referenceRate);
    void deleteCurrency(String id);
    List<StoredCurrency> getAllCurrencies();
}
