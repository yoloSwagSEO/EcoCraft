package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ExchangeRate(
        Currency from,
        Currency to,
        BigDecimal rate,
        BigDecimal fee
) {
    public ExchangeRate {
        if (from.id().equals(to.id())) {
            throw new IllegalArgumentException("Cannot exchange same currency");
        }
        if (rate.signum() <= 0) {
            throw new IllegalArgumentException("Rate must be positive");
        }
        if (fee.signum() < 0) {
            throw new IllegalArgumentException("Fee cannot be negative");
        }
    }

    public BigDecimal convert(BigDecimal amount) {
        var converted = amount.multiply(rate);
        if (fee.signum() > 0) {
            var feeAmount = converted.multiply(fee);
            converted = converted.subtract(feeAmount);
        }
        converted = converted.setScale(to.decimals(), RoundingMode.HALF_UP);
        return converted;
    }
}
