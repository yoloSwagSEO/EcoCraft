package net.ecocraft.core.compat.kubejs;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.core.EcoServerEvents;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class EcoBindings {

    public static long getBalance(ServerPlayer player) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return 0;
        Currency currency = reg.getDefault();
        return eco.getVirtualBalance(player.getUUID(), currency)
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public static long getBalance(ServerPlayer player, String currencyId) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return 0;
        Currency currency = reg.getById(currencyId);
        if (currency == null) return 0;
        return eco.getVirtualBalance(player.getUUID(), currency)
                .setScale(0, RoundingMode.DOWN).longValueExact();
    }

    public static boolean canAfford(ServerPlayer player, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.canAfford(player.getUUID(), BigDecimal.valueOf(amount), currency);
    }

    public static boolean deposit(ServerPlayer player, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.deposit(player.getUUID(), BigDecimal.valueOf(amount), currency).successful();
    }

    public static boolean withdraw(ServerPlayer player, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.withdraw(player.getUUID(), BigDecimal.valueOf(amount), currency).successful();
    }

    public static boolean transfer(ServerPlayer from, ServerPlayer to, long amount) {
        var eco = EcoServerEvents.getEconomy();
        var reg = EcoServerEvents.getCurrencyRegistry();
        if (eco == null || reg == null) return false;
        Currency currency = reg.getDefault();
        return eco.transfer(from.getUUID(), to.getUUID(), BigDecimal.valueOf(amount), currency).successful();
    }

    public static void setBalance(ServerPlayer player, long amount) {
        var ctx = EcoServerEvents.getContext();
        if (ctx == null) return;
        var eco = (EconomyProviderImpl) ctx.getEconomyProvider();
        Currency currency = ctx.getCurrencyRegistry().getDefault();
        eco.setBalance(player.getUUID(), BigDecimal.valueOf(amount), currency);
    }
}
