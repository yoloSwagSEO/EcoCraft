package net.ecocraft.mail.service;

import net.ecocraft.api.currency.Currency;
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

/**
 * End-to-end financial flow tests for the mail system.
 * Covers currency attachments, COD payments, fees, and refunds.
 */
class MailFinancialFlowTest {

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 2);

    private MailStorageProvider storage;
    private MailServiceTest.MockEconomy economy;
    private MailServiceTest.MockCurrencyRegistry currencies;
    private MailServiceTest.MockItemDeliverer itemDeliverer;
    private MailService service;

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        Path tempDir = Files.createTempDirectory("mail-financial-flow-test");
        storage = new MailStorageProvider(tempDir.resolve("mail.db"));
        storage.initialize();
        economy = new MailServiceTest.MockEconomy();
        currencies = new MailServiceTest.MockCurrencyRegistry(GOLD);
        itemDeliverer = new MailServiceTest.MockItemDeliverer();
        service = new MailService(storage, economy, currencies);
        service.setItemDeliverer(itemDeliverer);
    }

    @AfterEach
    void tearDown() {
        storage.shutdown();
    }

    private List<MailItemAttachment> swordItems() {
        return List.of(new MailItemAttachment("minecraft:diamond_sword", "Diamond Sword", null, 1));
    }

    @Test
    void sendMailWithCurrency_DebitsSender() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));

        service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                List.of(), 50000, "gold", 0, null, false);

        assertEquals(new BigDecimal("0.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    @Test
    void collectMailWithCurrency_CreditsRecipient() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("100.00"));

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                List.of(), 20000, "gold", 0, null, false);

        service.collectMail(RECIPIENT, mailId);

        assertEquals(new BigDecimal("300.00"), economy.getVirtualBalance(RECIPIENT, GOLD));
        assertEquals(new BigDecimal("300.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    @Test
    void codFullFlow() {
        service.setCodFeePercent(5);

        economy.setBalance(SENDER, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("500.00"));

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
                swordItems(), 0, null, 20000, "gold", false);

        assertEquals(new BigDecimal("1000.00"), economy.getVirtualBalance(SENDER, GOLD));

        service.payCOD(RECIPIENT, mailId);

        assertEquals(new BigDecimal("300.00"), economy.getVirtualBalance(RECIPIENT, GOLD));
        assertEquals(new BigDecimal("1190.00"), economy.getVirtualBalance(SENDER, GOLD));

        assertEquals(1, itemDeliverer.deliveries.size());
        assertEquals(RECIPIENT, itemDeliverer.deliveries.get(0).playerUuid());

        assertTrue(storage.getMailById(mailId).collected());
    }

    @Test
    void codReturn_RefundsCurrency() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("1000.00"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("500.00"));

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD + Currency", "",
                swordItems(), 5000, "gold", 20000, "gold", false);

        assertEquals(new BigDecimal("950.00"), economy.getVirtualBalance(SENDER, GOLD));

        service.returnCOD(RECIPIENT, mailId);

        assertTrue(storage.getMailById(mailId).returned());

        List<Mail> senderMails = storage.getMailsForPlayer(SENDER);
        assertEquals(1, senderMails.size());
        Mail returnMail = senderMails.get(0);
        assertEquals(SENDER, returnMail.recipientUuid());
        assertTrue(returnMail.subject().startsWith("Retour : "));
        assertTrue(returnMail.hasItems());
        assertEquals(5000, returnMail.currencyAmount());
        assertFalse(returnMail.hasCOD());

        service.collectMail(SENDER, returnMail.id());
        assertEquals(new BigDecimal("1000.00"), economy.getVirtualBalance(SENDER, GOLD));

        assertEquals(new BigDecimal("500.00"), economy.getVirtualBalance(RECIPIENT, GOLD));
    }

    @Test
    void sendCostCharged() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));

        long sendCostAmount = 100;
        Currency gold = currencies.getById("gold");
        BigDecimal costBD = MailService.fromSmallestUnit(sendCostAmount, gold);
        TransactionResult costResult = economy.withdraw(SENDER, costBD, gold);
        assertTrue(costResult.successful());

        service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "Text only",
                List.of(), 0, null, 0, null, false);

        assertEquals(new BigDecimal("499.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    @Test
    void readReceiptCostCharged() {
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));

        long readReceiptCostAmount = 200;
        Currency gold = currencies.getById("gold");
        BigDecimal costBD = MailService.fromSmallestUnit(readReceiptCostAmount, gold);
        TransactionResult costResult = economy.withdraw(SENDER, costBD, gold);
        assertTrue(costResult.successful());

        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Receipt", "",
                List.of(), 0, null, 0, null, true);

        Mail mail = storage.getMailById(mailId);
        assertTrue(mail.readReceipt());

        assertEquals(new BigDecimal("498.00"), economy.getVirtualBalance(SENDER, GOLD));
    }
}
