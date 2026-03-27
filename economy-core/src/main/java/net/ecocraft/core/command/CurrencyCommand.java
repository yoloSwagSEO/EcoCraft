package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.util.function.Supplier;

public class CurrencyCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<ExchangeService> exchange,
                                Supplier<PermissionChecker> permissions) {
        dispatcher.register(Commands.literal("currency")
            .then(Commands.literal("list")
                .executes(ctx -> listCurrencies(ctx.getSource(), currencies.get()))
            )
            .then(Commands.literal("convert")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .then(Commands.argument("from", StringArgumentType.word())
                        .then(Commands.argument("to", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                String fromId = StringArgumentType.getString(ctx, "from");
                                String toId = StringArgumentType.getString(ctx, "to");
                                return convert(ctx.getSource(), player, amount, fromId, toId,
                                    currencies.get(), exchange.get(), permissions.get());
                            })
                        )
                    )
                )
            )
        );
    }

    private static int listCurrencies(CommandSourceStack source, CurrencyRegistry currencies) {
        var all = currencies.listAll();
        if (all.isEmpty()) {
            source.sendFailure(Component.literal("No currencies registered"));
            return 0;
        }

        Currency def = currencies.getDefault();
        source.sendSuccess(() -> Component.literal("§6=== Currencies ==="), false);
        for (Currency c : all) {
            String marker = c.id().equals(def.id()) ? " §a(default)" : "";
            source.sendSuccess(() -> Component.literal(
                "§f" + c.symbol() + " " + c.name() + " §7(" + c.id() + ")" + marker
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int convert(CommandSourceStack source, ServerPlayer player, double amount,
                               String fromId, String toId,
                               CurrencyRegistry currencies, ExchangeService exchange,
                               PermissionChecker permissions) {
        if (!permissions.hasPermission(player, "economy.exchange")) {
            source.sendFailure(Component.literal("You don't have permission to exchange currencies"));
            return 0;
        }

        Currency from = currencies.getById(fromId);
        Currency to = currencies.getById(toId);
        if (from == null || to == null) {
            source.sendFailure(Component.literal("Unknown currency"));
            return 0;
        }

        var result = exchange.convert(player.getUUID(), BigDecimal.valueOf(amount), from, to);
        if (result.successful()) {
            source.sendSuccess(() -> Component.literal(
                "Converted " + amount + " " + from.symbol() + " to " + to.symbol()
            ), false);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }
}
