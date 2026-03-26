package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ListingsResponsePayload(List<ListingSummary> items, int page, int totalPages) implements CustomPacketPayload {

    public record ListingSummary(String itemId, String itemName, int rarityColor, long bestPrice, int listingCount, int totalAvailable) {}

    public static final StreamCodec<ByteBuf, ListingSummary> SUMMARY_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ListingSummary::itemId,
            ByteBufCodecs.STRING_UTF8, ListingSummary::itemName,
            ByteBufCodecs.VAR_INT, ListingSummary::rarityColor,
            ByteBufCodecs.VAR_LONG, ListingSummary::bestPrice,
            ByteBufCodecs.VAR_INT, ListingSummary::listingCount,
            ByteBufCodecs.VAR_INT, ListingSummary::totalAvailable,
            ListingSummary::new
    );

    public static final CustomPacketPayload.Type<ListingsResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "listings_response"));

    public static final StreamCodec<ByteBuf, ListingsResponsePayload> STREAM_CODEC = StreamCodec.composite(
            SUMMARY_CODEC.apply(ByteBufCodecs.list()), ListingsResponsePayload::items,
            ByteBufCodecs.VAR_INT, ListingsResponsePayload::page,
            ByteBufCodecs.VAR_INT, ListingsResponsePayload::totalPages,
            ListingsResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
