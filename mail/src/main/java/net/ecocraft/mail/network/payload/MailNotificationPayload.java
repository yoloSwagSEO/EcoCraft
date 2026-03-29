package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MailNotificationPayload(
        String eventType,
        String subject,
        String senderName
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MailNotificationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "notification"));

    public static final StreamCodec<ByteBuf, MailNotificationPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MailNotificationPayload::eventType,
            ByteBufCodecs.STRING_UTF8, MailNotificationPayload::subject,
            ByteBufCodecs.STRING_UTF8, MailNotificationPayload::senderName,
            MailNotificationPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
