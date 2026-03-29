package net.ecocraft.core.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import net.ecocraft.core.compat.kubejs.event.BalanceChangedEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPostEvent;
import net.ecocraft.core.compat.kubejs.event.TransactionPreEvent;

public interface EcoEventGroup {
    EventGroup GROUP = EventGroup.of("EcocraftEvents");

    EventHandler TRANSACTION = GROUP.server("transaction", () -> TransactionPreEvent.class);
    EventHandler TRANSACTION_AFTER = GROUP.server("transactionAfter", () -> TransactionPostEvent.class);
    EventHandler BALANCE_CHANGED = GROUP.server("balanceChanged", () -> BalanceChangedEvent.class);
}
