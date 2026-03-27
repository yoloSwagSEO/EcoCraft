package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.permission.PermissionChecker;
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
                                Supplier<PermissionChecker> permissions) {
        dispatcher.register(Commands.literal("eco")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("give")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                            return give(ctx.getSource(), target, amount, economy.get(), currencies.get());
                        })
                    )
                )
            )
            .then(Commands.literal("take")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                            return take(ctx.getSource(), target, amount, economy.get(), currencies.get());
                        })
                    )
                )
            )
            .then(Commands.literal("set")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
                        .executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            double amount = DoubleArgumentType.getDouble(ctx, "amount");
                            return set(ctx.getSource(), target, amount, economy.get(), currencies.get());
                        })
                    )
                )
            )
        );
    }

    private static int give(CommandSourceStack source, ServerPlayer target, double amount,
                            EconomyProvider economy, CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        economy.deposit(target.getUUID(), BigDecimal.valueOf(amount), currency);
        source.sendSuccess(() -> Component.literal(
            "Gave " + amount + " " + currency.symbol() + " to " + target.getName().getString()
        ), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int take(CommandSourceStack source, ServerPlayer target, double amount,
                            EconomyProvider economy, CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        var result = economy.withdraw(target.getUUID(), BigDecimal.valueOf(amount), currency);
        if (result.successful()) {
            source.sendSuccess(() -> Component.literal(
                "Took " + amount + " " + currency.symbol() + " from " + target.getName().getString()
            ), true);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }

    private static int set(CommandSourceStack source, ServerPlayer target, double amount,
                           EconomyProvider economy, CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        // Withdraw all, then deposit new amount
        BigDecimal current = economy.getBalance(target.getUUID(), currency);
        if (current.signum() > 0) {
            economy.withdraw(target.getUUID(), current, currency);
        }
        if (BigDecimal.valueOf(amount).signum() > 0) {
            economy.deposit(target.getUUID(), BigDecimal.valueOf(amount), currency);
        }
        source.sendSuccess(() -> Component.literal(
            "Set " + target.getName().getString() + "'s balance to " + amount + " " + currency.symbol()
        ), true);
        return Command.SINGLE_SUCCESS;
    }
}
