package net.ecocraft.ah.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateNPCSkinPayload(int entityId, String skinPlayerName, String linkedAhId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdateNPCSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_ah", "update_npc_skin"));

    public static final StreamCodec<ByteBuf, UpdateNPCSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdateNPCSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, UpdateNPCSkinPayload::skinPlayerName,
            ByteBufCodecs.STRING_UTF8, UpdateNPCSkinPayload::linkedAhId,
            UpdateNPCSkinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
