package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;
import java.util.List;

/** Registry for available currencies. */
public interface CurrencyRegistry {

    /**
     * Registers a currency. Throws {@link IllegalArgumentException} if a currency
     * with the same id is already registered.
     * @param currency the currency to register, must not be null
     */
    void register(Currency currency);

    /**
     * Returns the currency with the given id, or null if not found.
     * @param id the currency id
     * @return the currency, or null
     */
    @Nullable Currency getById(String id);

    /**
     * Returns the default currency (the first one registered).
     * @return the default currency
     * @throws IllegalStateException if no currencies are registered
     */
    Currency getDefault();

    /**
     * Returns an immutable list of all registered currencies.
     * @return all currencies
     */
    List<Currency> listAll();

    /**
     * Checks whether a currency with the given id is registered.
     * @param id the currency id
     * @return true if registered
     */
    boolean exists(String id);
}
