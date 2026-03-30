package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdatePostmanSkinPayload(int entityId, String skinPlayerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UpdatePostmanSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "update_postman_skin"));

    public static final StreamCodec<ByteBuf, UpdatePostmanSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, UpdatePostmanSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, UpdatePostmanSkinPayload::skinPlayerName,
            UpdatePostmanSkinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
