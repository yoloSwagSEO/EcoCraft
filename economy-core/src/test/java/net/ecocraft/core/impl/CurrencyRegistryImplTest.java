package net.ecocraft.core.impl;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyRegistryImplTest {

    private CurrencyRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new CurrencyRegistryImpl();
    }

    @Test
    void registerAndRetrieve() {
        var gold = Currency.virtual("gold", "Gold", "\u26C1", 2);
        registry.register(gold);
        assertEquals(gold, registry.getById("gold"));
        assertTrue(registry.exists("gold"));
    }

    @Test
    void firstRegisteredIsDefault() {
        var gold = Currency.virtual("gold", "Gold", "\u26C1", 2);
        var silver = Currency.virtual("silver", "Silver", "\u2218", 0);
        registry.register(gold);
        registry.register(silver);
        assertEquals(gold, registry.getDefault());
    }

    @Test
    void setExplicitDefault() {
        var gold = Currency.virtual("gold", "Gold", "\u26C1", 2);
        var silver = Currency.virtual("silver", "Silver", "\u2218", 0);
        registry.register(gold);
        registry.register(silver);
        registry.setDefault("silver");
        assertEquals(silver, registry.getDefault());
    }

    @Test
    void listAllCurrencies() {
        registry.register(Currency.virtual("gold", "Gold", "\u26C1", 2));
        registry.register(Currency.virtual("silver", "Silver", "\u2218", 0));
        assertEquals(2, registry.listAll().size());
    }

    @Test
    void unknownCurrencyReturnsNull() {
        assertNull(registry.getById("nonexistent"));
        assertFalse(registry.exists("nonexistent"));
    }
}
