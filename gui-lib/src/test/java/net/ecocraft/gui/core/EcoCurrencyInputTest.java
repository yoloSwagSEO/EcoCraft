package net.ecocraft.gui.core;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.SubUnit;
import net.ecocraft.gui.theme.Theme;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EcoCurrencyInput widget behavior.
 * <p>
 * Simple (non-composite) currencies require a Font for layout, so we test them
 * only for the null-font guard (no crash). Composite currencies can be fully
 * tested without a Font since buildCompositeLayout does not use it.
 */
class EcoCurrencyInputTest {

    private static final Theme THEME = Theme.dark();

    private static final Currency WOW_GOLD = Currency.builder("wow", "WoW Gold", "G")
        .decimals(0)
        .subUnits(
            new SubUnit("PP", "Platinum", 100),
            new SubUnit("PO", "Gold", 10),
            new SubUnit("PA", "Silver", 1)
        )
        .build();

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 2);

    @Test
    void setValueAndGetValue() {
        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.setValue(1500);
        assertEquals(1500, input.getValue());
    }

    @Test
    void setValueAndGetValue_Zero() {
        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.setValue(0);
        assertEquals(0, input.getValue());
    }

    @Test
    void minMaxClamping() {
        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.min(100).max(5000);

        input.setValue(50);
        assertEquals(100, input.getValue());

        input.setValue(9999);
        assertEquals(5000, input.getValue());

        input.setValue(2500);
        assertEquals(2500, input.getValue());
    }

    @Test
    void minMaxClamping_AtBoundaries() {
        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.min(100).max(5000);

        input.setValue(100);
        assertEquals(100, input.getValue());

        input.setValue(5000);
        assertEquals(5000, input.getValue());
    }

    @Test
    void responderCalled() {
        AtomicLong captured = new AtomicLong(-1);

        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.responder(captured::set);
        input.setValue(1234);

        assertEquals(1234, captured.get(), "Responder should be called with the new value");
    }

    @Test
    void responderCalledOnEachSetValue() {
        List<Long> calls = new ArrayList<>();

        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.responder(calls::add);

        input.setValue(100);
        input.setValue(200);
        input.setValue(300);

        assertEquals(3, calls.size());
        assertEquals(100L, calls.get(0));
        assertEquals(200L, calls.get(1));
        assertEquals(300L, calls.get(2));
    }

    @Test
    void compositeValueStorage() {
        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, WOW_GOLD, THEME);
        input.setValue(1234);
        assertEquals(1234, input.getValue());
    }

    @Test
    void simpleCurrencyWithNullFont_DoesNotCrash() {
        EcoCurrencyInput input = new EcoCurrencyInput(null, 0, 0, 200, GOLD, THEME);
        assertNotNull(input);
        input.setValue(1500);
        assertEquals(1500, input.getValue());
    }
}
