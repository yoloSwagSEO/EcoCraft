package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Fired when a mail is marked as read.
 */
public class MailReadEvent implements KubeEvent {
    private final String playerUuid;
    private final String mailId;

    public MailReadEvent(String playerUuid, String mailId) {
        this.playerUuid = playerUuid;
        this.mailId = mailId;
    }

    public String getPlayerUuid() { return playerUuid; }
    public String getMailId() { return mailId; }
}
