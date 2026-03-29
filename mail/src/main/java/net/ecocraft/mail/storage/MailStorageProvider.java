package net.ecocraft.mail.storage;

import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-backed storage provider for the mail system.
 *
 * <p>Uses a dedicated SQLite database file. All queries use SQL-standard syntax
 * (no SQLite-specific features) to ease future MariaDB migration.</p>
 */
public class MailStorageProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailStorageProvider.class);
    private Connection connection;

    public MailStorageProvider(Path dbPath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to connect to mail database", e);
        }
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public synchronized void initialize() {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mails (
                    id               TEXT PRIMARY KEY,
                    sender_uuid      TEXT,
                    sender_name      TEXT NOT NULL,
                    recipient_uuid   TEXT NOT NULL,
                    subject          TEXT NOT NULL,
                    body             TEXT NOT NULL DEFAULT '',
                    currency_amount  INTEGER NOT NULL DEFAULT 0,
                    currency_id      TEXT,
                    cod_amount       INTEGER NOT NULL DEFAULT 0,
                    cod_currency_id  TEXT,
                    read             INTEGER NOT NULL DEFAULT 0,
                    collected        INTEGER NOT NULL DEFAULT 0,
                    indestructible   INTEGER NOT NULL DEFAULT 0,
                    returned         INTEGER NOT NULL DEFAULT 0,
                    created_at       INTEGER NOT NULL,
                    available_at     INTEGER NOT NULL,
                    expires_at       INTEGER NOT NULL
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mails_recipient ON mails(recipient_uuid, collected)");
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS mail_items (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    mail_id    TEXT NOT NULL,
                    item_id    TEXT NOT NULL,
                    item_name  TEXT NOT NULL,
                    item_nbt   TEXT,
                    quantity   INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY (mail_id) REFERENCES mails(id) ON DELETE CASCADE
                )
                """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mail_items_mail ON mail_items(mail_id)");
            stmt.execute("PRAGMA foreign_keys = ON");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize mail database", e);
        }
    }

    public synchronized void shutdown() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            LOGGER.error("Failed to close mail database", e);
        }
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    public synchronized void createMail(Mail mail) {
        String sql = "INSERT INTO mails (id, sender_uuid, sender_name, recipient_uuid, subject, body, " +
                "currency_amount, currency_id, cod_amount, cod_currency_id, " +
                "read, collected, indestructible, returned, created_at, available_at, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mail.id());
            ps.setString(2, mail.senderUuid() != null ? mail.senderUuid().toString() : null);
            ps.setString(3, mail.senderName());
            ps.setString(4, mail.recipientUuid().toString());
            ps.setString(5, mail.subject());
            ps.setString(6, mail.body());
            ps.setLong(7, mail.currencyAmount());
            ps.setString(8, mail.currencyId());
            ps.setLong(9, mail.codAmount());
            ps.setString(10, mail.codCurrencyId());
            ps.setInt(11, mail.read() ? 1 : 0);
            ps.setInt(12, mail.collected() ? 1 : 0);
            ps.setInt(13, mail.indestructible() ? 1 : 0);
            ps.setInt(14, mail.returned() ? 1 : 0);
            ps.setLong(15, mail.createdAt());
            ps.setLong(16, mail.availableAt());
            ps.setLong(17, mail.expiresAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to create mail {}", mail.id(), e);
        }

        // Insert item attachments
        if (mail.items() != null) {
            for (MailItemAttachment item : mail.items()) {
                createMailItem(mail.id(), item);
            }
        }
    }

    private synchronized void createMailItem(String mailId, MailItemAttachment item) {
        String sql = "INSERT INTO mail_items (mail_id, item_id, item_name, item_nbt, quantity) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ps.setString(2, item.itemId());
            ps.setString(3, item.itemName());
            ps.setString(4, item.itemNbt());
            ps.setInt(5, item.quantity());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to create mail item for mail {}", mailId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public synchronized Mail getMailById(String mailId) {
        String sql = "SELECT * FROM mails WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapMail(rs);
        } catch (SQLException e) {
            LOGGER.error("Failed to get mail {}", mailId, e);
        }
        return null;
    }

    public synchronized List<Mail> getMailsForPlayer(UUID playerUuid) {
        String sql = "SELECT * FROM mails WHERE recipient_uuid = ? ORDER BY read ASC, created_at DESC";
        List<Mail> mails = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                mails.add(mapMail(rs));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get mails for player {}", playerUuid, e);
        }
        return mails;
    }

    public synchronized List<MailItemAttachment> getMailItems(String mailId) {
        String sql = "SELECT * FROM mail_items WHERE mail_id = ?";
        List<MailItemAttachment> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, mailId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new MailItemAttachment(
                    rs.getString("item_id"),
                    rs.getString("item_name"),
                    rs.getString("item_nbt"),
                    rs.getInt("quantity")
                ));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get mail items for {}", mailId, e);
        }
        return items;
    }

    public synchronized int countAvailableMails(UUID playerUuid) {
        String sql = "SELECT COUNT(*) FROM mails WHERE recipient_uuid = ? AND available_at <= ? AND (indestructible = 1 OR expires_at > ?)";
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            ps.setLong(2, now);
            ps.setLong(3, now);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            LOGGER.error("Failed to count mails for {}", playerUuid, e);
        }
        return 0;
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    public synchronized void markRead(String mailId) {
        execute("UPDATE mails SET read = 1 WHERE id = ?", mailId);
    }

    public synchronized void markCollected(String mailId) {
        execute("UPDATE mails SET collected = 1 WHERE id = ?", mailId);
    }

    public synchronized void markReturned(String mailId) {
        execute("UPDATE mails SET returned = 1 WHERE id = ?", mailId);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    public synchronized void deleteMail(String mailId) {
        execute("DELETE FROM mail_items WHERE mail_id = ?", mailId);
        execute("DELETE FROM mails WHERE id = ?", mailId);
    }

    public synchronized void deleteExpiredMails() {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM mails WHERE indestructible = 0 AND expires_at <= ?")) {
            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String id = rs.getString("id");
                deleteMail(id);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to delete expired mails", e);
        }
    }

    public synchronized int deleteAllMailsForPlayer(UUID playerUuid) {
        // First get all mail IDs for the player so we can delete their items
        List<String> mailIds = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM mails WHERE recipient_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                mailIds.add(rs.getString("id"));
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to get mail IDs for player {}", playerUuid, e);
            return 0;
        }

        for (String mailId : mailIds) {
            deleteMail(mailId);
        }
        return mailIds.size();
    }

    public synchronized List<Mail> getExpiredCODMails() {
        String sql = "SELECT * FROM mails WHERE indestructible = 0 AND expires_at <= ? AND cod_amount > 0 AND collected = 0 AND returned = 0";
        long now = System.currentTimeMillis();
        List<Mail> mails = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, now);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) mails.add(mapMail(rs));
        } catch (SQLException e) {
            LOGGER.error("Failed to get expired COD mails", e);
        }
        return mails;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void execute(String sql, String param) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("SQL error: {}", sql, e);
        }
    }

    private Mail mapMail(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        String senderUuidStr = rs.getString("sender_uuid");
        return new Mail(
            id,
            senderUuidStr != null ? UUID.fromString(senderUuidStr) : null,
            rs.getString("sender_name"),
            UUID.fromString(rs.getString("recipient_uuid")),
            rs.getString("subject"),
            rs.getString("body"),
            getMailItems(id),
            rs.getLong("currency_amount"),
            rs.getString("currency_id"),
            rs.getLong("cod_amount"),
            rs.getString("cod_currency_id"),
            rs.getInt("read") == 1,
            rs.getInt("collected") == 1,
            rs.getInt("indestructible") == 1,
            rs.getInt("returned") == 1,
            rs.getLong("created_at"),
            rs.getLong("available_at"),
            rs.getLong("expires_at")
        );
    }
}
