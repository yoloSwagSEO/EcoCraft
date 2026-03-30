package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: result of a conversion request.
 */
public record ExchangeResultPayload(boolean success, String message, long convertedAmount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExchangeResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "exchange_result"));

    public static final StreamCodec<ByteBuf, ExchangeResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ExchangeResultPayload::success,
            ByteBufCodecs.STRING_UTF8, ExchangeResultPayload::message,
            ByteBufCodecs.VAR_LONG, ExchangeResultPayload::convertedAmount,
            ExchangeResultPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
