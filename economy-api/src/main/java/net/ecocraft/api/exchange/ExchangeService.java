package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Service for converting amounts between currencies. */
public interface ExchangeService {

    /**
     * Converts the given amount from one currency to another for the player.
     * @param player the player UUID
     * @param amount the amount to convert (in the source currency)
     * @param from the source currency
     * @param to the target currency
     * @return a result indicating success or failure
     */
    TransactionResult convert(UUID player, BigDecimal amount, Currency from, Currency to);

    /**
     * Returns the exchange rate between two currencies, or null if none is configured.
     * @param from the source currency
     * @param to the target currency
     * @return the exchange rate, or null
     */
    @Nullable ExchangeRate getRate(Currency from, Currency to);

    /**
     * Returns all configured exchange rates.
     * @return an immutable list of exchange rates
     */
    List<ExchangeRate> listRates();
}
