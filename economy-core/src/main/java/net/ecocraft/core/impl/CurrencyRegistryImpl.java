package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CurrencyRegistryImpl implements CurrencyRegistry {

    private final Map<String, Currency> currencies = new ConcurrentHashMap<>();
    private String defaultId;

    @Override
    public void register(Currency currency) {
        if (currencies.containsKey(currency.id())) {
            throw new IllegalArgumentException("Currency already registered: " + currency.id());
        }
        currencies.put(currency.id(), currency);
        if (defaultId == null) {
            defaultId = currency.id();
        }
    }

    @Override
    public @Nullable Currency getById(String id) {
        return currencies.get(id);
    }

    @Override
    public Currency getDefault() {
        if (defaultId == null) {
            throw new IllegalStateException("No currencies registered");
        }
        return currencies.get(defaultId);
    }

    @Override
    public List<Currency> listAll() {
        return List.copyOf(currencies.values());
    }

    @Override
    public boolean exists(String id) {
        return currencies.containsKey(id);
    }

    @Override
    public void unregister(String id) {
        if (!currencies.containsKey(id)) {
            throw new IllegalArgumentException("Currency not registered: " + id);
        }
        if (id.equals(defaultId)) {
            throw new IllegalArgumentException("Cannot unregister the default currency: " + id);
        }
        currencies.remove(id);
    }

    public void setDefault(String id) {
        if (!currencies.containsKey(id)) {
            throw new IllegalArgumentException("Currency not registered: " + id);
        }
        this.defaultId = id;
    }
}
