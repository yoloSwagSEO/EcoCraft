package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> Client: sends available currencies, rates, balances, and config.
 */
public record ExchangeDataPayload(
        List<CurrencyData> currencies,
        double feePercent
) implements CustomPacketPayload {

    public record CurrencyData(
            String id,
            String name,
            String symbol,
            int decimals,
            long balance,
            double referenceRate,
            boolean exchangeable
    ) {
        public static final StreamCodec<ByteBuf, CurrencyData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public CurrencyData decode(ByteBuf buf) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                String symbol = ByteBufCodecs.STRING_UTF8.decode(buf);
                int decimals = ByteBufCodecs.VAR_INT.decode(buf);
                long balance = ByteBufCodecs.VAR_LONG.decode(buf);
                double referenceRate = ByteBufCodecs.DOUBLE.decode(buf);
                boolean exchangeable = ByteBufCodecs.BOOL.decode(buf);
                return new CurrencyData(id, name, symbol, decimals, balance, referenceRate, exchangeable);
            }

            @Override
            public void encode(ByteBuf buf, CurrencyData data) {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.symbol());
                ByteBufCodecs.VAR_INT.encode(buf, data.decimals());
                ByteBufCodecs.VAR_LONG.encode(buf, data.balance());
                ByteBufCodecs.DOUBLE.encode(buf, data.referenceRate());
                ByteBufCodecs.BOOL.encode(buf, data.exchangeable());
            }
        };
    }

    public static final CustomPacketPayload.Type<ExchangeDataPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "exchange_data"));

    public static final StreamCodec<ByteBuf, ExchangeDataPayload> STREAM_CODEC = StreamCodec.composite(
            CurrencyData.STREAM_CODEC.apply(ByteBufCodecs.list()), ExchangeDataPayload::currencies,
            ByteBufCodecs.DOUBLE, ExchangeDataPayload::feePercent,
            ExchangeDataPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
