package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NPCSkinPayload(int entityId, String skinPlayerName, String linkedAhId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<NPCSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "npc_skin"));

    public static final StreamCodec<ByteBuf, NPCSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NPCSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, NPCSkinPayload::skinPlayerName,
            ByteBufCodecs.STRING_UTF8, NPCSkinPayload::linkedAhId,
            NPCSkinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
