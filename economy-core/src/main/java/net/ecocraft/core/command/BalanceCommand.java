package net.ecocraft.core.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.core.permission.PermissionChecker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class BalanceCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                Supplier<EconomyProvider> economy,
                                Supplier<CurrencyRegistry> currencies,
                                Supplier<PermissionChecker> permissions) {
        dispatcher.register(Commands.literal("balance")
            // /balance — own balance
            .executes(ctx -> showOwnBalance(ctx.getSource(), economy.get(), currencies.get()))

            // /balance list — all balances (admin)
            .then(Commands.literal("list")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> showAllBalances(ctx.getSource(), economy.get(), currencies.get()))
            )

            // /balance of <name> — any player (online or offline)
            .then(Commands.literal("of")
                .requires(src -> src.hasPermission(1))
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "name");
                        return showOfflineBalance(ctx.getSource(), name, economy.get(), currencies.get());
                    })
                )
            )
        );

        // /bal alias
        dispatcher.register(Commands.literal("bal")
            .executes(ctx -> showOwnBalance(ctx.getSource(), economy.get(), currencies.get()))
        );
    }

    private static int showOwnBalance(CommandSourceStack source, EconomyProvider economy,
                                       CurrencyRegistry currencies) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("ecocraft_core.command.player_only"));
            return 0;
        }

        Currency currency = currencies.getDefault();
        var balance = economy.getVirtualBalance(player.getUUID(), currency);
        source.sendSuccess(() -> Component.translatable("ecocraft_core.command.balance.self",
                balance.toPlainString(), currency.symbol()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int showAllBalances(CommandSourceStack source, EconomyProvider economy,
                                        CurrencyRegistry currencies) {
        Currency currency = currencies.getDefault();
        var storage = net.ecocraft.core.EcoServerEvents.getStorage();
        if (storage == null) {
            source.sendFailure(Component.literal("Storage not available."));
            return 0;
        }

        var balances = storage.getProvider().getAllBalances(currency.id());
        if (balances.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("ecocraft_core.command.balance.list_empty"), false);
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("ecocraft_core.command.balance.list_header"), false);
        var server = source.getServer();
        int rank = 1;
        for (var entry : balances) {
            String name = resolvePlayerName(server, entry.playerUuid());
            int r = rank++;
            source.sendSuccess(() -> Component.literal(
                    "  #" + r + " " + name + ": " + entry.amount().toPlainString() + " " + currency.symbol()), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int showOfflineBalance(CommandSourceStack source, String playerName,
                                           EconomyProvider economy, CurrencyRegistry currencies) {
        var server = source.getServer();
        if (server == null) return 0;

        // Try online player first
        ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            Currency currency = currencies.getDefault();
            var balance = economy.getVirtualBalance(online.getUUID(), currency);
            source.sendSuccess(() -> Component.translatable("ecocraft_core.command.balance.other",
                    online.getName().getString(), balance.toPlainString(), currency.symbol()), false);
            return Command.SINGLE_SUCCESS;
        }

        // Try profile cache for offline players
        var profileCache = server.getProfileCache();
        if (profileCache != null) {
            Optional<com.mojang.authlib.GameProfile> profile = profileCache.get(playerName);
            if (profile.isPresent()) {
                UUID uuid = profile.get().getId();
                Currency currency = currencies.getDefault();
                var balance = economy.getVirtualBalance(uuid, currency);
                source.sendSuccess(() -> Component.translatable("ecocraft_core.command.balance.other",
                        playerName, balance.toPlainString(), currency.symbol()), false);
                return Command.SINGLE_SUCCESS;
            }
        }

        source.sendFailure(Component.translatable("ecocraft_core.command.balance.player_not_found", playerName));
        return 0;
    }

    private static String resolvePlayerName(MinecraftServer server, UUID uuid) {
        // Try online player
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (online != null) return online.getName().getString();

        // Try profile cache
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) return profile.get().getName();
        }

        // Fallback: short UUID
        return uuid.toString().substring(0, 8) + "...";
    }
}
