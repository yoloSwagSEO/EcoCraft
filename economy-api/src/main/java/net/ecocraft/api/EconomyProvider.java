package net.ecocraft.api;

import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyProvider {
    Account getAccount(UUID player, Currency currency);
    BigDecimal getBalance(UUID player, Currency currency);
    BigDecimal getVirtualBalance(UUID player, Currency currency);
    BigDecimal getVaultBalance(UUID player, Currency currency);
    TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency);
    TransactionResult deposit(UUID player, BigDecimal amount, Currency currency);
    TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency);
    boolean canAfford(UUID player, BigDecimal amount, Currency currency);
}
