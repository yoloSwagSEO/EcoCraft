package net.ecocraft.core.currency;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.SubUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CurrencyFormatterTest {

    // Simple currency with 2 decimals (like "1.50 G")
    private final Currency gold2dec = Currency.virtual("gold", "Gold", "G", 2);

    // Simple currency with 0 decimals
    private final Currency gold0dec = Currency.virtual("gold0", "Gold", "G", 0);

    // WoW-style composite: PP(1000) > PO(100) > PA(10) > PC(1)
    private final Currency wow = Currency.builder("wow", "WoW Gold", "G")
            .subUnits(
                    new SubUnit("PP", "Platinum", 1000),
                    new SubUnit("PO", "Gold", 100),
                    new SubUnit("PA", "Silver", 10),
                    new SubUnit("PC", "Copper", 1)
            )
            .build();

    // Base-8 composite: Sun(4096) > Crown(512) > Cog(64) > Spur(1)
    private final Currency base8 = Currency.builder("base8", "Fantasy", "F")
            .subUnits(
                    new SubUnit("Sun", "Sun", 4096),
                    new SubUnit("Crown", "Crown", 512),
                    new SubUnit("Cog", "Cog", 64),
                    new SubUnit("Spur", "Spur", 1)
            )
            .build();

    // ---- Simple currency with decimals ----

    @Test
    void formatSimpleCurrencyWithDecimals() {
        assertEquals("1.50 G", CurrencyFormatter.format(150, gold2dec));
        assertEquals("100.00 G", CurrencyFormatter.format(10000, gold2dec));
        assertEquals("0.01 G", CurrencyFormatter.format(1, gold2dec));
        assertEquals("0.00 G", CurrencyFormatter.format(0, gold2dec));
    }

    // ---- Simple currency without decimals ----

    @Test
    void formatSimpleCurrencyNoDecimals() {
        assertEquals("150 G", CurrencyFormatter.format(150, gold0dec));
        assertEquals("0 G", CurrencyFormatter.format(0, gold0dec));
    }

    @Test
    void formatWithThousandsSeparator() {
        assertEquals("1 234 567 G", CurrencyFormatter.format(1234567, gold0dec));
        assertEquals("1 234 567.89 G", CurrencyFormatter.format(123456789, gold2dec));
        assertEquals("1 000.00 G", CurrencyFormatter.format(100000, gold2dec));
    }

    // ---- Composite currency (WoW-style base-10) ----

    @Test
    void formatComposite() {
        assertEquals("1 PP 5 PO", CurrencyFormatter.format(1500, wow));
        assertEquals("1 PP", CurrencyFormatter.format(1000, wow));
        assertEquals("5 PO", CurrencyFormatter.format(500, wow));
        assertEquals("3 PA 2 PC", CurrencyFormatter.format(32, wow));
        assertEquals("0 PC", CurrencyFormatter.format(0, wow));
        assertEquals("1 PP 2 PO 3 PA 4 PC", CurrencyFormatter.format(1234, wow));
    }

    // ---- Composite currency (base-8) ----

    @Test
    void formatCompositeBase8() {
        assertEquals("1 Sun", CurrencyFormatter.format(4096, base8));
        assertEquals("1 Sun 1 Crown", CurrencyFormatter.format(4608, base8));
        assertEquals("63 Spur", CurrencyFormatter.format(63, base8));
        assertEquals("1 Cog", CurrencyFormatter.format(64, base8));
    }

    // ---- split / combine ----

    @Test
    void splitComposite() {
        assertArrayEquals(new long[]{1, 2, 3, 4}, CurrencyFormatter.split(1234, wow));
    }

    @Test
    void splitAndCombineRoundTrips() {
        long original = 9876;
        long[] parts = CurrencyFormatter.split(original, wow);
        assertEquals(original, CurrencyFormatter.combine(parts, wow));
    }

    @Test
    void combineComposite() {
        assertEquals(1500, CurrencyFormatter.combine(new long[]{1, 5, 0, 0}, wow));
    }

    // ---- toSmallestUnit / fromSmallestUnit ----

    @Test
    void toSmallestUnitWithDecimals() {
        assertEquals(15000, CurrencyFormatter.toSmallestUnit(new BigDecimal("150.00"), gold2dec));
        assertEquals(15050, CurrencyFormatter.toSmallestUnit(new BigDecimal("150.50"), gold2dec));
        assertEquals(100, CurrencyFormatter.toSmallestUnit(new BigDecimal("1.00"), gold2dec));
        assertEquals(1, CurrencyFormatter.toSmallestUnit(new BigDecimal("0.01"), gold2dec));
    }

    @Test
    void fromSmallestUnitWithDecimals() {
        assertEquals(new BigDecimal("150.00"), CurrencyFormatter.fromSmallestUnit(15000, gold2dec));
        assertEquals(new BigDecimal("150.50"), CurrencyFormatter.fromSmallestUnit(15050, gold2dec));
        assertEquals(new BigDecimal("0.01"), CurrencyFormatter.fromSmallestUnit(1, gold2dec));
    }

    @Test
    void toSmallestUnitNoDecimals() {
        assertEquals(150, CurrencyFormatter.toSmallestUnit(new BigDecimal("150"), gold0dec));
    }

    @Test
    void fromSmallestUnitNoDecimals() {
        assertEquals(new BigDecimal("150"), CurrencyFormatter.fromSmallestUnit(150, gold0dec));
    }

    @Test
    void roundTripConversion() {
        BigDecimal original = new BigDecimal("123.45");
        long smallest = CurrencyFormatter.toSmallestUnit(original, gold2dec);
        BigDecimal back = CurrencyFormatter.fromSmallestUnit(smallest, gold2dec);
        assertEquals(original, back);
    }
}
