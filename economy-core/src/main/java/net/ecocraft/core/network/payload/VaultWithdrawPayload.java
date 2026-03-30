package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server: withdraw balance as physical money items into inventory.
 * currencyId = currency to withdraw from.
 * amount = amount in base units to withdraw.
 */
public record VaultWithdrawPayload(String currencyId, long amount) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VaultWithdrawPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "vault_withdraw"));

    public static final StreamCodec<ByteBuf, VaultWithdrawPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VaultWithdrawPayload::currencyId,
            ByteBufCodecs.VAR_LONG, VaultWithdrawPayload::amount,
            VaultWithdrawPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
