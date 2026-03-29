package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SendMailResultPayload(boolean success, String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendMailResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "send_mail_result"));

    public static final StreamCodec<ByteBuf, SendMailResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SendMailResultPayload::success,
            ByteBufCodecs.STRING_UTF8, SendMailResultPayload::message,
            SendMailResultPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
