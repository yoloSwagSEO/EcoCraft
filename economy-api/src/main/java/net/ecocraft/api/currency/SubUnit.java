package net.ecocraft.api.currency;

import org.jetbrains.annotations.Nullable;

public record SubUnit(
        String code,
        String name,
        long multiplier,
        @Nullable String itemId,
        @Nullable Icon icon
) {
    public SubUnit {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("SubUnit code cannot be blank");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("SubUnit name cannot be blank");
        if (multiplier <= 0) throw new IllegalArgumentException("SubUnit multiplier must be positive");
    }
    public SubUnit(String code, String name, long multiplier) {
        this(code, name, multiplier, null, null);
    }
}
