package net.ecocraft.gui.util;

public final class NumberFormatter {
    private NumberFormatter() {}

    public static String format(long value, NumberFormat mode) {
        if (value == 0) return "0";

        boolean negative = value < 0;
        long abs = Math.abs(value);
        String prefix = negative ? "-" : "";

        return switch (mode) {
            case COMPACT -> prefix + formatCompact(abs);
            case FULL -> prefix + formatFull(abs);
            case EXACT -> prefix + String.valueOf(abs);
        };
    }

    public static String format(double value, NumberFormat mode) {
        return format(Math.round(value), mode);
    }

    private static String formatCompact(long value) {
        if (value >= 1_000_000_000L) {
            return formatWithSuffix(value, 1_000_000_000L, "G");
        } else if (value >= 1_000_000L) {
            return formatWithSuffix(value, 1_000_000L, "M");
        } else if (value >= 1_000L) {
            return formatWithSuffix(value, 1_000L, "k");
        }
        return String.valueOf(value);
    }

    private static String formatWithSuffix(long value, long divisor, String suffix) {
        long whole = value / divisor;
        long remainder = (value % divisor) * 10 / divisor;
        if (remainder == 0) {
            return whole + suffix;
        }
        return whole + "." + remainder + suffix;
    }

    private static String formatFull(long value) {
        String raw = String.valueOf(value);
        StringBuilder sb = new StringBuilder();
        int start = raw.length() % 3;
        if (start > 0) {
            sb.append(raw, 0, start);
        }
        for (int i = start; i < raw.length(); i += 3) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(raw, i, i + 3);
        }
        return sb.toString();
    }
}
