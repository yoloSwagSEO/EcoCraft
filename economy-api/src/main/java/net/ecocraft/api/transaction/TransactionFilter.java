package net.ecocraft.api.transaction;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record TransactionFilter(
        @Nullable UUID player,
        @Nullable TransactionType type,
        @Nullable Instant from,
        @Nullable Instant to,
        int offset,
        int limit
) {
    public TransactionFilter {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }
}
