package net.ecocraft.api.transaction;

import java.util.Objects;

/**
 * Extensible transaction type. Core types are defined as constants; modules can
 * define additional types via {@link #of(String)}.
 */
public interface TransactionType {

    TransactionType PAYMENT = of("PAYMENT");
    TransactionType TAX = of("TAX");
    TransactionType DEPOSIT = of("DEPOSIT");
    TransactionType WITHDRAWAL = of("WITHDRAWAL");
    TransactionType TRANSFER = of("TRANSFER");
    TransactionType EXCHANGE = of("EXCHANGE");
    TransactionType ADMIN_SET = of("ADMIN_SET");

    /** Returns the unique name identifying this transaction type. */
    String name();

    /** Creates a transaction type with the given name. */
    static TransactionType of(String name) {
        Objects.requireNonNull(name, "TransactionType name cannot be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("TransactionType name cannot be blank");
        }
        return new TransactionTypeImpl(name);
    }
}
