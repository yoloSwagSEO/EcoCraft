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
        List<String> availableEnchantments,
        List<BidHistoryResponsePayload.BidEntry> recentBids
) implements CustomPacketPayload {

    /**
     * Backward-compatible constructor: no available enchantments, no recent bids.
     */
    public ListingDetailResponsePayload(String itemId, String itemName, int rarityColor,
                                         List<ListingEntry> entries, PriceInfo priceInfo) {
        this(itemId, itemName, rarityColor, entries, priceInfo, List.of(), List.of());
    }

    /**
     * Backward-compatible constructor: no recent bids.
     */
    public ListingDetailResponsePayload(String itemId, String itemName, int rarityColor,
                                         List<ListingEntry> entries, PriceInfo priceInfo,
                                         List<String> availableEnchantments) {
        this(itemId, itemName, rarityColor, entries, priceInfo, availableEnchantments, List.of());
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

    public static final StreamCodec<ByteBuf, ListingDetailResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ListingDetailResponsePayload decode(ByteBuf buf) {
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemName = ByteBufCodecs.STRING_UTF8.decode(buf);
            int rarityColor = ByteBufCodecs.VAR_INT.decode(buf);
            List<ListingEntry> entries = ENTRY_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            PriceInfo priceInfo = PRICE_INFO_CODEC.decode(buf);
            List<String> availableEnchantments = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf);
            List<BidHistoryResponsePayload.BidEntry> recentBids =
                    BidHistoryResponsePayload.BID_ENTRY_CODEC.apply(ByteBufCodecs.list()).decode(buf);
            return new ListingDetailResponsePayload(itemId, itemName, rarityColor, entries, priceInfo,
                    availableEnchantments, recentBids);
        }

        @Override
        public void encode(ByteBuf buf, ListingDetailResponsePayload p) {
            ByteBufCodecs.STRING_UTF8.encode(buf, p.itemId());
            ByteBufCodecs.STRING_UTF8.encode(buf, p.itemName());
            ByteBufCodecs.VAR_INT.encode(buf, p.rarityColor());
            ENTRY_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.entries());
            PRICE_INFO_CODEC.encode(buf, p.priceInfo());
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, p.availableEnchantments());
            BidHistoryResponsePayload.BID_ENTRY_CODEC.apply(ByteBufCodecs.list()).encode(buf, p.recentBids());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
