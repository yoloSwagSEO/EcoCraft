package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.impl.CurrencyRegistryImpl;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.ecocraft.core.permission.EcoPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class EcoAdminCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies) {
        var currSuggestions = EcoCommands.currencySuggestions(currencies);

        dispatcher.register(Commands.literal("eco")
            .then(Commands.literal("give")
                .requires(src -> EcoPermissions.check(src, EcoPermissions.ADMIN_GIVE))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> give(ctx, economy.get(), currencies.get()))
                        .then(Commands.argument("currency", StringArgumentType.word())
                            .suggests(currSuggestions)
                            .executes(ctx -> give(ctx, economy.get(), currencies.get()))
                        )
                    )
                )
            )
            .then(Commands.literal("take")
                .requires(src -> EcoPermissions.check(src, EcoPermissions.ADMIN_TAKE))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> take(ctx, economy.get(), currencies.get()))
                        .then(Commands.argument("currency", StringArgumentType.word())
                            .suggests(currSuggestions)
                            .executes(ctx -> take(ctx, economy.get(), currencies.get()))
                        )
                    )
                )
            )
            .then(Commands.literal("set")
                .requires(src -> EcoPermissions.check(src, EcoPermissions.ADMIN_SET))
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(ctx -> set(ctx, economy.get(), currencies.get()))
                        .then(Commands.argument("currency", StringArgumentType.word())
                            .suggests(currSuggestions)
                            .executes(ctx -> set(ctx, economy.get(), currencies.get()))
                        )
                    )
                )
            )
            .then(Commands.literal("createcurrency")
                .requires(src -> EcoPermissions.check(src, EcoPermissions.ADMIN_SET))
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("symbol", StringArgumentType.string())
                            .executes(ctx -> createCurrency(ctx, currencies.get(), 1.0))
                            .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.001))
                                .executes(ctx -> createCurrency(ctx, currencies.get(),
                                        DoubleArgumentType.getDouble(ctx, "rate")))
                            )
                        )
                    )
                )
            )
        );
    }

    private static int give(CommandContext<CommandSourceStack> ctx,
                            EconomyProvider economy, CurrencyRegistry currencies) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        Currency currency = EcoCommands.resolveCurrency(ctx, currencies);
        CommandSourceStack source = ctx.getSource();

        economy.deposit(target.getUUID(), BigDecimal.valueOf(amount), currency);
        String formatted = CurrencyFormatter.format(BigDecimal.valueOf(amount).longValue(), currency);
        source.sendSuccess(() -> Component.translatable(
            "ecocraft_core.command.eco.give", formatted, target.getName().getString()
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int take(CommandContext<CommandSourceStack> ctx,
                            EconomyProvider economy, CurrencyRegistry currencies) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        Currency currency = EcoCommands.resolveCurrency(ctx, currencies);
        CommandSourceStack source = ctx.getSource();

        var result = economy.withdraw(target.getUUID(), BigDecimal.valueOf(amount), currency);
        if (result.successful()) {
            String formatted = CurrencyFormatter.format(BigDecimal.valueOf(amount).longValue(), currency);
            source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.eco.take", formatted, target.getName().getString()
            ), true);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }

    private static int createCurrency(CommandContext<CommandSourceStack> ctx,
                                        CurrencyRegistry currencies, double rate) {
        String id = StringArgumentType.getString(ctx, "id");
        String name = StringArgumentType.getString(ctx, "name");
        String symbol = StringArgumentType.getString(ctx, "symbol");
        CommandSourceStack source = ctx.getSource();

        if (currencies.exists(id)) {
            source.sendFailure(Component.translatable("ecocraft_core.command.eco.currency_exists", id));
            return 0;
        }

        Currency currency = Currency.builder(id, name, symbol)
                .decimals(2)
                .exchangeable(true)
                .referenceRate(rate)
                .build();

        currencies.register(currency);

        source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.eco.currency_created", name, symbol, String.format("%.3f", rate)
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandContext<CommandSourceStack> ctx,
                           EconomyProvider economy, CurrencyRegistry currencies) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        Currency currency = EcoCommands.resolveCurrency(ctx, currencies);
        CommandSourceStack source = ctx.getSource();

        // Direct set via EconomyProviderImpl to avoid phantom withdraw/deposit transactions
        ((EconomyProviderImpl) economy).setBalance(target.getUUID(), BigDecimal.valueOf(amount), currency);
        String formatted = CurrencyFormatter.format(BigDecimal.valueOf(amount).longValue(), currency);
        source.sendSuccess(() -> Component.translatable(
            "ecocraft_core.command.eco.set", target.getName().getString(), formatted
        ), true);
        return Command.SINGLE_SUCCESS;
    }
}
