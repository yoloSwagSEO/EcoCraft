package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: requests a currency conversion.
 */
public record ExchangeRequestPayload(long amount, String fromCurrencyId, String toCurrencyId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExchangeRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "exchange_request"));

    public static final StreamCodec<ByteBuf, ExchangeRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, ExchangeRequestPayload::amount,
            ByteBufCodecs.STRING_UTF8, ExchangeRequestPayload::fromCurrencyId,
            ByteBufCodecs.STRING_UTF8, ExchangeRequestPayload::toCurrencyId,
            ExchangeRequestPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
