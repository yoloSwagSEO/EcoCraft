package net.ecocraft.api.currency;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyTest {

    @Test
    void virtualCurrencyHasCorrectDefaults() {
        var gold = Currency.virtual("gold", "Or", "⛁", 2);
        assertEquals("gold", gold.id());
        assertEquals("Or", gold.name());
        assertEquals("⛁", gold.symbol());
        assertEquals(2, gold.decimals());
        assertFalse(gold.physical());
        assertNull(gold.itemId());
    }

    @Test
    void physicalCurrencyLinksToItem() {
        var coins = Currency.physical("coins", "Pièces", "$", 0, "lightmanscurrency:coin_gold");
        assertEquals("coins", coins.id());
        assertTrue(coins.physical());
        assertEquals("lightmanscurrency:coin_gold", coins.itemId());
    }

    @Test
    void decimalsCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            Currency.virtual("bad", "Bad", "X", -1)
        );
    }

    @Test
    void idCannotBeBlank() {
        assertThrows(IllegalArgumentException.class, () ->
            Currency.virtual("", "Empty", "X", 0)
        );
    }
}
