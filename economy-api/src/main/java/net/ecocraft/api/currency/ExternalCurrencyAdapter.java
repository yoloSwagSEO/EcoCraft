package net.ecocraft.api.currency;

import java.util.UUID;

/**
 * Adapter interface for external mods to expose their currency
 * to the EcoCraft economy system.
 */
public interface ExternalCurrencyAdapter {

    /** The mod id of the external mod providing this currency. */
    String modId();

    /** The currency definition. */
    Currency getCurrency();

    /** Get the player's balance in the smallest unit. */
    long getBalance(UUID player);

    /** Withdraw an amount from the player. Returns true on success. */
    boolean withdraw(UUID player, long amount);

    /** Deposit an amount to the player. Returns true on success. */
    boolean deposit(UUID player, long amount);

    /** Check whether the player can afford the given amount. */
    boolean canAfford(UUID player, long amount);
}
