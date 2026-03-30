package net.ecocraft.mail.compat.kubejs.event;

import dev.latvian.mods.kubejs.event.KubeEvent;

/**
 * Fired when a COD (Cash on Delivery) payment is made.
 */
public class CODPaidEvent implements KubeEvent {
    private final String payerUuid;
    private final String senderUuid;
    private final long amount;
    private final String mailId;

    public CODPaidEvent(String payerUuid, String senderUuid, long amount, String mailId) {
        this.payerUuid = payerUuid;
        this.senderUuid = senderUuid;
        this.amount = amount;
        this.mailId = mailId;
    }

    public String getPayerUuid() { return payerUuid; }
    public String getSenderUuid() { return senderUuid; }
    public long getAmount() { return amount; }
    public String getMailId() { return mailId; }
}
