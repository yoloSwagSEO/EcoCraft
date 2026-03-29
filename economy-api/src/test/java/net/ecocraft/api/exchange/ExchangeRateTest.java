package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRateTest {

    private static final Currency GOLD = Currency.virtual("gold", "Or", "⛁", 2);
    private static final Currency SILVER = Currency.virtual("silver", "Argent", "∘", 0);

    @Test
    void convertWithoutFee() {
        var rate = new ExchangeRate(GOLD, SILVER, new BigDecimal("100"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("500"), rate.convert(new BigDecimal("5")));
    }

    @Test
    void convertWithFee() {
        var rate = new ExchangeRate(GOLD, SILVER, new BigDecimal("100"), new BigDecimal("0.05"));
        assertEquals(new BigDecimal("950.00"), rate.convert(new BigDecimal("10")).setScale(2, RoundingMode.HALF_UP));
    }

    @Test
    void rateMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
            new ExchangeRate(GOLD, SILVER, BigDecimal.ZERO, BigDecimal.ZERO)
        );
    }

    @Test
    void feeCannotBeNegative() {
        assertThrows(IllegalArgumentException.class, () ->
            new ExchangeRate(GOLD, SILVER, BigDecimal.ONE, new BigDecimal("-0.1"))
        );
    }

    @Test
    void cannotExchangeSameCurrency() {
        assertThrows(IllegalArgumentException.class, () ->
            new ExchangeRate(GOLD, GOLD, BigDecimal.ONE, BigDecimal.ZERO)
        );
    }

    // C1: ExchangeRate.convert() rounding tests

    @Test
    void convertRoundsToTargetCurrencyDecimals() {
        // 10.50 * 3.33 = 34.965 → rounds to 34.97 (2 decimals)
        var rate = new ExchangeRate(SILVER, GOLD, new BigDecimal("3.33"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("34.97"), rate.convert(new BigDecimal("10.50")));
    }

    @Test
    void convertRoundsToZeroDecimalsForIntegerCurrency() {
        // 10.50 * 3.33 = 34.965 → rounds to 35 (0 decimals)
        var rate = new ExchangeRate(GOLD, SILVER, new BigDecimal("3.33"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("35"), rate.convert(new BigDecimal("10.50")));
    }

    @Test
    void convertWithFeeProducesCorrectlyRoundedResult() {
        // 10 * 3.33 = 33.30, fee 5% = 1.665, result = 31.635 → rounds to 31.64 (2 decimals)
        var rate = new ExchangeRate(SILVER, GOLD, new BigDecimal("3.33"), new BigDecimal("0.05"));
        assertEquals(new BigDecimal("31.64"), rate.convert(new BigDecimal("10")));
    }
}
