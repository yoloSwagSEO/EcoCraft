package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class BalanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<PermissionChecker> permissions) {
        dispatcher.register(Commands.literal("balance")
            .executes(ctx -> showOwnBalance(ctx.getSource(), economy.get(), currencies.get()))
            .then(Commands.argument("player", EntityArgument.player())
                .executes(ctx -> {
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    return showPlayerBalance(ctx.getSource(), target, economy.get(), currencies.get(), permissions.get());
                })
            )
        );

        // Alias
        dispatcher.register(Commands.literal("bal")
            .executes(ctx -> showOwnBalance(ctx.getSource(), economy.get(), currencies.get()))
        );
    }

    private static int showOwnBalance(CommandSourceStack source, EconomyProvider economy,
                                       CurrencyRegistry currencies) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var balance = economy.getBalance(player.getUUID(), currency);
        source.sendSuccess(() -> Component.literal(
            "Balance: " + balance.toPlainString() + " " + currency.symbol()
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showPlayerBalance(CommandSourceStack source, ServerPlayer target,
                                          EconomyProvider economy, CurrencyRegistry currencies,
                                          PermissionChecker permissions) {
        ServerPlayer sender = source.getPlayer();
        if (sender != null && !permissions.hasPermission(sender, "economy.balance.others")) {
            source.sendFailure(Component.literal("You don't have permission to check other players' balance"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var balance = economy.getBalance(target.getUUID(), currency);
        source.sendSuccess(() -> Component.literal(
            target.getName().getString() + "'s balance: " + balance.toPlainString() + " " + currency.symbol()
        ), false);
        return Command.SINGLE_SUCCESS;
    }
}
