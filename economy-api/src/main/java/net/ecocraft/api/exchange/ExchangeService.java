package net.ecocraft.api.exchange;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionResult;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ExchangeService {
    TransactionResult convert(UUID player, BigDecimal amount, Currency from, Currency to);
    @Nullable ExchangeRate getRate(Currency from, Currency to);
    List<ExchangeRate> listRates();
}
