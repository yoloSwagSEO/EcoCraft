package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.storage.DatabaseProvider;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExchangeServiceImpl implements ExchangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExchangeServiceImpl.class);

    /**
     * Simple configuration holder for exchange settings, decoupled from NeoForge config
     * so the service can be unit-tested without ModConfigSpec.
     */
    public record ExchangeConfig(
        double globalFeePercent,
        long minAmount,
        long maxAmount,
        long dailyLimitPerPlayer
    ) {
        /** Default config with no limits and 2% fee. */
        public static final ExchangeConfig DEFAULT = new ExchangeConfig(2.0, 0, 0, 0);
    }

    private final EconomyProvider economy;
    private final DatabaseProvider db;
    private final CurrencyRegistry registry;
    private final ExchangeConfig config;
    private final Map<String, ExchangeRate> rates = new ConcurrentHashMap<>();

    public ExchangeServiceImpl(EconomyProvider economy, DatabaseProvider db, CurrencyRegistry registry) {
        this(economy, db, registry, ExchangeConfig.DEFAULT);
    }

    public ExchangeServiceImpl(EconomyProvider economy, DatabaseProvider db, CurrencyRegistry registry,
                               ExchangeConfig config) {
        this.economy = economy;
        this.db = db;
        this.registry = registry;
        this.config = config;
    }

    /**
     * Loads all persisted exchange rates from the database into memory.
     */
    public void loadRatesFromStorage() {
        var storedRates = db.getAllExchangeRates();
        for (var stored : storedRates) {
            Currency from = registry.getById(stored.fromCurrency());
            Currency to = registry.getById(stored.toCurrency());
            if (from == null || to == null) {
                LOGGER.warn("Skipping persisted exchange rate {}->{}: currency not found in registry",
                    stored.fromCurrency(), stored.toCurrency());
                continue;
            }
            rates.put(rateKey(from, to), new ExchangeRate(from, to, stored.rate(), stored.feeRate()));
        }
        LOGGER.info("Loaded {} exchange rates from storage", storedRates.size());
    }

    public void registerRate(ExchangeRate rate) {
        rates.put(rateKey(rate.from(), rate.to()), rate);
    }

    /**
     * Persists an exchange rate to the database and updates the in-memory cache.
     */
    public void saveRate(String fromId, String toId, BigDecimal rate, BigDecimal fee) {
        Currency from = registry.getById(fromId);
        Currency to = registry.getById(toId);
        if (from == null || to == null) {
            throw new IllegalArgumentException("Unknown currency: " + (from == null ? fromId : toId));
        }
        db.saveExchangeRate(fromId, toId, rate, fee);
        rates.put(rateKey(from, to), new ExchangeRate(from, to, rate, fee));
    }

    /**
     * Removes an exchange rate from the database and in-memory cache.
     */
    public void removeRate(String fromId, String toId) {
        Currency from = registry.getById(fromId);
        Currency to = registry.getById(toId);
        db.deleteExchangeRate(fromId, toId);
        if (from != null && to != null) {
            rates.remove(rateKey(from, to));
        }
    }

    @Override
    public TransactionResult convert(UUID player, BigDecimal amount, Currency from, Currency to) {
        // Validate exchangeability
        if (!from.exchangeable() || !to.exchangeable()) {
            return TransactionResult.failure("Currency is not exchangeable: "
                + (!from.exchangeable() ? from.id() : to.id()));
        }

        ExchangeRate rate = getRate(from, to);
        if (rate == null) {
            return TransactionResult.failure("No exchange rate found for " + from.id() + " -> " + to.id());
        }

        // Validate min/max amount
        long minAmount = config.minAmount();
        if (minAmount > 0 && amount.compareTo(BigDecimal.valueOf(minAmount)) < 0) {
            return TransactionResult.failure("Amount below minimum exchange limit: " + minAmount);
        }
        long maxAmount = config.maxAmount();
        if (maxAmount > 0 && amount.compareTo(BigDecimal.valueOf(maxAmount)) > 0) {
            return TransactionResult.failure("Amount above maximum exchange limit: " + maxAmount);
        }

        // Check daily limit
        long dailyLimit = config.dailyLimitPerPlayer();
        if (dailyLimit > 0) {
            long todayTotal = db.getDailyExchangeTotal(player.toString(), from.id(), to.id());
            if (todayTotal + amount.longValue() > dailyLimit) {
                return TransactionResult.failure("Daily exchange limit exceeded (limit: " + dailyLimit
                    + ", already exchanged today: " + todayTotal + ")");
            }
        }

        var withdrawResult = economy.withdraw(player, amount, from);
        if (!withdrawResult.successful()) {
            return withdrawResult;
        }

        try {
            BigDecimal converted = rate.convert(amount);
            var depositResult = economy.deposit(player, converted, to);
            if (!depositResult.successful()) {
                // Rollback: re-deposit withdrawn amount
                economy.deposit(player, amount, from);
                return depositResult;
            }

            // Record daily exchange
            db.recordDailyExchange(player.toString(), from.id(), to.id(), amount.longValue());

            // Log transaction
            db.logTransaction(UUID.randomUUID(), player, null, amount, from.id(),
                TransactionType.EXCHANGE.name(), Instant.now());

            return depositResult;
        } catch (Exception e) {
            // Rollback: re-deposit withdrawn amount
            economy.deposit(player, amount, from);
            throw e;
        }
    }

    @Override
    public @Nullable ExchangeRate getRate(Currency from, Currency to) {
        // First check persisted/manual rates
        ExchangeRate direct = rates.get(rateKey(from, to));
        if (direct != null) {
            return direct;
        }

        // Compute cross-rate if both currencies are exchangeable with a reference rate
        if (from.exchangeable() && to.exchangeable()
                && from.referenceRate().signum() > 0 && to.referenceRate().signum() > 0) {
            BigDecimal crossRate = from.referenceRate().divide(to.referenceRate(), 10, RoundingMode.HALF_UP);
            BigDecimal globalFee = BigDecimal.valueOf(config.globalFeePercent())
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            return new ExchangeRate(from, to, crossRate, globalFee);
        }

        return null;
    }

    @Override
    public List<ExchangeRate> listRates() {
        return List.copyOf(rates.values());
    }

    private String rateKey(Currency from, Currency to) {
        return from.id() + "->" + to.id();
    }
}
