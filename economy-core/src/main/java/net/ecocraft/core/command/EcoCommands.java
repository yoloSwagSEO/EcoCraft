package net.ecocraft.core.command;

import com.mojang.brigadier.CommandDispatcher;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.Supplier;

public class EcoCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<ExchangeService> exchange,
                                Supplier<PermissionChecker> permissions) {
        BalanceCommand.register(dispatcher, economy, currencies, permissions);
        PayCommand.register(dispatcher, economy, currencies, permissions);
        CurrencyCommand.register(dispatcher, currencies, exchange, permissions);
        EcoAdminCommand.register(dispatcher, economy, currencies, permissions);
    }
}
