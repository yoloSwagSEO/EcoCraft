package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Fired when a COD mail is returned to the sender.
 */
public class CODReturnedEvent implements KubeEvent {
    private final String playerUuid;
    private final String mailId;

    public CODReturnedEvent(String playerUuid, String mailId) {
        this.playerUuid = playerUuid;
        this.mailId = mailId;
    }

    public String getPlayerUuid() { return playerUuid; }
    public String getMailId() { return mailId; }
}
