package net.ecocraft.api.transaction;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record TransactionFilter(
        UUID player,
        @Nullable TransactionType type,
        @Nullable Instant from,
        @Nullable Instant to,
        int offset,
        int limit
) {
}
