package net.ecocraft.mail.service;

import net.ecocraft.api.EconomyProvider;
import net.ecocraft.api.account.Account;
import net.ecocraft.api.currency.Currency;
import net.ecocraft.api.currency.CurrencyRegistry;
import net.ecocraft.api.transaction.TransactionResult;
import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import net.ecocraft.mail.storage.MailStorageProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MailServiceTest {

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    /** Simple in-memory EconomyProvider for tests — no Minecraft runtime needed. */
    static class MockEconomy implements EconomyProvider {
        private final Map<String, BigDecimal> balances = new HashMap<>();

        private String key(UUID player, Currency currency) {
            return player + ":" + currency.id();
        }

        public void setBalance(UUID player, Currency currency, BigDecimal amount) {
            balances.put(key(player, currency), amount);
        }

        @Override
        public BigDecimal getVirtualBalance(UUID player, Currency currency) {
            return balances.getOrDefault(key(player, currency), BigDecimal.ZERO);
        }

        @Override
        public BigDecimal getVaultBalance(UUID player, Currency currency) {
            return BigDecimal.ZERO;
        }

        @Override
        public Account getAccount(UUID player, Currency currency) {
            return null;
        }

        @Override
        public TransactionResult withdraw(UUID player, BigDecimal amount, Currency currency) {
            BigDecimal current = getVirtualBalance(player, currency);
            if (current.compareTo(amount) < 0) {
                return TransactionResult.failure("Insufficient funds");
            }
            balances.put(key(player, currency), current.subtract(amount));
            return TransactionResult.success(null);
        }

        @Override
        public TransactionResult deposit(UUID player, BigDecimal amount, Currency currency) {
            BigDecimal current = getVirtualBalance(player, currency);
            balances.put(key(player, currency), current.add(amount));
            return TransactionResult.success(null);
        }

        @Override
        public TransactionResult transfer(UUID from, UUID to, BigDecimal amount, Currency currency) {
            var w = withdraw(from, amount, currency);
            if (!w.successful()) return w;
            return deposit(to, amount, currency);
        }

        @Override
        public boolean canAfford(UUID player, BigDecimal amount, Currency currency) {
            return getVirtualBalance(player, currency).compareTo(amount) >= 0;
        }
    }

    /** Simple CurrencyRegistry backed by a map. */
    static class MockCurrencyRegistry implements CurrencyRegistry {
        private final Map<String, Currency> map = new LinkedHashMap<>();

        public MockCurrencyRegistry(Currency... currencies) {
            for (Currency c : currencies) map.put(c.id(), c);
        }

        @Override
        public void register(Currency currency) { map.put(currency.id(), currency); }

        @Override
        public Currency getById(String id) { return map.get(id); }

        @Override
        public Currency getDefault() { return map.values().iterator().next(); }

        @Override
        public List<Currency> listAll() { return new ArrayList<>(map.values()); }

        @Override
        public boolean exists(String id) { return map.containsKey(id); }

        @Override
        public void unregister(String id) { map.remove(id); }
    }

    /** Tracks items delivered via ItemDeliverer. */
    static class MockItemDeliverer implements MailService.ItemDeliverer {
        final List<DeliveryRecord> deliveries = new ArrayList<>();

        record DeliveryRecord(UUID playerUuid, List<MailItemAttachment> items) {}

        @Override
        public void deliver(UUID playerUuid, List<MailItemAttachment> items) {
            deliveries.add(new DeliveryRecord(playerUuid, items));
        }
    }

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 2);

    private MailStorageProvider storage;
    private MockEconomy economy;
    private MockCurrencyRegistry currencies;
    private MockItemDeliverer itemDeliverer;
    private MailService service;
    private Path tempDir;

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mail-service-test");
        storage = new MailStorageProvider(tempDir.resolve("mail.db"));
        storage.initialize();
        economy = new MockEconomy();
        currencies = new MockCurrencyRegistry(GOLD);
        itemDeliverer = new MockItemDeliverer();
        service = new MailService(storage, economy, currencies);
        service.setItemDeliverer(itemDeliverer);
    }

    @AfterEach
    void tearDown() {
        storage.shutdown();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<MailItemAttachment> swordItems() {
        return List.of(new MailItemAttachment("minecraft:diamond_sword", "Diamond Sword", null, 1));
    }

    // -------------------------------------------------------------------------
    // sendMail
    // -------------------------------------------------------------------------

    @Test
    void sendMailCreatesMailInStorage() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "Body",
                List.of(), 0, null, 0, null, false);

        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertEquals("Hello", mail.subject());
        assertEquals("Body", mail.body());
        assertEquals(SENDER, mail.senderUuid());
        assertEquals(RECIPIENT, mail.recipientUuid());
        assertFalse(mail.read());
        assertFalse(mail.collected());
        assertFalse(mail.indestructible());
    }

    @Test
    void sendMailWithCurrencyWithdrawsFromSender() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("500"));

        service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                List.of(), 100, "gold", 0, null, false);

        assertEquals(new BigDecimal("400"), economy.getVirtualBalance(SENDER, GOLD));
    }

    @Test
    void sendMailWithCurrencyFailsIfInsufficientFunds() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("50"));

        assertThrows(MailService.MailException.class, () ->
                service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                        List.of(), 100, "gold", 0, null, false));

        // Balance unchanged
        assertEquals(new BigDecimal("50"), economy.getVirtualBalance(SENDER, GOLD));
    }

    @Test
    void sendMailWithItems() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Item mail", "",
                swordItems(), 0, null, 0, null, false);

        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertTrue(mail.hasItems());
        assertEquals(1, mail.items().size());
        assertEquals("minecraft:diamond_sword", mail.items().get(0).itemId());
    }

    @Test
    void sendMailWithCOD() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD item", "",
                swordItems(), 0, null, 200, "gold", false);

        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertTrue(mail.hasCOD());
        assertEquals(200, mail.codAmount());
    }

    // -------------------------------------------------------------------------
    // sendSystemMail
    // -------------------------------------------------------------------------

    @Test
    void sendSystemMailCreatesIndestructibleMail() {
        String mailId = service.sendSystemMail("[Admin]", RECIPIENT, "Announcement", "Server reset",
                List.of(), 0, null, true, 0);

        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertNull(mail.senderUuid());
        assertEquals("[Admin]", mail.senderName());
        assertTrue(mail.indestructible());
        assertEquals(Long.MAX_VALUE, mail.expiresAt());
    }

    @Test
    void sendSystemMailWithCustomAvailableAt() {
        long futureTime = System.currentTimeMillis() + 60_000;

        String mailId = service.sendSystemMail("System", RECIPIENT, "Delayed", "",
                List.of(), 50, "gold", false, futureTime);

        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertEquals(futureTime, mail.availableAt());
        assertFalse(mail.isAvailable()); // still in the future
    }

    @Test
    void sendSystemMailInstantDeliveryWhenAvailableAtIsZero() {
        String mailId = service.sendSystemMail("System", RECIPIENT, "Instant", "",
                List.of(), 0, null, false, 0);

        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertTrue(mail.isAvailable());
    }

    // -------------------------------------------------------------------------
    // collectMail
    // -------------------------------------------------------------------------

    @Test
    void collectMailDeliversCurrencyAndMarksCollected() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                List.of(), 100, "gold", 0, null, false);

        service.collectMail(RECIPIENT, mailId);

        // Currency credited to recipient
        assertEquals(new BigDecimal("100"), economy.getVirtualBalance(RECIPIENT, GOLD));

        // Mail marked as collected
        Mail mail = storage.getMailById(mailId);
        assertTrue(mail.collected());
    }

    @Test
    void collectMailDeliversItems() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Items", "",
                swordItems(), 0, null, 0, null, false);

        service.collectMail(RECIPIENT, mailId);

        assertEquals(1, itemDeliverer.deliveries.size());
        assertEquals(RECIPIENT, itemDeliverer.deliveries.get(0).playerUuid());
        assertEquals("minecraft:diamond_sword", itemDeliverer.deliveries.get(0).items().get(0).itemId());
    }

    @Test
    void collectMailFailsIfAlreadyCollected() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                List.of(), 50, "gold", 0, null, false);

        service.collectMail(RECIPIENT, mailId);

        assertThrows(MailService.MailException.class, () ->
                service.collectMail(RECIPIENT, mailId));
    }

    @Test
    void collectMailFailsIfCOD() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD", "",
                swordItems(), 0, null, 100, "gold", false);

        assertThrows(MailService.MailException.class, () ->
                service.collectMail(RECIPIENT, mailId));
    }

    @Test
    void collectMailFailsIfNotOwner() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "", List.of(), 0, null, 0, null, false);
        UUID other = UUID.randomUUID();

        assertThrows(MailService.MailException.class, () ->
                service.collectMail(other, mailId));
    }

    // -------------------------------------------------------------------------
    // collectAllMails
    // -------------------------------------------------------------------------

    @Test
    void collectAllMailsSkipsCODMails() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));

        // Normal mail with currency
        service.sendMail(SENDER, "Sender", RECIPIENT, "Gift 1", "",
                List.of(), 50, "gold", 0, null, false);

        // COD mail
        service.sendMail(SENDER, "Sender", RECIPIENT, "COD", "",
                swordItems(), 0, null, 200, "gold", false);

        // Normal mail with items
        service.sendMail(SENDER, "Sender", RECIPIENT, "Gift 2", "",
                swordItems(), 0, null, 0, null, false);

        int collected = service.collectAllMails(RECIPIENT);

        assertEquals(2, collected);
        // Currency from first mail
        assertEquals(new BigDecimal("50"), economy.getVirtualBalance(RECIPIENT, GOLD));
        // Items from third mail
        assertEquals(1, itemDeliverer.deliveries.size());
    }

    @Test
    void collectAllMailsReturnsZeroWhenNothingToCollect() {
        // Text-only mail (no attachments)
        service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "Just text",
                List.of(), 0, null, 0, null, false);

        int collected = service.collectAllMails(RECIPIENT);
        assertEquals(0, collected);
    }

    // -------------------------------------------------------------------------
    // payCOD
    // -------------------------------------------------------------------------

    @Test
    void payCODWithdrawsFromRecipientAndDepositsToSender() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("500"));

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
                swordItems(), 0, null, 200, "gold", false);

        service.payCOD(RECIPIENT, mailId);

        // Recipient paid 200
        assertEquals(new BigDecimal("300"), economy.getVirtualBalance(RECIPIENT, GOLD));
        // Sender receives 200 (no fee by default)
        // Sender started with 1000, lost nothing for items, gains 200 from COD
        assertEquals(new BigDecimal("1200"), economy.getVirtualBalance(SENDER, GOLD));

        // Items delivered
        assertEquals(1, itemDeliverer.deliveries.size());

        // Mail marked as collected
        Mail mail = storage.getMailById(mailId);
        assertTrue(mail.collected());
    }

    @Test
    void payCODWithFeeDeductsFeeFromSenderPayment() {
        service.setCodFeePercent(5); // 5% fee

        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("500"));

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
                swordItems(), 0, null, 200, "gold", false);

        service.payCOD(RECIPIENT, mailId);

        // Recipient paid full 200
        assertEquals(new BigDecimal("300"), economy.getVirtualBalance(RECIPIENT, GOLD));
        // Sender receives 200 - 5% = 200 - 10 = 190
        assertEquals(new BigDecimal("1190"), economy.getVirtualBalance(SENDER, GOLD));
    }

    @Test
    void payCODFailsIfInsufficientFunds() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("50")); // not enough

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Expensive COD", "",
                swordItems(), 0, null, 200, "gold", false);

        assertThrows(MailService.MailException.class, () ->
                service.payCOD(RECIPIENT, mailId));

        // Balance unchanged
        assertEquals(new BigDecimal("50"), economy.getVirtualBalance(RECIPIENT, GOLD));
    }

    @Test
    void payCODDeliversCurrencyAttachmentToo() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("500"));

        // COD mail that also has currency attached
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD + Currency", "",
                swordItems(), 50, "gold", 200, "gold", false);

        service.payCOD(RECIPIENT, mailId);

        // Recipient: 500 - 200 (COD) + 50 (currency attachment) = 350
        assertEquals(new BigDecimal("350"), economy.getVirtualBalance(RECIPIENT, GOLD));
    }

    // -------------------------------------------------------------------------
    // returnCOD
    // -------------------------------------------------------------------------

    @Test
    void returnCODCreatesReturnMailToSenderAndMarksReturned() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
                swordItems(), 0, null, 100, "gold", false);

        service.returnCOD(RECIPIENT, mailId);

        // Original mail marked as returned
        Mail originalMail = storage.getMailById(mailId);
        assertTrue(originalMail.returned());

        // Return mail created for sender
        List<Mail> senderMails = storage.getMailsForPlayer(SENDER);
        assertEquals(1, senderMails.size());

        Mail returnMail = senderMails.get(0);
        assertEquals(SENDER, returnMail.recipientUuid());
        assertTrue(returnMail.subject().startsWith("Retour : "));
        assertTrue(returnMail.hasItems());
        assertFalse(returnMail.hasCOD());
        assertFalse(returnMail.hasCurrency());
    }

    @Test
    void returnCODFailsIfAlreadyReturned() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD", "",
                swordItems(), 0, null, 100, "gold", false);

        service.returnCOD(RECIPIENT, mailId);

        assertThrows(MailService.MailException.class, () ->
                service.returnCOD(RECIPIENT, mailId));
    }

    @Test
    void returnCODFailsIfAlreadyCollected() {
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("500"));

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD", "",
                swordItems(), 0, null, 100, "gold", false);

        service.payCOD(RECIPIENT, mailId);

        assertThrows(MailService.MailException.class, () ->
                service.returnCOD(RECIPIENT, mailId));
    }

    // -------------------------------------------------------------------------
    // deleteMail
    // -------------------------------------------------------------------------

    @Test
    void deleteMailSucceedsWhenCanDelete() {
        // Text-only mail — canDelete requires read
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "Text only",
                List.of(), 0, null, 0, null, false);

        // Mark as read via getMailDetail
        service.getMailDetail(mailId, RECIPIENT);

        service.deleteMail(RECIPIENT, mailId);

        assertNull(storage.getMailById(mailId));
    }

    @Test
    void deleteMailSucceedsAfterCollection() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000"));
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                swordItems(), 50, "gold", 0, null, false);

        // Collect first
        service.collectMail(RECIPIENT, mailId);
        // Mark as read
        service.getMailDetail(mailId, RECIPIENT);

        service.deleteMail(RECIPIENT, mailId);
        assertNull(storage.getMailById(mailId));
    }

    @Test
    void deleteMailFailsWhenNotCanDelete() {
        // Mail with uncollected items — canDelete should be false
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Items", "",
                swordItems(), 0, null, 0, null, false);

        assertThrows(MailService.MailException.class, () ->
                service.deleteMail(RECIPIENT, mailId));
    }

    @Test
    void deleteMailFailsWhenNotOwner() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "",
                List.of(), 0, null, 0, null, false);

        assertThrows(MailService.MailException.class, () ->
                service.deleteMail(UUID.randomUUID(), mailId));
    }

    // -------------------------------------------------------------------------
    // expireMails
    // -------------------------------------------------------------------------

    @Test
    void expireMailsAutoReturnsExpiredCODs() {
        // Create an expired COD mail directly in storage
        long past = System.currentTimeMillis() - 10_000;
        String mailId = UUID.randomUUID().toString();
        Mail expiredCOD = new Mail(
            mailId, SENDER, "Sender", RECIPIENT, "Expired COD", "",
            swordItems(), 0, null, 100, "gold",
            false, false, false, false, false,
            past - 60_000, past - 60_000, past  // expired
        );
        storage.createMail(expiredCOD);

        service.expireMails();

        // Original mail is deleted by deleteExpiredMails after being marked returned
        // (it's expired and not indestructible)
        assertNull(storage.getMailById(mailId));

        // Return mail created for sender
        List<Mail> senderMails = storage.getMailsForPlayer(SENDER);
        assertEquals(1, senderMails.size());
        Mail returnMail = senderMails.get(0);
        assertEquals(SENDER, returnMail.recipientUuid());
        assertTrue(returnMail.subject().contains("Retour"));
        assertTrue(returnMail.hasItems());
        assertFalse(returnMail.hasCOD());
    }

    @Test
    void expireMailsDeletesExpiredNonCODMails() {
        // Create an expired normal mail directly in storage
        long past = System.currentTimeMillis() - 10_000;
        String mailId = UUID.randomUUID().toString();
        Mail expiredNormal = new Mail(
            mailId, SENDER, "Sender", RECIPIENT, "Old mail", "",
            List.of(), 0, null, 0, null,
            false, false, false, false, false,
            past - 60_000, past - 60_000, past  // expired
        );
        storage.createMail(expiredNormal);

        service.expireMails();

        // Should be deleted
        assertNull(storage.getMailById(mailId));
    }

    // -------------------------------------------------------------------------
    // getMailsForPlayer
    // -------------------------------------------------------------------------

    @Test
    void getMailsForPlayerFiltersUnavailableAndExpired() {
        // Available mail
        service.sendMail(SENDER, "Sender", RECIPIENT, "Available", "", List.of(), 0, null, 0, null, false);

        // Future mail (not yet available)
        service.sendSystemMail("System", RECIPIENT, "Future", "", List.of(), 0, null, false,
                System.currentTimeMillis() + 60_000);

        List<Mail> mails = service.getMailsForPlayer(RECIPIENT);
        assertEquals(1, mails.size());
        assertEquals("Available", mails.get(0).subject());
    }

    // -------------------------------------------------------------------------
    // getMailDetail
    // -------------------------------------------------------------------------

    @Test
    void getMailDetailMarksAsRead() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Unread", "",
                List.of(), 0, null, 0, null, false);

        Mail mail = service.getMailDetail(mailId, RECIPIENT);

        assertTrue(mail.read());
        // Also persisted in storage
        assertTrue(storage.getMailById(mailId).read());
    }

    @Test
    void getMailDetailFailsForWrongPlayer() {
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Private", "",
                List.of(), 0, null, 0, null, false);

        assertThrows(MailService.MailException.class, () ->
                service.getMailDetail(mailId, UUID.randomUUID()));
    }
}
