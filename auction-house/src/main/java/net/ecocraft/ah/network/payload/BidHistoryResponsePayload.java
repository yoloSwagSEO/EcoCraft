package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record BidHistoryResponsePayload(
        String listingId,
        List<BidEntry> bids
) implements CustomPacketPayload {

    public record BidEntry(String bidderName, long amount, long timestamp) {}

    public static final StreamCodec<ByteBuf, BidEntry> BID_ENTRY_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BidEntry::bidderName,
            ByteBufCodecs.VAR_LONG, BidEntry::amount,
            ByteBufCodecs.VAR_LONG, BidEntry::timestamp,
            BidEntry::new
    );

    public static final CustomPacketPayload.Type<BidHistoryResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "bid_history_response"));

    public static final StreamCodec<ByteBuf, BidHistoryResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, BidHistoryResponsePayload::listingId,
            BID_ENTRY_CODEC.apply(ByteBufCodecs.list()), BidHistoryResponsePayload::bids,
            BidHistoryResponsePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() { return TYPE; }
}
