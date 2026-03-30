package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

import java.util.List;

/**
 * Fired when mails expire during the purge tick.
 */
public class MailExpiredEvent implements KubeEvent {
    private final List<String> mailIds;
    private final int count;

    public MailExpiredEvent(List<String> mailIds, int count) {
        this.mailIds = mailIds;
        this.count = count;
    }

    public List<String> getMailIds() { return mailIds; }
    public int getCount() { return count; }
}
