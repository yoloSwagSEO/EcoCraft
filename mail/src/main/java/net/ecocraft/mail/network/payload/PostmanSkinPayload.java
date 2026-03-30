package net.ecocraft.mail.network.payload;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PostmanSkinPayload(int entityId, String skinPlayerName) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PostmanSkinPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("ecocraft_mail", "postman_skin"));

    public static final StreamCodec<ByteBuf, PostmanSkinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PostmanSkinPayload::entityId,
            ByteBufCodecs.STRING_UTF8, PostmanSkinPayload::skinPlayerName,
            PostmanSkinPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
