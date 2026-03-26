package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.transaction.TransactionResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExchangeServiceImpl implements ExchangeService {

    private final EconomyProvider economy;
    private final Map<String, ExchangeRate> rates = new ConcurrentHashMap<>();

    public ExchangeServiceImpl(EconomyProvider economy) {
        this.economy = economy;
    }

    public void registerRate(ExchangeRate rate) {
        rates.put(rateKey(rate.from(), rate.to()), rate);
    }

    @Override
    public TransactionResult convert(UUID player, BigDecimal amount, Currency from, Currency to) {
        ExchangeRate rate = getRate(from, to);
        if (rate == null) {
            return TransactionResult.failure("No exchange rate found for " + from.id() + " -> " + to.id());
        }

        var withdrawResult = economy.withdraw(player, amount, from);
        if (!withdrawResult.successful()) {
            return withdrawResult;
        }

        BigDecimal converted = rate.convert(amount);
        return economy.deposit(player, converted, to);
    }

    @Override
    public @Nullable ExchangeRate getRate(Currency from, Currency to) {
        return rates.get(rateKey(from, to));
    }

    @Override
    public List<ExchangeRate> listRates() {
        return List.copyOf(rates.values());
    }

    private String rateKey(Currency from, Currency to) {
        return from.id() + "->" + to.id();
    }
}
