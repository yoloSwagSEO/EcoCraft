package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlaceBidPayload(String listingId, long amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlaceBidPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "place_bid"));

    public static final StreamCodec<ByteBuf, PlaceBidPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PlaceBidPayload::listingId,
            ByteBufCodecs.VAR_LONG, PlaceBidPayload::amount,
            PlaceBidPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
