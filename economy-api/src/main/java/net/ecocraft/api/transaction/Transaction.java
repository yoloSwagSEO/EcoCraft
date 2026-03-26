package net.ecocraft.api.transaction;

import net.ecocraft.api.currency.Currency;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Transaction(
        UUID id,
        @Nullable UUID from,
        @Nullable UUID to,
        BigDecimal amount,
        Currency currency,
        TransactionType type,
        Instant timestamp
) {
    public Transaction {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }
    }
}
