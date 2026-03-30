package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SaveDraftPayload(String recipient, String subject, String body, long currency, long cod) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SaveDraftPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "save_draft"));

    public static final StreamCodec<ByteBuf, SaveDraftPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SaveDraftPayload decode(ByteBuf buf) {
            String recipient = ByteBufCodecs.STRING_UTF8.decode(buf);
            String subject = ByteBufCodecs.STRING_UTF8.decode(buf);
            String body = ByteBufCodecs.STRING_UTF8.decode(buf);
            long currency = ByteBufCodecs.VAR_LONG.decode(buf);
            long cod = ByteBufCodecs.VAR_LONG.decode(buf);
            return new SaveDraftPayload(recipient, subject, body, currency, cod);
        }

        @Override
        public void encode(ByteBuf buf, SaveDraftPayload p) {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.recipient());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.subject());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.body());
            ByteBufCodecs.VAR_LONG.encode(buf, p.currency());
            ByteBufCodecs.VAR_LONG.encode(buf, p.cod());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
