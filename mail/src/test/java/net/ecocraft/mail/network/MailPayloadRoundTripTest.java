package net.ecocraft.mail.network;

import net.ecocraft.mail.network.payload.*;
import net.ecocraft.mail.network.payload.DraftsResponsePayload.DraftEntry;
import net.ecocraft.mail.network.payload.MailDetailResponsePayload.ItemEntry;
import net.ecocraft.mail.network.payload.MailListResponsePayload.MailSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MailPayloadRoundTripTest {

    @Test
    void collectMailPayload() {
        var original = new CollectMailPayload("mail-001");
        assertEquals(original, PayloadTestHelper.roundTrip(original, CollectMailPayload.STREAM_CODEC));
    }

    @Test
    void collectMailPayloadAll() {
        var original = new CollectMailPayload("ALL");
        assertEquals(original, PayloadTestHelper.roundTrip(original, CollectMailPayload.STREAM_CODEC));
    }

    @Test
    void collectMailResultPayload() {
        var original = new CollectMailResultPayload(true, "Collected!", 3, 500L);
        var decoded = PayloadTestHelper.roundTrip(original, CollectMailResultPayload.STREAM_CODEC);
        assertEquals(original.success(), decoded.success());
        assertEquals(original.message(), decoded.message());
        assertEquals(original.itemsCollected(), decoded.itemsCollected());
        assertEquals(original.currencyCollected(), decoded.currencyCollected());
    }

    @Test
    void deleteDraftPayload() {
        var original = new DeleteDraftPayload("draft-123");
        assertEquals(original, PayloadTestHelper.roundTrip(original, DeleteDraftPayload.STREAM_CODEC));
    }

    @Test
    void deleteMailPayload() {
        var original = new DeleteMailPayload("mail-456");
        assertEquals(original, PayloadTestHelper.roundTrip(original, DeleteMailPayload.STREAM_CODEC));
    }

    @Test
    void mailNotificationPayload() {
        var original = new MailNotificationPayload("NEW_MAIL", "Hello!", "Steve");
        assertEquals(original, PayloadTestHelper.roundTrip(original, MailNotificationPayload.STREAM_CODEC));
    }

    @Test
    void markReadPayload() {
        var original = new MarkReadPayload("mail-789");
        assertEquals(original, PayloadTestHelper.roundTrip(original, MarkReadPayload.STREAM_CODEC));
    }

    @Test
    void openMailboxPayload() {
        var original = new OpenMailboxPayload(42);
        assertEquals(original, PayloadTestHelper.roundTrip(original, OpenMailboxPayload.STREAM_CODEC));
    }

    @Test
    void payCODPayload() {
        var original = new PayCODPayload("mail-cod-001");
        assertEquals(original, PayloadTestHelper.roundTrip(original, PayCODPayload.STREAM_CODEC));
    }

    @Test
    void postmanSkinPayload() {
        var original = new PostmanSkinPayload(10, "Notch");
        assertEquals(original, PayloadTestHelper.roundTrip(original, PostmanSkinPayload.STREAM_CODEC));
    }

    @Test
    void requestDraftsPayload() {
        assertNotNull(PayloadTestHelper.roundTrip(new RequestDraftsPayload(), RequestDraftsPayload.STREAM_CODEC));
    }

    @Test
    void requestMailDetailPayload() {
        var original = new RequestMailDetailPayload("mail-001");
        assertEquals(original, PayloadTestHelper.roundTrip(original, RequestMailDetailPayload.STREAM_CODEC));
    }

    @Test
    void requestMailListPayload() {
        assertNotNull(PayloadTestHelper.roundTrip(new RequestMailListPayload(), RequestMailListPayload.STREAM_CODEC));
    }

    @Test
    void requestSentMailsPayload() {
        assertNotNull(PayloadTestHelper.roundTrip(new RequestSentMailsPayload(), RequestSentMailsPayload.STREAM_CODEC));
    }

    @Test
    void returnCODPayload() {
        var original = new ReturnCODPayload("mail-cod-002");
        assertEquals(original, PayloadTestHelper.roundTrip(original, ReturnCODPayload.STREAM_CODEC));
    }

    @Test
    void sendMailResultPayload() {
        var original = new SendMailResultPayload(false, "Recipient not found");
        assertEquals(original, PayloadTestHelper.roundTrip(original, SendMailResultPayload.STREAM_CODEC));
    }

    @Test
    void updatePostmanSkinPayload() {
        var original = new UpdatePostmanSkinPayload(15, "Jeb");
        assertEquals(original, PayloadTestHelper.roundTrip(original, UpdatePostmanSkinPayload.STREAM_CODEC));
    }

    @Test
    void saveDraftPayload() {
        var original = new SaveDraftPayload("Alex", "Trade Offer", "Want to trade diamonds?", 100L, 50L);
        var decoded = PayloadTestHelper.roundTrip(original, SaveDraftPayload.STREAM_CODEC);
        assertEquals(original.recipient(), decoded.recipient());
        assertEquals(original.subject(), decoded.subject());
        assertEquals(original.body(), decoded.body());
        assertEquals(original.currency(), decoded.currency());
        assertEquals(original.cod(), decoded.cod());
    }

    @Test
    void sendMailPayloadWithReadReceipt() {
        var original = new SendMailPayload("Alex", "Gift", "Here are some diamonds!", List.of(0, 1, 5), 100L, 0L, true);
        var decoded = PayloadTestHelper.roundTrip(original, SendMailPayload.STREAM_CODEC);
        assertEquals(original.recipientName(), decoded.recipientName());
        assertEquals(original.subject(), decoded.subject());
        assertEquals(original.body(), decoded.body());
        assertEquals(original.inventorySlots(), decoded.inventorySlots());
        assertEquals(original.currencyAmount(), decoded.currencyAmount());
        assertEquals(original.codAmount(), decoded.codAmount());
        assertTrue(decoded.readReceipt(), "readReceipt must survive round-trip");
    }

    @Test
    void sendMailPayloadWithoutReadReceipt() {
        var original = new SendMailPayload("Steve", "Hello", "Hi there", List.of(), 0L, 250L, false);
        var decoded = PayloadTestHelper.roundTrip(original, SendMailPayload.STREAM_CODEC);
        assertEquals(original.recipientName(), decoded.recipientName());
        assertFalse(decoded.readReceipt(), "readReceipt=false must survive round-trip");
        assertEquals(250L, decoded.codAmount());
    }

    @Test
    void draftsResponsePayload() {
        var draft = new DraftEntry("d-001", "Alex", "Trade", "Let's trade!", 100L, 50L, 1711700000000L);
        var original = new DraftsResponsePayload(List.of(draft));
        var decoded = PayloadTestHelper.roundTrip(original, DraftsResponsePayload.STREAM_CODEC);
        assertEquals(1, decoded.drafts().size());
        var d = decoded.drafts().get(0);
        assertEquals("d-001", d.id());
        assertEquals("Alex", d.recipient());
        assertEquals("Trade", d.subject());
        assertEquals("Let's trade!", d.body());
        assertEquals(100L, d.currencyAmount());
        assertEquals(50L, d.codAmount());
        assertEquals(1711700000000L, d.createdAt());
    }

    @Test
    void mailDetailResponsePayload() {
        var item = new ItemEntry("minecraft:diamond", "Diamond", "{}", 64);
        var original = new MailDetailResponsePayload("mail-001", "uuid-abc", "Steve", "Gift", "Here are diamonds!",
                List.of(item), 500L, "gold", 100L, "gold",
                true, false, false, false, 1711700000000L, 1711700060000L, 1711786400000L);
        var decoded = PayloadTestHelper.roundTrip(original, MailDetailResponsePayload.STREAM_CODEC);
        assertEquals("mail-001", decoded.id());
        assertEquals("uuid-abc", decoded.senderUuid());
        assertEquals("Steve", decoded.senderName());
        assertEquals("Gift", decoded.subject());
        assertEquals(1, decoded.items().size());
        assertEquals("minecraft:diamond", decoded.items().get(0).itemId());
        assertEquals(64, decoded.items().get(0).quantity());
        assertEquals(500L, decoded.currencyAmount());
        assertEquals("gold", decoded.currencyId());
        assertEquals(100L, decoded.codAmount());
        assertTrue(decoded.read());
        assertFalse(decoded.collected());
        assertFalse(decoded.indestructible());
        assertFalse(decoded.returned());
    }

    @Test
    void mailListResponsePayload() {
        var summary = new MailSummary("mail-001", "Steve", "Hello", true, false, false,
                true, true, true, 250L, 100L, 1711700000000L);
        var original = new MailListResponsePayload(List.of(summary), "G", 12, 10L, 5L, true, 20L, 3);
        var decoded = PayloadTestHelper.roundTrip(original, MailListResponsePayload.STREAM_CODEC);
        assertEquals(1, decoded.mails().size());
        assertEquals("mail-001", decoded.mails().get(0).id());
        assertEquals("G", decoded.currencySymbol());
        assertEquals(12, decoded.maxItemAttachments());
        assertEquals(10L, decoded.sendCost());
        assertEquals(5L, decoded.sendCostPerItem());
        assertTrue(decoded.allowReadReceipt());
        assertEquals(20L, decoded.readReceiptCost());
        assertEquals(3, decoded.codFeePercent());
    }

    @Test
    void mailSettingsPayload() {
        var original = new MailSettingsPayload(true, true, true, true, false, 12, 30, 10L, 5, true, 20L, 5L);
        var decoded = PayloadTestHelper.roundTrip(original, MailSettingsPayload.STREAM_CODEC);
        assertTrue(decoded.allowPlayerMail());
        assertTrue(decoded.allowItemAttachments());
        assertTrue(decoded.allowCurrencyAttachments());
        assertTrue(decoded.allowCOD());
        assertFalse(decoded.allowMailboxCraft());
        assertEquals(12, decoded.maxItemAttachments());
        assertEquals(30, decoded.mailExpiryDays());
        assertEquals(10L, decoded.sendCost());
        assertEquals(5, decoded.codFeePercent());
        assertTrue(decoded.allowReadReceipt());
        assertEquals(20L, decoded.readReceiptCost());
        assertEquals(5L, decoded.sendCostPerItem());
    }

    @Test
    void updateMailSettingsPayload() {
        var original = new UpdateMailSettingsPayload(false, false, true, false, true, 6, 14, 25L, 10, false, 0L, 3L);
        var decoded = PayloadTestHelper.roundTrip(original, UpdateMailSettingsPayload.STREAM_CODEC);
        assertFalse(decoded.allowPlayerMail());
        assertFalse(decoded.allowItemAttachments());
        assertTrue(decoded.allowCurrencyAttachments());
        assertFalse(decoded.allowCOD());
        assertTrue(decoded.allowMailboxCraft());
        assertEquals(6, decoded.maxItemAttachments());
        assertEquals(14, decoded.mailExpiryDays());
        assertEquals(25L, decoded.sendCost());
        assertEquals(10, decoded.codFeePercent());
        assertFalse(decoded.allowReadReceipt());
    }

    @Test
    void sentMailsResponsePayload() {
        var summary = new MailSummary("sent-001", "Alex", "Re: Trade", false, false, false,
                false, false, false, 0L, 0L, 1711700000000L);
        var original = new SentMailsResponsePayload(List.of(summary));
        var decoded = PayloadTestHelper.roundTrip(original, SentMailsResponsePayload.STREAM_CODEC);
        assertEquals(1, decoded.sentMails().size());
        assertEquals("sent-001", decoded.sentMails().get(0).id());
    }
}
