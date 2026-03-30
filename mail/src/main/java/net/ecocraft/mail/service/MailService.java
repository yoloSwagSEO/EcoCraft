package net.ecocraft.mail.service;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import net.ecocraft.mail.storage.MailStorageProvider;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Business logic layer for the mail system.
 *
 * <p>Orchestrates {@link MailStorageProvider} and {@link EconomyProvider}.
 * Minecraft-specific types (ServerPlayer, ItemStack) are kept out of this class
 * so that the service remains fully unit-testable without a running server.</p>
 *
 * <p>Item delivery is delegated to an injectable {@link ItemDeliverer} functional interface,
 * set at runtime when the server starts.</p>
 */
public class MailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);

    /** Default mail expiry: 30 days in milliseconds. */
    public static final long DEFAULT_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;

    /** Default COD fee percentage (0 = no fee). */
    public static final int DEFAULT_COD_FEE_PERCENT = 0;

    private final MailStorageProvider storage;
    private final EconomyProvider economy;
    private final CurrencyRegistry currencies;

    private int codFeePercent = DEFAULT_COD_FEE_PERCENT;
    private long expiryMs = DEFAULT_EXPIRY_MS;

    @Nullable
    private ItemDeliverer itemDeliverer;

    @Nullable
    private ReadReceiptNotifier readReceiptNotifier;

    /** KubeJS event dispatcher; null when KubeJS is not loaded. */
    @Nullable
    private MailEventDispatcher mailEventDispatcher;

    /**
     * Functional interface for delivering items to a player.
     * Decoupled from Minecraft classes so {@link MailService} stays unit-testable.
     */
    @FunctionalInterface
    public interface ItemDeliverer {
        void deliver(UUID playerUuid, List<MailItemAttachment> items);
    }

    /**
     * Functional interface for sending read receipt notifications.
     * Decoupled from Minecraft classes so {@link MailService} stays unit-testable.
     */
    @FunctionalInterface
    public interface ReadReceiptNotifier {
        void notify(UUID senderUuid, String recipientName, String subject);
    }

    /**
     * Event dispatcher interface for KubeJS integration.
     * PRE methods return false to cancel the operation; POST methods are fire-and-forget.
     * Null when KubeJS is not loaded.
     */
    public interface MailEventDispatcher {
        boolean fireMailSending(UUID senderUuid, UUID recipientUuid, String subject,
                                 boolean hasItems, boolean hasCurrency, long codAmount);
        void fireMailSent(Mail mail);
        void fireMailReceived(Mail mail);
        void fireMailCollected(UUID playerUuid, Mail mail);
        void fireMailRead(UUID playerUuid, String mailId);
        void fireMailDeleted(UUID playerUuid, String mailId);
        void fireCODPaid(UUID payerUuid, UUID senderUuid, long amount, String mailId);
        void fireCODReturned(UUID playerUuid, String mailId);
        void fireMailExpired(List<String> mailIds, int count);
    }

    public MailService(MailStorageProvider storage, EconomyProvider economy, CurrencyRegistry currencies) {
        this.storage = storage;
        this.economy = economy;
        this.currencies = currencies;
    }

    // -------------------------------------------------------------------------
    // Configuration setters
    // -------------------------------------------------------------------------

    public void setItemDeliverer(@Nullable ItemDeliverer deliverer) {
        this.itemDeliverer = deliverer;
    }

    public void setReadReceiptNotifier(@Nullable ReadReceiptNotifier notifier) {
        this.readReceiptNotifier = notifier;
    }

    /** Sets the KubeJS event dispatcher (called from MailServerEvents when KubeJS is loaded). */
    public void setMailEventDispatcher(@Nullable MailEventDispatcher dispatcher) {
        this.mailEventDispatcher = dispatcher;
    }

    public void setCodFeePercent(int percent) {
        this.codFeePercent = percent;
    }

    public void setExpiryMs(long expiryMs) {
        this.expiryMs = expiryMs;
    }

    public MailStorageProvider getStorage() {
        return storage;
    }

    public @Nullable String getDefaultCurrencyId() {
        var c = currencies.getDefault();
        return c != null ? c.id() : null;
    }

    public String getDefaultCurrencySymbol() {
        var c = currencies.getDefault();
        return c != null ? c.symbol() : "G";
    }

    // -------------------------------------------------------------------------
    // Send mail (player-to-player)
    // -------------------------------------------------------------------------

    /**
     * Sends a player-to-player mail.
     *
     * <p>If currency is attached, it is withdrawn from the sender immediately.
     * Items are assumed already removed from the sender's inventory by the caller.</p>
     *
     * @return the mail ID
     * @throws MailException if currency withdrawal fails
     */
    public String sendMail(UUID senderUuid, String senderName, UUID recipientUuid,
                           String subject, String body,
                           List<MailItemAttachment> items,
                           long currencyAmount, @Nullable String currencyId,
                           long codAmount, @Nullable String codCurrencyId,
                           boolean readReceipt) {

        // KubeJS PRE event
        if (mailEventDispatcher != null && senderUuid != null &&
            !mailEventDispatcher.fireMailSending(senderUuid, recipientUuid, subject,
                    items != null && !items.isEmpty(),
                    currencyAmount > 0 && currencyId != null,
                    codAmount)) {
            throw new MailException("Envoi de mail bloque par un script");
        }

        // Withdraw currency from sender if attached
        if (currencyAmount > 0 && currencyId != null) {
            Currency currency = resolveCurrency(currencyId);
            BigDecimal amount = fromSmallestUnit(currencyAmount, currency);
            TransactionResult result = economy.withdraw(senderUuid, amount, currency);
            if (!result.successful()) {
                throw new MailException("Fonds insuffisants : " + result.errorMessage());
            }
        }

        long now = System.currentTimeMillis();
        String mailId = UUID.randomUUID().toString();

        Mail mail = new Mail(
            mailId,
            senderUuid,
            senderName,
            recipientUuid,
            subject,
            body != null ? body : "",
            items != null ? items : List.of(),
            currencyAmount,
            currencyId,
            codAmount,
            codCurrencyId,
            false,  // read
            false,  // collected
            false,  // indestructible
            false,  // returned
            readReceipt,
            now,    // createdAt
            now,    // availableAt (instant delivery)
            now + expiryMs  // expiresAt
        );

        storage.createMail(mail);
        LOGGER.info("Mail sent from {} to {}: {}", senderName, recipientUuid, subject);

        // KubeJS POST events
        if (mailEventDispatcher != null) {
            mailEventDispatcher.fireMailSent(mail);
            mailEventDispatcher.fireMailReceived(mail);
        }

        return mailId;
    }

    // -------------------------------------------------------------------------
    // Send system mail (public API for other modules)
    // -------------------------------------------------------------------------

    /**
     * Sends a system mail (no sender UUID). This is the public API for other modules
     * (AH integration, quests, admin tools).
     *
     * @param senderName    display name (e.g., "Hotel des Ventes", "[Admin]")
     * @param recipientUuid recipient player
     * @param subject       mail subject
     * @param body          message body
     * @param items         item attachments (can be empty)
     * @param currencyAmount currency amount (0 if none)
     * @param currencyId    currency ID (null if no currency)
     * @param indestructible if true, mail never auto-expires
     * @param availableAt   delivery time (epoch ms), 0 for instant
     * @return the mail ID
     */
    public String sendSystemMail(String senderName, UUID recipientUuid,
                                  String subject, String body,
                                  List<MailItemAttachment> items,
                                  long currencyAmount, @Nullable String currencyId,
                                  boolean indestructible, long availableAt) {

        long now = System.currentTimeMillis();
        if (availableAt <= 0) {
            availableAt = now;
        }

        String mailId = UUID.randomUUID().toString();

        Mail mail = new Mail(
            mailId,
            null,  // system mail — no sender UUID
            senderName,
            recipientUuid,
            subject,
            body != null ? body : "",
            items != null ? items : List.of(),
            currencyAmount,
            currencyId,
            0,     // no COD on system mails
            null,
            false,  // read
            false,  // collected
            indestructible,
            false,  // returned
            false,  // readReceipt
            now,    // createdAt
            availableAt,
            indestructible ? Long.MAX_VALUE : now + expiryMs
        );

        storage.createMail(mail);
        LOGGER.info("System mail sent to {}: {}", recipientUuid, subject);

        // KubeJS POST events
        if (mailEventDispatcher != null) {
            mailEventDispatcher.fireMailSent(mail);
            mailEventDispatcher.fireMailReceived(mail);
        }

        return mailId;
    }

    // -------------------------------------------------------------------------
    // Collect
    // -------------------------------------------------------------------------

    /**
     * Collects a single mail: delivers items and currency to the player.
     *
     * @throws MailException if mail cannot be collected
     */
    public void markRead(UUID playerUuid, String mailId, @Nullable String readerName) {
        Mail mail = getAndValidateOwnership(mailId, playerUuid);
        if (!mail.read()) {
            storage.markRead(mailId);
            // Send read receipt notification to sender if enabled
            if (mail.readReceipt() && mail.senderUuid() != null && readReceiptNotifier != null) {
                String reader = readerName != null ? readerName : playerUuid.toString();
                readReceiptNotifier.notify(mail.senderUuid(), reader, mail.subject());
            }
            // KubeJS POST event
            if (mailEventDispatcher != null) {
                mailEventDispatcher.fireMailRead(playerUuid, mailId);
            }
        }
    }

    /** Overload for backwards compatibility. */
    public void markRead(UUID playerUuid, String mailId) {
        markRead(playerUuid, mailId, null);
    }

    /**
     * @throws MailException if mail cannot be collected
     */
    public void collectMail(UUID playerUuid, String mailId) {
        Mail mail = getAndValidateOwnership(mailId, playerUuid);

        if (!mail.isAvailable()) {
            throw new MailException("Ce mail n'est pas encore disponible");
        }
        if (mail.collected()) {
            throw new MailException("Ce mail a deja ete collecte");
        }
        if (mail.hasCOD()) {
            throw new MailException("Ce mail est en contre-remboursement, utilisez Payer & Collecter");
        }

        deliverAttachments(playerUuid, mail);
        storage.markRead(mailId);
        storage.markCollected(mailId);

        // KubeJS POST event
        if (mailEventDispatcher != null) {
            mailEventDispatcher.fireMailCollected(playerUuid, mail);
        }
    }

    /**
     * Collects all available, non-COD mails with attachments.
     *
     * @return the number of mails collected
     */
    public int collectAllMails(UUID playerUuid) {
        List<Mail> mails = storage.getMailsForPlayer(playerUuid);
        int count = 0;

        for (Mail mail : mails) {
            if (!mail.isAvailable()) continue;
            if (mail.collected()) continue;
            if (mail.hasCOD()) continue;
            if (!mail.hasAttachments()) continue;
            if (mail.isExpired()) continue;

            deliverAttachments(playerUuid, mail);
            storage.markCollected(mail.id());
            count++;
        }

        return count;
    }

    // -------------------------------------------------------------------------
    // COD (Cash on Delivery)
    // -------------------------------------------------------------------------

    /**
     * Pays COD and collects the mail.
     * Withdraws COD amount from recipient, deposits (minus fee) to sender, delivers items.
     *
     * @throws MailException if payment fails
     */
    public void payCOD(UUID playerUuid, String mailId) {
        Mail mail = getAndValidateOwnership(mailId, playerUuid);

        if (!mail.hasCOD()) {
            throw new MailException("Ce mail n'est pas en contre-remboursement");
        }
        if (mail.collected()) {
            throw new MailException("Ce mail a deja ete collecte");
        }

        Currency codCurrency = resolveCurrency(mail.codCurrencyId());

        // Withdraw COD amount from recipient
        BigDecimal codAmountBD = fromSmallestUnit(mail.codAmount(), codCurrency);
        TransactionResult withdrawResult = economy.withdraw(playerUuid, codAmountBD, codCurrency);
        if (!withdrawResult.successful()) {
            throw new MailException("Fonds insuffisants pour le contre-remboursement : " + withdrawResult.errorMessage());
        }

        // Calculate fee and deposit to sender
        long feeAmount = (long) (mail.codAmount() * codFeePercent / 100.0);
        long senderAmount = mail.codAmount() - feeAmount;

        if (senderAmount > 0 && mail.senderUuid() != null) {
            BigDecimal senderAmountBD = fromSmallestUnit(senderAmount, codCurrency);
            economy.deposit(mail.senderUuid(), senderAmountBD, codCurrency);
        }

        // Deliver items and currency to recipient
        deliverAttachments(playerUuid, mail);
        storage.markCollected(mailId);

        LOGGER.info("COD paid for mail {}: {} {} (fee: {})", mailId, codAmountBD, codCurrency.symbol(), feeAmount);

        // KubeJS POST events
        if (mailEventDispatcher != null) {
            if (mail.senderUuid() != null) {
                mailEventDispatcher.fireCODPaid(playerUuid, mail.senderUuid(), mail.codAmount(), mailId);
            }
            mailEventDispatcher.fireMailCollected(playerUuid, mail);
        }
    }

    /**
     * Returns a COD mail to the sender. Creates a new mail with the items (no COD, no currency).
     *
     * @throws MailException if mail cannot be returned
     */
    public void returnCOD(UUID playerUuid, String mailId) {
        Mail mail = getAndValidateOwnership(mailId, playerUuid);

        if (!mail.hasCOD()) {
            throw new MailException("Ce mail n'est pas en contre-remboursement");
        }
        if (mail.collected()) {
            throw new MailException("Ce mail a deja ete collecte");
        }
        if (mail.returned()) {
            throw new MailException("Ce mail a deja ete retourne");
        }
        if (mail.senderUuid() == null) {
            throw new MailException("Impossible de retourner un mail systeme");
        }

        // Create return mail to original sender with items, no COD, no currency
        long now = System.currentTimeMillis();
        String returnMailId = UUID.randomUUID().toString();

        Mail returnMail = new Mail(
            returnMailId,
            null,  // system return — no sender UUID
            mail.recipientUuid().toString(),  // the returner becomes the "sender name"
            mail.senderUuid(),  // send back to original sender
            "Retour : " + mail.subject(),
            "",
            mail.items() != null ? mail.items() : List.of(),
            0,     // no currency on return
            null,
            0,     // no COD on return
            null,
            false,
            false,
            false,
            false,
            false,  // readReceipt
            now,
            now,
            now + expiryMs
        );

        storage.createMail(returnMail);
        storage.markReturned(mailId);

        LOGGER.info("COD mail {} returned to sender {}", mailId, mail.senderUuid());

        // KubeJS POST event
        if (mailEventDispatcher != null) {
            mailEventDispatcher.fireCODReturned(playerUuid, mailId);
        }
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a mail. Only allowed if canDelete() is true.
     *
     * @throws MailException if mail cannot be deleted
     */
    public void deleteMail(UUID playerUuid, String mailId) {
        Mail mail = getAndValidateOwnership(mailId, playerUuid);

        if (!mail.canDelete()) {
            throw new MailException("Ce mail ne peut pas etre supprime (pieces jointes non collectees)");
        }

        storage.deleteMail(mailId);

        // KubeJS POST event
        if (mailEventDispatcher != null) {
            mailEventDispatcher.fireMailDeleted(playerUuid, mailId);
        }
    }

    /**
     * Admin: deletes all mails for a player (regardless of read/collected state).
     *
     * @return number of mails deleted
     */
    public int deleteAllMailsForPlayer(UUID playerUuid) {
        int count = storage.deleteAllMailsForPlayer(playerUuid);
        LOGGER.info("Admin deleted {} mail(s) for player {}", count, playerUuid);
        return count;
    }

    // -------------------------------------------------------------------------
    // Expiration
    // -------------------------------------------------------------------------

    /**
     * Processes expired mails:
     * 1. Auto-return expired COD mails to sender
     * 2. Delete all other expired mails
     */
    public void expireMails() {
        // Step 1: auto-return expired COD mails
        List<Mail> expiredCODs = storage.getExpiredCODMails();
        for (Mail mail : expiredCODs) {
            if (mail.senderUuid() != null) {
                // Create return mail to sender
                long now = System.currentTimeMillis();
                String returnMailId = UUID.randomUUID().toString();

                Mail returnMail = new Mail(
                    returnMailId,
                    null,
                    "Systeme",
                    mail.senderUuid(),
                    "Retour (expire) : " + mail.subject(),
                    "",
                    mail.items() != null ? mail.items() : List.of(),
                    0,
                    null,
                    0,
                    null,
                    false,
                    false,
                    false,
                    false,
                    false,  // readReceipt
                    now,
                    now,
                    now + expiryMs
                );

                storage.createMail(returnMail);
                LOGGER.info("Expired COD mail {} auto-returned to {}", mail.id(), mail.senderUuid());
            }
            storage.markReturned(mail.id());
        }

        // Collect expired COD mail IDs for the KubeJS event
        List<String> expiredMailIds = new ArrayList<>();
        for (Mail codMail : expiredCODs) {
            expiredMailIds.add(codMail.id());
        }

        // Step 2: delete all other expired mails
        storage.deleteExpiredMails();

        // KubeJS POST event
        if (mailEventDispatcher != null && !expiredMailIds.isEmpty()) {
            mailEventDispatcher.fireMailExpired(expiredMailIds, expiredMailIds.size());
        }
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /**
     * Returns available, non-expired mails for the player.
     */
    public List<Mail> getMailsForPlayer(UUID playerUuid) {
        List<Mail> allMails = storage.getMailsForPlayer(playerUuid);
        List<Mail> result = new ArrayList<>();
        for (Mail mail : allMails) {
            if (mail.isAvailable() && !mail.isExpired()) {
                result.add(mail);
            }
        }
        return result;
    }

    /**
     * Returns sent mails for the player (non-expired).
     */
    public List<Mail> getSentMailsForPlayer(UUID playerUuid) {
        List<Mail> allMails = storage.getMailsSentByPlayer(playerUuid);
        List<Mail> result = new ArrayList<>();
        for (Mail mail : allMails) {
            if (!mail.isExpired()) {
                result.add(mail);
            }
        }
        return result;
    }

    /**
     * Gets full mail detail, validates ownership, and marks as read.
     *
     * @return the mail
     * @throws MailException if mail not found or not owned by player
     */
    public Mail getMailDetail(String mailId, UUID playerUuid) {
        Mail mail = getAndValidateOwnership(mailId, playerUuid);

        if (!mail.read()) {
            storage.markRead(mailId);
            // Return updated mail with read=true
            mail = new Mail(
                mail.id(), mail.senderUuid(), mail.senderName(), mail.recipientUuid(),
                mail.subject(), mail.body(), mail.items(), mail.currencyAmount(), mail.currencyId(),
                mail.codAmount(), mail.codCurrencyId(),
                true,  // now read
                mail.collected(), mail.indestructible(), mail.returned(), mail.readReceipt(),
                mail.createdAt(), mail.availableAt(), mail.expiresAt()
            );
        }

        return mail;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Mail getAndValidateOwnership(String mailId, UUID playerUuid) {
        Mail mail = storage.getMailById(mailId);
        if (mail == null) {
            throw new MailException("Mail introuvable : " + mailId);
        }
        if (!mail.recipientUuid().equals(playerUuid)) {
            throw new MailException("Ce mail ne vous appartient pas");
        }
        return mail;
    }

    private void deliverAttachments(UUID playerUuid, Mail mail) {
        // Deliver currency
        if (mail.hasCurrency()) {
            Currency currency = resolveCurrency(mail.currencyId());
            BigDecimal amount = fromSmallestUnit(mail.currencyAmount(), currency);
            economy.deposit(playerUuid, amount, currency);
        }

        // Deliver items
        if (mail.hasItems() && itemDeliverer != null) {
            itemDeliverer.deliver(playerUuid, mail.items());
        }
    }

    private Currency resolveCurrency(String currencyId) {
        Currency currency = currencies.getById(currencyId);
        if (currency == null) {
            throw new MailException("Devise inconnue : " + currencyId);
        }
        return currency;
    }

    /**
     * Converts a stored long back to a BigDecimal.
     */
    public static BigDecimal fromSmallestUnit(long amount, Currency currency) {
        return BigDecimal.valueOf(amount);
    }

    /**
     * Converts a BigDecimal amount to long for storage.
     */
    public static long toSmallestUnit(BigDecimal amount, Currency currency) {
        return amount.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    // -------------------------------------------------------------------------
    // Exception
    // -------------------------------------------------------------------------

    public static class MailException extends RuntimeException {
        public MailException(String message) {
            super(message);
        }
    }
}
