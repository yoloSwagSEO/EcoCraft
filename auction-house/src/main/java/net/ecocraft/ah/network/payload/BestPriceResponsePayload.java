package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BestPriceResponsePayload(String itemId, long bestPrice) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BestPriceResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "best_price_response"));

    public static final StreamCodec<ByteBuf, BestPriceResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BestPriceResponsePayload::itemId,
            ByteBufCodecs.VAR_LONG, BestPriceResponsePayload::bestPrice,
            BestPriceResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
