package net.ecocraft.ah.data;

import java.text.Normalizer;
import java.util.List;
import java.util.UUID;

/**
 * Represents an Auction House instance with its own configuration.
 */
public record AHInstance(
        String id,
        String slug,
        String name,
        int saleRate,
        int depositRate,
        List<Integer> durations,
        boolean allowBuyout,
        boolean allowAuction,
        String taxRecipient,
        boolean overridePermTax
) {
    public static final String DEFAULT_ID = "00000000-0000-0000-0000-000000000001";
    public static final int DEFAULT_SALE_RATE = 5;
    public static final int DEFAULT_DEPOSIT_RATE = 2;
    public static final List<Integer> DEFAULT_DURATIONS = List.of(12, 24, 48);
    public static final String DEFAULT_NAME = "Hôtel des Ventes";

    public static AHInstance create(String name) {
        return new AHInstance(UUID.randomUUID().toString(), slugify(name), name,
                DEFAULT_SALE_RATE, DEFAULT_DEPOSIT_RATE, DEFAULT_DURATIONS, true, true, "", false);
    }

    public static AHInstance createDefault() {
        return new AHInstance(DEFAULT_ID, "default", DEFAULT_NAME,
                DEFAULT_SALE_RATE, DEFAULT_DEPOSIT_RATE, DEFAULT_DURATIONS, true, true, "", false);
    }

    public AHInstance withConfig(String name, int saleRate, int depositRate, List<Integer> durations,
                                 boolean allowBuyout, boolean allowAuction, String taxRecipient,
                                 boolean overridePermTax) {
        return new AHInstance(id, slugify(name), name, saleRate, depositRate, durations, allowBuyout, allowAuction,
                taxRecipient != null ? taxRecipient : "", overridePermTax);
    }

    public static String slugify(String name) {
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD);
        return normalized
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
}
