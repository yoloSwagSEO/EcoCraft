package net.ecocraft.api.transaction;

import java.util.Objects;

/**
 * Value-based implementation of {@link TransactionType}.
 * Two instances are equal if they share the same name.
 */
record TransactionTypeImpl(String name) implements TransactionType {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransactionType that)) return false;
        return name.equals(that.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
