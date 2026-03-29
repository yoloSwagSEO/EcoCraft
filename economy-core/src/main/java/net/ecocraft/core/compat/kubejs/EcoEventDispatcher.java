package net.ecocraft.core.compat.kubejs;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.transaction.TransactionType;
import net.ecocraft.core.compat.kubejs.event.BalanceChangedEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPreEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPostEvent;
import net.ecocraft.core.impl.EconomyProviderImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

public class EcoEventDispatcher implements EconomyProviderImpl.TransactionEventDispatcher {

    private final MinecraftServer server;

    public EcoEventDispatcher(MinecraftServer server) {
        this.server = server;
    }

    private @Nullable ServerPlayer getPlayer(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean firePreTransaction(UUID player, BigDecimal amount, Currency currency,
                                       TransactionType type, @Nullable UUID target) {
        if (!EcoEventGroup.TRANSACTION.hasListeners()) return true;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return true; // offline player — skip event, allow operation

        long amountLong = amount.setScale(0, RoundingMode.DOWN).longValueExact();
        ServerPlayer targetPlayer = target != null ? getPlayer(target) : null;
        var event = new TransactionPreEvent(sp, amountLong, currency.id(), type.name(), targetPlayer);
        EcoEventGroup.TRANSACTION.post(event);
        return !event.isCancelled();
    }

    @Override
    public void firePostTransaction(UUID player, BigDecimal amount, Currency currency,
                                     TransactionType type, @Nullable UUID target, boolean success) {
        if (!EcoEventGroup.TRANSACTION_AFTER.hasListeners()) return;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return;

        long amountLong = amount.setScale(0, RoundingMode.DOWN).longValueExact();
        ServerPlayer targetPlayer = target != null ? getPlayer(target) : null;
        EcoEventGroup.TRANSACTION_AFTER.post(new TransactionPostEvent(sp, amountLong, currency.id(),
                type.name(), targetPlayer, success));
    }

    @Override
    public void fireBalanceChanged(UUID player, long oldBalance, long newBalance,
                                    Currency currency, String cause) {
        if (!EcoEventGroup.BALANCE_CHANGED.hasListeners()) return;
        ServerPlayer sp = getPlayer(player);
        if (sp == null) return;

        EcoEventGroup.BALANCE_CHANGED.post(new BalanceChangedEvent(sp, oldBalance, newBalance,
                currency.id(), cause));
    }
}
