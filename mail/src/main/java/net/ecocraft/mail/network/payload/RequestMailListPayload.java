package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestMailListPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestMailListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "request_mail_list"));

    public static final StreamCodec<ByteBuf, RequestMailListPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestMailListPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
