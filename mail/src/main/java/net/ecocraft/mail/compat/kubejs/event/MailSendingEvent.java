package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired before a mail is sent. Can be cancelled to prevent sending.
 */
public class MailSendingEvent implements KubeEvent {
    private final ServerPlayer sender;
    private final String recipientUuid;
    private final String subject;
    private final boolean hasItems;
    private final boolean hasCurrency;
    private final long codAmount;
    private boolean cancelled = false;
    private String message = "Envoi de mail bloque par un script";

    public MailSendingEvent(ServerPlayer sender, String recipientUuid, String subject,
                             boolean hasItems, boolean hasCurrency, long codAmount) {
        this.sender = sender;
        this.recipientUuid = recipientUuid;
        this.subject = subject;
        this.hasItems = hasItems;
        this.hasCurrency = hasCurrency;
        this.codAmount = codAmount;
    }

    public ServerPlayer getSender() { return sender; }
    public String getRecipientUuid() { return recipientUuid; }
    public String getSubject() { return subject; }
    public boolean hasItems() { return hasItems; }
    public boolean hasCurrency() { return hasCurrency; }
    public long getCodAmount() { return codAmount; }
    public boolean isCancelled() { return cancelled; }
    public String getMessage() { return message; }
    public void cancel() { cancelled = true; }
    public void setMessage(String msg) { this.message = msg; }
}
