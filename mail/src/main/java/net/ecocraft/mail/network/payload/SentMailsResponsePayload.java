package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * S->C: response carrying the player's sent mails.
 */
public record SentMailsResponsePayload(List<MailListResponsePayload.MailSummary> sentMails) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SentMailsResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "sent_mails_response"));

    public static final StreamCodec<ByteBuf, SentMailsResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SentMailsResponsePayload decode(ByteBuf buf) {
            List<MailListResponsePayload.MailSummary> sentMails =
                    MailListResponsePayload.SUMMARY_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()).decode(buf);
            return new SentMailsResponsePayload(sentMails);
        }

        @Override
        public void encode(ByteBuf buf, SentMailsResponsePayload p) {
            MailListResponsePayload.SUMMARY_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()).encode(buf, p.sentMails());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
