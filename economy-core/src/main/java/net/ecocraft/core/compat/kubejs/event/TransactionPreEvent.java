package net.ecocraft.core.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public class TransactionPreEvent implements KubeEvent {
    private final ServerPlayer player;
    private final long amount;
    private final String currency;
    private final String type;
    private final @Nullable ServerPlayer target;
    private boolean cancelled = false;
    private String message = "Operation cancelled by script";

    public TransactionPreEvent(ServerPlayer player, long amount, String currency,
                                String type, @Nullable ServerPlayer target) {
        this.player = player;
        this.amount = amount;
        this.currency = currency;
        this.type = type;
        this.target = target;
    }

    public ServerPlayer getPlayer() { return player; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getType() { return type; }
    public @Nullable ServerPlayer getTarget() { return target; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }

    public void cancel() { this.cancelled = true; }
    public void setMessage(String message) { this.message = message; }
}
