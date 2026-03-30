package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyFormatter;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.exchange.ExchangeRate;
import net.ecocraft.api.exchange.ExchangeService;
import net.ecocraft.api.transaction.TransactionFilter;
import net.ecocraft.api.transaction.TransactionLog;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.impl.ExchangeServiceImpl;
import net.ecocraft.core.permission.EcoPermissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Supplier;

public class CurrencyCommand {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<ExchangeService> exchange,
                                Supplier<TransactionLog> transactionLog) {
        var currSuggestions = EcoCommands.currencySuggestions(currencies);

        dispatcher.register(Commands.literal("currency")
            .then(Commands.literal("list")
                .executes(ctx -> listCurrencies(ctx.getSource(), currencies.get()))
            )
            .then(Commands.literal("convert")
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01))
                    .then(Commands.argument("from", StringArgumentType.word())
                        .suggests(currSuggestions)
                        .then(Commands.argument("to", StringArgumentType.word())
                            .suggests(currSuggestions)
                            .executes(ctx -> {
                                ServerPlayer player = ctx.getSource().getPlayerOrException();
                                double amount = DoubleArgumentType.getDouble(ctx, "amount");
                                String fromId = StringArgumentType.getString(ctx, "from");
                                String toId = StringArgumentType.getString(ctx, "to");
                                return convert(ctx.getSource(), player, amount, fromId, toId,
                                    currencies.get(), exchange.get());
                            })
                        )
                    )
                )
            )
            .then(Commands.literal("rate")
                .then(Commands.argument("currency", StringArgumentType.word())
                    .suggests(currSuggestions)
                    .executes(ctx -> {
                        String currId = StringArgumentType.getString(ctx, "currency");
                        return showRate(ctx.getSource(), currId, currencies.get(), exchange.get());
                    })
                )
            )
            .then(Commands.literal("setrate")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("currency", StringArgumentType.word())
                    .suggests(currSuggestions)
                    .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.0001))
                        .executes(ctx -> {
                            String currId = StringArgumentType.getString(ctx, "currency");
                            double rate = DoubleArgumentType.getDouble(ctx, "rate");
                            return setRate(ctx.getSource(), currId, rate, currencies.get(), exchange.get());
                        })
                    )
                )
            )
            .then(Commands.literal("history")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> showHistory(ctx.getSource(), currencies.get(), transactionLog.get()))
            )
        );
    }

    private static int listCurrencies(CommandSourceStack source, CurrencyRegistry currencies) {
        var all = currencies.listAll();
        if (all.isEmpty()) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.none"));
            return 0;
        }

        Currency def = currencies.getDefault();
        source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.header"), false);
        for (Currency c : all) {
            String marker = c.id().equals(def.id())
                ? Component.translatable("ecocraft_core.command.currency.default_marker").getString()
                : "";
            String exchangeMarker = c.exchangeable()
                ? Component.translatable("ecocraft_core.command.currency.exchangeable_marker").getString()
                : "";
            source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.currency.entry", c.symbol(), c.name(), c.id(), marker + exchangeMarker
            ), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int convert(CommandSourceStack source, ServerPlayer player, double amount,
                               String fromId, String toId,
                               CurrencyRegistry currencies, ExchangeService exchange) {
        if (!PermissionAPI.getPermission(player, EcoPermissions.EXCHANGE)) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.exchange_no_permission"));
            return 0;
        }

        Currency from = currencies.getById(fromId);
        Currency to = currencies.getById(toId);
        if (from == null || to == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.unknown"));
            return 0;
        }

        long smallestUnit = CurrencyFormatter.toSmallestUnit(BigDecimal.valueOf(amount), from);
        BigDecimal bdAmount = CurrencyFormatter.fromSmallestUnit(smallestUnit, from);
        var result = exchange.convert(player.getUUID(), bdAmount, from, to);
        if (result.successful()) {
            String formattedFrom = CurrencyFormatter.format(smallestUnit, from);
            source.sendSuccess(() -> Component.translatable(
                "ecocraft_core.command.currency.converted", formattedFrom, to.name()
            ), false);
            return Command.SINGLE_SUCCESS;
        } else {
            source.sendFailure(Component.literal(result.errorMessage()));
            return 0;
        }
    }

    private static int showRate(CommandSourceStack source, String currencyId,
                                CurrencyRegistry currencies, ExchangeService exchange) {
        Currency currency = currencies.getById(currencyId);
        if (currency == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.unknown"));
            return 0;
        }

        if (!currency.exchangeable()) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.not_exchangeable", currency.name()));
            return 0;
        }

        // Find the reference currency (rate = 1.0) to display against
        Currency refCurrency = null;
        for (Currency c : currencies.listAll()) {
            if (c.isReference() && c.exchangeable()) {
                refCurrency = c;
                break;
            }
        }

        if (refCurrency == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.no_reference"));
            return 0;
        }

        if (currency.id().equals(refCurrency.id())) {
            source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.is_reference",
                    currency.name()), false);
            return Command.SINGLE_SUCCESS;
        }

        ExchangeRate rate = exchange.getRate(currency, refCurrency);
        if (rate == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.no_rate",
                    currency.name(), refCurrency.name()));
            return 0;
        }

        String rateStr = rate.rate().setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String feeStr = rate.feeRate().multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        Currency ref = refCurrency;
        source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.rate_info",
                currency.symbol(), rateStr, ref.symbol(), feeStr), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int setRate(CommandSourceStack source, String currencyId, double rate,
                               CurrencyRegistry currencies, ExchangeService exchange) {
        Currency currency = currencies.getById(currencyId);
        if (currency == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.unknown"));
            return 0;
        }

        // Find the reference currency
        Currency refCurrency = null;
        for (Currency c : currencies.listAll()) {
            if (c.isReference() && c.exchangeable()) {
                refCurrency = c;
                break;
            }
        }

        if (refCurrency == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.no_reference"));
            return 0;
        }

        if (currency.id().equals(refCurrency.id())) {
            source.sendFailure(Component.translatable("ecocraft_core.command.currency.cannot_set_reference_rate"));
            return 0;
        }

        // Save the rate via ExchangeServiceImpl
        if (exchange instanceof ExchangeServiceImpl impl) {
            BigDecimal rateVal = BigDecimal.valueOf(rate);
            BigDecimal fee = BigDecimal.valueOf(2).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            impl.saveRate(currency.id(), refCurrency.id(), rateVal, fee);
            // Also save reverse rate
            BigDecimal reverseRate = BigDecimal.ONE.divide(rateVal, 10, RoundingMode.HALF_UP);
            impl.saveRate(refCurrency.id(), currency.id(), reverseRate, fee);

            String rateStr = rateVal.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            Currency ref = refCurrency;
            source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.rate_set",
                    currency.symbol(), rateStr, ref.symbol()), true);
            return Command.SINGLE_SUCCESS;
        }

        source.sendFailure(Component.literal("Exchange service does not support rate persistence."));
        return 0;
    }

    private static int showHistory(CommandSourceStack source, CurrencyRegistry currencies,
                                   TransactionLog transactionLog) {
        if (transactionLog == null) {
            source.sendFailure(Component.literal("Transaction log not available."));
            return 0;
        }

        var filter = new TransactionFilter(null, TransactionType.EXCHANGE, null, null, 0, 10);
        var page = transactionLog.getHistory(filter);

        if (page.items().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.history_empty"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.history_header"), false);
        MinecraftServer server = source.getServer();

        for (var tx : page.items()) {
            String playerName = tx.from() != null ? resolvePlayerName(server, tx.from()) : "?";
            String formatted = CurrencyFormatter.format(tx.amount().longValue(), tx.currency());
            String time = TIME_FMT.format(tx.timestamp());
            source.sendSuccess(() -> Component.translatable("ecocraft_core.command.currency.history_entry",
                    time, playerName, formatted), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        if (server == null) return uuid.toString().substring(0, 8) + "...";
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }
        return uuid.toString().substring(0, 8) + "...";
    }
}
