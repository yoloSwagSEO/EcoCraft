package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BalanceUpdatePayload(long balance, String currencySymbol) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BalanceUpdatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "balance_update"));

    public static final StreamCodec<ByteBuf, BalanceUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, BalanceUpdatePayload::balance,
            ByteBufCodecs.STRING_UTF8, BalanceUpdatePayload::currencySymbol,
            BalanceUpdatePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
