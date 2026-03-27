package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenAHPayload(int entityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenAHPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "open_ah"));

    public static final StreamCodec<ByteBuf, OpenAHPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenAHPayload::entityId, OpenAHPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
