package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Fired when a player collects attachments from a mail.
 */
public class MailCollectedEvent implements KubeEvent {
    private final String playerUuid;
    private final String mailId;
    private final int itemCount;
    private final long currencyAmount;

    public MailCollectedEvent(String playerUuid, String mailId, int itemCount, long currencyAmount) {
        this.playerUuid = playerUuid;
        this.mailId = mailId;
        this.itemCount = itemCount;
        this.currencyAmount = currencyAmount;
    }

    public String getPlayerUuid() { return playerUuid; }
    public String getMailId() { return mailId; }
    public int getItemCount() { return itemCount; }
    public long getCurrencyAmount() { return currencyAmount; }
}
