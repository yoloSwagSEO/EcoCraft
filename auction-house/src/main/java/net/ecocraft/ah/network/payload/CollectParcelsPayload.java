package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CollectParcelsPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CollectParcelsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "collect_parcels"));

    public static final StreamCodec<ByteBuf, CollectParcelsPayload> STREAM_CODEC =
            StreamCodec.unit(new CollectParcelsPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
