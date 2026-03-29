package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record MailListResponsePayload(List<MailSummary> mails) implements CustomPacketPayload {

    public record MailSummary(
            String id,
            String senderName,
            String subject,
            boolean read,
            boolean collected,
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
            boolean hasItems = ByteBufCodecs.BOOL.decode(buf);
            boolean hasCurrency = ByteBufCodecs.BOOL.decode(buf);
            boolean hasCOD = ByteBufCodecs.BOOL.decode(buf);
            long codAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long currencyAmount = ByteBufCodecs.VAR_LONG.decode(buf);
            long createdAt = ByteBufCodecs.VAR_LONG.decode(buf);
            return new MailSummary(id, senderName, subject, read, collected, hasItems, hasCurrency, hasCOD, codAmount, currencyAmount, createdAt);
        }

        @Override
        public void encode(ByteBuf buf, MailSummary s) {
            ByteBufCodecs.STRING_UTF8.encode(buf, s.id());
            ByteBufCodecs.STRING_UTF8.encode(buf, s.senderName());
            ByteBufCodecs.STRING_UTF8.encode(buf, s.subject());
            ByteBufCodecs.BOOL.encode(buf, s.read());
            ByteBufCodecs.BOOL.encode(buf, s.collected());
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
            return new MailListResponsePayload(mails);
        }

        @Override
        public void encode(ByteBuf buf, MailListResponsePayload p) {
            SUMMARY_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.mails());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
