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

public class PayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<PermissionChecker> permissions) {
        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> {
                        ServerPlayer sender = ctx.getSource().getPlayerOrException();
                        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                        double amount = DoubleArgumentType.getDouble(ctx, "amount");
                        return pay(ctx.getSource(), sender, target, amount, economy.get(), currencies.get(), permissions.get());
                    })
                )
            )
        );
    }

    private static int pay(CommandSourceStack source, ServerPlayer sender, ServerPlayer target,
                           double amount, EconomyProvider economy, CurrencyRegistry currencies,
                           PermissionChecker permissions) {
        if (!permissions.hasPermission(sender, "economy.pay")) {
            source.sendFailure(Component.translatable("ecocraft_core.command.pay.no_permission"));
            return 0;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("ecocraft_core.command.pay.self"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var result = economy.transfer(sender.getUUID(), target.getUUID(),
            BigDecimal.valueOf(amount), currency);

        if (result.successful()) {
            source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.pay.sent", amount, currency.symbol(), target.getName().getString()
            ), false);
            target.sendSystemMessage(Component.translatable(
                "ecocraft_core.command.pay.received", amount, currency.symbol(), sender.getName().getString()
            ));
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }
}
