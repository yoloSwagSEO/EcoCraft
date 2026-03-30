package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> Client: sends all currencies with balances and physical info for the vault screen.
 */
public record VaultDataPayload(List<VaultCurrencyData> currencies) implements CustomPacketPayload {

    public record VaultCurrencyData(
            String id,
            String name,
            String symbol,
            int decimals,
            long balance,
            boolean physical,
            List<VaultSubUnitData> subUnits
    ) {
        public static final StreamCodec<ByteBuf, VaultCurrencyData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public VaultCurrencyData decode(ByteBuf buf) {
                String id = ByteBufCodecs.STRING_UTF8.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                String symbol = ByteBufCodecs.STRING_UTF8.decode(buf);
                int decimals = ByteBufCodecs.VAR_INT.decode(buf);
                long balance = ByteBufCodecs.VAR_LONG.decode(buf);
                boolean physical = ByteBufCodecs.BOOL.decode(buf);
                List<VaultSubUnitData> subUnits = VaultSubUnitData.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
                return new VaultCurrencyData(id, name, symbol, decimals, balance, physical, subUnits);
            }

            @Override
            public void encode(ByteBuf buf, VaultCurrencyData data) {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.id());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.name());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.symbol());
                ByteBufCodecs.VAR_INT.encode(buf, data.decimals());
                ByteBufCodecs.VAR_LONG.encode(buf, data.balance());
                ByteBufCodecs.BOOL.encode(buf, data.physical());
                VaultSubUnitData.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, data.subUnits());
            }
        };
    }

    public record VaultSubUnitData(
            String code,
            String name,
            long multiplier,
            String itemId
    ) {
        public static final StreamCodec<ByteBuf, VaultSubUnitData> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public VaultSubUnitData decode(ByteBuf buf) {
                String code = ByteBufCodecs.STRING_UTF8.decode(buf);
                String name = ByteBufCodecs.STRING_UTF8.decode(buf);
                long multiplier = ByteBufCodecs.VAR_LONG.decode(buf);
                String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
                return new VaultSubUnitData(code, name, multiplier, itemId);
            }

            @Override
            public void encode(ByteBuf buf, VaultSubUnitData data) {
                ByteBufCodecs.STRING_UTF8.encode(buf, data.code());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.name());
                ByteBufCodecs.VAR_LONG.encode(buf, data.multiplier());
                ByteBufCodecs.STRING_UTF8.encode(buf, data.itemId());
            }
        };
    }

    public static final CustomPacketPayload.Type<VaultDataPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "vault_data"));

    public static final StreamCodec<ByteBuf, VaultDataPayload> STREAM_CODEC = StreamCodec.composite(
            VaultCurrencyData.STREAM_CODEC.apply(ByteBufCodecs.list()), VaultDataPayload::currencies,
            VaultDataPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
