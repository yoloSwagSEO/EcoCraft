package net.ecocraft.core.impl;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.Transaction;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.storage.DatabaseProvider;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class EconomyProviderImpl implements EconomyProvider {

    private final DatabaseProvider db;
    private final CurrencyRegistry registry;

    public EconomyProviderImpl(DatabaseProvider db, CurrencyRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    @Override
    public Account getAccount(UUID player, Currency currency) {
        BigDecimal virtual = db.getVirtualBalance(player, currency.id());
        return new Account(player, currency, virtual, BigDecimal.ZERO);
    }

    @Override
    public BigDecimal getBalance(UUID player, Currency currency) {
        return db.getVirtualBalance(player, currency.id());
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
            BigDecimal current = db.getVirtualBalance(player, currency.id());
            if (current.compareTo(amount) < 0) {
                return TransactionResult.failure("Insufficient funds");
            }
            BigDecimal newBalance = current.subtract(amount);
            db.setVirtualBalance(player, currency.id(), newBalance);

            var tx = new Transaction(UUID.randomUUID(), player, null, amount, currency,
                TransactionType.WITHDRAWAL, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            return TransactionResult.success(tx);
        }
    }

    @Override
    public TransactionResult deposit(UUID player, BigDecimal amount, Currency currency) {
        synchronized (db) {
            BigDecimal current = db.getVirtualBalance(player, currency.id());
            BigDecimal newBalance = current.add(amount);
            db.setVirtualBalance(player, currency.id(), newBalance);

            var tx = new Transaction(UUID.randomUUID(), null, player, amount, currency,
                TransactionType.DEPOSIT, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            return TransactionResult.success(tx);
        }
    }

    @Override
    public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency) {
        synchronized (db) {
            BigDecimal senderBalance = db.getVirtualBalance(from, currency.id());
            if (senderBalance.compareTo(amount) < 0) {
                return TransactionResult.failure("Insufficient funds");
            }

            db.setVirtualBalance(from, currency.id(), senderBalance.subtract(amount));
            BigDecimal receiverBalance = db.getVirtualBalance(to, currency.id());
            db.setVirtualBalance(to, currency.id(), receiverBalance.add(amount));

            var tx = new Transaction(UUID.randomUUID(), from, to, amount, currency,
                TransactionType.PAYMENT, Instant.now());
            db.logTransaction(tx.id(), tx.from(), tx.to(), tx.amount(),
                currency.id(), tx.type().name(), tx.timestamp());

            return TransactionResult.success(tx);
        }
    }

    @Override
    public boolean canAfford(UUID player, BigDecimal amount, Currency currency) {
        return db.getVirtualBalance(player, currency.id()).compareTo(amount) >= 0;
    }
}
