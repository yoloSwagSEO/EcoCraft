package net.ecocraft.api;

import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;

import java.math.BigDecimal;
import java.util.UUID;

/** Core economy operations: balance queries, deposits, withdrawals, and transfers. */
public interface EconomyProvider {

    /**
     * Returns the account for the given player and currency.
     * @param player the player UUID
     * @param currency the currency
     * @return the account, never null (created on-demand if absent)
     */
    Account getAccount(UUID player, Currency currency);

    /**
     * Returns the player's virtual (non-vault) balance for the given currency.
     * @param player the player UUID
     * @param currency the currency
     * @return the balance, never null
     */
    BigDecimal getVirtualBalance(UUID player, Currency currency);

    /**
     * Returns the player's vault balance for the given currency.
     * @param player the player UUID
     * @param currency the currency
     * @return the balance, never null
     */
    BigDecimal getVaultBalance(UUID player, Currency currency);

    /**
     * Withdraws the given amount from the player's virtual balance.
     * @param player the player UUID
     * @param amount must be strictly positive (> 0)
     * @param currency the currency
     * @return a result indicating success or failure
     */
    TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency);

    /**
     * Deposits the given amount into the player's virtual balance.
     * @param player the player UUID
     * @param amount must be strictly positive (> 0)
     * @param currency the currency
     * @return a result indicating success or failure
     */
    TransactionResult deposit(UUID player, BigDecimal amount, Currency currency);

    /**
     * Transfers the given amount from one player to another.
     * @param from the sender UUID
     * @param to the receiver UUID
     * @param amount must be strictly positive (> 0)
     * @param currency the currency
     * @return a result indicating success or failure
     */
    TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency);

    /**
     * Checks whether the player can afford the given amount.
     * @param player the player UUID
     * @param amount the amount to check
     * @param currency the currency
     * @return true if the player's balance is >= amount
     */
    boolean canAfford(UUID player, BigDecimal amount, Currency currency);
}
