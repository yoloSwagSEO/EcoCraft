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
        PriceInfo priceInfo,
        List<String> availableEnchantments
) implements CustomPacketPayload {

    /**
     * Backward-compatible constructor: no available enchantments.
     */
    public ListingDetailResponsePayload(String itemId, String itemName, int rarityColor,
                                         List<ListingEntry> entries, PriceInfo priceInfo) {
        this(itemId, itemName, rarityColor, entries, priceInfo, List.of());
    }

    public record ListingEntry(String listingId, String sellerName, int quantity, long unitPrice, String type, long expiresInMs, String itemNbt) {}

    public record PriceInfo(long avgPrice, long minPrice, long maxPrice, int volume7d) {}

    public static final StreamCodec<ByteBuf, ListingEntry> ENTRY_CODEC = new StreamCodec<>() {
        @Override
        public ListingEntry decode(ByteBuf buf) {
            String listingId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String sellerName = ByteBufCodecs.STRING_UTF8.decode(buf);
            int quantity = ByteBufCodecs.VAR_INT.decode(buf);
            long unitPrice = ByteBufCodecs.VAR_LONG.decode(buf);
            String type = ByteBufCodecs.STRING_UTF8.decode(buf);
            long expiresInMs = ByteBufCodecs.VAR_LONG.decode(buf);
            String itemNbt = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new ListingEntry(listingId, sellerName, quantity, unitPrice, type, expiresInMs, itemNbt);
        }

        @Override
        public void encode(ByteBuf buf, ListingEntry entry) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.listingId());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.sellerName());
            ByteBufCodecs.VAR_INT.encode(buf, entry.quantity());
            ByteBufCodecs.VAR_LONG.encode(buf, entry.unitPrice());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.type());
            ByteBufCodecs.VAR_LONG.encode(buf, entry.expiresInMs());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemNbt() != null ? entry.itemNbt() : "");
        }
    };

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
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ListingDetailResponsePayload::availableEnchantments,
            ListingDetailResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
