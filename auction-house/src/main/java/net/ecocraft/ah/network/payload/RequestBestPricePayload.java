package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestBestPricePayload(String ahId, String fingerprint, String itemId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestBestPricePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "request_best_price"));

    public static final StreamCodec<ByteBuf, RequestBestPricePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestBestPricePayload::ahId,
            ByteBufCodecs.STRING_UTF8, RequestBestPricePayload::fingerprint,
            ByteBufCodecs.STRING_UTF8, RequestBestPricePayload::itemId,
            RequestBestPricePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
