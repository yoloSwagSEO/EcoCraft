package net.ecocraft.core.impl;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.Transaction;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.storage.DatabaseProvider;

import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public class EconomyProviderImpl implements EconomyProvider {

    // --- Event dispatcher (optional, injected when KubeJS is present) ---

    public interface TransactionEventDispatcher {
        /** Returns false if the event was cancelled. */
        boolean firePreTransaction(UUID player, BigDecimal amount, Currency currency,
                                    TransactionType type, @Nullable UUID target);
        void firePostTransaction(UUID player, BigDecimal amount, Currency currency,
                                  TransactionType type, @Nullable UUID target, boolean success);
        void fireBalanceChanged(UUID player, long oldBalance, long newBalance,
                                 Currency currency, String cause);
    }

    private final DatabaseProvider db;
    private final CurrencyRegistry registry;
    @Nullable
    private TransactionEventDispatcher eventDispatcher;

    public EconomyProviderImpl(DatabaseProvider db, CurrencyRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    public void setEventDispatcher(@Nullable TransactionEventDispatcher dispatcher) {
        this.eventDispatcher = dispatcher;
    }

    @Override
    public Account getAccount(UUID player, Currency currency) {
        BigDecimal virtual = db.getVirtualBalance(player, currency.id());
        return new Account(player, currency, virtual, BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getVirtualBalance(UUID player, Currency currency) {
        return db.getVirtualBalance(player, currency.id());
    }

    @Override
    public BigDecimal getVaultBalance(UUID player, Currency currency) {
        return BigDecimal.ZERO; // vault block will override this
    }

    @Override
    public TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency) {
        synchronized (db) {
            if (amount.signum() <= 0) {
                return TransactionResult.failure("Amount must be positive");
            }

            // PRE event — cancellable
            if (eventDispatcher != null &&
                !eventDispatcher.firePreTransaction(player, amount, currency, TransactionType.WITHDRAWAL, null)) {
                return TransactionResult.failure("Operation cancelled");
            }

            BigDecimal current = db.getVirtualBalance(player, currency.id());
            if (current.compareTo(amount) < 0) {
                if (eventDispatcher != null) {
                    eventDispatcher.firePostTransaction(player, amount, currency, TransactionType.WITHDRAWAL, null, false);
                }
                return TransactionResult.failure("Insufficient funds");
            }
            BigDecimal newBalance = current.subtract(amount);
            db.setVirtualBalance(player, currency.id(), newBalance);

            var tx = new Transaction(UUID.randomUUID(), player, null, amount, currency,
                TransactionType.WITHDRAWAL, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            // POST events
            if (eventDispatcher != null) {
                eventDispatcher.firePostTransaction(player, amount, currency, TransactionType.WITHDRAWAL, null, true);
                long oldBal = current.setScale(0, RoundingMode.DOWN).longValueExact();
                long newBal = newBalance.setScale(0, RoundingMode.DOWN).longValueExact();
                eventDispatcher.fireBalanceChanged(player, oldBal, newBal, currency, "WITHDRAWAL");
            }

            return TransactionResult.success(tx);
        }
    }

    @Override
    public TransactionResult deposit(UUID player, BigDecimal amount, Currency currency) {
        synchronized (db) {
            if (amount.signum() <= 0) {
                return TransactionResult.failure("Amount must be positive");
            }

            // PRE event — cancellable
            if (eventDispatcher != null &&
                !eventDispatcher.firePreTransaction(player, amount, currency, TransactionType.DEPOSIT, null)) {
                return TransactionResult.failure("Operation cancelled");
            }

            BigDecimal current = db.getVirtualBalance(player, currency.id());
            BigDecimal newBalance = current.add(amount);
            db.setVirtualBalance(player, currency.id(), newBalance);

            var tx = new Transaction(UUID.randomUUID(), null, player, amount, currency,
                TransactionType.DEPOSIT, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            // POST events
            if (eventDispatcher != null) {
                eventDispatcher.firePostTransaction(player, amount, currency, TransactionType.DEPOSIT, null, true);
                long oldBal = current.setScale(0, RoundingMode.DOWN).longValueExact();
                long newBal = newBalance.setScale(0, RoundingMode.DOWN).longValueExact();
                eventDispatcher.fireBalanceChanged(player, oldBal, newBal, currency, "DEPOSIT");
            }

            return TransactionResult.success(tx);
        }
    }

    @Override
    public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency) {
        synchronized (db) {
            if (amount.signum() <= 0) {
                return TransactionResult.failure("Amount must be positive");
            }

            // PRE event — cancellable
            if (eventDispatcher != null &&
                !eventDispatcher.firePreTransaction(from, amount, currency, TransactionType.TRANSFER, to)) {
                return TransactionResult.failure("Operation cancelled");
            }

            BigDecimal senderBalance = db.getVirtualBalance(from, currency.id());
            if (senderBalance.compareTo(amount) < 0) {
                if (eventDispatcher != null) {
                    eventDispatcher.firePostTransaction(from, amount, currency, TransactionType.TRANSFER, to, false);
                }
                return TransactionResult.failure("Insufficient funds");
            }

            db.setVirtualBalance(from, currency.id(), senderBalance.subtract(amount));
            BigDecimal receiverBalance = db.getVirtualBalance(to, currency.id());
            db.setVirtualBalance(to, currency.id(), receiverBalance.add(amount));

            var tx = new Transaction(UUID.randomUUID(), from, to, amount, currency,
                TransactionType.PAYMENT, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            // POST events
            if (eventDispatcher != null) {
                eventDispatcher.firePostTransaction(from, amount, currency, TransactionType.TRANSFER, to, true);
                long oldSender = senderBalance.setScale(0, RoundingMode.DOWN).longValueExact();
                long newSender = senderBalance.subtract(amount).setScale(0, RoundingMode.DOWN).longValueExact();
                eventDispatcher.fireBalanceChanged(from, oldSender, newSender, currency, "TRANSFER");
                long oldReceiver = receiverBalance.setScale(0, RoundingMode.DOWN).longValueExact();
                long newReceiver = receiverBalance.add(amount).setScale(0, RoundingMode.DOWN).longValueExact();
                eventDispatcher.fireBalanceChanged(to, oldReceiver, newReceiver, currency, "TRANSFER");
            }

            return TransactionResult.success(tx);
        }
    }

    /**
     * Directly sets a player's balance without creating phantom withdraw/deposit transactions.
     * Used by admin commands. Logs a single ADMIN_SET transaction.
     */
    public void setBalance(UUID player, BigDecimal amount, Currency currency) {
        synchronized (db) {
            BigDecimal oldBalance = db.getVirtualBalance(player, currency.id());
            db.setVirtualBalance(player, currency.id(), amount);

            var tx = new Transaction(UUID.randomUUID(), null, player, amount, currency,
                TransactionType.ADMIN_SET, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            if (eventDispatcher != null) {
                long oldBal = oldBalance.setScale(0, RoundingMode.DOWN).longValueExact();
                long newBal = amount.setScale(0, RoundingMode.DOWN).longValueExact();
                eventDispatcher.fireBalanceChanged(player, oldBal, newBal, currency, "ADMIN_SET");
            }
        }
    }

    @Override
    public boolean canAfford(UUID player, BigDecimal amount, Currency currency) {
        return db.getVirtualBalance(player, currency.id()).compareTo(amount) >= 0;
    }
}
