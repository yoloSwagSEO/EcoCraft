package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AHActionResultPayload(boolean success, String message) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AHActionResultPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "action_result"));

    public static final StreamCodec<ByteBuf, AHActionResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, AHActionResultPayload::success,
            ByteBufCodecs.STRING_UTF8, AHActionResultPayload::message,
            AHActionResultPayload::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
