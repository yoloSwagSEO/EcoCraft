package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: result of a vault deposit/withdraw operation.
 */
public record VaultResultPayload(boolean success, String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VaultResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "vault_result"));

    public static final StreamCodec<ByteBuf, VaultResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, VaultResultPayload::success,
            ByteBufCodecs.STRING_UTF8, VaultResultPayload::message,
            VaultResultPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
