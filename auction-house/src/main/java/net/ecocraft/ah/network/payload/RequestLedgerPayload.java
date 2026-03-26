package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestLedgerPayload(String period, String typeFilter, int page) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestLedgerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "request_ledger"));

    public static final StreamCodec<ByteBuf, RequestLedgerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RequestLedgerPayload::period,
            ByteBufCodecs.STRING_UTF8, RequestLedgerPayload::typeFilter,
            ByteBufCodecs.VAR_INT, RequestLedgerPayload::page,
            RequestLedgerPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
