package net.ecocraft.mail.compat.kubejs;

import net.ecocraft.mail.MailServerEvents;
import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import net.ecocraft.mail.service.MailService;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class MailBindings {

    private static MailService getService() {
        return MailServerEvents.getService();
    }

    /**
     * Sends a system mail (from scripts/admin tools).
     *
     * @return the mail ID, or null on failure
     */
    public static String sendSystemMail(String senderName, String recipientUuid,
                                         String subject, String body,
                                         long currencyAmount, String currencyId,
                                         boolean indestructible) {
        var service = getService();
        if (service == null) return null;
        try {
            return service.sendSystemMail(senderName, UUID.fromString(recipientUuid),
                    subject, body, List.of(), currencyAmount, currencyId, indestructible, 0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets the list of mails for a player.
     */
    public static List<Mail> getMailsForPlayer(String playerUuid) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        try {
            return service.getMailsForPlayer(UUID.fromString(playerUuid));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Gets sent mails for a player.
     */
    public static List<Mail> getSentMails(String playerUuid) {
        var service = getService();
        if (service == null) return Collections.emptyList();
        try {
            return service.getSentMailsForPlayer(UUID.fromString(playerUuid));
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Collects a single mail for a player.
     *
     * @return true if collected successfully
     */
    public static boolean collectMail(String playerUuid, String mailId) {
        var service = getService();
        if (service == null) return false;
        try {
            service.collectMail(UUID.fromString(playerUuid), mailId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Deletes a mail.
     *
     * @return true if deleted successfully
     */
    public static boolean deleteMail(String playerUuid, String mailId) {
        var service = getService();
        if (service == null) return false;
        try {
            service.deleteMail(UUID.fromString(playerUuid), mailId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Admin: deletes all mails for a player.
     *
     * @return number of mails deleted
     */
    public static int deleteAllMails(String playerUuid) {
        var service = getService();
        if (service == null) return 0;
        try {
            return service.deleteAllMailsForPlayer(UUID.fromString(playerUuid));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Marks a mail as read.
     *
     * @return true if marked successfully
     */
    public static boolean markRead(String playerUuid, String mailId) {
        var service = getService();
        if (service == null) return false;
        try {
            service.markRead(UUID.fromString(playerUuid), mailId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
