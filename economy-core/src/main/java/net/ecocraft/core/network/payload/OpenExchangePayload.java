package net.ecocraft.core.network.payload;

import io.netty.buffer.ByteBuf;
import net.ecocraft.core.EcoCraftCoreMod;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: tells the client to open the exchange screen.
 * entityId = 0 means opened via block, otherwise the NPC entity id.
 */
public record OpenExchangePayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenExchangePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(EcoCraftCoreMod.MOD_ID, "open_exchange"));

    public static final StreamCodec<ByteBuf, OpenExchangePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenExchangePayload::entityId,
            OpenExchangePayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
