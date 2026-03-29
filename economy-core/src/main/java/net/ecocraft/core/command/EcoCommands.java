package net.ecocraft.core.command;

import com.mojang.brigadier.CommandDispatcher;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.Supplier;

public class EcoCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<ExchangeService> exchange) {
        BalanceCommand.register(dispatcher, economy, currencies);
        PayCommand.register(dispatcher, economy, currencies);
        CurrencyCommand.register(dispatcher, currencies, exchange);
        EcoAdminCommand.register(dispatcher, economy, currencies);
    }
}
