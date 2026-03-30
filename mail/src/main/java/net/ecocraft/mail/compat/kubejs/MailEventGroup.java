package net.ecocraft.mail.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import net.ecocraft.mail.compat.kubejs.event.*;

public interface MailEventGroup {
    EventGroup GROUP = EventGroup.of("EcocraftMailEvents");

    EventHandler MAIL_SENDING = GROUP.server("mailSending", () -> MailSendingEvent.class);
    EventHandler MAIL_SENT = GROUP.server("mailSent", () -> MailSentEvent.class);
    EventHandler MAIL_RECEIVED = GROUP.server("mailReceived", () -> MailReceivedEvent.class);
    EventHandler MAIL_COLLECTED = GROUP.server("mailCollected", () -> MailCollectedEvent.class);
    EventHandler MAIL_READ = GROUP.server("mailRead", () -> MailReadEvent.class);
    EventHandler MAIL_DELETED = GROUP.server("mailDeleted", () -> MailDeletedEvent.class);
    EventHandler COD_PAID = GROUP.server("codPaid", () -> CODPaidEvent.class);
    EventHandler COD_RETURNED = GROUP.server("codReturned", () -> CODReturnedEvent.class);
    EventHandler MAIL_EXPIRED = GROUP.server("mailExpired", () -> MailExpiredEvent.class);
}
