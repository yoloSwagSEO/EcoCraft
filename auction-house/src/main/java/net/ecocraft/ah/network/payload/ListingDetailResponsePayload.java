package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ListingDetailResponsePayload(
        String itemId,
        String itemName,
        int rarityColor,
        List<ListingEntry> entries,
        PriceInfo priceInfo
) implements CustomPacketPayload {

    public record ListingEntry(String listingId, String sellerName, int quantity, long unitPrice, String type, long expiresInMs) {}

    public record PriceInfo(long avgPrice, long minPrice, long maxPrice, int volume7d) {}

    public static final StreamCodec<ByteBuf, ListingEntry> ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ListingEntry::listingId,
            ByteBufCodecs.STRING_UTF8, ListingEntry::sellerName,
            ByteBufCodecs.VAR_INT, ListingEntry::quantity,
            ByteBufCodecs.VAR_LONG, ListingEntry::unitPrice,
            ByteBufCodecs.STRING_UTF8, ListingEntry::type,
            ByteBufCodecs.VAR_LONG, ListingEntry::expiresInMs,
            ListingEntry::new
    );

    public static final StreamCodec<ByteBuf, PriceInfo> PRICE_INFO_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PriceInfo::avgPrice,
            ByteBufCodecs.VAR_LONG, PriceInfo::minPrice,
            ByteBufCodecs.VAR_LONG, PriceInfo::maxPrice,
            ByteBufCodecs.VAR_INT, PriceInfo::volume7d,
            PriceInfo::new
    );

    public static final CustomPacketPayload.Type<ListingDetailResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "listing_detail_response"));

    public static final StreamCodec<ByteBuf, ListingDetailResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ListingDetailResponsePayload::itemId,
            ByteBufCodecs.STRING_UTF8, ListingDetailResponsePayload::itemName,
            ByteBufCodecs.VAR_INT, ListingDetailResponsePayload::rarityColor,
            ENTRY_CODEC.apply(ByteBufCodecs.list()), ListingDetailResponsePayload::entries,
            PRICE_INFO_CODEC, ListingDetailResponsePayload::priceInfo,
            ListingDetailResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
