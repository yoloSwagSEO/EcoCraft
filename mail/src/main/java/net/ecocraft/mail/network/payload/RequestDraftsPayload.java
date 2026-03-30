package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestDraftsPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDraftsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "request_drafts"));

    public static final StreamCodec<ByteBuf, RequestDraftsPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestDraftsPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
