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
import net.ecocraft.core.storage.DatabaseProvider;
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
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<DatabaseProvider> database) {
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
                            .executes(ctx -> createCurrency(ctx, currencies.get(), database.get(), 1.0))
                            .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.001))
                                .executes(ctx -> createCurrency(ctx, currencies.get(), database.get(),
                                        DoubleArgumentType.getDouble(ctx, "rate")))
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("deletecurrency")
                .requires(src -> EcoPermissions.check(src, EcoPermissions.ADMIN_SET))
                .then(Commands.argument("id", StringArgumentType.word())
                    .suggests(currSuggestions)
                    .executes(ctx -> deleteCurrency(ctx, currencies.get(), database.get()))
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

        long smallestUnit = CurrencyFormatter.toSmallestUnit(BigDecimal.valueOf(amount), currency);
        BigDecimal bdAmount = CurrencyFormatter.fromSmallestUnit(smallestUnit, currency);
        economy.deposit(target.getUUID(), bdAmount, currency);
        String formatted = CurrencyFormatter.format(smallestUnit, currency);
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

        long smallestUnit = CurrencyFormatter.toSmallestUnit(BigDecimal.valueOf(amount), currency);
        BigDecimal bdAmount = CurrencyFormatter.fromSmallestUnit(smallestUnit, currency);
        var result = economy.withdraw(target.getUUID(), bdAmount, currency);
        if (result.successful()) {
            String formatted = CurrencyFormatter.format(smallestUnit, currency);
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
                                        CurrencyRegistry currencies, DatabaseProvider db, double rate) {
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
        db.saveCurrency(id, name, symbol, 2, false, null, true, BigDecimal.valueOf(rate));

        source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.eco.currency_created", name, symbol, String.format("%.3f", rate)
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int deleteCurrency(CommandContext<CommandSourceStack> ctx,
                                       CurrencyRegistry currencies, DatabaseProvider db) {
        String id = StringArgumentType.getString(ctx, "id");
        CommandSourceStack source = ctx.getSource();

        if (!currencies.exists(id)) {
            source.sendFailure(Component.translatable("ecocraft_core.command.eco.currency_not_found", id));
            return 0;
        }

        // Prevent deleting the default currency
        if (currencies.getDefault().id().equals(id)) {
            source.sendFailure(Component.translatable("ecocraft_core.command.eco.currency_is_default", id));
            return 0;
        }

        currencies.unregister(id);
        db.deleteCurrency(id);

        source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.eco.currency_deleted", id
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
        long smallestUnit = CurrencyFormatter.toSmallestUnit(BigDecimal.valueOf(amount), currency);
        BigDecimal bdAmount = CurrencyFormatter.fromSmallestUnit(smallestUnit, currency);
        ((EconomyProviderImpl) economy).setBalance(target.getUUID(), bdAmount, currency);
        String formatted = CurrencyFormatter.format(smallestUnit, currency);
        source.sendSuccess(() -> Component.translatable(
            "ecocraft_core.command.eco.set", target.getName().getString(), formatted
        ), true);
        return Command.SINGLE_SUCCESS;
    }
}
