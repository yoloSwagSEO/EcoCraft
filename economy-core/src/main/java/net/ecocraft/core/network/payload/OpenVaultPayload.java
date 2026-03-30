package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: tells the client to open the vault screen.
 */
public record OpenVaultPayload() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenVaultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "open_vault"));

    public static final StreamCodec<ByteBuf, OpenVaultPayload> STREAM_CODEC = StreamCodec.unit(new OpenVaultPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
