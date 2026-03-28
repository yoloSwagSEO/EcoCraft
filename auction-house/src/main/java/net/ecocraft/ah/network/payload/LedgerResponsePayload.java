package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record LedgerResponsePayload(
        List<LedgerEntry> entries,
        long netProfit,
        long totalSales,
        long totalPurchases,
        long taxesPaid,
        int page,
        int totalPages
) implements CustomPacketPayload {

    public record LedgerEntry(String itemId, String itemName, int rarityColor, String type, long amount, String counterparty, long timestamp, String ahId, String ahName, String itemNbt) {}

    public static final StreamCodec<ByteBuf, LedgerEntry> ENTRY_CODEC = new StreamCodec<>() {
        @Override
        public LedgerEntry decode(ByteBuf buf) {
            String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemName = ByteBufCodecs.STRING_UTF8.decode(buf);
            int rarityColor = ByteBufCodecs.VAR_INT.decode(buf);
            String type = ByteBufCodecs.STRING_UTF8.decode(buf);
            long amount = ByteBufCodecs.VAR_LONG.decode(buf);
            String counterparty = ByteBufCodecs.STRING_UTF8.decode(buf);
            long timestamp = ByteBufCodecs.VAR_LONG.decode(buf);
            String ahId = ByteBufCodecs.STRING_UTF8.decode(buf);
            String ahName = ByteBufCodecs.STRING_UTF8.decode(buf);
            String itemNbt = ByteBufCodecs.STRING_UTF8.decode(buf);
            return new LedgerEntry(itemId, itemName, rarityColor, type, amount, counterparty, timestamp, ahId, ahName, itemNbt);
        }

        @Override
        public void encode(ByteBuf buf, LedgerEntry entry) {
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemId());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemName());
            ByteBufCodecs.VAR_INT.encode(buf, entry.rarityColor());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.type());
            ByteBufCodecs.VAR_LONG.encode(buf, entry.amount());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.counterparty());
            ByteBufCodecs.VAR_LONG.encode(buf, entry.timestamp());
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.ahId() != null ? entry.ahId() : "");
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.ahName() != null ? entry.ahName() : "");
            ByteBufCodecs.STRING_UTF8.encode(buf, entry.itemNbt() != null ? entry.itemNbt() : "");
        }
    };

    public static final CustomPacketPayload.Type<LedgerResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "ledger_response"));

    public static final StreamCodec<ByteBuf, LedgerResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        private static final StreamCodec<ByteBuf, List<LedgerEntry>> LIST_CODEC = ENTRY_CODEC.apply(ByteBufCodecs.list());

        @Override
        public LedgerResponsePayload decode(ByteBuf buf) {
            List<LedgerEntry> entries = LIST_CODEC.decode(buf);
            long netProfit = ByteBufCodecs.VAR_LONG.decode(buf);
            long totalSales = ByteBufCodecs.VAR_LONG.decode(buf);
            long totalPurchases = ByteBufCodecs.VAR_LONG.decode(buf);
            long taxesPaid = ByteBufCodecs.VAR_LONG.decode(buf);
            int page = ByteBufCodecs.VAR_INT.decode(buf);
            int totalPages = ByteBufCodecs.VAR_INT.decode(buf);
            return new LedgerResponsePayload(entries, netProfit, totalSales, totalPurchases, taxesPaid, page, totalPages);
        }

        @Override
        public void encode(ByteBuf buf, LedgerResponsePayload payload) {
            LIST_CODEC.encode(buf, payload.entries());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.netProfit());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.totalSales());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.totalPurchases());
            ByteBufCodecs.VAR_LONG.encode(buf, payload.taxesPaid());
            ByteBufCodecs.VAR_INT.encode(buf, payload.page());
            ByteBufCodecs.VAR_INT.encode(buf, payload.totalPages());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
