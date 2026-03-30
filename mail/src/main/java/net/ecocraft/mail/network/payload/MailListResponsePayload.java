package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record MailListResponsePayload(List<MailSummary> mails, String currencySymbol, int maxItemAttachments, long sendCost, long sendCostPerItem, boolean allowReadReceipt, long readReceiptCost, int codFeePercent) implements CustomPacketPayload {

    public record MailSummary(
            String id,
            String senderName,
            String subject,
            boolean read,
            boolean collected,
            boolean returned,
            boolean hasItems,
            boolean hasCurrency,
            boolean hasCOD,
            long codAmount,
            long currencyAmount,
            long createdAt
    ) {}

    public static final StreamCodec<ByteBuf, MailSummary> SUMMARY_CODEC = new StreamCodec<>() {
        @Override
        public MailSummary decode(ByteBuf buf) {
            String id = ByteBufCodecs.STRING_UTF8.decode(buf);
            String senderName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String subject = ByteBufCodecs.STRING_UTF8.decode(buf);
            boolean read = ByteBufCodecs.BOOL.decode(buf);
            boolean collected = ByteBufCodecs.BOOL.decode(buf);
            boolean returned = ByteBufCodecs.BOOL.decode(buf);
            boolean hasItems = ByteBufCodecs.BOOL.decode(buf);
            boolean hasCurrency = ByteBufCodecs.BOOL.decode(buf);
            boolean hasCOD = ByteBufCodecs.BOOL.decode(buf);
            long codAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long currencyAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long createdAt = ByteBufCodecs.VAR_LONG.decode(buf);
            return new MailSummary(id, senderName, subject, read, collected, returned, hasItems, hasCurrency, hasCOD, codAmount, currencyAmount, createdAt);
        }

        @Override
        public void encode(ByteBuf buf, MailSummary s) {
            ByteBufCodecs.STRING_UTF8.encode(buf, s.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, s.senderName());
            ByteBufCodecs.STRING_UTF8.encode(buf, s.subject());
            ByteBufCodecs.BOOL.encode(buf, s.read());
            ByteBufCodecs.BOOL.encode(buf, s.collected());
            ByteBufCodecs.BOOL.encode(buf, s.returned());
            ByteBufCodecs.BOOL.encode(buf, s.hasItems());
            ByteBufCodecs.BOOL.encode(buf, s.hasCurrency());
            ByteBufCodecs.BOOL.encode(buf, s.hasCOD());
            ByteBufCodecs.VAR_LONG.encode(buf, s.codAmount());
            ByteBufCodecs.VAR_LONG.encode(buf, s.currencyAmount());
            ByteBufCodecs.VAR_LONG.encode(buf, s.createdAt());
        }
    };

    public static final CustomPacketPayload.Type<MailListResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "mail_list_response"));

    public static final StreamCodec<ByteBuf, MailListResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MailListResponsePayload decode(ByteBuf buf) {
            List<MailSummary> mails = SUMMARY_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            String currencySymbol = ByteBufCodecs.STRING_UTF8.decode(buf);
            int maxItemAttachments = ByteBufCodecs.VAR_INT.decode(buf);
            long sendCost = ByteBufCodecs.VAR_LONG.decode(buf);
            long sendCostPerItem = ByteBufCodecs.VAR_LONG.decode(buf);
            boolean allowReadReceipt = ByteBufCodecs.BOOL.decode(buf);
            long readReceiptCost = ByteBufCodecs.VAR_LONG.decode(buf);
            int codFeePercent = ByteBufCodecs.VAR_INT.decode(buf);
            return new MailListResponsePayload(mails, currencySymbol, maxItemAttachments, sendCost, sendCostPerItem, allowReadReceipt, readReceiptCost, codFeePercent);
        }

        @Override
        public void encode(ByteBuf buf, MailListResponsePayload p) {
            SUMMARY_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.mails());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.currencySymbol());
            ByteBufCodecs.VAR_INT.encode(buf, p.maxItemAttachments());
            ByteBufCodecs.VAR_LONG.encode(buf, p.sendCost());
            ByteBufCodecs.VAR_LONG.encode(buf, p.sendCostPerItem());
            ByteBufCodecs.BOOL.encode(buf, p.allowReadReceipt());
            ByteBufCodecs.VAR_LONG.encode(buf, p.readReceiptCost());
            ByteBufCodecs.VAR_INT.encode(buf, p.codFeePercent());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
