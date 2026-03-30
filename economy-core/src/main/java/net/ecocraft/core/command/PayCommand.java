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
import net.ecocraft.core.permission.EcoPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class PayCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies) {
        var currSuggestions = EcoCommands.currencySuggestions(currencies);

        dispatcher.register(Commands.literal("pay")
            .then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .executes(ctx -> executePay(ctx, economy.get(), currencies.get()))
                    .then(Commands.argument("currency", StringArgumentType.word())
                        .suggests(currSuggestions)
                        .executes(ctx -> executePay(ctx, economy.get(), currencies.get()))
                    )
                )
            )
        );
    }

    private static int executePay(CommandContext<CommandSourceStack> ctx,
                                  EconomyProvider economy, CurrencyRegistry currencies) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        double amount = DoubleArgumentType.getDouble(ctx, "amount");
        Currency currency = EcoCommands.resolveCurrency(ctx, currencies);
        return pay(ctx.getSource(), sender, target, amount, economy, currency);
    }

    private static int pay(CommandSourceStack source, ServerPlayer sender, ServerPlayer target,
                           double amount, EconomyProvider economy, Currency currency) {
        if (!PermissionAPI.getPermission(sender, EcoPermissions.PAY)) {
            source.sendFailure(Component.translatable("ecocraft_core.command.pay.no_permission"));
            return 0;
        }

        if (sender.getUUID().equals(target.getUUID())) {
            source.sendFailure(Component.translatable("ecocraft_core.command.pay.self"));
            return 0;
        }

        long smallestUnit = CurrencyFormatter.toSmallestUnit(BigDecimal.valueOf(amount), currency);
        BigDecimal bdAmount = CurrencyFormatter.fromSmallestUnit(smallestUnit, currency);
        var result = economy.transfer(sender.getUUID(), target.getUUID(),
            bdAmount, currency);

        if (result.successful()) {
            String formatted = CurrencyFormatter.format(smallestUnit, currency);
            source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.pay.sent", formatted, target.getName().getString()
            ), false);
            target.sendSystemMessage(Component.translatable(
                "ecocraft_core.command.pay.received", formatted, sender.getName().getString()
            ));
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }
}
