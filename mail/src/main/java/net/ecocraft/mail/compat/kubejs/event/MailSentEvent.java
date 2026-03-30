package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Fired after a mail has been sent successfully.
 */
public class MailSentEvent implements KubeEvent {
    private final String mailId;
    private final String senderName;
    @Nullable
    private final String senderUuid;
    private final String recipientUuid;
    private final String subject;
    private final boolean hasItems;
    private final boolean hasCurrency;
    private final long codAmount;

    public MailSentEvent(String mailId, String senderName, @Nullable String senderUuid,
                          String recipientUuid, String subject,
                          boolean hasItems, boolean hasCurrency, long codAmount) {
        this.mailId = mailId;
        this.senderName = senderName;
        this.senderUuid = senderUuid;
        this.recipientUuid = recipientUuid;
        this.subject = subject;
        this.hasItems = hasItems;
        this.hasCurrency = hasCurrency;
        this.codAmount = codAmount;
    }

    public String getMailId() { return mailId; }
    public String getSenderName() { return senderName; }
    @Nullable
    public String getSenderUuid() { return senderUuid; }
    public String getRecipientUuid() { return recipientUuid; }
    public String getSubject() { return subject; }
    public boolean hasItems() { return hasItems; }
    public boolean hasCurrency() { return hasCurrency; }
    public long getCodAmount() { return codAmount; }
}
