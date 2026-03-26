package net.ecocraft.api.transaction;

import org.jetbrains.annotations.Nullable;

public record TransactionResult(
        boolean successful,
        @Nullable Transaction transaction,
        @Nullable String errorMessage
) {
    public static TransactionResult success(Transaction transaction) {
        return new TransactionResult(true, transaction, null);
    }

    public static TransactionResult failure(String errorMessage) {
        return new TransactionResult(false, null, errorMessage);
    }
}
