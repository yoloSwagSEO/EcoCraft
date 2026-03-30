package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Fired when a mail is delivered to a recipient's mailbox.
 */
public class MailReceivedEvent implements KubeEvent {
    private final String recipientUuid;
    private final String mailId;
    private final String subject;
    private final String senderName;

    public MailReceivedEvent(String recipientUuid, String mailId, String subject, String senderName) {
        this.recipientUuid = recipientUuid;
        this.mailId = mailId;
        this.subject = subject;
        this.senderName = senderName;
    }

    public String getRecipientUuid() { return recipientUuid; }
    public String getMailId() { return mailId; }
    public String getSubject() { return subject; }
    public String getSenderName() { return senderName; }
}
