package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestListingsPayload(String search, String category, int page, int pageSize) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestListingsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "request_listings"));

    public static final StreamCodec<ByteBuf, RequestListingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestListingsPayload::search,
            ByteBufCodecs.STRING_UTF8, RequestListingsPayload::category,
            ByteBufCodecs.VAR_INT, RequestListingsPayload::page,
            ByteBufCodecs.VAR_INT, RequestListingsPayload::pageSize,
            RequestListingsPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
