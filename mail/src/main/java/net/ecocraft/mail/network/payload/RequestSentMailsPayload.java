package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * C->S: request the player's sent mails (on-demand when switching to Sent tab).
 */
public record RequestSentMailsPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestSentMailsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "request_sent_mails"));

    public static final StreamCodec<ByteBuf, RequestSentMailsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestSentMailsPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
