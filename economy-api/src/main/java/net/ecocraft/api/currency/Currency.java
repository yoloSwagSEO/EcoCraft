package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;

public record Currency(
        String id,
        String name,
        String symbol,
        int decimals,
        boolean physical,
        @Nullable String itemId
) {
    public Currency {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Currency id cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Currency name cannot be blank");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Currency symbol cannot be blank");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("Decimals cannot be negative");
        }
        if (physical && (itemId == null || itemId.isBlank())) {
            throw new IllegalArgumentException("Physical currency must have an itemId");
        }
    }

    public static Currency virtual(String id, String name, String symbol, int decimals) {
        return new Currency(id, name, symbol, decimals, false, null);
    }

    public static Currency physical(String id, String name, String symbol, int decimals, String itemId) {
        return new Currency(id, name, symbol, decimals, true, itemId);
    }
}
