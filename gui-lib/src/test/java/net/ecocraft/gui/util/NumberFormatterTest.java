package net.ecocraft.gui.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NumberFormatterTest {

    @Test
    void compactBelow1000() {
        assertEquals("0", NumberFormatter.format(0, NumberFormat.COMPACT));
        assertEquals("500", NumberFormatter.format(500, NumberFormat.COMPACT));
        assertEquals("999", NumberFormatter.format(999, NumberFormat.COMPACT));
    }

    @Test
    void compactThousands() {
        assertEquals("1k", NumberFormatter.format(1000, NumberFormat.COMPACT));
        assertEquals("1.5k", NumberFormatter.format(1500, NumberFormat.COMPACT));
        assertEquals("35.5k", NumberFormatter.format(35500, NumberFormat.COMPACT));
        assertEquals("999.9k", NumberFormatter.format(999900, NumberFormat.COMPACT));
    }

    @Test
    void compactMillions() {
        assertEquals("1M", NumberFormatter.format(1_000_000, NumberFormat.COMPACT));
        assertEquals("2.5M", NumberFormatter.format(2_500_000, NumberFormat.COMPACT));
    }

    @Test
    void compactBillions() {
        assertEquals("1G", NumberFormatter.format(1_000_000_000L, NumberFormat.COMPACT));
        assertEquals("3.4G", NumberFormatter.format(3_400_000_000L, NumberFormat.COMPACT));
    }

    @Test
    void compactNoTrailingZero() {
        assertEquals("2k", NumberFormatter.format(2000, NumberFormat.COMPACT));
        assertEquals("1M", NumberFormatter.format(1_000_000, NumberFormat.COMPACT));
    }

    @Test
    void fullFormat() {
        assertEquals("0", NumberFormatter.format(0, NumberFormat.FULL));
        assertEquals("500", NumberFormatter.format(500, NumberFormat.FULL));
        assertEquals("1 000", NumberFormatter.format(1000, NumberFormat.FULL));
        assertEquals("35 500", NumberFormatter.format(35500, NumberFormat.FULL));
        assertEquals("1 000 000", NumberFormatter.format(1_000_000, NumberFormat.FULL));
    }

    @Test
    void exactFormat() {
        assertEquals("0", NumberFormatter.format(0, NumberFormat.EXACT));
        assertEquals("500", NumberFormatter.format(500, NumberFormat.EXACT));
        assertEquals("35500", NumberFormatter.format(35500, NumberFormat.EXACT));
        assertEquals("1000000", NumberFormatter.format(1_000_000, NumberFormat.EXACT));
    }

    @Test
    void negativeNumbers() {
        assertEquals("-500", NumberFormatter.format(-500, NumberFormat.COMPACT));
        assertEquals("-1.5k", NumberFormatter.format(-1500, NumberFormat.COMPACT));
        assertEquals("-35 500", NumberFormatter.format(-35500, NumberFormat.FULL));
    }
}
