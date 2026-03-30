package net.ecocraft.mail.storage;

import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MailStorageProviderTest {

    private MailStorageProvider db;
    private Path tempDir;

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000099");

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mail-test");
        db = new MailStorageProvider(tempDir.resolve("mail-test.db"));
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        db.shutdown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Mail makeMail(String id, UUID recipient, String subject, boolean read, boolean collected,
                          boolean indestructible, long codAmount, long expiresAt) {
        return new Mail(
            id, SENDER, "SenderName", recipient, subject, "Body text",
            List.of(), 0L, null, codAmount, codAmount > 0 ? "gold" : null,
            read, collected, indestructible, false, false,
            System.currentTimeMillis(), System.currentTimeMillis(), expiresAt
        );
    }

    private Mail makeMailWithItems(String id, UUID recipient, List<MailItemAttachment> items) {
        return new Mail(
            id, SENDER, "SenderName", recipient, "Subject", "Body",
            items, 0L, null, 0L, null,
            false, false, false, false, false,
            System.currentTimeMillis(), System.currentTimeMillis(),
            System.currentTimeMillis() + 86400_000L * 30
        );
    }

    private Mail makeMailWithCurrency(String id, UUID recipient, long amount) {
        return new Mail(
            id, SENDER, "SenderName", recipient, "Currency mail", "Body",
            List.of(), amount, "gold", 0L, null,
            false, false, false, false, false,
            System.currentTimeMillis(), System.currentTimeMillis(),
            System.currentTimeMillis() + 86400_000L * 30
        );
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void createAndRetrieveMail() {
        var items = List.of(
            new MailItemAttachment("minecraft:diamond", "Diamant", null, 5),
            new MailItemAttachment("minecraft:iron_ingot", "Lingot de fer", "{\"tag\":\"test\"}", 64)
        );
        Mail mail = makeMailWithItems("mail-1", PLAYER_A, items);
        db.createMail(mail);

        Mail loaded = db.getMailById("mail-1");
        assertNotNull(loaded);
        assertEquals("mail-1", loaded.id());
        assertEquals(SENDER, loaded.senderUuid());
        assertEquals("SenderName", loaded.senderName());
        assertEquals(PLAYER_A, loaded.recipientUuid());
        assertEquals("Subject", loaded.subject());
        assertEquals("Body", loaded.body());
        assertFalse(loaded.read());
        assertFalse(loaded.collected());

        // Check item attachments
        assertEquals(2, loaded.items().size());
        assertEquals("minecraft:diamond", loaded.items().get(0).itemId());
        assertEquals(5, loaded.items().get(0).quantity());
        assertEquals("minecraft:iron_ingot", loaded.items().get(1).itemId());
        assertEquals("{\"tag\":\"test\"}", loaded.items().get(1).itemNbt());
        assertEquals(64, loaded.items().get(1).quantity());
    }

    @Test
    void getMailsForPlayer() {
        long now = System.currentTimeMillis();
        long future = now + 86400_000L * 30;

        // Unread mail created earlier
        Mail unread1 = new Mail("m1", SENDER, "S", PLAYER_A, "Unread old", "",
            List.of(), 0, null, 0, null, false, false, false, false, false,
            now - 2000, now - 2000, future);
        // Unread mail created later
        Mail unread2 = new Mail("m2", SENDER, "S", PLAYER_A, "Unread new", "",
            List.of(), 0, null, 0, null, false, false, false, false, false,
            now - 1000, now - 1000, future);
        // Read mail
        Mail readMail = new Mail("m3", SENDER, "S", PLAYER_A, "Read", "",
            List.of(), 0, null, 0, null, true, false, false, false, false,
            now - 500, now - 500, future);
        // Another player's mail
        Mail otherPlayer = new Mail("m4", SENDER, "S", PLAYER_B, "Other", "",
            List.of(), 0, null, 0, null, false, false, false, false, false,
            now, now, future);

        db.createMail(unread1);
        db.createMail(unread2);
        db.createMail(readMail);
        db.createMail(otherPlayer);

        List<Mail> result = db.getMailsForPlayer(PLAYER_A);
        assertEquals(3, result.size());
        // Unread first (sorted by created_at DESC), then read
        assertEquals("m2", result.get(0).id()); // unread, newer
        assertEquals("m1", result.get(1).id()); // unread, older
        assertEquals("m3", result.get(2).id()); // read
    }

    @Test
    void markReadAndCollected() {
        Mail mail = makeMail("m1", PLAYER_A, "Test", false, false, false, 0,
            System.currentTimeMillis() + 86400_000L * 30);
        db.createMail(mail);

        db.markRead("m1");
        Mail loaded = db.getMailById("m1");
        assertTrue(loaded.read());
        assertFalse(loaded.collected());

        db.markCollected("m1");
        loaded = db.getMailById("m1");
        assertTrue(loaded.collected());
    }

    @Test
    void deleteMail() {
        var items = List.of(new MailItemAttachment("minecraft:stone", "Pierre", null, 1));
        Mail mail = makeMailWithItems("m1", PLAYER_A, items);
        db.createMail(mail);

        assertNotNull(db.getMailById("m1"));
        assertEquals(1, db.getMailItems("m1").size());

        db.deleteMail("m1");

        assertNull(db.getMailById("m1"));
        assertEquals(0, db.getMailItems("m1").size());
    }

    @Test
    void deleteExpiredMails() {
        long past = System.currentTimeMillis() - 1000;
        long future = System.currentTimeMillis() + 86400_000L * 30;

        // Expired, not indestructible -> should be deleted
        Mail expired = makeMail("m1", PLAYER_A, "Expired", false, false, false, 0, past);
        // Not expired -> should stay
        Mail active = makeMail("m2", PLAYER_A, "Active", false, false, false, 0, future);
        // Expired but indestructible -> should stay
        Mail indestructible = makeMail("m3", PLAYER_A, "Indestructible", false, false, true, 0, past);

        db.createMail(expired);
        db.createMail(active);
        db.createMail(indestructible);

        db.deleteExpiredMails();

        assertNull(db.getMailById("m1"));
        assertNotNull(db.getMailById("m2"));
        assertNotNull(db.getMailById("m3"));
    }

    @Test
    void deleteExpiredMailsKeepsIndestructible() {
        long past = System.currentTimeMillis() - 1000;

        Mail indestructible = makeMail("m1", PLAYER_A, "Admin mail", false, false, true, 0, past);
        db.createMail(indestructible);

        db.deleteExpiredMails();

        assertNotNull(db.getMailById("m1"));
    }

    @Test
    void getExpiredCODMails() {
        long past = System.currentTimeMillis() - 1000;
        long future = System.currentTimeMillis() + 86400_000L * 30;

        // Expired COD, uncollected, unreturned -> should be returned
        Mail expiredCod = makeMail("m1", PLAYER_A, "COD expired", false, false, false, 100, past);
        // Expired COD but collected -> should NOT be returned
        Mail collectedCod = makeMail("m2", PLAYER_A, "COD collected", false, true, false, 100, past);
        // Expired COD but already returned -> should NOT be returned
        Mail returnedCod = new Mail("m3", SENDER, "S", PLAYER_A, "COD returned", "",
            List.of(), 0, null, 100, "gold", false, false, false, true, false,
            System.currentTimeMillis() - 2000, System.currentTimeMillis() - 2000, past);
        // Not expired COD -> should NOT be returned
        Mail activeCod = makeMail("m4", PLAYER_A, "COD active", false, false, false, 100, future);
        // Expired non-COD -> should NOT be returned
        Mail expiredNoCod = makeMail("m5", PLAYER_A, "No COD", false, false, false, 0, past);

        db.createMail(expiredCod);
        db.createMail(collectedCod);
        db.createMail(returnedCod);
        db.createMail(activeCod);
        db.createMail(expiredNoCod);

        List<Mail> result = db.getExpiredCODMails();
        assertEquals(1, result.size());
        assertEquals("m1", result.get(0).id());
    }

    @Test
    void countAvailableMails() {
        long now = System.currentTimeMillis();
        long future = now + 86400_000L * 30;
        long past = now - 1000;

        // Available, not expired -> count
        Mail available = makeMail("m1", PLAYER_A, "Available", false, false, false, 0, future);
        // Not yet available (availableAt in future) -> don't count
        Mail notYet = new Mail("m2", SENDER, "S", PLAYER_A, "Not yet", "",
            List.of(), 0, null, 0, null, false, false, false, false, false,
            now, now + 86400_000L, future);
        // Expired, not indestructible -> don't count
        Mail expired = makeMail("m3", PLAYER_A, "Expired", false, false, false, 0, past);
        // Expired but indestructible -> count (indestructible never expires)
        Mail indestructibleExpired = makeMail("m4", PLAYER_A, "Indestructible", false, false, true, 0, past);
        // Different player -> don't count
        Mail otherPlayer = makeMail("m5", PLAYER_B, "Other", false, false, false, 0, future);

        db.createMail(available);
        db.createMail(notYet);
        db.createMail(expired);
        db.createMail(indestructibleExpired);
        db.createMail(otherPlayer);

        assertEquals(2, db.countAvailableMails(PLAYER_A));
    }

    @Test
    void canDelete() {
        // Text-only mail, read -> can delete
        Mail textOnly = makeMail("m1", PLAYER_A, "Text", true, false, false, 0,
            System.currentTimeMillis() + 86400_000L * 30);
        assertTrue(textOnly.canDelete());

        // Mail with currency, read but not collected -> cannot delete
        Mail withCurrency = makeMailWithCurrency("m2", PLAYER_A, 100);
        Mail readWithCurrency = new Mail(
            withCurrency.id(), withCurrency.senderUuid(), withCurrency.senderName(),
            withCurrency.recipientUuid(), withCurrency.subject(), withCurrency.body(),
            withCurrency.items(), withCurrency.currencyAmount(), withCurrency.currencyId(),
            withCurrency.codAmount(), withCurrency.codCurrencyId(),
            true, false, false, false, false,
            withCurrency.createdAt(), withCurrency.availableAt(), withCurrency.expiresAt()
        );
        assertFalse(readWithCurrency.canDelete());

        // Mail with currency, read and collected -> can delete
        Mail collectedWithCurrency = new Mail(
            withCurrency.id(), withCurrency.senderUuid(), withCurrency.senderName(),
            withCurrency.recipientUuid(), withCurrency.subject(), withCurrency.body(),
            withCurrency.items(), withCurrency.currencyAmount(), withCurrency.currencyId(),
            withCurrency.codAmount(), withCurrency.codCurrencyId(),
            true, true, false, false, false,
            withCurrency.createdAt(), withCurrency.availableAt(), withCurrency.expiresAt()
        );
        assertTrue(collectedWithCurrency.canDelete());

        // Unread mail -> cannot delete
        Mail unread = makeMail("m3", PLAYER_A, "Unread", false, false, false, 0,
            System.currentTimeMillis() + 86400_000L * 30);
        assertFalse(unread.canDelete());
    }

    @Test
    void markReturned() {
        Mail mail = makeMail("m1", PLAYER_A, "COD", false, false, false, 500,
            System.currentTimeMillis() + 86400_000L * 30);
        db.createMail(mail);

        db.markReturned("m1");
        Mail loaded = db.getMailById("m1");
        assertTrue(loaded.returned());
    }

    @Test
    void systemMailWithNullSender() {
        Mail systemMail = new Mail(
            "sys-1", null, "Hôtel des Ventes", PLAYER_A, "Vente réussie", "Votre objet a été vendu.",
            List.of(), 1000L, "gold", 0L, null,
            false, false, true, false, false,
            System.currentTimeMillis(), System.currentTimeMillis(),
            System.currentTimeMillis() + 86400_000L * 30
        );
        db.createMail(systemMail);

        Mail loaded = db.getMailById("sys-1");
        assertNotNull(loaded);
        assertNull(loaded.senderUuid());
        assertEquals("Hôtel des Ventes", loaded.senderName());
        assertTrue(loaded.indestructible());
        assertEquals(1000L, loaded.currencyAmount());
        assertEquals("gold", loaded.currencyId());
    }
}
