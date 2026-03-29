package net.ecocraft.api;

import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface EconomyProvider {
    Account getAccount(UUID player, Currency currency);
    BigDecimal getVirtualBalance(UUID player, Currency currency);
    BigDecimal getVaultBalance(UUID player, Currency currency);

    /**
     * Withdraws the given amount from the player's virtual balance.
     * @param amount must be strictly positive (> 0)
     */
    TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency);

    /**
     * Deposits the given amount into the player's virtual balance.
     * @param amount must be strictly positive (> 0)
     */
    TransactionResult deposit(UUID player, BigDecimal amount, Currency currency);

    /**
     * Transfers the given amount from one player to another.
     * @param amount must be strictly positive (> 0)
     */
    TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency);

    boolean canAfford(UUID player, BigDecimal amount, Currency currency);
}
