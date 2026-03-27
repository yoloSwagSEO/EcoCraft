package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AHContextPayload(String ahId, String ahName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AHContextPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "ah_context"));

    public static final StreamCodec<ByteBuf, AHContextPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AHContextPayload::ahId,
            ByteBufCodecs.STRING_UTF8, AHContextPayload::ahName,
            AHContextPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
