package net.ecocraft.mail.service;

import net.ecocraft.api.currency.Currency;
import net.ecocraft.mail.data.Mail;
import net.ecocraft.mail.data.MailItemAttachment;
import net.ecocraft.mail.storage.MailStorageProvider;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that toggling mail features ON/OFF and changing fees produces correct behavior.
 */
class MailConfigBehaviorTest {

    private static final Currency GOLD = Currency.virtual("gold", "Gold", "G", 2);

    private MailStorageProvider storage;
    private MailServiceTest.MockEconomy economy;
    private MailServiceTest.MockCurrencyRegistry currencies;
    private MailServiceTest.MockItemDeliverer itemDeliverer;
    private MailService service;
    private Path tempDir;

    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID RECIPIENT = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("mail-config-test");
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

    private List<MailItemAttachment> threeItems() {
        return List.of(
            new MailItemAttachment("minecraft:diamond_sword", "Diamond Sword", null, 1),
            new MailItemAttachment("minecraft:diamond_pickaxe", "Diamond Pickaxe", null, 1),
            new MailItemAttachment("minecraft:diamond_axe", "Diamond Axe", null, 1)
        );
    }

    // Feature toggle: player mail disabled
    @Test
    void sendMail_WhenPlayerMailDisabled_Fails() {
        service.setAllowPlayerMail(false);
        assertThrows(MailService.MailException.class, () ->
            service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "Body",
                List.of(), 0, null, 0, null, false));
    }

    // Feature toggle: item attachments disabled
    @Test
    void sendMail_WhenItemAttachmentsDisabled_FailsWithItems() {
        service.setAllowItemAttachments(false);
        assertThrows(MailService.MailException.class, () ->
            service.sendMail(SENDER, "Sender", RECIPIENT, "Items", "",
                swordItems(), 0, null, 0, null, false));
    }

    // Feature toggle: currency attachments disabled
    @Test
    void sendMail_WhenCurrencyDisabled_FailsWithCurrency() {
        service.setAllowCurrencyAttachments(false);
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));
        assertThrows(MailService.MailException.class, () ->
            service.sendMail(SENDER, "Sender", RECIPIENT, "Gift", "",
                List.of(), 10000, "gold", 0, null, false));
    }

    // Feature toggle: COD disabled
    @Test
    void sendMail_WhenCODDisabled_FailsWithCOD() {
        service.setAllowCOD(false);
        assertThrows(MailService.MailException.class, () ->
            service.sendMail(SENDER, "Sender", RECIPIENT, "COD", "",
                swordItems(), 0, null, 20000, "gold", false));
    }

    // Feature toggle: read receipt disabled - mail created but readReceipt forced false
    @Test
    void sendMail_WhenReadReceiptDisabled_IgnoresReceipt() {
        service.setAllowReadReceipt(false);
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "",
            List.of(), 0, null, 0, null, true);
        Mail mail = storage.getMailById(mailId);
        assertNotNull(mail);
        assertFalse(mail.readReceipt(), "Read receipt should be false when feature is disabled");
    }

    // COD fee: 10% on 1000 gold -> sender receives 900
    @Test
    void codFee_10Percent_DeductsCorrectly() {
        service.setCodFeePercent(10);
        economy.setBalance(SENDER, GOLD, new BigDecimal("0.00"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("1500.00"));
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
            swordItems(), 0, null, 100000, "gold", false);
        service.payCOD(RECIPIENT, mailId);
        assertEquals(new BigDecimal("900.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    // COD fee: 0% on 1000 gold -> sender receives 1000
    @Test
    void codFee_0Percent_NoDeduction() {
        service.setCodFeePercent(0);
        economy.setBalance(SENDER, GOLD, new BigDecimal("0.00"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("1500.00"));
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
            swordItems(), 0, null, 100000, "gold", false);
        service.payCOD(RECIPIENT, mailId);
        assertEquals(new BigDecimal("1000.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    // COD fee: 50% on 1000 gold -> sender receives 500
    @Test
    void codFee_50Percent_DeductsHalf() {
        service.setCodFeePercent(50);
        economy.setBalance(SENDER, GOLD, new BigDecimal("0.00"));
        economy.setBalance(RECIPIENT, GOLD, new BigDecimal("1500.00"));
        String mailId = service.sendMail(SENDER, "Sender", RECIPIENT, "COD Item", "",
            swordItems(), 0, null, 100000, "gold", false);
        service.payCOD(RECIPIENT, mailId);
        assertEquals(new BigDecimal("500.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    // Send cost: 100 gold flat fee charged on send
    @Test
    void sendCost_ChargedOnSend() {
        service.setSendCost(10000);
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));
        service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "",
            List.of(), 0, null, 0, null, false);
        assertEquals(new BigDecimal("400.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    // Send cost per item: 3 items at 50 gold each = 150 gold charged
    @Test
    void sendCostPerItem_ChargedPerAttachment() {
        service.setSendCostPerItem(5000);
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));
        service.sendMail(SENDER, "Sender", RECIPIENT, "Items", "",
            threeItems(), 0, null, 0, null, false);
        assertEquals(new BigDecimal("350.00"), economy.getVirtualBalance(SENDER, GOLD));
    }

    // Read receipt cost: 200 gold charged when readReceipt is true
    @Test
    void readReceiptCost_ChargedWhenEnabled() {
        service.setReadReceiptCost(20000);
        economy.setBalance(SENDER, GOLD, new BigDecimal("500.00"));
        service.sendMail(SENDER, "Sender", RECIPIENT, "Hello", "",
            List.of(), 0, null, 0, null, true);
        assertEquals(new BigDecimal("300.00"), economy.getVirtualBalance(SENDER, GOLD));
    }
}
