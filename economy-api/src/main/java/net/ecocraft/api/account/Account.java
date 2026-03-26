package net.ecocraft.api.account;

import net.ecocraft.api.currency.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public record Account(
        UUID owner,
        Currency currency,
        BigDecimal virtualBalance,
        BigDecimal vaultBalance
) {
    public Account {
        if (virtualBalance.signum() < 0) {
            throw new IllegalArgumentException("Virtual balance cannot be negative");
        }
        if (vaultBalance.signum() < 0) {
            throw new IllegalArgumentException("Vault balance cannot be negative");
        }
    }

    public BigDecimal totalBalance() {
        return virtualBalance.add(vaultBalance);
    }

    public static Account empty(UUID owner, Currency currency) {
        return new Account(owner, currency, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
