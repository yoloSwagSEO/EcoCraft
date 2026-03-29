package net.ecocraft.core.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

public class BalanceChangedEvent implements KubeEvent {
    private final ServerPlayer player;
    private final long oldBalance;
    private final long newBalance;
    private final String currency;
    private final String cause;

    public BalanceChangedEvent(ServerPlayer player, long oldBalance, long newBalance,
                                String currency, String cause) {
        this.player = player;
        this.oldBalance = oldBalance;
        this.newBalance = newBalance;
        this.currency = currency;
        this.cause = cause;
    }

    public ServerPlayer getPlayer() { return player; }
    public long getOldBalance() { return oldBalance; }
    public long getNewBalance() { return newBalance; }
    public String getCurrency() { return currency; }
    public String getCause() { return cause; }
}
