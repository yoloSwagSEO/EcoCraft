package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateListingPayload(String listingType, long price, int durationHours) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateListingPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "create_listing"));

    public static final StreamCodec<ByteBuf, CreateListingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CreateListingPayload::listingType,
            ByteBufCodecs.VAR_LONG, CreateListingPayload::price,
            ByteBufCodecs.VAR_INT, CreateListingPayload::durationHours,
            CreateListingPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
