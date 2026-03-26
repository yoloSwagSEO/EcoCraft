package net.ecocraft.core.impl;

import net.ecocraft.api.Page;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.*;
import net.ecocraft.core.storage.DatabaseProvider;

import java.util.List;

public class TransactionLogImpl implements TransactionLog {

    private final DatabaseProvider db;
    private final CurrencyRegistry registry;

    public TransactionLogImpl(DatabaseProvider db, CurrencyRegistry registry) {
        this.db = db;
        this.registry = registry;
    }

    @Override
    public Page<Transaction> getHistory(TransactionFilter filter) {
        String typeStr = filter.type() != null ? filter.type().name() : null;

        var records = db.getTransactionHistory(
            filter.player(), typeStr, filter.from(), filter.to(),
            filter.offset(), filter.limit()
        );

        long total = db.getTransactionCount(
            filter.player(), typeStr, filter.from(), filter.to()
        );

        List<Transaction> transactions = records.stream().map(r -> {
            Currency currency = registry.getById(r.currencyId());
            if (currency == null) {
                currency = Currency.virtual(r.currencyId(), r.currencyId(), "?", 0);
            }
            return new Transaction(
                r.id(), r.from(), r.to(), r.amount(), currency,
                TransactionType.valueOf(r.type()), r.timestamp()
            );
        }).toList();

        return new Page<>(transactions, filter.offset(), filter.limit(), total);
    }
}
