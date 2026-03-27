package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record MyListingsResponsePayload(
        List<MyListingEntry> entries,
        long revenue7d,
        long taxesPaid7d,
        int parcelsToCollect
) implements CustomPacketPayload {

    public record MyListingEntry(
            String listingId,
            String itemId,
            String itemName,
            int rarityColor,
            long price,
            String type,
            String status,
            long expiresInMs,
            int bidCount,
            boolean canCollect,
            String itemNbt
    ) {}

    public static final StreamCodec<ByteBuf, MyListingEntry> ENTRY_CODEC = new StreamCodec<>() {
        @Override
        public MyListingEntry decode(ByteBuf buf) {
            String listingId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemName = ByteBufCodecs.STRING_UTF8.decode(buf);
            int rarityColor = ByteBufCodecs.VAR_INT.decode(buf);
            long price = ByteBufCodecs.VAR_LONG.decode(buf);
            String type = ByteBufCodecs.STRING_UTF8.decode(buf);
            String status = ByteBufCodecs.STRING_UTF8.decode(buf);
            long expiresInMs = ByteBufCodecs.VAR_LONG.decode(buf);
            int bidCount = ByteBufCodecs.VAR_INT.decode(buf);
            boolean canCollect = ByteBufCodecs.BOOL.decode(buf);
            String itemNbt = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new MyListingEntry(listingId, itemId, itemName, rarityColor, price, type, status, expiresInMs, bidCount, canCollect, itemNbt);
        }

        @Override
        public void encode(ByteBuf buf, MyListingEntry entry) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.listingId());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemId());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemName());
            ByteBufCodecs.VAR_INT.encode(buf, entry.rarityColor());
            ByteBufCodecs.VAR_LONG.encode(buf, entry.price());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.type());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.status());
            ByteBufCodecs.VAR_LONG.encode(buf, entry.expiresInMs());
            ByteBufCodecs.VAR_INT.encode(buf, entry.bidCount());
            ByteBufCodecs.BOOL.encode(buf, entry.canCollect());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemNbt() != null ? entry.itemNbt() : "");
        }
    };

    public static final CustomPacketPayload.Type<MyListingsResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "my_listings_response"));

    public static final StreamCodec<ByteBuf, MyListingsResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        private static final StreamCodec<ByteBuf, List<MyListingEntry>> LIST_CODEC = ENTRY_CODEC.apply(ByteBufCodecs.list());

        @Override
        public MyListingsResponsePayload decode(ByteBuf buf) {
            List<MyListingEntry> entries = LIST_CODEC.decode(buf);
            long revenue7d = ByteBufCodecs.VAR_LONG.decode(buf);
            long taxesPaid7d = ByteBufCodecs.VAR_LONG.decode(buf);
            int parcelsToCollect = ByteBufCodecs.VAR_INT.decode(buf);
            return new MyListingsResponsePayload(entries, revenue7d, taxesPaid7d, parcelsToCollect);
        }

        @Override
        public void encode(ByteBuf buf, MyListingsResponsePayload payload) {
            LIST_CODEC.encode(buf, payload.entries());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.revenue7d());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.taxesPaid7d());
            ByteBufCodecs.VAR_INT.encode(buf, payload.parcelsToCollect());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
