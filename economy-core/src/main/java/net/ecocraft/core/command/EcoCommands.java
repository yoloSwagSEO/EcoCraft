package net.ecocraft.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.transaction.TransactionLog;
import net.ecocraft.core.storage.DatabaseProvider;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.Supplier;

public class EcoCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<ExchangeService> exchange,
                                Supplier<TransactionLog> transactionLog,
                                Supplier<DatabaseProvider> database) {
        BalanceCommand.register(dispatcher, economy, currencies);
        PayCommand.register(dispatcher, economy, currencies);
        CurrencyCommand.register(dispatcher, currencies, exchange, transactionLog);
        EcoAdminCommand.register(dispatcher, economy, currencies, database);
    }

    /**
     * Reusable suggestion provider that suggests all registered currency IDs.
     */
    public static SuggestionProvider<CommandSourceStack> currencySuggestions(Supplier<CurrencyRegistry> registry) {
        return (ctx, builder) -> {
            CurrencyRegistry reg = registry.get();
            if (reg != null) {
                for (Currency c : reg.listAll()) {
                    builder.suggest(c.id());
                }
            }
            return builder.buildFuture();
        };
    }

    /**
     * Resolves a currency from the optional "currency" argument.
     * Falls back to the default currency if the argument is not provided.
     */
    public static Currency resolveCurrency(CommandContext<CommandSourceStack> ctx, CurrencyRegistry registry) {
        try {
            String id = StringArgumentType.getString(ctx, "currency");
            Currency c = registry.getById(id);
            if (c == null) throw new RuntimeException("Unknown currency: " + id);
            return c;
        } catch (IllegalArgumentException e) {
            return registry.getDefault();
        }
    }
}
