package net.ecocraft.api.currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.StringJoiner;

/**
 * Formats monetary amounts according to their Currency definition.
 * <p>
 * Simple currencies use decimal formatting; composite currencies split
 * into sub-units and display each non-zero denomination.
 */
public final class CurrencyFormatter {

    private CurrencyFormatter() {}

    /**
     * Formats an amount in the smallest unit into a human-readable string.
     * <p>
     * Simple currency example: {@code format(150, gold2dec)} → {@code "1.50 G"}<br>
     * Composite example: {@code format(1500, wow)} → {@code "1 PP 5 PO"}
     */
    public static String format(long amount, Currency currency) {
        if (currency.isComposite()) {
            return formatComposite(amount, currency);
        }
        return formatSimple(amount, currency);
    }

    /**
     * Splits an amount into parts for each sub-unit (highest to lowest).
     * Only meaningful for composite currencies; simple currencies return a single-element array.
     */
    public static long[] split(long amount, Currency currency) {
        if (!currency.isComposite()) {
            return new long[]{amount};
        }
        List<SubUnit> units = currency.subUnits();
        long[] parts = new long[units.size()];
        long remaining = amount;
        for (int i = 0; i < units.size(); i++) {
            long mult = units.get(i).multiplier();
            parts[i] = remaining / mult;
            remaining = remaining % mult;
        }
        return parts;
    }

    /**
     * Combines sub-unit parts back into a single amount in the smallest unit.
     */
    public static long combine(long[] parts, Currency currency) {
        if (!currency.isComposite()) {
            return parts.length > 0 ? parts[0] : 0;
        }
        List<SubUnit> units = currency.subUnits();
        long total = 0;
        for (int i = 0; i < Math.min(parts.length, units.size()); i++) {
            total += parts[i] * units.get(i).multiplier();
        }
        return total;
    }

    /**
     * Converts a display-level BigDecimal amount to the smallest storage unit.
     * E.g. with decimals=2: 150.50 → 15050
     */
    public static long toSmallestUnit(BigDecimal displayAmount, Currency currency) {
        return displayAmount.movePointRight(currency.decimals())
                .setScale(0, RoundingMode.DOWN)
                .longValueExact();
    }

    /**
     * Converts a smallest-unit long back to a display-level BigDecimal.
     * E.g. with decimals=2: 15050 → 150.50
     */
    public static BigDecimal fromSmallestUnit(long smallestUnit, Currency currency) {
        return BigDecimal.valueOf(smallestUnit)
                .movePointLeft(currency.decimals())
                .setScale(currency.decimals(), RoundingMode.DOWN);
    }

    // ---- internals ----

    private static String formatSimple(long amount, Currency currency) {
        int dec = currency.decimals();
        if (dec <= 0) {
            return amount + " " + currency.symbol();
        }
        long divisor = pow10(dec);
        long whole = amount / divisor;
        long frac = amount % divisor;
        String fracStr = String.format("%0" + dec + "d", frac);
        return whole + "." + fracStr + " " + currency.symbol();
    }

    private static String formatComposite(long amount, Currency currency) {
        long[] parts = split(amount, currency);
        List<SubUnit> units = currency.subUnits();

        StringJoiner joiner = new StringJoiner(" ");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != 0) {
                joiner.add(parts[i] + " " + units.get(i).code());
            }
        }
        // If everything is zero, show "0 <smallest unit>"
        if (joiner.length() == 0) {
            return "0 " + units.get(units.size() - 1).code();
        }
        return joiner.toString();
    }

    private static long pow10(int exp) {
        long result = 1;
        for (int i = 0; i < exp; i++) result *= 10;
        return result;
    }
}
