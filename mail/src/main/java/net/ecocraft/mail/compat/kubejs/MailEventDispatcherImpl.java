package net.ecocraft.mail.compat.kubejs;

import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.service.MailService;
import net.ecocraft.mail.compat.kubejs.event.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class MailEventDispatcherImpl implements MailService.MailEventDispatcher {

    private final MinecraftServer server;

    public MailEventDispatcherImpl(MinecraftServer server) {
        this.server = server;
    }

    private @Nullable ServerPlayer getPlayer(UUID uuid) {
        return server.getPlayerList().getPlayer(uuid);
    }

    @Override
    public boolean fireMailSending(UUID senderUuid, UUID recipientUuid, String subject,
                                    boolean hasItems, boolean hasCurrency, long codAmount) {
        if (!MailEventGroup.MAIL_SENDING.hasListeners()) return true;
        ServerPlayer sp = getPlayer(senderUuid);
        if (sp == null) return true;
        var event = new MailSendingEvent(sp, recipientUuid.toString(), subject,
                hasItems, hasCurrency, codAmount);
        MailEventGroup.MAIL_SENDING.post(event);
        return !event.isCancelled();
    }

    @Override
    public void fireMailSent(Mail mail) {
        if (!MailEventGroup.MAIL_SENT.hasListeners()) return;
        MailEventGroup.MAIL_SENT.post(new MailSentEvent(
                mail.id(), mail.senderName(),
                mail.senderUuid() != null ? mail.senderUuid().toString() : null,
                mail.recipientUuid().toString(), mail.subject(),
                mail.hasItems(), mail.hasCurrency(), mail.codAmount()));
    }

    @Override
    public void fireMailReceived(Mail mail) {
        if (!MailEventGroup.MAIL_RECEIVED.hasListeners()) return;
        MailEventGroup.MAIL_RECEIVED.post(new MailReceivedEvent(
                mail.recipientUuid().toString(), mail.id(), mail.subject(), mail.senderName()));
    }

    @Override
    public void fireMailCollected(UUID playerUuid, Mail mail) {
        if (!MailEventGroup.MAIL_COLLECTED.hasListeners()) return;
        MailEventGroup.MAIL_COLLECTED.post(new MailCollectedEvent(
                playerUuid.toString(), mail.id(),
                mail.items() != null ? mail.items().size() : 0,
                mail.currencyAmount()));
    }

    @Override
    public void fireMailRead(UUID playerUuid, String mailId) {
        if (!MailEventGroup.MAIL_READ.hasListeners()) return;
        MailEventGroup.MAIL_READ.post(new MailReadEvent(playerUuid.toString(), mailId));
    }

    @Override
    public void fireMailDeleted(UUID playerUuid, String mailId) {
        if (!MailEventGroup.MAIL_DELETED.hasListeners()) return;
        MailEventGroup.MAIL_DELETED.post(new MailDeletedEvent(playerUuid.toString(), mailId));
    }

    @Override
    public void fireCODPaid(UUID payerUuid, UUID senderUuid, long amount, String mailId) {
        if (!MailEventGroup.COD_PAID.hasListeners()) return;
        MailEventGroup.COD_PAID.post(new CODPaidEvent(
                payerUuid.toString(), senderUuid.toString(), amount, mailId));
    }

    @Override
    public void fireCODReturned(UUID playerUuid, String mailId) {
        if (!MailEventGroup.COD_RETURNED.hasListeners()) return;
        MailEventGroup.COD_RETURNED.post(new CODReturnedEvent(playerUuid.toString(), mailId));
    }

    @Override
    public void fireMailExpired(List<String> mailIds, int count) {
        if (!MailEventGroup.MAIL_EXPIRED.hasListeners()) return;
        MailEventGroup.MAIL_EXPIRED.post(new MailExpiredEvent(mailIds, count));
    }
}
